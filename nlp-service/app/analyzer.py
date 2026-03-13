from collections import Counter

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
    'audio': {'音质', '低音', '高音', '音场'},
    'noise_canceling': {'降噪', '通透'},
    'battery': {'续航', '电池', '充电'},
    'comfort': {'佩戴', '耳压', '舒适'},
    'connectivity': {'连接', '断连', '断开', '蓝牙'},
    'call_quality': {'通话', '麦克风', '收音'},
}


def detect_aspect(text: str) -> str:
    for aspect, keywords in ASPECT_KEYWORDS.items():
        if any(keyword in text for keyword in keywords):
            return aspect
    return 'general'


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


def build_clusters(aspect_sentiments: list[dict]) -> list[dict]:
    negative_aspects = [
        item['aspect']
        for item in aspect_sentiments
        if item['polarity'] == 'NEGATIVE'
    ]
    counter = Counter(negative_aspects)
    clusters = [
        {
            'aspect': aspect,
            'title': f'{aspect} related negative feedback',
            'mentionCount': count,
        }
        for aspect, count in counter.items()
    ]
    return sorted(clusters, key=lambda item: (-item['mentionCount'], item['aspect']))
