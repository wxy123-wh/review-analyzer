from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.cluster import KMeans
from sklearn.metrics import silhouette_score
import numpy as np
import logging

logger = logging.getLogger(__name__)

def cluster_reviews(docs, k_min, k_max):
    """
    Cluster reviews using TF-IDF and KMeans with adaptive K selection.
    """
    if not docs:
        return []

    # 1. Prepare texts
    # Prefer tokens joined by space if available, else raw text (for TF-IDF)
    corpus = []
    doc_ids = []
    ids_map = {} # index -> doc_id
    
    for i, doc in enumerate(docs):
        text = " ".join(doc.tokens) if doc.tokens else doc.text
        if not text.strip():
            continue
        corpus.append(text)
        doc_ids.append(doc.id)
        ids_map[i] = doc.id

    if len(corpus) < k_min:
        logger.warning(f"Not enough documents ({len(corpus)}) for k_min={k_min}. Returning single cluster.")
        return [_create_single_cluster(docs)]

    # 2. Vectorize
    vectorizer = TfidfVectorizer(max_features=1000, stop_words='english') # 'english' might not suit Chinese, but better than nothing if no stopwords file provided
    try:
        tfidf_matrix = vectorizer.fit_transform(corpus)
    except ValueError:
        return [_create_single_cluster(docs)]

    # 3. Determine best K
    best_k = k_min
    best_score = -1
    
    # If corpus size is small, cap k_max
    real_k_max = min(k_max, len(corpus) - 1)
    if real_k_max < k_min:
        real_k_max = k_min

    if real_k_max > k_min:
        for k in range(k_min, real_k_max + 1):
            kmeans = KMeans(n_clusters=k, random_state=42, n_init=10)
            labels = kmeans.fit_predict(tfidf_matrix)
            score = silhouette_score(tfidf_matrix, labels)
            if score > best_score:
                best_score = score
                best_k = k
    else:
        best_k = k_min

    # 4. Final Clustering
    kmeans = KMeans(n_clusters=best_k, random_state=42, n_init=10)
    labels = kmeans.fit_predict(tfidf_matrix)

    # 5. Extract Results
    clusters = []
    feature_names = np.array(vectorizer.get_feature_names_out())
    
    for i in range(best_k):
        # Find docs in this cluster
        indices = np.where(labels == i)[0]
        if len(indices) == 0:
            continue
            
        cluster_doc_ids = [doc_ids[idx] for idx in indices]
        
        # Calculate Neg Rate
        neg_count = 0
        total_count = len(indices)
        for idx in indices:
            # Find original doc to check sentiment
            # We need to map back to original input doc list since 'corpus' index matches 'docs' index (skipping empty)
            # Actually we built doc_ids list which maps corpus index to ID. We need to find doc by ID.
            # Optimization: Pre-map ID to Sentiment
            pass 
        
        # Calculate Top Terms using centroid
        centroid = kmeans.cluster_centers_[i]
        top_indices = centroid.argsort()[-10:][::-1] # Top 10 terms
        top_terms = feature_names[top_indices].tolist()
        
        # Calculate neg rate
        cluster_neg_rate = _calculate_neg_rate(cluster_doc_ids, docs)

        clusters.append({
            "review_ids": cluster_doc_ids,
            "top_terms": top_terms,
            "neg_rate": cluster_neg_rate
        })
        
    # Sort clusters by size desc
    clusters.sort(key=lambda x: len(x["review_ids"]), reverse=True)
    return clusters

def _create_single_cluster(docs):
    return {
        "review_ids": [d.id for d in docs],
        "top_terms": ["all"],
        "neg_rate": _calculate_neg_rate([d.id for d in docs], docs)
    }

def _calculate_neg_rate(ids, all_docs):
    target_ids = set(ids)
    neg = 0
    total = 0
    for d in all_docs:
        if d.id in target_ids:
            total += 1
            if d.sentiment_label == "NEG":
                neg += 1
    return neg / total if total > 0 else 0.0
