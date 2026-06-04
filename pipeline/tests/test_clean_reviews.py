# -*- coding: utf-8 -*-

from __future__ import annotations

import json
from pathlib import Path

from pipeline.clean_reviews import clean_jsonl


def read_jsonl(path: Path) -> list[dict]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def test_clean_jsonl_should_preserve_short_and_default_positive_reviews(tmp_path: Path) -> None:
    raw = tmp_path / "raw.jsonl"
    cleaned = tmp_path / "cleaned.jsonl"
    removed = tmp_path / "removed.jsonl"
    summary = tmp_path / "summary.json"
    raw.write_text(
        "\n".join(
            [
                json.dumps({"source": "jd", "sourceReviewId": "1", "productCode": "p1", "content": "好评"}, ensure_ascii=False),
                json.dumps({"source": "jd", "sourceReviewId": "2", "productCode": "p1", "content": "可以"}, ensure_ascii=False),
            ]
        )
        + "\n",
        encoding="utf-8",
    )

    result = clean_jsonl(raw, cleaned, removed, summary)

    assert result["rawCount"] == 2
    assert result["cleanedCount"] == 2
    assert result["removedCount"] == 0
    assert [item["content"] for item in read_jsonl(cleaned)] == ["好评", "可以"]


def test_clean_jsonl_should_strip_html_and_remove_empty_invalid_and_duplicates(tmp_path: Path) -> None:
    raw = tmp_path / "raw.jsonl"
    cleaned = tmp_path / "cleaned.jsonl"
    removed = tmp_path / "removed.jsonl"
    summary = tmp_path / "summary.json"
    raw.write_text(
        "\n".join(
            [
                json.dumps({"source": "jd", "sourceReviewId": "1", "productCode": "p1", "content": "<p>佩戴&nbsp;舒适</p>"}, ensure_ascii=False),
                json.dumps({"source": "jd", "sourceReviewId": "2", "productCode": "p1", "content": "<br>"}, ensure_ascii=False),
                json.dumps({"source": "jd", "sourceReviewId": "1", "productCode": "p1", "content": "重复评论"}, ensure_ascii=False),
                "{not-json}",
            ]
        )
        + "\n",
        encoding="utf-8",
    )

    result = clean_jsonl(raw, cleaned, removed, summary)

    assert result == {
        "rawCount": 4,
        "cleanedCount": 1,
        "removedCount": 3,
        "htmlCleanedCount": 1,
        "exactDuplicateCount": 1,
        "emptyContentCount": 1,
        "invalidJsonCount": 1,
        "placeholderContentCount": 0,
    }
    assert read_jsonl(cleaned)[0]["content"] == "佩戴 舒适"
    assert [item["removeReason"] for item in read_jsonl(removed)] == ["empty_content", "exact_duplicate", "invalid_json"]


def test_clean_jsonl_should_remove_platform_placeholder_but_keep_real_follow_up(tmp_path: Path) -> None:
    raw = tmp_path / "raw.jsonl"
    cleaned = tmp_path / "cleaned.jsonl"
    removed = tmp_path / "removed.jsonl"
    summary = tmp_path / "summary.json"
    raw.write_text(
        "\n".join(
            [
                json.dumps({"source": "jd", "sourceReviewId": "1", "productCode": "p1", "content": "此用户未及时填写评价内容"}, ensure_ascii=False),
                json.dumps({"source": "jd", "sourceReviewId": "2", "productCode": "p1", "content": "此用户未及时填写评价内容 | [追评]: 蓝牙经常断连"}, ensure_ascii=False),
                json.dumps({"source": "jd", "sourceReviewId": "3", "productCode": "p1", "content": "该用户未填写评价内容"}, ensure_ascii=False),
            ]
        )
        + "\n",
        encoding="utf-8",
    )

    result = clean_jsonl(raw, cleaned, removed, summary)

    assert result["rawCount"] == 3
    assert result["cleanedCount"] == 1
    assert result["removedCount"] == 2
    assert result["placeholderContentCount"] == 3
    assert read_jsonl(cleaned)[0]["content"] == "[追评]: 蓝牙经常断连"
    assert [item["removeReason"] for item in read_jsonl(removed)] == ["placeholder_content", "placeholder_content"]
