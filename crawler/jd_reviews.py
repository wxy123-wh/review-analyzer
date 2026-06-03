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
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Iterable

try:
    from .common.progress import load_progress, save_progress
    from .common.writer import JsonlReviewWriter
except ImportError:
    from common.progress import load_progress, save_progress
    from common.writer import JsonlReviewWriter


RISK_TEXTS = ["访问过于频繁", "操作过于频繁", "安全验证", "请完成验证", "验证码", "系统繁忙"]


@dataclass
class CrawlResult:
    productUrl: str
    productCode: str
    category: str
    outputPath: str
    progressPath: str
    status: str
    capturedPackets: int
    newReviewCount: int
    message: str

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


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


def extract_reviews_from_payload(payload: Any, product_code: str, category: str) -> list[dict[str, Any]]:
    comments = find_comment_objects(payload)
    return [
        to_review(item, product_code, category)
        for item in comments
        if normalize_text(first_non_empty(item.get("content"), item.get("commentData"), item.get("commentContent")))
    ]


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


def jd_product_id_from_url(product_url: str) -> str:
    match = re.search(r"item\.jd\.com/(\d+)\.html", product_url)
    if match:
        return match.group(1)
    match = re.search(r"(^|[^\d])(\d{6,})([^\d]|$)", product_url)
    if match:
        return match.group(2)
    raise ValueError("无法从 productUrl 中识别京东商品 ID，请传入类似 https://item.jd.com/100127936932.html 的链接")


def jd_product_url(product_url: str | None = None, product_id: str | None = None) -> str:
    if product_url:
        product_id = jd_product_id_from_url(product_url)
    if not product_id:
        raise ValueError("必须提供 productUrl 或 productId")
    return f"https://item.jd.com/{product_id}.html"


def collect_jd_reviews(
    *,
    product_url: str | None = None,
    product_id: str | None = None,
    product_code: str,
    category: str,
    output: Path,
    progress_path: Path,
    max_packets: int = 20,
    wait_seconds: int = 120,
    dry_run_payloads: Iterable[Any] | None = None,
    sleep_fn: Callable[[float, float], None] = sleep_safely,
) -> CrawlResult:
    """Collect JD reviews into JSONL.

    The live path only listens to packets received by a user-controlled browser.
    It never bypasses login, captcha, or platform risk controls.
    """

    resolved_url = jd_product_url(product_url, product_id)
    progress = load_progress(progress_path)
    writer = JsonlReviewWriter(output)

    if dry_run_payloads is not None:
        captured_packets = 0
        new_review_count = 0
        for payload in dry_run_payloads:
            reviews = extract_reviews_from_payload(payload, product_code, category)
            if not reviews:
                continue
            new_review_count += writer.write_many(reviews)
            captured_packets += 1
        progress["capturedPackets"] = int(progress.get("capturedPackets", 0)) + captured_packets
        progress["lastRunMode"] = "dry-run"
        save_progress(progress_path, progress)
        return CrawlResult(
            productUrl=resolved_url,
            productCode=product_code,
            category=category,
            outputPath=str(output),
            progressPath=str(progress_path),
            status="SUCCEEDED",
            capturedPackets=captured_packets,
            newReviewCount=new_review_count,
            message="dry-run 解析完成，未打开浏览器。",
        )

    try:
        from DrissionPage import ChromiumPage
    except ImportError as exc:
        raise RuntimeError("缺少 DrissionPage。请先执行：python -m pip install -r crawler/requirements.txt") from exc

    page = ChromiumPage()
    page.listen.start()
    print(f"打开京东商品页：{resolved_url}")
    page.get(resolved_url)
    print("请在浏览器中正常登录并打开评论区域；如出现验证，请人工处理。脚本不会绕过验证。")

    captured_packets = 0
    new_review_count = 0
    deadline = time.time() + wait_seconds
    while time.time() < deadline and captured_packets < max_packets:
        reason = detect_risk_state(page)
        if reason:
            message = f"检测到平台验证/风控提示：{reason}。请人工处理后重新运行脚本。"
            print(message)
            progress["lastRiskReason"] = reason
            save_progress(progress_path, progress)
            return CrawlResult(
                productUrl=resolved_url,
                productCode=product_code,
                category=category,
                outputPath=str(output),
                progressPath=str(progress_path),
                status="NEEDS_HUMAN_VERIFICATION",
                capturedPackets=captured_packets,
                newReviewCount=new_review_count,
                message=message,
            )

        packet = page.listen.wait(timeout=3)
        if not packet:
            continue
        payload = parse_packet_body(packet)
        reviews = extract_reviews_from_payload(payload, product_code, category)
        if not reviews:
            continue

        new_count = writer.write_many(reviews)
        new_review_count += new_count
        captured_packets += 1
        progress["lastPacketUrl"] = str(getattr(packet, "url", ""))
        progress["capturedPackets"] = int(progress.get("capturedPackets", 0)) + 1
        save_progress(progress_path, progress)
        print(f"捕获评论包 {captured_packets}/{max_packets}，新增 {new_count} 条，输出：{output}")
        sleep_fn(4, 8)

    message = f"采集结束。当前输出文件：{output}"
    print(message)
    return CrawlResult(
        productUrl=resolved_url,
        productCode=product_code,
        category=category,
        outputPath=str(output),
        progressPath=str(progress_path),
        status="SUCCEEDED",
        capturedPackets=captured_packets,
        newReviewCount=new_review_count,
        message=message,
    )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Collect JD reviews into crawler/output/raw_reviews.jsonl")
    parser.add_argument("--product-url", default="", help="JD product URL, for example https://item.jd.com/100127936932.html")
    parser.add_argument("--product-id", default="", help="JD numeric product id, for example 100127936932")
    parser.add_argument("--product-code", required=True, help="Internal productCode used by backend")
    parser.add_argument("--category", default="bluetooth-earphone", help="Product category for later LLM prompts")
    parser.add_argument("--output", default="crawler/output/raw_reviews.jsonl")
    parser.add_argument("--progress", default="crawler/output/jd_progress.json")
    parser.add_argument("--max-packets", type=int, default=20)
    parser.add_argument("--wait-seconds", type=int, default=120)
    parser.add_argument("--dry-run-packet", default="", help="Optional JSON file with one captured packet payload for parser testing")
    return parser


def main() -> int:
    args = build_parser().parse_args()
    try:
        dry_run_payloads = None
        if args.dry_run_packet:
            dry_run_payloads = [json.loads(Path(args.dry_run_packet).read_text(encoding="utf-8"))]
        result = collect_jd_reviews(
            product_url=args.product_url or None,
            product_id=args.product_id or None,
            product_code=args.product_code,
            category=args.category,
            output=Path(args.output),
            progress_path=Path(args.progress),
            max_packets=args.max_packets,
            wait_seconds=args.wait_seconds,
            dry_run_payloads=dry_run_payloads,
        )
    except (RuntimeError, ValueError) as exc:
        print(str(exc))
        return 2

    print(json.dumps(result.to_dict(), ensure_ascii=False, indent=2))
    return 3 if result.status == "NEEDS_HUMAN_VERIFICATION" else 0


if __name__ == "__main__":
    sys.exit(main())
