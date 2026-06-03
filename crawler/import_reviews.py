# -*- coding: utf-8 -*-

from __future__ import annotations

import argparse
import json
from pathlib import Path

import requests


def read_jsonl(path: Path) -> list[dict]:
    reviews: list[dict] = []
    with path.open("r", encoding="utf-8") as file:
        for line in file:
            line = line.strip()
            if line:
                reviews.append(json.loads(line))
    return reviews


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
    reviews = read_jsonl(Path(args.input))
    payload = {
        "provider": args.provider,
        "platform": args.platform,
        "productCode": args.product_code,
        "reviews": reviews,
    }
    if args.cleaning_summary:
        summary_path = Path(args.cleaning_summary)
        if summary_path.exists():
            payload["cleaningSummary"] = json.loads(summary_path.read_text(encoding="utf-8"))
    url = f"{args.backend.rstrip('/')}/api/v1/reviews/import"
    response = requests.post(url, json=payload, timeout=30)
    print(response.status_code)
    print(json.dumps(response.json(), ensure_ascii=False, indent=2))
    response.raise_for_status()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
