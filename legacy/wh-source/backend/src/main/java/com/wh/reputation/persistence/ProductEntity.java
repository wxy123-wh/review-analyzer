package com.wh.reputation.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "product")
public class ProductEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 64)
    private String brand;

    @Column(length = 64)
    private String model;

    @Column(length = 32)
    private String type;

    @Column(name = "is_competitor", nullable = false)
    private boolean isCompetitor;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ProductEntity() {
    }

    public ProductEntity(String name, String brand, String model, String type, boolean isCompetitor,
            LocalDateTime createdAt) {
        this.name = name;
        this.brand = brand;
        this.model = model;
        this.type = type;
        this.isCompetitor = isCompetitor;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getBrand() {
        return brand;
    }

    public String getModel() {
        return model;
    }

    public String getType() {
        return type;
    }

    public boolean isCompetitor() {
        return isCompetitor;
    }

    public void setCompetitor(boolean competitor) {
        isCompetitor = competitor;
    }
}
