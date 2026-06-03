# -*- coding: utf-8 -*-

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


def load_progress(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {"lastPage": 0, "seenReviewIds": []}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return {"lastPage": 0, "seenReviewIds": []}


def save_progress(path: Path, state: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")
