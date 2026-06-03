# -*- coding: utf-8 -*-
"""
Compliant JD review collector.

This script borrows the safe ideas from the downloaded reference project:
browser-assisted collection, network packet listening, JSONL output, dedupe,
progress checkpoints, conservative pacing, and human handling for risk checks.
It does not bypass login, captcha, or platform risk controls.
"""

from __future__ import annotations

import argparse
import json
import random
import re
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from common.progress import load_progress, save_progress
from common.writer import JsonlReviewWriter


RISK_TEXTS = ["访问过于频繁", "操作过于频繁", "安全验证", "请完成验证", "验证码", "系统繁忙"]


def normalize_text(value: Any) -> str:
    if value is None:
        return ""
    text = str(value).replace("\r", " ").replace("\n", " ")
    return re.sub(r"\s+", " ", text).strip()


def first_non_empty(*values: Any) -> Any:
    for value in values:
        if value is None:
            continue
        if isinstance(value, str) and not value.strip():
            continue
        return value
    return None


def walk(value: Any):
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from walk(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk(child)


def find_comment_objects(payload: Any) -> list[dict[str, Any]]:
    candidates: list[dict[str, Any]] = []
    for obj in walk(payload):
        content = first_non_empty(
            obj.get("content"),
            obj.get("commentData"),
            obj.get("commentContent"),
            obj.get("comment"),
        )
        comment_id = first_non_empty(obj.get("id"), obj.get("commentId"), obj.get("guid"))
        if content and (comment_id or obj.get("creationTime") or obj.get("score")):
            candidates.append(obj)
    return candidates


def to_review(raw: dict[str, Any], product_code: str, category: str) -> dict[str, Any]:
    rating = first_non_empty(raw.get("score"), raw.get("commentScore"), raw.get("star"))
    try:
        rating_value = float(rating) if rating is not None else None
    except (TypeError, ValueError):
        rating_value = None

    review_time = first_non_empty(raw.get("creationTime"), raw.get("referenceTime"), raw.get("time"))
    if not review_time:
        review_time = datetime.now(timezone.utc).isoformat()

    source_review_id = normalize_text(first_non_empty(raw.get("id"), raw.get("commentId"), raw.get("guid")))
    return {
        "source": "jd",
        "sourceReviewId": source_review_id,
        "productCode": product_code,
        "category": category,
        "rating": rating_value,
        "content": normalize_text(first_non_empty(raw.get("content"), raw.get("commentData"), raw.get("commentContent"))),
        "reviewTime": normalize_text(review_time),
        "skuInfo": normalize_text(first_non_empty(raw.get("referenceName"), raw.get("productColor"), raw.get("sku"))),
        "anonymizedAuthorId": normalize_text(first_non_empty(raw.get("nickname"), raw.get("userClientShow"))),
    }


def parse_packet_body(packet: Any) -> Any:
    body = getattr(getattr(packet, "response", None), "body", None)
    if isinstance(body, (dict, list)):
        return body
    if isinstance(body, bytes):
        body = body.decode("utf-8", errors="ignore")
    if not body:
        return None
    text = str(body).strip()
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        match = re.search(r"(\{.*\}|\[.*\])", text, re.S)
        if not match:
            return None
        try:
            return json.loads(match.group(1))
        except json.JSONDecodeError:
            return None


def detect_risk_state(page: Any) -> str:
    for text in RISK_TEXTS:
        try:
            if page.ele(f'xpath://*[contains(text(), "{text}")]', timeout=0.2):
                return text
        except Exception:
            continue
    return ""


def sleep_safely(min_seconds: float, max_seconds: float) -> None:
    seconds = random.uniform(min_seconds, max_seconds)
    print(f"等待 {seconds:.1f} 秒，降低访问频率...")
    time.sleep(seconds)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Collect JD reviews into crawler/output/raw_reviews.jsonl")
    parser.add_argument("--product-id", required=True, help="JD numeric product id, for example 100127936932")
    parser.add_argument("--product-code", required=True, help="Internal productCode used by backend")
    parser.add_argument("--category", default="bluetooth-earphone", help="Product category for later LLM prompts")
    parser.add_argument("--output", default="crawler/output/raw_reviews.jsonl")
    parser.add_argument("--progress", default="crawler/output/jd_progress.json")
    parser.add_argument("--max-packets", type=int, default=20)
    parser.add_argument("--wait-seconds", type=int, default=120)
    return parser


def main() -> int:
    args = build_parser().parse_args()

    try:
        from DrissionPage import ChromiumPage
    except ImportError:
        print("缺少 DrissionPage。请先执行：python -m pip install -r crawler/requirements.txt")
        return 2

    output = Path(args.output)
    progress_path = Path(args.progress)
    progress = load_progress(progress_path)
    writer = JsonlReviewWriter(output)

    page = ChromiumPage()
    page.listen.start()
    url = f"https://item.jd.com/{args.product_id}.html"
    print(f"打开京东商品页：{url}")
    page.get(url)
    print("请在浏览器中正常登录并打开评论区域；如出现验证，请人工处理。脚本不会绕过验证。")

    captured_packets = 0
    deadline = time.time() + args.wait_seconds
    while time.time() < deadline and captured_packets < args.max_packets:
        reason = detect_risk_state(page)
        if reason:
            print(f"检测到平台验证/风控提示：{reason}。请人工处理后重新运行脚本。")
            save_progress(progress_path, progress)
            return 3

        packet = page.listen.wait(timeout=3)
        if not packet:
            continue
        payload = parse_packet_body(packet)
        comments = find_comment_objects(payload)
        if not comments:
            continue

        reviews = [
            to_review(item, args.product_code, args.category)
            for item in comments
            if normalize_text(first_non_empty(item.get("content"), item.get("commentData"), item.get("commentContent")))
        ]
        new_count = writer.write_many(reviews)
        captured_packets += 1
        progress["lastPacketUrl"] = str(getattr(packet, "url", ""))
        progress["capturedPackets"] = int(progress.get("capturedPackets", 0)) + 1
        save_progress(progress_path, progress)
        print(f"捕获评论包 {captured_packets}/{args.max_packets}，新增 {new_count} 条，输出：{output}")
        sleep_safely(4, 8)

    print(f"采集结束。当前输出文件：{output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
