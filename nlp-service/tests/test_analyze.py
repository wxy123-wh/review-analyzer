from typing import cast

from fastapi.testclient import TestClient

from app.main import app  # pyright: ignore[reportImplicitRelativeImport]


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
    payload = cast(dict[str, object], response.json())
    aspect_sentiments = cast(list[dict[str, object]], payload['aspectSentiments'])
    issue_clusters = cast(list[dict[str, object]], payload['issueClusters'])
    assert payload['jobId'] == 'job-1'
    assert isinstance(aspect_sentiments, list)
    assert isinstance(issue_clusters, list)
    assert aspect_sentiments[0]['aspect'] == 'battery'
    assert aspect_sentiments[0]['polarity'] == 'POSITIVE'
    assert cast(float, aspect_sentiments[0]['score']) > 0
    assert aspect_sentiments[1]['aspect'] == 'bluetooth'
    assert aspect_sentiments[1]['polarity'] == 'NEGATIVE'
    assert cast(float, aspect_sentiments[1]['score']) < 0
    assert issue_clusters[0]['aspect'] == 'bluetooth'
    assert issue_clusters[0]['title'] == '蓝牙连接稳定性不足'
    assert cast(int, issue_clusters[0]['mentionCount']) >= 1


def test_analyze_should_normalize_canonical_taxonomy_aliases() -> None:
    response = client.post(
        '/analyze',
        json={
            'jobId': 'job-aliases',
            'productCode': 'demo-earphone',
            'reviews': ['降噪一般', '麦克风收音清晰'],
        },
    )

    assert response.status_code == 200
    payload = cast(dict[str, object], response.json())
    aspect_sentiments = cast(list[dict[str, object]], payload['aspectSentiments'])
    issue_clusters = cast(list[dict[str, object]], payload['issueClusters'])
    assert aspect_sentiments[0]['aspect'] == 'noise-canceling'
    assert aspect_sentiments[0]['polarity'] == 'NEGATIVE'
    assert aspect_sentiments[1]['aspect'] == 'microphone'
    assert aspect_sentiments[1]['polarity'] == 'POSITIVE'
    assert {cast(str, cluster['aspect']) for cluster in issue_clusters} == {'noise-canceling'}


def test_analyze_should_mark_neutral_canonical_comment_without_cluster() -> None:
    response = client.post(
        '/analyze',
        json={
            'jobId': 'job-2',
            'productCode': 'demo-earphone',
            'reviews': ['佩戴体验正常'],
        },
    )

    assert response.status_code == 200
    payload = cast(dict[str, object], response.json())
    aspect_sentiments = cast(list[dict[str, object]], payload['aspectSentiments'])
    issue_clusters = cast(list[dict[str, object]], payload['issueClusters'])
    assert aspect_sentiments[0]['aspect'] == 'comfort'
    assert aspect_sentiments[0]['polarity'] == 'NEUTRAL'
    assert aspect_sentiments[0]['score'] == 0
    assert issue_clusters == []


def test_analyze_should_preserve_review_indexes_confidence_and_unknown_aspect_contract() -> None:
    response = client.post(
        '/analyze',
        json={
            'jobId': 'job-contract',
            'productCode': 'demo-earphone',
            'reviews': ['好用', '这个描述没有命中任何已知维度'],
        },
    )

    assert response.status_code == 200
    payload = cast(dict[str, object], response.json())
    assert payload['jobId'] == 'job-contract'
    assert payload['aspectSentiments'] == [
        {
            'reviewIndex': 0,
            'aspect': 'unknown',
            'polarity': 'POSITIVE',
            'score': 0.82,
            'confidence': 0.74,
        },
        {
            'reviewIndex': 1,
            'aspect': 'unknown',
            'polarity': 'NEUTRAL',
            'score': 0,
            'confidence': 0.88,
        },
    ]
    assert payload['issueClusters'] == []


def test_analyze_should_group_and_sort_negative_clusters_by_mentions_then_aspect() -> None:
    response = client.post(
        '/analyze',
        json={
            'jobId': 'job-clusters',
            'productCode': 'demo-earphone',
            'reviews': ['蓝牙断开', '蓝牙不稳', '续航掉电', '降噪一般'],
        },
    )

    assert response.status_code == 200
    payload = cast(dict[str, object], response.json())
    assert payload['issueClusters'] == [
        {
            'aspect': 'bluetooth',
            'title': '蓝牙连接稳定性不足',
            'mentionCount': 2,
        },
        {
            'aspect': 'battery',
            'title': '续航体验波动',
            'mentionCount': 1,
        },
        {
            'aspect': 'noise-canceling',
            'title': '降噪效果一致性不足',
            'mentionCount': 1,
        },
    ]


def test_analyze_should_reject_requests_with_empty_reviews() -> None:
    response = client.post(
        '/analyze',
        json={
            'jobId': 'job-empty',
            'productCode': 'demo-earphone',
            'reviews': [],
        },
    )

    assert response.status_code == 200
    payload = cast(dict[str, object], response.json())
    assert payload['jobId'] == 'job-empty'
    assert payload['aspectSentiments'] == []
    assert payload['issueClusters'] == []


def test_analyze_should_require_job_id_product_code_and_reviews() -> None:
    response = client.post(
        '/analyze',
        json={
            'productCode': 'demo-earphone',
        },
    )

    assert response.status_code == 422
    payload = cast(dict[str, object], response.json())
    detail = cast(list[dict[str, object]], payload['detail'])
    error_locations = [tuple(cast(list[object], item['loc'])) for item in detail]
    assert ('body', 'jobId') in error_locations
    assert ('body', 'reviews') in error_locations
