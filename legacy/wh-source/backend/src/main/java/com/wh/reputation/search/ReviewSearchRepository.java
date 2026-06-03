package com.wh.reputation.search;

import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface ReviewSearchRepository extends ElasticsearchRepository<ReviewDocument, Long> {

    @Query("{\"knn\": {\"field\": \"embedding\", \"query_vector\": ?0, \"k\": 100, \"num_candidates\": 200, \"filter\": {\"term\": {\"productId\": ?1}} }}")
    List<ReviewDocument> searchByVector(List<Float> embedding, Long productId);

    @Query("{\"knn\": {\"field\": \"embedding\", \"query_vector\": ?0, \"k\": 100, \"num_candidates\": 200 }}")
    List<ReviewDocument> searchByVectorGlobal(List<Float> embedding);
}
