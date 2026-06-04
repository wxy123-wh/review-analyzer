# -*- coding: utf-8 -*-
"""Conservative JSONL review cleaning.

The cleaner preserves valid user feedback distribution. It removes invalid JSON,
empty content, platform placeholder comments, and exact duplicates, while
normalizing technical noise such as HTML tags and whitespace.
"""

from __future__ import annotations

import argparse
import hashlib
import html
import json
import re
from pathlib import Path
from typing import Any


HTML_TAG_RE = re.compile(r"<[^>]+>")
WHITESPACE_RE = re.compile(r"\s+")
ZERO_WIDTH_RE = re.compile(r"[\u200b\u200c\u200d\ufeff]")
PLACEHOLDER_COMMENT_RE = re.compile(r"(?:此|该)?用户(?:未及时|没有|未|尚未|未按时)?填写评价内容")
LEADING_SEPARATOR_RE = re.compile(r"^[\s|｜:：,，;；。.!！?？\-—_]+")
TRAILING_SEPARATOR_RE = re.compile(r"[\s|｜:：,，;；\-—_]+$")


def clean_content(value: Any) -> tuple[str, bool, bool]:
    raw = "" if value is None else str(value)
    unescaped = html.unescape(raw)
    without_tags = HTML_TAG_RE.sub(" ", unescaped)
    without_zero_width = ZERO_WIDTH_RE.sub("", without_tags)
    normalized = WHITESPACE_RE.sub(" ", without_zero_width.replace("\r", " ").replace("\n", " ")).strip()
    without_placeholder = PLACEHOLDER_COMMENT_RE.sub(" ", normalized)
    without_placeholder = LEADING_SEPARATOR_RE.sub("", without_placeholder)
    without_placeholder = TRAILING_SEPARATOR_RE.sub("", without_placeholder)
    normalized = WHITESPACE_RE.sub(" ", without_placeholder).strip()
    html_cleaned = bool(HTML_TAG_RE.search(raw)) or unescaped != raw
    placeholder_cleaned = bool(PLACEHOLDER_COMMENT_RE.search(raw)) or bool(PLACEHOLDER_COMMENT_RE.search(unescaped))
    return normalized, html_cleaned, placeholder_cleaned


def stable_hash(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def text(value: Any) -> str:
    return "" if value is None else str(value).strip()


def review_key(review: dict[str, Any]) -> str:
    source = text(review.get("source"))
    source_review_id = text(review.get("sourceReviewId"))
    if source_review_id:
        return f"id:{source}|{source_review_id}"
    product_code = text(review.get("productCode"))
    content = text(review.get("content"))
    review_time = text(review.get("reviewTime"))
    return "hash:" + stable_hash(f"{product_code}|{content}|{review_time}")


def removed_record(line_number: int, reason: str, raw: Any) -> dict[str, Any]:
    return {
        "lineNumber": line_number,
        "removeReason": reason,
        "raw": raw,
    }


def clean_jsonl(input_path: Path, output_path: Path, removed_output_path: Path, summary_path: Path) -> dict[str, int]:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    removed_output_path.parent.mkdir(parents=True, exist_ok=True)
    summary_path.parent.mkdir(parents=True, exist_ok=True)

    seen: set[str] = set()
    summary = {
        "rawCount": 0,
        "cleanedCount": 0,
        "removedCount": 0,
        "htmlCleanedCount": 0,
        "exactDuplicateCount": 0,
        "emptyContentCount": 0,
        "invalidJsonCount": 0,
        "placeholderContentCount": 0,
    }

    with input_path.open("r", encoding="utf-8") as source, output_path.open("w", encoding="utf-8") as cleaned, removed_output_path.open("w", encoding="utf-8") as removed:
        for line_number, line in enumerate(source, start=1):
            raw_line = line.rstrip("\n")
            if not raw_line.strip():
                summary["rawCount"] += 1
                summary["removedCount"] += 1
                summary["emptyContentCount"] += 1
                removed.write(json.dumps(removed_record(line_number, "empty_content", raw_line), ensure_ascii=False) + "\n")
                continue

            summary["rawCount"] += 1
            try:
                review = json.loads(raw_line)
            except json.JSONDecodeError:
                summary["removedCount"] += 1
                summary["invalidJsonCount"] += 1
                removed.write(json.dumps(removed_record(line_number, "invalid_json", raw_line), ensure_ascii=False) + "\n")
                continue

            if not isinstance(review, dict):
                summary["removedCount"] += 1
                summary["invalidJsonCount"] += 1
                removed.write(json.dumps(removed_record(line_number, "invalid_json", review), ensure_ascii=False) + "\n")
                continue

            cleaned_content, html_cleaned, placeholder_cleaned = clean_content(review.get("content"))
            if placeholder_cleaned:
                summary["placeholderContentCount"] += 1
            if not cleaned_content:
                summary["removedCount"] += 1
                if placeholder_cleaned:
                    removed.write(json.dumps(removed_record(line_number, "placeholder_content", review), ensure_ascii=False) + "\n")
                else:
                    summary["emptyContentCount"] += 1
                    removed.write(json.dumps(removed_record(line_number, "empty_content", review), ensure_ascii=False) + "\n")
                continue

            cleaned_review = dict(review)
            cleaned_review["content"] = cleaned_content
            key = review_key(cleaned_review)
            if key in seen:
                summary["removedCount"] += 1
                summary["exactDuplicateCount"] += 1
                removed.write(json.dumps(removed_record(line_number, "exact_duplicate", cleaned_review), ensure_ascii=False) + "\n")
                continue

            seen.add(key)
            if html_cleaned:
                summary["htmlCleanedCount"] += 1
            summary["cleanedCount"] += 1
            cleaned.write(json.dumps(cleaned_review, ensure_ascii=False) + "\n")

    summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return summary


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Conservatively clean raw review JSONL without changing VOC distribution")
    parser.add_argument("--input", default="crawler/output/raw_reviews.jsonl")
    parser.add_argument("--output", default="pipeline/output/cleaned_reviews.jsonl")
    parser.add_argument("--removed-output", default="pipeline/output/removed_reviews.jsonl")
    parser.add_argument("--summary-output", default="pipeline/output/cleaning_summary.json")
    return parser


def main() -> int:
    args = build_parser().parse_args()
    summary = clean_jsonl(
        Path(args.input),
        Path(args.output),
        Path(args.removed_output),
        Path(args.summary_output),
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
