package com.wh.reputation.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReviewAspectResultRepository extends JpaRepository<ReviewAspectResultEntity, Long> {
    @Modifying
    @Query("delete from ReviewAspectResultEntity r where r.review.id in :reviewIds")
    void deleteByReviewIdIn(@Param("reviewIds") Collection<Long> reviewIds);

    @EntityGraph(attributePaths = {"aspect"})
    List<ReviewAspectResultEntity> findByReviewIdIn(Collection<Long> reviewIds);

    @EntityGraph(attributePaths = {"aspect"})
    List<ReviewAspectResultEntity> findByReviewId(Long reviewId);
}

