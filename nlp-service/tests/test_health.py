from fastapi.testclient import TestClient

from app.main import app


client = TestClient(app)


def test_health_should_return_up() -> None:
    response = client.get('/health')
    assert response.status_code == 200
    payload = response.json()
    assert payload['status'] == 'UP'
