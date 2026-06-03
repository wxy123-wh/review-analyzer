# -*- coding: utf-8 -*-

from __future__ import annotations

import hashlib
import json
from typing import Any


def stable_hash(value: Any) -> str:
    text = json.dumps(value, ensure_ascii=False, sort_keys=True, default=str)
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def review_key(review: dict[str, Any]) -> str:
    source = str(review.get("source") or "").strip()
    source_id = str(review.get("sourceReviewId") or "").strip()
    if source_id:
        return f"{source}|{source_id}"
    return stable_hash(
        {
            "productCode": review.get("productCode"),
            "content": review.get("content"),
            "reviewTime": review.get("reviewTime"),
            "rating": review.get("rating"),
        }
    )
