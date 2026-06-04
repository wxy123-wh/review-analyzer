from pathlib import Path
import sys

import pytest

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))


@pytest.fixture(autouse=True)
def force_rule_mode(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("NLP_FORCE_LOCAL", "true")
