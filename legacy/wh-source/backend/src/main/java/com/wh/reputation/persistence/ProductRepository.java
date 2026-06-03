package com.wh.reputation.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {
    Optional<ProductEntity> findFirstByNameOrderByIdAsc(String name);

    @Query("""
            select p from ProductEntity p
            where p.name = :name
              and ((:brand is null and p.brand is null) or p.brand = :brand)
              and ((:model is null and p.model is null) or p.model = :model)
            """)
    Optional<ProductEntity> findExisting(
            @Param("name") String name,
            @Param("brand") String brand,
            @Param("model") String model
    );
}
