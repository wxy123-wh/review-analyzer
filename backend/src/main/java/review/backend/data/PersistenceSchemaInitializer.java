package review.backend.data;

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
                CREATE TABLE IF NOT EXISTS competitors (
                    id BIGSERIAL PRIMARY KEY,
                    product_id BIGINT NOT NULL REFERENCES products(id),
                    competitor_product_id BIGINT NOT NULL REFERENCES products(id),
                    relation_type VARCHAR(32) NOT NULL DEFAULT 'same_segment',
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(product_id, competitor_product_id)
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
                    error_message TEXT,
                    analysis_handoff_status VARCHAR(64) NOT NULL DEFAULT 'NOT_READY',
                    analysis_handoff_note TEXT,
                    source_url TEXT,
                    external_job_id VARCHAR(128),
                    taxonomy_id BIGINT
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS reviews_raw (
                    id BIGSERIAL PRIMARY KEY,
                    source VARCHAR(64) NOT NULL,
                    source_review_id VARCHAR(128) NOT NULL,
                    product_id BIGINT NOT NULL REFERENCES products(id),
                    rating NUMERIC(3,1),
                    content TEXT NOT NULL,
                    review_time TIMESTAMP WITH TIME ZONE,
                    anonymized_author_id VARCHAR(128),
                    provider VARCHAR(64),
                    platform VARCHAR(64),
                    external_product_code VARCHAR(128),
                    sync_job_id BIGINT REFERENCES sync_jobs(id),
                    external_dedupe_key VARCHAR(255),
                    fetch_metadata TEXT,
                    fetched_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(source, source_review_id)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS review_aspects (
                    id BIGSERIAL PRIMARY KEY,
                    review_id BIGINT NOT NULL REFERENCES reviews_raw(id),
                    aspect VARCHAR(64) NOT NULL,
                    ux_primary_label VARCHAR(64),
                    ux_secondary_label VARCHAR(64),
                    sentiment_polarity VARCHAR(16) NOT NULL,
                    sentiment_score NUMERIC(5,4) NOT NULL,
                    confidence NUMERIC(5,4) NOT NULL,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS ux_taxonomies (
                    id BIGSERIAL PRIMARY KEY,
                    name VARCHAR(128) NOT NULL,
                    product_category VARCHAR(128) NOT NULL,
                    version INTEGER NOT NULL DEFAULT 1,
                    active BOOLEAN NOT NULL DEFAULT TRUE,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS ux_primary_labels (
                    id BIGSERIAL PRIMARY KEY,
                    taxonomy_id BIGINT NOT NULL REFERENCES ux_taxonomies(id),
                    label_name VARCHAR(64) NOT NULL,
                    sort_order INTEGER NOT NULL DEFAULT 0
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS ux_secondary_labels (
                    id BIGSERIAL PRIMARY KEY,
                    taxonomy_id BIGINT NOT NULL REFERENCES ux_taxonomies(id),
                    primary_label_id BIGINT NOT NULL REFERENCES ux_primary_labels(id),
                    label_name VARCHAR(64) NOT NULL,
                    synonyms TEXT,
                    description TEXT,
                    sort_order INTEGER NOT NULL DEFAULT 0,
                    enabled BOOLEAN NOT NULL DEFAULT TRUE
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS product_taxonomy_bindings (
                    product_code VARCHAR(64) PRIMARY KEY,
                    taxonomy_id BIGINT NOT NULL REFERENCES ux_taxonomies(id),
                    bound_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS review_semantic_labels (
                    id BIGSERIAL PRIMARY KEY,
                    review_id BIGINT NOT NULL REFERENCES reviews_raw(id),
                    aspect VARCHAR(64) NOT NULL,
                    sentiment_polarity VARCHAR(16) NOT NULL,
                    confidence NUMERIC(5,4) NOT NULL,
                    taxonomy_id BIGINT,
                    taxonomy_version INTEGER,
                    ux_primary_label VARCHAR(64),
                    ux_secondary_label VARCHAR(64),
                    standardized_reason VARCHAR(64),
                    evidence TEXT,
                    negative_intensity_score INTEGER NOT NULL DEFAULT 1,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS issue_clusters (
                    id BIGSERIAL PRIMARY KEY,
                    product_id BIGINT NOT NULL REFERENCES products(id),
                    aspect VARCHAR(64) NOT NULL,
                    ux_primary_label VARCHAR(64),
                    ux_secondary_label VARCHAR(64),
                    title VARCHAR(255) NOT NULL,
                    keywords TEXT NOT NULL,
                    representative_review_ids TEXT NOT NULL,
                    severity_score NUMERIC(6,4) NOT NULL,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS issue_scores (
                    id BIGSERIAL PRIMARY KEY,
                    issue_cluster_id BIGINT NOT NULL REFERENCES issue_clusters(id),
                    negative_rate NUMERIC(6,4) NOT NULL,
                    mention_volume NUMERIC(10,2) NOT NULL,
                    trend_growth NUMERIC(6,4) NOT NULL,
                    competitor_gap NUMERIC(6,4) NOT NULL,
                    priority_score NUMERIC(6,4) NOT NULL,
                    weight_config JSONB NOT NULL,
                    scored_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS improvement_actions (
                    id BIGSERIAL PRIMARY KEY,
                    product_id BIGINT NOT NULL REFERENCES products(id),
                    issue_cluster_id BIGINT REFERENCES issue_clusters(id),
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
                CREATE TABLE IF NOT EXISTS validation_metrics (
                    id BIGSERIAL PRIMARY KEY,
                    action_id BIGINT NOT NULL REFERENCES improvement_actions(id),
                    window_start TIMESTAMP WITH TIME ZONE NOT NULL,
                    window_end TIMESTAMP WITH TIME ZONE NOT NULL,
                    before_metrics JSONB NOT NULL,
                    after_metrics JSONB NOT NULL,
                    conclusion TEXT,
                    calculated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS analysis_jobs (
                    id BIGSERIAL PRIMARY KEY,
                    product_code VARCHAR(64) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    finished_at TIMESTAMP WITH TIME ZONE,
                    error_message TEXT,
                    taxonomy_id BIGINT,
                    taxonomy_version INTEGER
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS data_quality_runs (
                    id BIGSERIAL PRIMARY KEY,
                    product_code VARCHAR(64) NOT NULL,
                    raw_count INTEGER NOT NULL DEFAULT 0,
                    cleaned_count INTEGER NOT NULL DEFAULT 0,
                    removed_count INTEGER NOT NULL DEFAULT 0,
                    html_cleaned_count INTEGER NOT NULL DEFAULT 0,
                    exact_duplicate_count INTEGER NOT NULL DEFAULT 0,
                    empty_content_count INTEGER NOT NULL DEFAULT 0,
                    invalid_json_count INTEGER NOT NULL DEFAULT 0,
                    summary_json TEXT NOT NULL,
                    imported_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """,
                "ALTER TABLE improvement_actions ADD COLUMN IF NOT EXISTS issue_ref VARCHAR(128)",
                "ALTER TABLE sync_jobs ADD COLUMN IF NOT EXISTS platform VARCHAR(64) NOT NULL DEFAULT 'taobao'",
                "ALTER TABLE sync_jobs ADD COLUMN IF NOT EXISTS fetched_count INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE sync_jobs ADD COLUMN IF NOT EXISTS analysis_handoff_status VARCHAR(64) NOT NULL DEFAULT 'NOT_READY'",
                "ALTER TABLE sync_jobs ADD COLUMN IF NOT EXISTS analysis_handoff_note TEXT",
                "ALTER TABLE sync_jobs ADD COLUMN IF NOT EXISTS source_url TEXT",
                "ALTER TABLE sync_jobs ADD COLUMN IF NOT EXISTS external_job_id VARCHAR(128)",
                "ALTER TABLE sync_jobs ADD COLUMN IF NOT EXISTS taxonomy_id BIGINT",
                "ALTER TABLE reviews_raw ADD COLUMN IF NOT EXISTS provider VARCHAR(64)",
                "ALTER TABLE reviews_raw ADD COLUMN IF NOT EXISTS platform VARCHAR(64)",
                "ALTER TABLE reviews_raw ADD COLUMN IF NOT EXISTS external_product_code VARCHAR(128)",
                "ALTER TABLE reviews_raw ADD COLUMN IF NOT EXISTS sync_job_id BIGINT",
                "ALTER TABLE reviews_raw ADD COLUMN IF NOT EXISTS external_dedupe_key VARCHAR(255)",
                "ALTER TABLE reviews_raw ADD COLUMN IF NOT EXISTS fetch_metadata TEXT",
                "ALTER TABLE review_aspects ADD COLUMN IF NOT EXISTS ux_primary_label VARCHAR(64)",
                "ALTER TABLE review_aspects ADD COLUMN IF NOT EXISTS ux_secondary_label VARCHAR(64)",
                "ALTER TABLE review_semantic_labels ADD COLUMN IF NOT EXISTS taxonomy_id BIGINT",
                "ALTER TABLE review_semantic_labels ADD COLUMN IF NOT EXISTS taxonomy_version INTEGER",
                "ALTER TABLE review_semantic_labels ADD COLUMN IF NOT EXISTS ux_primary_label VARCHAR(64)",
                "ALTER TABLE review_semantic_labels ADD COLUMN IF NOT EXISTS ux_secondary_label VARCHAR(64)",
                "ALTER TABLE review_semantic_labels ADD COLUMN IF NOT EXISTS standardized_reason VARCHAR(64)",
                "ALTER TABLE review_semantic_labels ADD COLUMN IF NOT EXISTS evidence TEXT",
                "ALTER TABLE review_semantic_labels ADD COLUMN IF NOT EXISTS negative_intensity_score INTEGER NOT NULL DEFAULT 1",
                "ALTER TABLE issue_clusters ADD COLUMN IF NOT EXISTS ux_primary_label VARCHAR(64)",
                "ALTER TABLE issue_clusters ADD COLUMN IF NOT EXISTS ux_secondary_label VARCHAR(64)",
                "ALTER TABLE analysis_jobs ADD COLUMN IF NOT EXISTS taxonomy_id BIGINT",
                "ALTER TABLE analysis_jobs ADD COLUMN IF NOT EXISTS taxonomy_version INTEGER"
        );

        for (String statement : statements) {
            jdbcTemplate.execute(statement);
        }
    }
}
