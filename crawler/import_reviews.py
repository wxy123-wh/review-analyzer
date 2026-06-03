# -*- coding: utf-8 -*-

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import requests


def read_jsonl(path: Path) -> list[dict]:
    reviews: list[dict] = []
    with path.open("r", encoding="utf-8") as file:
        for line in file:
            line = line.strip()
            if line:
                reviews.append(json.loads(line))
    return reviews


def import_reviews_jsonl(
    *,
    input_path: Path,
    backend: str,
    product_code: str,
    provider: str = "local-jsonl",
    platform: str = "jd",
    cleaning_summary_path: Path | None = None,
    timeout_seconds: int = 30,
) -> dict[str, Any]:
    reviews = read_jsonl(input_path)
    payload: dict[str, Any] = {
        "provider": provider,
        "platform": platform,
        "productCode": product_code,
        "reviews": reviews,
    }
    if cleaning_summary_path and cleaning_summary_path.exists():
        payload["cleaningSummary"] = json.loads(cleaning_summary_path.read_text(encoding="utf-8"))
    url = f"{backend.rstrip('/')}/api/v1/reviews/import"
    response = requests.post(url, json=payload, timeout=timeout_seconds)
    response.raise_for_status()
    return response.json()


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Import raw_reviews.jsonl into review-analyzer backend")
    parser.add_argument("--input", default="crawler/output/raw_reviews.jsonl")
    parser.add_argument("--cleaning-summary", default="", help="Optional cleaning_summary.json produced by pipeline/clean_reviews.py")
    parser.add_argument("--backend", default="http://localhost:8080")
    parser.add_argument("--provider", default="local-jsonl")
    parser.add_argument("--platform", default="jd")
    parser.add_argument("--product-code", required=True)
    return parser


def main() -> int:
    args = build_parser().parse_args()
    result = import_reviews_jsonl(
        input_path=Path(args.input),
        backend=args.backend,
        product_code=args.product_code,
        provider=args.provider,
        platform=args.platform,
        cleaning_summary_path=Path(args.cleaning_summary) if args.cleaning_summary else None,
    )
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
