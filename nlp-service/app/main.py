from datetime import datetime, timezone

from fastapi import FastAPI

from .analyzer import (
    analyze_reviews,
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
    aspect_sentiments, issue_clusters, _mode = analyze_reviews(payload.productCode, payload.reviews)

    return {
        'jobId': payload.jobId,
        'aspectSentiments': aspect_sentiments,
        'issueClusters': issue_clusters,
    }
