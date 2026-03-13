from pydantic import BaseModel


class AnalyzeRequest(BaseModel):
    jobId: str
    productCode: str
    reviews: list[str]


class AspectSentiment(BaseModel):
    reviewIndex: int
    aspect: str
    polarity: str
    score: float
    confidence: float


class IssueCluster(BaseModel):
    aspect: str
    title: str
    mentionCount: int


class AnalyzeResponse(BaseModel):
    jobId: str
    aspectSentiments: list[AspectSentiment]
    issueClusters: list[IssueCluster]
