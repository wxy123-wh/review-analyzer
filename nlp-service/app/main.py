from datetime import datetime, timezone

from fastapi import FastAPI, HTTPException

from .analyzer import (
    LlmAnalysisError,
    LlmConfigurationError,
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
    try:
        aspect_sentiments, issue_clusters, mode = analyze_reviews(payload.productCode, payload.reviews, payload.taxonomy)
    except LlmConfigurationError as ex:
        raise HTTPException(
            status_code=503,
            detail={
                'code': ex.code,
                'message': 'LLM API key/model is not configured. Set OPENAI_API_KEY and OPENAI_MODEL, or explicitly set NLP_FORCE_LOCAL=true for rule-based demo mode.',
            },
        ) from ex
    except LlmAnalysisError as ex:
        raise HTTPException(
            status_code=502,
            detail={
                'code': ex.code,
                'message': 'LLM analysis failed and local fallback is disabled.',
            },
        ) from ex

    return {
        'jobId': payload.jobId,
        'aspectSentiments': aspect_sentiments,
        'issueClusters': issue_clusters,
        'analysisMode': mode,
        'llmUsed': mode == 'llm',
        'fallbackReason': None if mode in {'llm', 'empty'} else mode,
    }
