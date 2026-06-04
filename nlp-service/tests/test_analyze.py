import http.client
from typing import cast

from fastapi.testclient import TestClient

from app import analyzer  # pyright: ignore[reportImplicitRelativeImport]
from app.analyzer import (  # pyright: ignore[reportImplicitRelativeImport]
    build_system_prompt,
    extract_json_payload,
    normalize_taxonomy,
    parse_llm_results,
    taxonomy_primary_enum,
    taxonomy_secondary_enum,
)
from app.main import app  # pyright: ignore[reportImplicitRelativeImport]


client = TestClient(app)


def test_analyze_should_return_aspects_and_clusters() -> None:
    response = client.post(
        '/analyze',
        json={
            'jobId': 'job-1',
            'productCode': 'jd-100127936932',
            'reviews': ['续航很好', '连接偶尔断开'],
        },
    )

    assert response.status_code == 200
    payload = cast(dict[str, object], response.json())
    aspect_sentiments = cast(list[dict[str, object]], payload['aspectSentiments'])
    issue_clusters = cast(list[dict[str, object]], payload['issueClusters'])
    assert payload['jobId'] == 'job-1'
    assert payload['analysisMode'] == 'local-rule'
    assert payload['llmUsed'] is False
    assert payload['fallbackReason'] == 'local-rule'
    assert isinstance(aspect_sentiments, list)
    assert isinstance(issue_clusters, list)
    assert aspect_sentiments[0]['aspect'] == 'battery'
    assert aspect_sentiments[0]['polarity'] == 'POSITIVE'
    assert aspect_sentiments[0]['uxSecondaryLabel'] == '电池与续航'
    assert aspect_sentiments[0]['standardizedReason'] == '续航持久'
    assert cast(float, aspect_sentiments[0]['score']) > 0
    assert aspect_sentiments[1]['aspect'] == 'bluetooth'
    assert aspect_sentiments[1]['polarity'] == 'NEGATIVE'
    assert aspect_sentiments[1]['uxSecondaryLabel'] == '连接与稳定性'
    assert aspect_sentiments[1]['standardizedReason'] == '蓝牙断连'
    assert aspect_sentiments[1]['negativeIntensityScore'] == 3
    assert cast(float, aspect_sentiments[1]['score']) < 0
    assert issue_clusters[0]['aspect'] == 'bluetooth'
    assert issue_clusters[0]['title'] == '蓝牙连接稳定性不足'
    assert cast(int, issue_clusters[0]['mentionCount']) >= 1


def test_analyze_should_normalize_canonical_taxonomy_aliases() -> None:
    response = client.post(
        '/analyze',
        json={
            'jobId': 'job-aliases',
            'productCode': 'jd-100127936932',
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
            'productCode': 'jd-100127936932',
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
            'productCode': 'jd-100127936932',
            'reviews': ['好用', '这个描述没有命中任何已知维度'],
        },
    )

    assert response.status_code == 200
    payload = cast(dict[str, object], response.json())
    assert payload['jobId'] == 'job-contract'
    aspect_sentiments = cast(list[dict[str, object]], payload['aspectSentiments'])
    assert aspect_sentiments[0] == {
        'reviewIndex': 0,
        'aspect': 'unknown',
        'polarity': 'POSITIVE',
        'score': 0.82,
        'confidence': 0.74,
        'uxPrimaryLabel': '无明显问题',
        'uxSecondaryLabel': '无明显问题',
        'standardizedReason': '无明显问题',
        'evidence': '好用',
        'negativeIntensityScore': 1,
    }
    assert aspect_sentiments[1] == {
        'reviewIndex': 1,
        'aspect': 'unknown',
        'polarity': 'NEUTRAL',
        'score': 0,
        'confidence': 0.88,
        'uxPrimaryLabel': '无明显问题',
        'uxSecondaryLabel': '无明显问题',
        'standardizedReason': '无明显问题',
        'evidence': '这个描述没有命中任何已知维度',
        'negativeIntensityScore': 1,
    }
    assert payload['issueClusters'] == []


def test_analyze_should_group_and_sort_negative_clusters_by_mentions_then_aspect() -> None:
    response = client.post(
        '/analyze',
        json={
            'jobId': 'job-clusters',
            'productCode': 'jd-100127936932',
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
            'uxPrimaryLabel': '产品硬件',
            'uxSecondaryLabel': '连接与稳定性',
        },
        {
            'aspect': 'battery',
            'title': '续航体验波动',
            'mentionCount': 1,
            'uxPrimaryLabel': '产品硬件',
            'uxSecondaryLabel': '电池与续航',
        },
        {
            'aspect': 'noise-canceling',
            'title': '降噪效果一致性不足',
            'mentionCount': 1,
            'uxPrimaryLabel': '声音表现',
            'uxSecondaryLabel': '降噪与通透',
        },
    ]


def test_analyze_should_use_custom_taxonomy_labels() -> None:
    response = client.post(
        '/analyze',
        json={
            'jobId': 'job-taxonomy',
            'productCode': 'phone-1',
            'taxonomy': {
                'primaryLabels': [
                    {
                        'labelName': '系统体验',
                        'secondaryLabels': [
                            {
                                'labelName': '发热控制',
                                'synonyms': ['发热', '烫手'],
                                'description': '机身温度和散热体验',
                            }
                        ],
                    },
                    {
                        'labelName': '无明显问题',
                        'secondaryLabels': [{'labelName': '无明显问题', 'synonyms': ['其他']}],
                    },
                ]
            },
            'reviews': ['打游戏很烫手'],
        },
    )

    assert response.status_code == 200
    payload = cast(dict[str, object], response.json())
    aspect_sentiments = cast(list[dict[str, object]], payload['aspectSentiments'])
    assert aspect_sentiments[0]['aspect'] == 'unknown'
    assert aspect_sentiments[0]['uxPrimaryLabel'] == '系统体验'
    assert aspect_sentiments[0]['uxSecondaryLabel'] == '发热控制'


def test_analyze_should_reject_requests_with_empty_reviews() -> None:
    response = client.post(
        '/analyze',
        json={
            'jobId': 'job-empty',
            'productCode': 'jd-100127936932',
            'reviews': [],
        },
    )

    assert response.status_code == 200
    payload = cast(dict[str, object], response.json())
    assert payload['jobId'] == 'job-empty'
    assert payload['aspectSentiments'] == []
    assert payload['issueClusters'] == []
    assert payload['analysisMode'] == 'empty'
    assert payload['llmUsed'] is False


def test_analyze_should_fail_without_llm_config_when_rule_mode_is_not_explicit(monkeypatch) -> None:
    monkeypatch.setenv('NLP_FORCE_LOCAL', 'false')
    monkeypatch.delenv('OPENAI_API_KEY', raising=False)
    monkeypatch.delenv('LLM_API_KEY', raising=False)
    monkeypatch.delenv('OPENAI_MODEL', raising=False)
    monkeypatch.delenv('LLM_MODEL', raising=False)

    response = client.post(
        '/analyze',
        json={
            'jobId': 'job-no-key',
            'productCode': 'jd-100127936932',
            'reviews': ['连接偶尔断开'],
        },
    )

    assert response.status_code == 503
    payload = cast(dict[str, object], response.json())
    detail = cast(dict[str, object], payload['detail'])
    assert detail['code'] == 'llm_config_missing'


def test_llm_system_prompt_should_escape_json_example_braces() -> None:
    taxonomy = normalize_taxonomy()
    prompt = build_system_prompt(taxonomy)

    assert '"sentiment": "NEGATIVE/NEUTRAL/POSITIVE"' in prompt
    assert f'"uxPrimaryLabel": "{taxonomy_primary_enum(taxonomy)}"' in prompt
    assert f'"uxSecondaryLabel": "{taxonomy_secondary_enum(taxonomy)}"' in prompt
    assert '{primary_enum}' not in prompt
    assert '{secondary_enum}' not in prompt


def test_extract_json_payload_should_tolerate_thinking_text_before_json_array() -> None:
    payload = extract_json_payload(
        '<think>先分析：{"draft": "not final"}，最终只看下面数组。</think>\n'
        '[{"sentiment":"NEGATIVE","uxSecondaryLabel":"连接与稳定性"}]'
    )

    assert payload == [{'sentiment': 'NEGATIVE', 'uxSecondaryLabel': '连接与稳定性'}]


def test_parse_llm_results_should_recover_positive_taxonomy_label_from_review_text() -> None:
    taxonomy = normalize_taxonomy()
    results = parse_llm_results(
        '[{"sentiment":"POSITIVE","uxSecondaryLabel":"无明显问题","confidence":0.8}]',
        1,
        ['佩戴很舒适，长时间戴也不压耳'],
        taxonomy,
    )

    assert results[0]['polarity'] == 'POSITIVE'
    assert results[0]['uxPrimaryLabel'] == '产品体验'
    assert results[0]['uxSecondaryLabel'] == '佩戴与人体工学'
    assert results[0]['aspect'] == 'comfort'


def test_analyze_should_return_json_error_when_llm_connection_closes(monkeypatch) -> None:
    monkeypatch.setenv('NLP_FORCE_LOCAL', 'false')
    monkeypatch.setenv('NLP_ALLOW_LLM_FALLBACK', 'false')
    monkeypatch.setenv('OPENAI_API_KEY', 'test-key')
    monkeypatch.setenv('OPENAI_MODEL', 'test-model')

    def raise_remote_disconnect(*args: object, **kwargs: object) -> str:
        raise http.client.RemoteDisconnected('Remote end closed connection without response')

    monkeypatch.setattr(analyzer, 'call_openai_compatible', raise_remote_disconnect)

    response = client.post(
        '/analyze',
        json={
            'jobId': 'job-remote-disconnect',
            'productCode': 'jd-100127936932',
            'reviews': ['连接偶尔断开'],
        },
    )

    assert response.status_code == 502
    assert response.headers['content-type'].startswith('application/json')
    payload = cast(dict[str, object], response.json())
    detail = cast(dict[str, object], payload['detail'])
    assert detail['code'] == 'llm_analysis_failed'


def test_analyze_should_require_job_id_product_code_and_reviews() -> None:
    response = client.post(
        '/analyze',
        json={
            'productCode': 'jd-100127936932',
        },
    )

    assert response.status_code == 422
    payload = cast(dict[str, object], response.json())
    detail = cast(list[dict[str, object]], payload['detail'])
    error_locations = [tuple(cast(list[object], item['loc'])) for item in detail]
    assert ('body', 'jobId') in error_locations
    assert ('body', 'reviews') in error_locations
