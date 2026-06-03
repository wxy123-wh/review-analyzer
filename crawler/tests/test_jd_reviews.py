# -*- coding: utf-8 -*-

from __future__ import annotations

import json
from pathlib import Path

from crawler.common.dedupe import review_key
from crawler.jd_reviews import (
    collect_jd_reviews,
    extract_reviews_from_payload,
    jd_product_id_from_url,
    parse_packet_body,
)


class DummyResponse:
    def __init__(self, body):
        self.body = body


class DummyPacket:
    def __init__(self, body):
        self.response = DummyResponse(body)


def test_parse_packet_body_accepts_jsonp_wrapped_payload():
    packet = DummyPacket('fetchJSON_comment98vv123({"comments":[{"id":"c1","content":"佩戴舒服","score":5}]});')

    payload = parse_packet_body(packet)

    assert payload["comments"][0]["id"] == "c1"


def test_extract_reviews_normalizes_jd_comment_fields():
    payload = {
        "comments": [
            {
                "id": "c1",
                "content": " 蓝牙\n偶尔断连 ",
                "score": "2",
                "creationTime": "2026-06-01 10:20:00",
                "referenceName": "黑色 标准版",
                "nickname": "用户A",
            }
        ]
    }

    reviews = extract_reviews_from_payload(payload, "jd-100", "bluetooth-earphone")

    assert reviews == [
        {
            "source": "jd",
            "sourceReviewId": "c1",
            "productCode": "jd-100",
            "category": "bluetooth-earphone",
            "rating": 2.0,
            "content": "蓝牙 偶尔断连",
            "reviewTime": "2026-06-01 10:20:00",
            "skuInfo": "黑色 标准版",
            "anonymizedAuthorId": "用户A",
        }
    ]


def test_review_key_uses_source_and_source_review_id():
    assert review_key({"source": "jd", "sourceReviewId": "100"}) == "jd|100"
    assert review_key({"source": "taobao", "sourceReviewId": "100"}) == "taobao|100"


def test_collect_jd_reviews_dry_run_appends_only_new_source_review_ids(tmp_path: Path):
    payload = {
        "comments": [
            {"id": "c1", "content": "续航不错", "score": 5, "creationTime": "2026-06-01 10:20:00"},
            {"id": "c1", "content": "续航不错", "score": 5, "creationTime": "2026-06-01 10:20:00"},
            {"id": "c2", "content": "通话有杂音", "score": 2, "creationTime": "2026-06-02 11:20:00"},
        ]
    }
    output = tmp_path / "raw_reviews.jsonl"
    progress = tmp_path / "progress.json"

    first = collect_jd_reviews(
        product_url="https://item.jd.com/100127936932.html",
        product_code="jd-100127936932",
        category="bluetooth-earphone",
        output=output,
        progress_path=progress,
        dry_run_payloads=[payload],
    )
    second = collect_jd_reviews(
        product_url="https://item.jd.com/100127936932.html",
        product_code="jd-100127936932",
        category="bluetooth-earphone",
        output=output,
        progress_path=progress,
        dry_run_payloads=[payload],
    )

    lines = [json.loads(line) for line in output.read_text(encoding="utf-8").splitlines()]
    assert first.newReviewCount == 2
    assert second.newReviewCount == 0
    assert [line["sourceReviewId"] for line in lines] == ["c1", "c2"]


def test_jd_product_id_from_url_accepts_full_url():
    assert jd_product_id_from_url("https://item.jd.com/100127936932.html") == "100127936932"
