package com.wh.review.backend.persistence;

import jakarta.annotation.PostConstruct;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PersistenceSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public PersistenceSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void init() {
        List<String> statements = List.of(
                """
                CREATE TABLE IF NOT EXISTS products (
                    id BIGSERIAL PRIMARY KEY,
                    product_code VARCHAR(64) UNIQUE NOT NULL,
                    product_name VARCHAR(255) NOT NULL,
                    brand VARCHAR(128) NOT NULL,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS improvement_actions (
                    id BIGSERIAL PRIMARY KEY,
                    product_id BIGINT NOT NULL REFERENCES products(id),
                    issue_cluster_id BIGINT,
                    issue_ref VARCHAR(128),
                    action_name VARCHAR(255) NOT NULL,
                    action_desc TEXT,
                    launched_at TIMESTAMP WITH TIME ZONE,
                    owner VARCHAR(128),
                    status VARCHAR(32) NOT NULL DEFAULT 'planned',
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS sync_jobs (
                    id BIGSERIAL PRIMARY KEY,
                    provider VARCHAR(64) NOT NULL,
                    platform VARCHAR(64) NOT NULL DEFAULT 'taobao',
                    target_product_code VARCHAR(64) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    fetched_count INTEGER NOT NULL DEFAULT 0,
                    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    finished_at TIMESTAMP WITH TIME ZONE,
                    error_message TEXT
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS analysis_jobs (
                    id BIGSERIAL PRIMARY KEY,
                    product_code VARCHAR(64) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    finished_at TIMESTAMP WITH TIME ZONE,
                    error_message TEXT
                )
                """,
                "ALTER TABLE improvement_actions ADD COLUMN IF NOT EXISTS issue_ref VARCHAR(128)",
                "ALTER TABLE sync_jobs ADD COLUMN IF NOT EXISTS platform VARCHAR(64) NOT NULL DEFAULT 'taobao'",
                "ALTER TABLE sync_jobs ADD COLUMN IF NOT EXISTS fetched_count INTEGER NOT NULL DEFAULT 0"
        );

        for (String statement : statements) {
            jdbcTemplate.execute(statement);
        }
    }
}
