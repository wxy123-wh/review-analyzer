from pydantic import AliasChoices, BaseModel, ConfigDict, Field


class UxSecondaryLabelConfig(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    label: str = Field(validation_alias=AliasChoices('label', 'labelName'))
    synonyms: list[str] = Field(default_factory=list)
    description: str | None = None


class UxPrimaryLabelConfig(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    label: str = Field(validation_alias=AliasChoices('label', 'labelName'))
    synonyms: list[str] = Field(default_factory=list)
    description: str | None = None
    secondaryLabels: list[UxSecondaryLabelConfig] = Field(
        default_factory=list,
        validation_alias=AliasChoices('secondaryLabels', 'uxSecondaryLabels'),
    )


class TaxonomyFallbackLabels(BaseModel):
    primaryLabel: str = '无明显问题'
    secondaryLabel: str = '无明显问题'


class AnalyzeTaxonomy(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    uxPrimaryLabels: list[UxPrimaryLabelConfig] = Field(
        default_factory=list,
        validation_alias=AliasChoices('uxPrimaryLabels', 'primaryLabels'),
    )
    fallbackLabels: TaxonomyFallbackLabels = Field(default_factory=TaxonomyFallbackLabels)


class AnalyzeRequest(BaseModel):
    jobId: str
    productCode: str
    reviews: list[str]
    taxonomy: AnalyzeTaxonomy | None = None


class AspectSentiment(BaseModel):
    reviewIndex: int
    aspect: str
    polarity: str
    score: float
    confidence: float
    uxPrimaryLabel: str | None = None
    uxSecondaryLabel: str | None = None
    standardizedReason: str | None = None
    evidence: str | None = None
    negativeIntensityScore: int | None = None


class IssueCluster(BaseModel):
    aspect: str
    title: str
    mentionCount: int
    uxPrimaryLabel: str | None = None
    uxSecondaryLabel: str | None = None


class AnalyzeResponse(BaseModel):
    jobId: str
    aspectSentiments: list[AspectSentiment]
    issueClusters: list[IssueCluster]
