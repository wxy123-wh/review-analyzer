from typing import cast

from datetime import datetime

from fastapi.testclient import TestClient

from app.main import app  # pyright: ignore[reportImplicitRelativeImport]


client = TestClient(app)


def test_health_should_return_up() -> None:
    response = client.get('/health')
    assert response.status_code == 200
    payload = cast(dict[str, str], response.json())
    assert payload['status'] == 'UP'
    parsed = datetime.fromisoformat(payload['timestamp'])
    assert parsed.tzinfo is not None
