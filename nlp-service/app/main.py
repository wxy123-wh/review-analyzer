from datetime import datetime, timezone

from fastapi import FastAPI

from .analyzer import (
    build_clusters,
    confidence_from_text,
    detect_aspect,
    detect_polarity,
    score_from_polarity,
)
from .schemas import AnalyzeRequest, AnalyzeResponse

app = FastAPI(title='wh-nlp-service', version='0.1.0')


@app.get('/health')
def health() -> dict:
    return {
        'status': 'UP',
        'timestamp': datetime.now(timezone.utc).isoformat(),
    }


@app.post('/analyze', response_model=AnalyzeResponse)
def analyze(payload: AnalyzeRequest) -> dict:
    aspect_sentiments: list[dict] = []

    for idx, review in enumerate(payload.reviews):
        aspect = detect_aspect(review)
        polarity = detect_polarity(review)
        aspect_sentiments.append(
            {
                'reviewIndex': idx,
                'aspect': aspect,
                'polarity': polarity,
                'score': score_from_polarity(polarity),
                'confidence': confidence_from_text(review),
            }
        )

    return {
        'jobId': payload.jobId,
        'aspectSentiments': aspect_sentiments,
        'issueClusters': build_clusters(aspect_sentiments),
    }
