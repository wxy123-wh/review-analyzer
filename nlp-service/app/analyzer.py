from collections import Counter
from typing import TypedDict

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

CLUSTER_TITLES = {
    'battery': '续航体验波动',
    'bluetooth': '蓝牙连接稳定性不足',
    'noise-canceling': '降噪效果一致性不足',
    'comfort': '佩戴舒适度反馈分化',
    'microphone': '通话收音表现待优化',
}


class AspectSentimentResult(TypedDict):
    reviewIndex: int
    aspect: str
    polarity: str
    score: float
    confidence: float


class IssueClusterResult(TypedDict):
    aspect: str
    title: str
    mentionCount: int


def detect_aspect(text: str) -> str:
    for aspect, keywords in ASPECT_KEYWORDS.items():
        if any(keyword in text for keyword in keywords):
            return aspect
    return 'unknown'


def detect_polarity(text: str) -> str:
    if any(marker in text for marker in NEGATIVE_MARKERS):
        return 'NEGATIVE'
    if any(marker in text for marker in POSITIVE_MARKERS):
        return 'POSITIVE'
    return 'NEUTRAL'


def score_from_polarity(polarity: str) -> float:
    if polarity == 'NEGATIVE':
        return -0.78
    if polarity == 'POSITIVE':
        return 0.82
    return 0


def confidence_from_text(text: str) -> float:
    return 0.74 if len(text) < 8 else 0.88


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
