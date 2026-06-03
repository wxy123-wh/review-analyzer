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

FALLBACK_PRIMARY_LABEL = '无明显问题'
FALLBACK_SECONDARY_LABEL = '无明显问题'

DEFAULT_TAXONOMY_DATA = {
    'fallbackLabels': {
        'primaryLabel': FALLBACK_PRIMARY_LABEL,
        'secondaryLabel': FALLBACK_SECONDARY_LABEL,
    },
    'uxPrimaryLabels': [
        {
            'label': '产品硬件',
            'secondaryLabels': [
                {'label': '电池与续航', 'synonyms': ['续航', '电池', '充电', '耗电', '掉电']},
                {'label': '连接与稳定性', 'synonyms': ['连接', '断连', '断开', '蓝牙', '网络', '稳定', 'connectivity']},
                {'label': '材质与品控', 'synonyms': ['做工', '材质', '质量', '瑕疵', '损坏']},
            ],
        },
        {
            'label': '产品体验',
            'secondaryLabels': [
                {'label': '佩戴与人体工学', 'synonyms': ['佩戴', '舒适', '重量', '尺寸', '耳压']},
                {'label': '交互控制', 'synonyms': ['按键', '触控', '操作', '设置', '控制']},
                {'label': '软件与生态', 'synonyms': ['软件', '系统', 'app', '兼容', '升级']},
            ],
        },
        {
            'label': '声音表现',
            'secondaryLabels': [
                {'label': '音质体验', 'synonyms': ['音质', '声音', '低音', '高音', '清晰']},
                {'label': '底噪与杂音', 'synonyms': ['杂音', '底噪', '噪音', '电流声']},
                {'label': '降噪与通透', 'synonyms': ['降噪', '通透', '隔音', 'noise canceling', 'noise_canceling']},
                {'label': '麦克风与通话', 'synonyms': ['通话', '麦克风', '收音', '语音', 'call quality', 'call_quality']},
            ],
        },
        {
            'label': '服务与履约',
            'secondaryLabels': [
                {'label': '物流与包装', 'synonyms': ['物流', '快递', '包装', '发货']},
                {'label': '售后响应', 'synonyms': ['售后', '客服', '退换', '维修']},
            ],
        },
        {
            'label': '价格价值',
            'secondaryLabels': [
                {'label': '价格变动', 'synonyms': ['价格', '降价', '涨价', '保价']},
                {'label': '性价比预期', 'synonyms': ['性价比', '值得', '划算', '贵']},
            ],
        },
        {
            'label': FALLBACK_PRIMARY_LABEL,
            'secondaryLabels': [
                {'label': FALLBACK_SECONDARY_LABEL, 'synonyms': ['其他', '无明显问题']},
            ],
        },
    ],
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
    uxPrimaryLabel: str
    uxSecondaryLabel: str


class LlmConfig(TypedDict):
    api_key: str
    model: str
    base_url: str
    timeout: int


class TaxonomySecondaryLabel(TypedDict):
    primaryLabel: str
    label: str
    synonyms: list[str]
    description: str


class TaxonomyDefinition(TypedDict):
    primaryLabels: list[str]
    secondaryLabels: list[TaxonomySecondaryLabel]
    fallbackPrimaryLabel: str
    fallbackSecondaryLabel: str


SYSTEM_PROMPT_TEMPLATE = '''你是电商产品评论 VOC 分析员。
任务：只根据真实评论正文，逐条输出结构化 JSON，不要输出 Markdown 或解释文字。

必须按输入顺序返回同样长度的 JSON 数组，每个对象包含：
{
  "sentiment": "NEGATIVE/NEUTRAL/POSITIVE",
  "aspect": "battery/bluetooth/noise-canceling/comfort/microphone/unknown",
  "negativeIntensityScore": 1-5,
  "uxPrimaryLabel": "{primary_enum}",
  "uxSecondaryLabel": "{secondary_enum}",
  "standardizedReason": "20个中文字符以内",
  "confidence": 0到1的小数,
  "evidence": "评论中的短证据"
}

uxPrimaryLabel 只能使用枚举：{primary_enum}。
uxSecondaryLabel 只能使用枚举：{secondary_enum}。
UX 标签层级和同义词如下：
{taxonomy_lines}

aspect 只能使用枚举：battery, bluetooth, noise-canceling, comfort, microphone, unknown。
sentiment 只能使用枚举：NEGATIVE, NEUTRAL, POSITIVE。
优先判断 uxPrimaryLabel 和 uxSecondaryLabel；aspect 只是兼容旧系统的辅助字段。
负面评论的 negativeIntensityScore 按 1=轻微摩擦、2=预期落差、3=核心痛点、4=致命故障、5=品牌危机；非负面评论可填 1。
如果评论涉及多个问题，选择对使用影响最大的主问题。
如果没有明确问题或无法归类，uxPrimaryLabel 和 uxSecondaryLabel 都填“无明显问题”。'''


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


def _dump_model(value: Any) -> dict[str, Any]:
    if value is None:
        return {}
    if hasattr(value, 'model_dump'):
        dumped = value.model_dump()
        return dumped if isinstance(dumped, dict) else {}
    return value if isinstance(value, dict) else {}


def _list_value(value: Any) -> list[Any]:
    return value if isinstance(value, list) else []


def _string_list(value: Any) -> list[str]:
    return [normalize_cell(item) for item in _list_value(value) if normalize_cell(item)]


def normalize_taxonomy(taxonomy: Any | None = None) -> TaxonomyDefinition:
    raw = _dump_model(taxonomy) or DEFAULT_TAXONOMY_DATA
    fallback = _dump_model(raw.get('fallbackLabels'))
    fallback_primary = normalize_cell(fallback.get('primaryLabel')) or FALLBACK_PRIMARY_LABEL
    fallback_secondary = normalize_cell(fallback.get('secondaryLabel')) or FALLBACK_SECONDARY_LABEL
    raw_primaries = _list_value(raw.get('uxPrimaryLabels') or raw.get('primaryLabels'))

    primary_labels: list[str] = []
    secondary_labels: list[TaxonomySecondaryLabel] = []
    for primary_item in raw_primaries:
        primary_data = _dump_model(primary_item)
        primary_label = normalize_cell(primary_data.get('label') or primary_data.get('labelName'))
        if not primary_label:
            continue
        primary_labels.append(primary_label)
        raw_secondaries = _list_value(primary_data.get('secondaryLabels') or primary_data.get('uxSecondaryLabels'))
        for secondary_item in raw_secondaries:
            secondary_data = _dump_model(secondary_item)
            secondary_label = normalize_cell(secondary_data.get('label') or secondary_data.get('labelName'))
            if not secondary_label:
                continue
            secondary_labels.append(
                {
                    'primaryLabel': primary_label,
                    'label': secondary_label,
                    'synonyms': _string_list(secondary_data.get('synonyms')),
                    'description': normalize_cell(secondary_data.get('description')),
                }
            )

    if not secondary_labels:
        return normalize_taxonomy(DEFAULT_TAXONOMY_DATA)
    if fallback_primary not in primary_labels:
        primary_labels.append(fallback_primary)
    if not any(item['label'] == fallback_secondary for item in secondary_labels):
        secondary_labels.append(
            {
                'primaryLabel': fallback_primary,
                'label': fallback_secondary,
                'synonyms': ['其他', '无明显问题'],
                'description': '无法归入具体体验标签的评论',
            }
        )
    return {
        'primaryLabels': primary_labels,
        'secondaryLabels': secondary_labels,
        'fallbackPrimaryLabel': fallback_primary,
        'fallbackSecondaryLabel': fallback_secondary,
    }


def taxonomy_primary_enum(taxonomy: TaxonomyDefinition) -> str:
    return '/'.join(taxonomy['primaryLabels'])


def taxonomy_secondary_enum(taxonomy: TaxonomyDefinition) -> str:
    return '/'.join(item['label'] for item in taxonomy['secondaryLabels'])


def taxonomy_prompt_lines(taxonomy: TaxonomyDefinition) -> str:
    lines: list[str] = []
    for secondary in taxonomy['secondaryLabels']:
        synonyms = '、'.join(secondary['synonyms']) if secondary['synonyms'] else '无'
        description = f"，说明：{secondary['description']}" if secondary['description'] else ''
        lines.append(f"- {secondary['primaryLabel']} -> {secondary['label']}；同义词：{synonyms}{description}")
    return '\n'.join(lines)


def build_system_prompt(taxonomy: TaxonomyDefinition) -> str:
    return SYSTEM_PROMPT_TEMPLATE.format(
        primary_enum=taxonomy_primary_enum(taxonomy),
        secondary_enum=taxonomy_secondary_enum(taxonomy),
        taxonomy_lines=taxonomy_prompt_lines(taxonomy),
    )


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


def call_openai_compatible(
    product_code: str,
    reviews: list[str],
    config: LlmConfig,
    taxonomy: TaxonomyDefinition,
) -> str:
    body = {
        'model': config['model'],
        'temperature': 0,
        'messages': [
            {'role': 'system', 'content': build_system_prompt(taxonomy)},
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


def legacy_aspect_for_secondary(ux_secondary_label: str) -> str:
    return normalize_aspect(ux_secondary_label)


def label_matches(text: str, term: str) -> bool:
    if not term:
        return False
    if re.search(r'[A-Za-z]', term):
        return term.lower() in text.lower()
    return term in text


def fallback_secondary(taxonomy: TaxonomyDefinition) -> TaxonomySecondaryLabel:
    fallback_label = taxonomy['fallbackSecondaryLabel']
    for secondary in taxonomy['secondaryLabels']:
        if secondary['label'] == fallback_label:
            return secondary
    return {
        'primaryLabel': taxonomy['fallbackPrimaryLabel'],
        'label': fallback_label,
        'synonyms': ['其他', '无明显问题'],
        'description': '',
    }


def primary_for_secondary(taxonomy: TaxonomyDefinition, ux_secondary_label: str) -> str:
    for secondary in taxonomy['secondaryLabels']:
        if secondary['label'] == ux_secondary_label:
            return secondary['primaryLabel']
    return taxonomy['fallbackPrimaryLabel']


def normalize_ux_secondary_label(taxonomy: TaxonomyDefinition, value: Any) -> str:
    raw = normalize_cell(value)
    for secondary in taxonomy['secondaryLabels']:
        if raw == secondary['label']:
            return raw
    return taxonomy['fallbackSecondaryLabel']


def detect_taxonomy_secondary(text: str, taxonomy: TaxonomyDefinition) -> TaxonomySecondaryLabel:
    fallback = fallback_secondary(taxonomy)
    best: tuple[int, int, TaxonomySecondaryLabel] | None = None
    for secondary in taxonomy['secondaryLabels']:
        if secondary['label'] == taxonomy['fallbackSecondaryLabel']:
            continue
        terms = [secondary['label'], *secondary['synonyms']]
        matched_terms = [term for term in terms if label_matches(text, term)]
        if not matched_terms:
            continue
        score = (len(matched_terms), max(len(term) for term in matched_terms))
        if best is None or score > (best[0], best[1]):
            best = (score[0], score[1], secondary)
    return best[2] if best is not None else fallback


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


def default_reason(aspect: str, polarity: str, ux_secondary_label: str) -> str:
    if ux_secondary_label != FALLBACK_SECONDARY_LABEL and aspect == 'unknown':
        return ux_secondary_label
    if polarity == 'POSITIVE':
        return POSITIVE_REASONS.get(aspect, POSITIVE_REASONS['unknown'])
    if polarity == 'NEGATIVE':
        return NEGATIVE_REASONS.get(aspect, NEGATIVE_REASONS['unknown'])
    return '无明显问题'


def normalize_reason(value: Any, aspect: str, polarity: str, ux_secondary_label: str) -> str:
    raw = normalize_cell(value)
    if not raw:
        return default_reason(aspect, polarity, ux_secondary_label)
    return raw[:20]


def evidence_from_text(text: str) -> str:
    cleaned = normalize_cell(text).replace('\n', ' ')
    return cleaned[:40]


def semantic_fields(
    taxonomy: TaxonomyDefinition,
    ux_secondary_label: str,
    aspect: str,
    polarity: str,
    review: str,
    item: dict[str, Any] | None = None,
) -> dict[str, Any]:
    item = item or {}
    primary_label = primary_for_secondary(taxonomy, ux_secondary_label)
    return {
        'uxPrimaryLabel': primary_label,
        'uxSecondaryLabel': ux_secondary_label,
        'standardizedReason': normalize_reason(
            item.get('standardizedReason') or item.get('reason'),
            aspect,
            polarity,
            ux_secondary_label,
        ),
        'evidence': normalize_cell(item.get('evidence'))[:40] or evidence_from_text(review),
        'negativeIntensityScore': clamp_int(item.get('negativeIntensityScore'), 1 if polarity != 'NEGATIVE' else 3, 1, 5),
    }


def build_clusters(aspect_sentiments: list[AspectSentimentResult]) -> list[IssueClusterResult]:
    negative_labels = [
        (item['aspect'], item['uxPrimaryLabel'], item['uxSecondaryLabel'])
        for item in aspect_sentiments
        if item['polarity'] == 'NEGATIVE' and item['uxSecondaryLabel'] != FALLBACK_SECONDARY_LABEL
    ]
    counter = Counter(negative_labels)
    clusters: list[IssueClusterResult] = [
        {
            'aspect': aspect,
            'title': CLUSTER_TITLES.get(aspect, f'{ux_secondary_label}反馈待优化'),
            'mentionCount': count,
            'uxPrimaryLabel': ux_primary_label,
            'uxSecondaryLabel': ux_secondary_label,
        }
        for (aspect, ux_primary_label, ux_secondary_label), count in counter.items()
    ]
    return sorted(clusters, key=cluster_sort_key)


def cluster_sort_key(item: IssueClusterResult) -> tuple[int, str]:
    return (-item['mentionCount'], item['uxSecondaryLabel'])


def fallback_analysis(reviews: list[str], taxonomy: TaxonomyDefinition) -> list[AspectSentimentResult]:
    aspect_sentiments: list[AspectSentimentResult] = []
    for idx, review in enumerate(reviews):
        secondary = detect_taxonomy_secondary(review, taxonomy)
        ux_secondary_label = secondary['label']
        aspect = legacy_aspect_for_secondary(ux_secondary_label)
        polarity = detect_polarity(review)
        aspect_sentiments.append(
            {
                'reviewIndex': idx,
                'aspect': aspect,
                'polarity': polarity,
                'score': score_from_polarity(polarity),
                'confidence': confidence_from_text(review),
                **semantic_fields(taxonomy, ux_secondary_label, aspect, polarity, review),
            }
        )
    return aspect_sentiments


def parse_llm_results(
    text: str,
    expected_count: int,
    reviews: list[str],
    taxonomy: TaxonomyDefinition,
) -> list[AspectSentimentResult]:
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
        ux_secondary_label = normalize_ux_secondary_label(taxonomy, item.get('uxSecondaryLabel'))
        aspect = legacy_aspect_for_secondary(ux_secondary_label)
        polarity = normalize_polarity(item.get('sentiment') or item.get('label'))
        fallback_confidence = confidence_from_text(reviews[idx])
        results.append(
            {
                'reviewIndex': idx,
                'aspect': aspect,
                'polarity': polarity,
                'score': score_from_polarity(polarity),
                'confidence': clamp_confidence(item.get('confidence'), fallback_confidence),
                **semantic_fields(taxonomy, ux_secondary_label, aspect, polarity, reviews[idx], item),
            }
        )
    return results


def analyze_reviews(
    product_code: str,
    reviews: list[str],
    taxonomy_payload: Any | None = None,
) -> tuple[list[AspectSentimentResult], list[IssueClusterResult], str]:
    taxonomy = normalize_taxonomy(taxonomy_payload)
    config = llm_config()
    if not reviews:
        return [], [], 'empty'
    if config is None:
        aspect_sentiments = fallback_analysis(reviews, taxonomy)
        return aspect_sentiments, build_clusters(aspect_sentiments), 'local-fallback'
    try:
        response_text = call_openai_compatible(product_code, reviews, config, taxonomy)
        aspect_sentiments = parse_llm_results(response_text, len(reviews), reviews, taxonomy)
        return aspect_sentiments, build_clusters(aspect_sentiments), 'llm'
    except (urllib.error.URLError, TimeoutError, ValueError, json.JSONDecodeError):
        aspect_sentiments = fallback_analysis(reviews, taxonomy)
        return aspect_sentiments, build_clusters(aspect_sentiments), 'local-fallback-after-llm-error'
