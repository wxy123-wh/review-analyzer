from sklearn.feature_extraction.text import CountVectorizer
from sklearn.decomposition import LatentDirichletAllocation
import numpy as np
import logging

logger = logging.getLogger(__name__)

def extract_topics(docs, num_topics):
    """
    Extract topics using LDA.
    """
    if not docs:
        return []

    # 1. Prepare Text
    corpus = []
    doc_ids = []
    
    for doc in docs:
        text = " ".join(doc.tokens) if doc.tokens else doc.text
        if not text.strip():
            continue
        corpus.append(text)
        doc_ids.append(doc.id)

    if len(corpus) == 0:
        return []

    real_topics = min(num_topics, len(corpus))
    if real_topics < 1:
        real_topics = 1

    # 2. Vectorize (LDA uses Counts)
    vectorizer = CountVectorizer(max_features=1000, stop_words='english')
    try:
        dtm = vectorizer.fit_transform(corpus)
    except ValueError:
        # Empty vocabulary or similar error
        return []

    # 3. LDA Model
    lda = LatentDirichletAllocation(n_components=real_topics, random_state=42)
    lda.fit(dtm)

    # 4. Extract Results
    feature_names = np.array(vectorizer.get_feature_names_out())
    topic_results = []
    
    # Document-Topic Distribution to find evidence
    doc_topic_dist = lda.transform(dtm)
    
    for topic_idx, topic in enumerate(lda.components_):
        # Top keywords
        top_indices = topic.argsort()[-10:][::-1]
        top_words = feature_names[top_indices].tolist()
        
        # Calculate weight (sum of topic probabilities across all docs / total docs)
        # Or simple average probability
        topic_weight = np.mean(doc_topic_dist[:, topic_idx])
        
        # Find Evidence IDs (docs with high probability for this topic)
        # Threshold: > 1/num_topics or top N docs
        doc_indices = np.argsort(doc_topic_dist[:, topic_idx])[-5:][::-1] # Top 5 evidence
        evidence_ids = [doc_ids[i] for i in doc_indices if doc_topic_dist[i, topic_idx] > 0.1]
        
        topic_results.append({
            "topic_id": topic_idx,
            "key_words": top_words,
            "weight": float(topic_weight),
            "evidence_ids": evidence_ids
        })
        
    # Sort by weight desc
    topic_results.sort(key=lambda x: x["weight"], reverse=True)
    
    return topic_results
