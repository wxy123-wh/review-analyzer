import json
import os
import re
import urllib.error
import urllib.request
from collections import Counter
from typing import Any, TypedDict

NEGATIVE_MARKERS = {
    '差',
    '断开',
    '噪音',
    '卡顿',
    '不稳',
    '发热',
    '一般',
    '掉电',
    '衰减',
}

POSITIVE_MARKERS = {
    '好',
    '稳定',
    '清晰',
    '舒适',
    '流畅',
    '满意',
}

ASPECT_KEYWORDS = {
    'battery': {'续航', '电池', '充电'},
    'bluetooth': {'连接', '断连', '断开', '蓝牙', 'connectivity'},
    'noise-canceling': {'降噪', '通透', 'noise canceling', 'noise_canceling'},
    'comfort': {'佩戴', '耳压', '舒适'},
    'microphone': {'通话', '麦克风', '收音', 'call quality', 'call_quality'},
}

ASPECT_ALIASES = {
    'battery': 'battery',
    '电池与续航': 'battery',
    '续航': 'battery',
    '充电': 'battery',
    'bluetooth': 'bluetooth',
    'connectivity': 'bluetooth',
    '连接与稳定性': 'bluetooth',
    '蓝牙连接': 'bluetooth',
    '连接': 'bluetooth',
    'noise-canceling': 'noise-canceling',
    'noise_canceling': 'noise-canceling',
    '降噪与通透': 'noise-canceling',
    '降噪': 'noise-canceling',
    'comfort': 'comfort',
    '佩戴与人体工学': 'comfort',
    '佩戴': 'comfort',
    '舒适': 'comfort',
    'microphone': 'microphone',
    'call_quality': 'microphone',
    '麦克风与通话': 'microphone',
    '通话': 'microphone',
    '收音': 'microphone',
}

POLARITY_ALIASES = {
    'NEGATIVE': 'NEGATIVE',
    '负面': 'NEGATIVE',
    '0': 'NEGATIVE',
    0: 'NEGATIVE',
    'NEUTRAL': 'NEUTRAL',
    '中性': 'NEUTRAL',
    'POSITIVE': 'POSITIVE',
    '正面': 'POSITIVE',
    '1': 'POSITIVE',
    1: 'POSITIVE',
}

CLUSTER_TITLES = {
    'battery': '续航体验波动',
    'bluetooth': '蓝牙连接稳定性不足',
    'noise-canceling': '降噪效果一致性不足',
    'comfort': '佩戴舒适度反馈分化',
    'microphone': '通话收音表现待优化',
}

UX_LABELS = {
    'battery': ('产品硬件', '电池与续航'),
    'bluetooth': ('产品硬件', '连接与稳定性'),
    'noise-canceling': ('声音表现', '降噪与通透'),
    'comfort': ('产品体验', '佩戴与人体工学'),
    'microphone': ('声音表现', '麦克风与通话'),
    'unknown': ('无明显问题', '无明显问题'),
}

POSITIVE_REASONS = {
    'battery': '续航持久',
    'bluetooth': '连接稳定',
    'noise-canceling': '降噪明显',
    'comfort': '佩戴舒适',
    'microphone': '通话清晰',
    'unknown': '无明显问题',
}

NEGATIVE_REASONS = {
    'battery': '续航不足',
    'bluetooth': '蓝牙断连',
    'noise-canceling': '降噪不足',
    'comfort': '佩戴不适',
    'microphone': '通话不清晰',
    'unknown': '综合体验问题',
}

VALID_UX_PRIMARY_LABELS = {'产品硬件', '声音表现', '产品体验', '服务与履约', '价格价值', '无明显问题'}
VALID_UX_SECONDARY_LABELS = {
    '连接与稳定性',
    '电池与续航',
    '佩戴与人体工学',
    '材质与品控',
    '音质体验',
    '底噪与杂音',
    '降噪与通透',
    '交互控制',
    '软件与生态',
    '麦克风与通话',
    '售后响应',
    '物流与包装',
    '价格变动',
    '性价比预期',
    '无明显问题',
}


class AspectSentimentResult(TypedDict):
    reviewIndex: int
    aspect: str
    polarity: str
    score: float
    confidence: float
    uxPrimaryLabel: str
    uxSecondaryLabel: str
    standardizedReason: str
    evidence: str
    negativeIntensityScore: int


class IssueClusterResult(TypedDict):
    aspect: str
    title: str
    mentionCount: int


class LlmConfig(TypedDict):
    api_key: str
    model: str
    base_url: str
    timeout: int


SYSTEM_PROMPT = '''你是电商产品评论 VOC 分析员。
任务：只根据真实评论正文，逐条输出结构化 JSON，不要输出 Markdown 或解释文字。

必须按输入顺序返回同样长度的 JSON 数组，每个对象包含：
{
  "sentiment": "NEGATIVE/NEUTRAL/POSITIVE",
  "aspect": "battery/bluetooth/noise-canceling/comfort/microphone/unknown",
  "negativeIntensityScore": 1-5,
  "uxPrimaryLabel": "产品硬件/声音表现/产品体验/服务与履约/价格价值/无明显问题",
  "uxSecondaryLabel": "连接与稳定性/电池与续航/佩戴与人体工学/材质与品控/音质体验/底噪与杂音/降噪与通透/交互控制/软件与生态/麦克风与通话/售后响应/物流与包装/价格变动/性价比预期/无明显问题",
  "standardizedReason": "20个中文字符以内",
  "confidence": 0到1的小数,
  "evidence": "评论中的短证据"
}

aspect 只能使用枚举：battery, bluetooth, noise-canceling, comfort, microphone, unknown。
sentiment 只能使用枚举：NEGATIVE, NEUTRAL, POSITIVE。
负面评论的 negativeIntensityScore 按 1=轻微摩擦、2=预期落差、3=核心痛点、4=致命故障、5=品牌危机；非负面评论可填 1。
如果评论涉及多个问题，选择对使用影响最大的主问题。'''


def env_value(*names: str, default: str = '') -> str:
    for name in names:
        value = os.environ.get(name)
        if value:
            return value.strip()
    return default


def llm_config() -> LlmConfig | None:
    if os.environ.get('PYTEST_CURRENT_TEST') or env_value('NLP_FORCE_LOCAL').lower() in {'1', 'true', 'yes'}:
        return None
    api_key = env_value('OPENAI_API_KEY', 'LLM_API_KEY')
    model = env_value('OPENAI_MODEL', 'LLM_MODEL')
    if not api_key or not model:
        return None
    return {
        'api_key': api_key,
        'model': model,
        'base_url': env_value('OPENAI_BASE_URL', 'LLM_BASE_URL', default='https://api.openai.com/v1').rstrip('/'),
        'timeout': int(env_value('OPENAI_TIMEOUT_SECONDS', 'LLM_TIMEOUT_SECONDS', default='60')),
    }


def build_chat_url(base_url: str) -> str:
    return base_url if base_url.endswith('/chat/completions') else f'{base_url}/chat/completions'


def normalize_cell(value: Any) -> str:
    return '' if value is None else str(value).strip()


def extract_json_payload(text: str) -> Any:
    text = normalize_cell(text)
    if not text:
        raise ValueError('empty llm response')
    fenced = re.search(r'```(?:json)?\s*(.*?)\s*```', text, flags=re.DOTALL | re.IGNORECASE)
    if fenced:
        text = fenced.group(1).strip()
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        starts = [index for index in (text.find('['), text.find('{')) if index >= 0]
        start = min(starts) if starts else -1
        end = max(text.rfind(']'), text.rfind('}'))
        if start < 0 or end < start:
            raise
        return json.loads(text[start : end + 1])


def parse_openai_text(data: dict[str, Any]) -> str:
    choices = data.get('choices') or []
    if not choices:
        raise ValueError('OpenAI-compatible response missing choices')
    message = choices[0].get('message') or {}
    content = message.get('content')
    if isinstance(content, list):
        return ''.join(normalize_cell(item.get('text') if isinstance(item, dict) else item) for item in content)
    return normalize_cell(content)


def call_openai_compatible(product_code: str, reviews: list[str], config: LlmConfig) -> str:
    body = {
        'model': config['model'],
        'temperature': 0,
        'messages': [
            {'role': 'system', 'content': SYSTEM_PROMPT},
            {
                'role': 'user',
                'content': '商品编码：%s\n真实评论 JSON 数组：\n%s'
                % (product_code, json.dumps([normalize_cell(item) for item in reviews], ensure_ascii=False)),
            },
        ],
    }
    request = urllib.request.Request(
        build_chat_url(config['base_url']),
        data=json.dumps(body, ensure_ascii=False).encode('utf-8'),
        headers={
            'Content-Type': 'application/json',
            'Authorization': 'Bearer %s' % config['api_key'],
        },
        method='POST',
    )
    with urllib.request.urlopen(request, timeout=config['timeout']) as response:
        return parse_openai_text(json.loads(response.read().decode('utf-8')))


def detect_aspect(text: str) -> str:
    for aspect, keywords in ASPECT_KEYWORDS.items():
        if any(keyword in text for keyword in keywords):
            return aspect
    return 'unknown'


def normalize_aspect(value: Any) -> str:
    raw = normalize_cell(value)
    if not raw:
        return 'unknown'
    return ASPECT_ALIASES.get(raw, ASPECT_ALIASES.get(raw.lower(), 'unknown'))


def detect_polarity(text: str) -> str:
    if any(marker in text for marker in NEGATIVE_MARKERS):
        return 'NEGATIVE'
    if any(marker in text for marker in POSITIVE_MARKERS):
        return 'POSITIVE'
    return 'NEUTRAL'


def normalize_polarity(value: Any) -> str:
    raw = normalize_cell(value)
    return POLARITY_ALIASES.get(value, POLARITY_ALIASES.get(raw, POLARITY_ALIASES.get(raw.upper(), 'NEUTRAL')))


def score_from_polarity(polarity: str) -> float:
    if polarity == 'NEGATIVE':
        return -0.78
    if polarity == 'POSITIVE':
        return 0.82
    return 0


def confidence_from_text(text: str) -> float:
    return 0.74 if len(text) < 8 else 0.88


def clamp_confidence(value: Any, fallback: float) -> float:
    try:
        numeric = float(value)
    except (TypeError, ValueError):
        return fallback
    return max(0.0, min(1.0, numeric))


def clamp_int(value: Any, fallback: int, minimum: int, maximum: int) -> int:
    try:
        numeric = int(value)
    except (TypeError, ValueError):
        return fallback
    return max(minimum, min(maximum, numeric))


def normalize_ux_primary(value: Any, aspect: str) -> str:
    raw = normalize_cell(value)
    if raw in VALID_UX_PRIMARY_LABELS:
        return raw
    return UX_LABELS.get(aspect, UX_LABELS['unknown'])[0]


def normalize_ux_secondary(value: Any, aspect: str) -> str:
    raw = normalize_cell(value)
    if raw in VALID_UX_SECONDARY_LABELS:
        return raw
    return UX_LABELS.get(aspect, UX_LABELS['unknown'])[1]


def default_reason(aspect: str, polarity: str) -> str:
    if polarity == 'POSITIVE':
        return POSITIVE_REASONS.get(aspect, POSITIVE_REASONS['unknown'])
    if polarity == 'NEGATIVE':
        return NEGATIVE_REASONS.get(aspect, NEGATIVE_REASONS['unknown'])
    return '无明显问题'


def normalize_reason(value: Any, aspect: str, polarity: str) -> str:
    raw = normalize_cell(value)
    if not raw:
        return default_reason(aspect, polarity)
    return raw[:20]


def evidence_from_text(text: str) -> str:
    cleaned = normalize_cell(text).replace('\n', ' ')
    return cleaned[:40]


def semantic_fields(
    aspect: str,
    polarity: str,
    review: str,
    item: dict[str, Any] | None = None,
) -> dict[str, Any]:
    item = item or {}
    return {
        'uxPrimaryLabel': normalize_ux_primary(item.get('uxPrimaryLabel'), aspect),
        'uxSecondaryLabel': normalize_ux_secondary(item.get('uxSecondaryLabel'), aspect),
        'standardizedReason': normalize_reason(item.get('standardizedReason') or item.get('reason'), aspect, polarity),
        'evidence': normalize_cell(item.get('evidence'))[:40] or evidence_from_text(review),
        'negativeIntensityScore': clamp_int(item.get('negativeIntensityScore'), 1 if polarity != 'NEGATIVE' else 3, 1, 5),
    }


def build_clusters(aspect_sentiments: list[AspectSentimentResult]) -> list[IssueClusterResult]:
    negative_aspects = [
        item['aspect']
        for item in aspect_sentiments
        if item['polarity'] == 'NEGATIVE'
    ]
    counter = Counter(negative_aspects)
    clusters: list[IssueClusterResult] = [
        {
            'aspect': aspect,
            'title': CLUSTER_TITLES.get(aspect, '综合体验反馈待优化'),
            'mentionCount': count,
        }
        for aspect, count in counter.items()
        if aspect in CLUSTER_TITLES
    ]
    return sorted(clusters, key=cluster_sort_key)


def cluster_sort_key(item: IssueClusterResult) -> tuple[int, str]:
    return (-item['mentionCount'], item['aspect'])


def fallback_analysis(reviews: list[str]) -> list[AspectSentimentResult]:
    aspect_sentiments: list[AspectSentimentResult] = []
    for idx, review in enumerate(reviews):
        aspect = detect_aspect(review)
        polarity = detect_polarity(review)
        aspect_sentiments.append(
            {
                'reviewIndex': idx,
                'aspect': aspect,
                'polarity': polarity,
                'score': score_from_polarity(polarity),
                'confidence': confidence_from_text(review),
                **semantic_fields(aspect, polarity, review),
            }
        )
    return aspect_sentiments


def parse_llm_results(text: str, expected_count: int, reviews: list[str]) -> list[AspectSentimentResult]:
    parsed = extract_json_payload(text)
    if isinstance(parsed, dict) and isinstance(parsed.get('results'), list):
        parsed = parsed['results']
    if not isinstance(parsed, list):
        raise ValueError('llm response must be a JSON array or contain results array')
    if len(parsed) != expected_count:
        raise ValueError('llm response count mismatch')

    results: list[AspectSentimentResult] = []
    for idx, item in enumerate(parsed):
        if not isinstance(item, dict):
            raise ValueError('llm response item must be object')
        aspect = normalize_aspect(item.get('aspect') or item.get('uxSecondaryLabel'))
        polarity = normalize_polarity(item.get('sentiment') or item.get('label'))
        fallback_confidence = confidence_from_text(reviews[idx])
        results.append(
            {
                'reviewIndex': idx,
                'aspect': aspect,
                'polarity': polarity,
                'score': score_from_polarity(polarity),
                'confidence': clamp_confidence(item.get('confidence'), fallback_confidence),
                **semantic_fields(aspect, polarity, reviews[idx], item),
            }
        )
    return results


def analyze_reviews(product_code: str, reviews: list[str]) -> tuple[list[AspectSentimentResult], list[IssueClusterResult], str]:
    config = llm_config()
    if not reviews:
        return [], [], 'empty'
    if config is None:
        aspect_sentiments = fallback_analysis(reviews)
        return aspect_sentiments, build_clusters(aspect_sentiments), 'local-fallback'
    try:
        response_text = call_openai_compatible(product_code, reviews, config)
        aspect_sentiments = parse_llm_results(response_text, len(reviews), reviews)
        return aspect_sentiments, build_clusters(aspect_sentiments), 'llm'
    except (urllib.error.URLError, TimeoutError, ValueError, json.JSONDecodeError):
        aspect_sentiments = fallback_analysis(reviews)
        return aspect_sentiments, build_clusters(aspect_sentiments), 'local-fallback-after-llm-error'
