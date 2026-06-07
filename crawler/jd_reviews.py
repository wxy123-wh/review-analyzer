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
import socket
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
DEFAULT_CATEGORY = "bluetooth-earphone"
DEFAULT_OUTPUT = "crawler/output/raw_reviews.jsonl"
DEFAULT_PROGRESS = "crawler/output/jd_progress.json"
DEFAULT_PROFILE_DIR = "crawler/output/browser-profile/jd"
COMPLIANCE_NOTICE = "合规提示：脚本只监听你正常浏览产生的评论数据；不会绕过登录、验证码或平台风控。出现验证时请人工处理。"
JD_COMMENT_ENDPOINT_HINTS = (
    "comment",
    "getcomment",
    "getfoldcommentlist",
    "pc_club_productpagecomments",
    "client.action",
)


@dataclass
class CrawlResult:
    productUrl: str
    productCode: str
    productName: str
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


def extract_reviews_from_payload(payload: Any, product_code: str, category: str, product_name: str = "") -> list[dict[str, Any]]:
    comments = find_comment_objects(payload)
    return [
        to_review(item, product_code, category, product_name)
        for item in comments
        if normalize_text(first_non_empty(item.get("content"), item.get("commentData"), item.get("commentContent")))
    ]


def to_review(raw: dict[str, Any], product_code: str, category: str, product_name: str = "") -> dict[str, Any]:
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
        "productName": normalize_text(product_name),
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


def load_payloads_from_file(path: Path) -> list[Any]:
    text = path.read_text(encoding="utf-8").strip()
    if not text:
        return []
    if path.suffix.lower() in {".jsonl", ".ndjson"}:
        payloads: list[Any] = []
        for line in text.splitlines():
            line = line.strip()
            if line:
                payloads.append(json.loads(line))
        return payloads
    payload = json.loads(text)
    if isinstance(payload, dict) and isinstance(payload.get("payloads"), list):
        return list(payload["payloads"])
    if isinstance(payload, list):
        return payload
    return [payload]


def packet_url(packet: Any) -> str:
    return normalize_text(getattr(packet, "url", ""))


def looks_like_jd_comment_endpoint(url: str) -> bool:
    text = url.lower()
    return any(hint in text for hint in JD_COMMENT_ENDPOINT_HINTS)


def write_payloads_to_jsonl(
    payloads: Iterable[Any],
    *,
    writer: JsonlReviewWriter,
    product_code: str,
    category: str,
    product_name: str,
) -> tuple[int, int]:
    captured_packets = 0
    new_review_count = 0
    for payload in payloads:
        reviews = extract_reviews_from_payload(payload, product_code, category, product_name)
        if not reviews:
            continue
        new_review_count += writer.write_many(reviews)
        captured_packets += 1
    return captured_packets, new_review_count


def dump_debug_payload(debug_dir: Path | None, *, label: str, url: str, payload: Any) -> None:
    if debug_dir is None:
        return
    debug_dir.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    safe_label = re.sub(r"[^a-zA-Z0-9_.-]+", "-", label).strip("-") or "packet"
    path = debug_dir / f"{stamp}_{safe_label}.json"
    record = {
        "capturedAt": datetime.now(timezone.utc).isoformat(),
        "url": url,
        "payload": payload,
    }
    path.write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8")


def detect_risk_state(page: Any) -> str:
    for text in RISK_TEXTS:
        try:
            if page.ele(f'xpath://*[contains(text(), "{text}")]', timeout=0.2):
                return text
        except Exception:
            continue
    return ""


def wait_for_manual_verification(page: Any, reason: str, wait_seconds: int) -> bool:
    if wait_seconds <= 0:
        return False
    deadline = time.time() + wait_seconds
    print(f"检测到平台验证/风控提示：{reason}。请在当前浏览器窗口中人工完成验证，最多等待 {wait_seconds} 秒。")
    while time.time() < deadline:
        time.sleep(3)
        if not detect_risk_state(page):
            print("人工验证已处理，继续监听评论数据。")
            return True
    return False


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


def safe_filename(value: str) -> str:
    return re.sub(r"[^a-zA-Z0-9_.-]+", "-", value).strip("-") or "product"


def find_browser_path() -> str:
    candidates = [
        r"C:\Program Files\Google\Chrome\Application\chrome.exe",
        r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
        r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
        r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    ]
    for candidate in candidates:
        if Path(candidate).exists():
            return candidate
    return ""


def get_free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


def build_browser_page(*, profile_dir: Path | None = None, browser_path: str = "") -> Any:
    try:
        from DrissionPage import ChromiumOptions, ChromiumPage
    except ImportError as exc:
        raise RuntimeError("缺少 DrissionPage。请先执行：python -m pip install -r crawler/requirements.txt") from exc

    options = ChromiumOptions()
    resolved_browser = browser_path or find_browser_path()
    if resolved_browser:
        options.set_browser_path(resolved_browser)
        print(f"使用浏览器：{resolved_browser}")
    if profile_dir is not None:
        profile_dir.mkdir(parents=True, exist_ok=True)
        options.set_user_data_path(str(profile_dir))
        print(f"使用独立浏览器资料目录：{profile_dir}")
    options.set_local_port(get_free_port())
    return ChromiumPage(options)


def replay_payloads(
    *,
    resolved_url: str,
    product_code: str,
    product_name: str,
    category: str,
    output: Path,
    progress_path: Path,
    progress: dict[str, Any],
    writer: JsonlReviewWriter,
    payloads: Iterable[Any],
    reason: str,
) -> CrawlResult:
    captured_packets, new_review_count = write_payloads_to_jsonl(
        payloads,
        writer=writer,
        product_code=product_code,
        category=category,
        product_name=product_name,
    )
    progress["lastRunMode"] = "demo-replay"
    progress["demoReplayReason"] = reason
    progress["capturedPackets"] = int(progress.get("capturedPackets", 0)) + captured_packets
    save_progress(progress_path, progress)
    message = f"{reason} 已切换为 demo replay：从离线评论包写入 {new_review_count} 条新评论。"
    print(message)
    return CrawlResult(
        productUrl=resolved_url,
        productCode=product_code,
        productName=normalize_text(product_name),
        category=category,
        outputPath=str(output),
        progressPath=str(progress_path),
        status="DEMO_REPLAYED",
        capturedPackets=captured_packets,
        newReviewCount=new_review_count,
        message=message,
    )


def collect_jd_reviews(
    *,
    product_url: str | None = None,
    product_id: str | None = None,
    product_code: str,
    category: str,
    output: Path,
    progress_path: Path,
    product_name: str = "",
    max_packets: int = 20,
    wait_seconds: int = 120,
    verification_wait_seconds: int = 180,
    profile_dir: Path | None = Path(DEFAULT_PROFILE_DIR),
    browser_path: str = "",
    debug_dir: Path | None = None,
    dry_run_payloads: Iterable[Any] | None = None,
    demo_replay_payloads: Iterable[Any] | None = None,
    demo_replay_on_empty: bool = False,
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
        captured_packets, new_review_count = write_payloads_to_jsonl(
            dry_run_payloads,
            writer=writer,
            product_code=product_code,
            category=category,
            product_name=product_name,
        )
        progress["capturedPackets"] = int(progress.get("capturedPackets", 0)) + captured_packets
        progress["lastRunMode"] = "dry-run"
        save_progress(progress_path, progress)
        return CrawlResult(
            productUrl=resolved_url,
            productCode=product_code,
            productName=normalize_text(product_name),
            category=category,
            outputPath=str(output),
            progressPath=str(progress_path),
            status="SUCCEEDED",
            capturedPackets=captured_packets,
            newReviewCount=new_review_count,
            message="dry-run 解析完成，未打开浏览器。",
        )

    try:
        page = build_browser_page(profile_dir=profile_dir, browser_path=browser_path)
    except RuntimeError as exc:
        if demo_replay_on_empty and demo_replay_payloads is not None:
            return replay_payloads(
                resolved_url=resolved_url,
                product_code=product_code,
                product_name=product_name,
                category=category,
                output=output,
                progress_path=progress_path,
                progress=progress,
                writer=writer,
                payloads=demo_replay_payloads,
                reason=f"现场浏览器未能启动：{exc}",
            )
        raise

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
            progress["lastRiskReason"] = reason
            save_progress(progress_path, progress)
            if wait_for_manual_verification(page, reason, verification_wait_seconds):
                deadline = time.time() + wait_seconds
                continue
            message = f"检测到平台验证/风控提示：{reason}。人工处理等待超时，请处理后重新运行脚本。"
            print(message)
            if demo_replay_on_empty and demo_replay_payloads is not None and new_review_count == 0:
                return replay_payloads(
                    resolved_url=resolved_url,
                    product_code=product_code,
                    product_name=product_name,
                    category=category,
                    output=output,
                    progress_path=progress_path,
                    progress=progress,
                    writer=writer,
                    payloads=demo_replay_payloads,
                    reason=message,
                )
            return CrawlResult(
                productUrl=resolved_url,
                productCode=product_code,
                productName=normalize_text(product_name),
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
        url = packet_url(packet)
        reviews = extract_reviews_from_payload(payload, product_code, category, product_name)
        if not reviews:
            continue

        if not looks_like_jd_comment_endpoint(url):
            print(f"捕获到可解析评论包，但 URL 不像标准评论接口，已保守写入并记录：{url}")
        dump_debug_payload(debug_dir, label=f"packet_{captured_packets + 1}", url=url, payload=payload)
        new_count = writer.write_many(reviews)
        new_review_count += new_count
        captured_packets += 1
        progress["lastPacketUrl"] = url
        progress["capturedPackets"] = int(progress.get("capturedPackets", 0)) + 1
        progress["lastRunMode"] = "live"
        save_progress(progress_path, progress)
        print(f"捕获评论包 {captured_packets}/{max_packets}，新增 {new_count} 条，输出：{output}")
        sleep_fn(4, 8)

    if demo_replay_on_empty and demo_replay_payloads is not None and new_review_count == 0:
        return replay_payloads(
            resolved_url=resolved_url,
            product_code=product_code,
            product_name=product_name,
            category=category,
            output=output,
            progress_path=progress_path,
            progress=progress,
            writer=writer,
            payloads=demo_replay_payloads,
            reason="现场监听时间内没有捕获到可用评论包。",
        )

    message = f"采集结束。当前输出文件：{output}"
    print(message)
    return CrawlResult(
        productUrl=resolved_url,
        productCode=product_code,
        productName=normalize_text(product_name),
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
    parser.add_argument("--interactive", action="store_true", help="Use Chinese step-by-step CLI prompts")
    parser.add_argument("--product-url", default="", help="JD product URL, for example https://item.jd.com/100127936932.html")
    parser.add_argument("--product-id", default="", help="JD numeric product id, for example 100127936932")
    parser.add_argument("--product-code", default="", help="Internal productCode used by backend")
    parser.add_argument("--product-name", default="", help="Human-readable productName written into every JSONL review")
    parser.add_argument("--category", default=DEFAULT_CATEGORY, help="Product category for later LLM prompts")
    parser.add_argument("--output", default=DEFAULT_OUTPUT)
    parser.add_argument("--progress", default=DEFAULT_PROGRESS)
    parser.add_argument("--profile-dir", default=DEFAULT_PROFILE_DIR, help="Independent browser profile dir used to keep normal login state")
    parser.add_argument("--browser-path", default="", help="Optional Chrome/Edge executable path")
    parser.add_argument("--debug-dir", default="", help="Optional dir for accepted packet debug JSON files")
    parser.add_argument("--max-packets", type=int, default=20)
    parser.add_argument("--wait-seconds", type=int, default=120)
    parser.add_argument("--verification-wait-seconds", type=int, default=180, help="Seconds to wait for manual captcha/risk verification")
    parser.add_argument("--dry-run-packet", default="", help="Optional JSON file with one captured packet payload for parser testing")
    parser.add_argument("--demo-replay-packet", default="", help="Optional JSON/JSONL packet file for honest offline demo replay")
    parser.add_argument("--demo-replay-on-empty", action="store_true", help="Replay demo packet if live capture gets no reviews or needs verification")
    return parser


def prompt_text(
    label: str,
    *,
    default: str = "",
    required: bool = False,
    input_fn: Callable[[str], str] = input,
    print_fn: Callable[[str], None] = print,
) -> str:
    while True:
        default_hint = f"（默认：{default}）" if default else ""
        value = input_fn(f"{label}{default_hint}: ").strip()
        if value:
            return value
        if default:
            return default
        if not required:
            return ""
        print_fn(f"{label}不能为空，请重新输入。")


def prompt_int(
    label: str,
    *,
    default: int,
    min_value: int = 1,
    input_fn: Callable[[str], str] = input,
    print_fn: Callable[[str], None] = print,
) -> int:
    while True:
        value = input_fn(f"{label}（默认：{default}）: ").strip()
        if not value:
            return default
        try:
            parsed = int(value)
        except ValueError:
            print_fn(f"{label}必须是整数，请重新输入。")
            continue
        if parsed < min_value:
            print_fn(f"{label}不能小于 {min_value}，请重新输入。")
            continue
        return parsed


def split_product_locator(locator: str) -> tuple[str | None, str | None]:
    text = normalize_text(locator)
    if re.fullmatch(r"\d{6,}", text):
        return None, text
    return text, None


def interactive_output_default(product_code: str) -> str:
    return f"crawler/output/raw_reviews_{safe_filename(product_code)}.jsonl"


def interactive_progress_default(product_code: str) -> str:
    return f"crawler/output/progress/jd_{safe_filename(product_code)}.json"


def prompt_interactive_args(
    args: argparse.Namespace,
    *,
    input_fn: Callable[[str], str] = input,
    print_fn: Callable[[str], None] = print,
) -> argparse.Namespace:
    print_fn("京东评论采集 CLI 向导")
    print_fn(COMPLIANCE_NOTICE)
    print_fn("请按提示输入信息；有默认值时直接回车会使用默认值。")

    locator_default = args.product_url or args.product_id
    while True:
        locator = prompt_text(
            "1. 京东商品链接或商品 ID（例如 https://item.jd.com/100127936932.html）",
            default=locator_default,
            required=True,
            input_fn=input_fn,
            print_fn=print_fn,
        )
        product_url, product_id = split_product_locator(locator)
        try:
            resolved_url = jd_product_url(product_url, product_id)
            break
        except ValueError as exc:
            print_fn(str(exc))
            locator_default = ""

    derived_product_id = jd_product_id_from_url(resolved_url)
    product_code_default = args.product_code or f"jd-{derived_product_id}"
    product_code = prompt_text(
        "2. 内部 productCode（系统数据库识别商品用，例如 jd-100127936932）",
        default=product_code_default,
        required=True,
        input_fn=input_fn,
        print_fn=print_fn,
    )
    product_name = prompt_text(
        "3. 商品名称 productName（会写入每条 JSONL 评论）",
        default=args.product_name,
        required=True,
        input_fn=input_fn,
        print_fn=print_fn,
    )
    category = prompt_text(
        "4. 品类 category（给后续 LLM/标签体系使用，例如 bluetooth-earphone）",
        default=args.category or DEFAULT_CATEGORY,
        required=True,
        input_fn=input_fn,
        print_fn=print_fn,
    )
    max_packets = prompt_int(
        "5. 最大抓包数 maxPackets",
        default=args.max_packets,
        min_value=1,
        input_fn=input_fn,
        print_fn=print_fn,
    )

    output_default = args.output if args.output != DEFAULT_OUTPUT else interactive_output_default(product_code)
    progress_default = args.progress if args.progress != DEFAULT_PROGRESS else interactive_progress_default(product_code)
    output = prompt_text(
        "6. 输出 JSONL 路径 output",
        default=output_default,
        required=True,
        input_fn=input_fn,
        print_fn=print_fn,
    )
    progress = prompt_text(
        "7. 进度文件路径 progress",
        default=progress_default,
        required=True,
        input_fn=input_fn,
        print_fn=print_fn,
    )
    wait_seconds = prompt_int(
        "8. 等待评论包总秒数 waitSeconds",
        default=args.wait_seconds,
        min_value=1,
        input_fn=input_fn,
        print_fn=print_fn,
    )
    verification_wait_seconds = prompt_int(
        "9. 出现登录/验证/风控时等待人工处理秒数 verificationWaitSeconds",
        default=args.verification_wait_seconds,
        min_value=0,
        input_fn=input_fn,
        print_fn=print_fn,
    )

    args.product_url = resolved_url
    args.product_id = ""
    args.product_code = product_code
    args.product_name = product_name
    args.category = category
    args.max_packets = max_packets
    args.output = output
    args.progress = progress
    args.profile_dir = args.profile_dir or DEFAULT_PROFILE_DIR
    args.wait_seconds = wait_seconds
    args.verification_wait_seconds = verification_wait_seconds
    return args


def validate_args(args: argparse.Namespace) -> None:
    missing: list[str] = []
    if not args.product_url and not args.product_id:
        missing.append("--product-url 或 --product-id")
    if not args.product_code:
        missing.append("--product-code")
    if not args.category:
        missing.append("--category")
    if missing:
        joined = "、".join(missing)
        raise ValueError(f"缺少必要参数：{joined}。可以直接运行 python crawler/jd_reviews.py 进入中文交互式 CLI，或按旧方式补齐参数。")


def parse_args(
    argv: list[str] | None = None,
    *,
    input_fn: Callable[[str], str] = input,
    print_fn: Callable[[str], None] = print,
) -> argparse.Namespace:
    actual_argv = sys.argv[1:] if argv is None else list(argv)
    args = build_parser().parse_args(actual_argv)
    if args.interactive or not actual_argv:
        args = prompt_interactive_args(args, input_fn=input_fn, print_fn=print_fn)
    validate_args(args)
    return args


def main(argv: list[str] | None = None) -> int:
    try:
        args = parse_args(argv)
    except ValueError as exc:
        print(str(exc))
        return 2

    try:
        dry_run_payloads = None
        if args.dry_run_packet:
            dry_run_payloads = load_payloads_from_file(Path(args.dry_run_packet))
        demo_replay_payloads = None
        if args.demo_replay_packet:
            demo_replay_payloads = load_payloads_from_file(Path(args.demo_replay_packet))
        result = collect_jd_reviews(
            product_url=args.product_url or None,
            product_id=args.product_id or None,
            product_code=args.product_code,
            product_name=args.product_name,
            category=args.category,
            output=Path(args.output),
            progress_path=Path(args.progress),
            profile_dir=Path(args.profile_dir) if args.profile_dir else None,
            browser_path=args.browser_path,
            debug_dir=Path(args.debug_dir) if args.debug_dir else None,
            max_packets=args.max_packets,
            wait_seconds=args.wait_seconds,
            verification_wait_seconds=args.verification_wait_seconds,
            dry_run_payloads=dry_run_payloads,
            demo_replay_payloads=demo_replay_payloads,
            demo_replay_on_empty=args.demo_replay_on_empty,
        )
    except (RuntimeError, ValueError) as exc:
        print(str(exc))
        return 2

    print(json.dumps(result.to_dict(), ensure_ascii=False, indent=2))
    return 3 if result.status == "NEEDS_HUMAN_VERIFICATION" else 0


if __name__ == "__main__":
    sys.exit(main())
