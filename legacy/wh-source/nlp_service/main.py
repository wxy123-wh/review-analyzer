from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from typing import List, Optional
import uvicorn
import logging
from clustering import cluster_reviews
from topic_modeling import extract_topics

# Configuration
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="Reputation NLP Service")

# Data Models (Matching Java NlpClient)
class ReviewDoc(BaseModel):
    id: int
    text: str
    tokens: List[str] = []
    sentiment_label: Optional[str] = None

class ClusterRequest(BaseModel):
    docs: List[ReviewDoc]
    k_min: int = Field(default=2, alias="k_min")
    k_max: int = Field(default=8, alias="k_max")

class ClusterItem(BaseModel):
    review_ids: List[int] = Field(..., alias="review_ids")
    top_terms: List[str] = Field(..., alias="top_terms")
    neg_rate: float = Field(..., alias="neg_rate")

class ClusterResponse(BaseModel):
    clusters: List[ClusterItem]

class TopicRequest(BaseModel):
    docs: List[ReviewDoc]
    num_topics: int = Field(default=3, alias="num_topics")

class TopicItem(BaseModel):
    topic_id: int = Field(..., alias="topic_id")
    key_words: List[str] = Field(..., alias="key_words")
    weight: float
    evidence_ids: List[int] = Field(..., alias="evidence_ids")

class TopicResponse(BaseModel):
    topics: List[TopicItem]

@app.post("/cluster", response_model=ClusterResponse)
def api_cluster(req: ClusterRequest):
    if not req.docs:
        return ClusterResponse(clusters=[])
    
    try:
        logger.info(f"Received cluster request with {len(req.docs)} docs")
        results = cluster_reviews(req.docs, req.k_min, req.k_max)
        return ClusterResponse(clusters=results)
    except Exception as e:
        logger.error(f"Clustering error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/topics", response_model=TopicResponse)
def api_topics(req: TopicRequest):
    if not req.docs:
        return TopicResponse(topics=[])
    
    try:
        logger.info(f"Received topic request with {len(req.docs)} docs")
        results = extract_topics(req.docs, req.num_topics)
        return TopicResponse(topics=results)
    except Exception as e:
        logger.error(f"Topic modeling error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/health")
def health_check():
    """健康检查端点"""
    return {"status": "ok", "service": "NLP Service"}

if __name__ == "__main__":
    logger.info("=" * 60)
    logger.info("启动 NLP 服务...")
    logger.info("服务地址: http://0.0.0.0:8000")
    logger.info("健康检查: http://0.0.0.0:8000/health")
    logger.info("=" * 60)
    uvicorn.run(app, host="0.0.0.0", port=8000, log_level="info")

