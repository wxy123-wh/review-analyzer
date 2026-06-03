package com.wh.reputation.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.time.LocalDateTime;

public interface ReviewRepository extends JpaRepository<ReviewEntity, Long>, JpaSpecificationExecutor<ReviewEntity> {
        boolean existsByHash(String hash);

        @Query("select r.hash from ReviewEntity r where r.hash in :hashes")
        List<String> findExistingHashes(@Param("hashes") Collection<String> hashes);

        @Query("select distinct r.product.id from ReviewEntity r where r.tokensJson is null")
        List<Long> findProductIdsWithMissingTokens();

        @Query("""
                        select r.id from ReviewEntity r
                        where r.product.id = :productId
                          and (:start is null or coalesce(r.reviewTime, r.createdAt) >= :start)
                          and (:endExclusive is null or coalesce(r.reviewTime, r.createdAt) < :endExclusive)
                        order by r.id asc
                        """)
        List<Long> findIdsForAnalysis(
                        @Param("productId") Long productId,
                        @Param("start") LocalDateTime start,
                        @Param("endExclusive") LocalDateTime endExclusive);

        @Query("select r.id from ReviewEntity r")
        List<Long> findAllIds();
}
