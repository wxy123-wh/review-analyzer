# -*- coding: utf-8 -*-

from __future__ import annotations

import json
from pathlib import Path
from typing import Iterable

from .dedupe import review_key


class JsonlReviewWriter:
    def __init__(self, path: Path) -> None:
        self.path = path
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.seen = self._load_seen()

    def _load_seen(self) -> set[str]:
        if not self.path.exists():
            return set()
        seen: set[str] = set()
        with self.path.open("r", encoding="utf-8") as file:
            for line in file:
                line = line.strip()
                if not line:
                    continue
                try:
                    seen.add(review_key(json.loads(line)))
                except json.JSONDecodeError:
                    continue
        return seen

    def write_many(self, reviews: Iterable[dict]) -> int:
        new_count = 0
        with self.path.open("a", encoding="utf-8") as file:
            for review in reviews:
                key = review_key(review)
                if key in self.seen:
                    continue
                self.seen.add(key)
                file.write(json.dumps(review, ensure_ascii=False) + "\n")
                new_count += 1
        return new_count
