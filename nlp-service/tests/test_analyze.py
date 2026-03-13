from fastapi.testclient import TestClient

from app.main import app


client = TestClient(app)


def test_analyze_should_return_aspects_and_clusters() -> None:
    response = client.post(
        '/analyze',
        json={
            'jobId': 'job-1',
            'productCode': 'demo-earphone',
            'reviews': ['续航很好', '连接偶尔断开'],
        },
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload['jobId'] == 'job-1'
    assert isinstance(payload['aspectSentiments'], list)
    assert isinstance(payload['issueClusters'], list)
    assert payload['aspectSentiments'][0]['aspect'] == 'battery'
    assert payload['aspectSentiments'][0]['polarity'] == 'POSITIVE'
    assert payload['aspectSentiments'][0]['score'] > 0
    assert payload['aspectSentiments'][1]['aspect'] == 'connectivity'
    assert payload['aspectSentiments'][1]['polarity'] == 'NEGATIVE'
    assert payload['aspectSentiments'][1]['score'] < 0
    assert payload['issueClusters'][0]['aspect'] == 'connectivity'
    assert payload['issueClusters'][0]['mentionCount'] >= 1


def test_analyze_should_mark_neutral_comment_without_cluster() -> None:
    response = client.post(
        '/analyze',
        json={
            'jobId': 'job-2',
            'productCode': 'demo-earphone',
            'reviews': ['包装完整发货快'],
        },
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload['aspectSentiments'][0]['polarity'] == 'NEUTRAL'
    assert payload['aspectSentiments'][0]['score'] == 0
    assert payload['issueClusters'] == []
