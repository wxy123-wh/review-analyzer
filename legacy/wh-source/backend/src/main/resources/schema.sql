CREATE TABLE IF NOT EXISTS platform (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) UNIQUE NOT NULL,
  created_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS product (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  brand VARCHAR(64) NULL,
  model VARCHAR(64) NULL,
  type VARCHAR(32) NULL,
  is_competitor TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @db := DATABASE();
SET @product_type_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db
    AND TABLE_NAME = 'product'
    AND COLUMN_NAME = 'type'
);
SET @product_type_sql := IF(@product_type_exists = 0,
  'ALTER TABLE product ADD COLUMN type VARCHAR(32) NULL',
  'SELECT 1'
);
PREPARE stmt FROM @product_type_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @is_competitor_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db
    AND TABLE_NAME = 'product'
    AND COLUMN_NAME = 'is_competitor'
);
SET @is_competitor_sql := IF(@is_competitor_exists = 0,
  'ALTER TABLE product ADD COLUMN is_competitor TINYINT(1) NOT NULL DEFAULT 0',
  'SELECT 1'
);
PREPARE stmt FROM @is_competitor_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS review (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  platform_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  review_id_raw VARCHAR(128) NULL,
  rating INT NULL,
  content_raw TEXT NOT NULL,
  content_clean TEXT NOT NULL,
  tokens_json JSON NULL,
  review_time DATETIME NULL,
  like_count INT NULL,
  batch_id VARCHAR(64) NULL,
  hash VARCHAR(64) NOT NULL UNIQUE,
  overall_sentiment_label VARCHAR(8) NOT NULL DEFAULT 'NEU',
  overall_sentiment_score DOUBLE NOT NULL DEFAULT 0.0,
  created_at DATETIME NOT NULL,
  INDEX idx_review_product_time (product_id, review_time),
  INDEX idx_review_platform (platform_id),
  INDEX idx_review_overall_sent (overall_sentiment_label),
  INDEX idx_review_batch (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @tokens_json_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db
    AND TABLE_NAME = 'review'
    AND COLUMN_NAME = 'tokens_json'
);
SET @tokens_json_sql := IF(@tokens_json_exists = 0,
  'ALTER TABLE review ADD COLUMN tokens_json JSON NULL',
  'SELECT 1'
);
PREPARE stmt FROM @tokens_json_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @batch_id_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db
    AND TABLE_NAME = 'review'
    AND COLUMN_NAME = 'batch_id'
);
SET @batch_id_sql := IF(@batch_id_exists = 0,
  'ALTER TABLE review ADD COLUMN batch_id VARCHAR(64) NULL',
  'SELECT 1'
);
PREPARE stmt FROM @batch_id_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS aspect (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(32) UNIQUE NOT NULL,
  product_type VARCHAR(32) NULL,
  keywords_json JSON NOT NULL,
  created_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @aspect_product_type_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db
    AND TABLE_NAME = 'aspect'
    AND COLUMN_NAME = 'product_type'
);
SET @aspect_product_type_sql := IF(@aspect_product_type_exists = 0,
  'ALTER TABLE aspect ADD COLUMN product_type VARCHAR(32) NULL',
  'SELECT 1'
);
PREPARE stmt FROM @aspect_product_type_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS review_aspect_result (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  review_id BIGINT NOT NULL,
  aspect_id BIGINT NOT NULL,
  hit_keywords_json JSON NOT NULL,
  sentiment_label VARCHAR(8) NOT NULL,
  sentiment_score DOUBLE NOT NULL DEFAULT 0.0,
  confidence DOUBLE NOT NULL DEFAULT 0.0,
  created_at DATETIME NOT NULL,
  INDEX idx_rar_review (review_id),
  INDEX idx_rar_aspect (aspect_id),
  INDEX idx_rar_aspect_sent (aspect_id, sentiment_label),
  CONSTRAINT fk_rar_review FOREIGN KEY (review_id) REFERENCES review(id),
  CONSTRAINT fk_rar_aspect FOREIGN KEY (aspect_id) REFERENCES aspect(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS topic_result (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  start_date DATE NULL,
  end_date DATE NULL,
  topic_count INT NOT NULL,
  topics_json JSON NOT NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_topic_product_time (product_id, start_date, end_date),
  INDEX idx_topic_created (product_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `cluster` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  start_date DATE NULL,
  end_date DATE NULL,
  k INT NOT NULL,
  top_terms_json JSON NOT NULL,
  size INT NOT NULL,
  neg_rate DOUBLE NOT NULL DEFAULT 0.0,
  created_at DATETIME NOT NULL,
  INDEX idx_cluster_product_time (product_id, start_date, end_date),
  INDEX idx_cluster_created (product_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS review_cluster (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  review_id BIGINT NOT NULL,
  cluster_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_rc_review (review_id),
  INDEX idx_rc_cluster (cluster_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS alert (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  metric VARCHAR(32) NOT NULL,
  aspect_id BIGINT NULL,
  window_start DATE NOT NULL,
  window_end DATE NOT NULL,
  current_value DOUBLE NOT NULL,
  prev_value DOUBLE NOT NULL,
  threshold DOUBLE NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'new',
  created_at DATETIME NOT NULL,
  INDEX idx_alert_product_status (product_id, status),
  INDEX idx_alert_product_window (product_id, window_start, window_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS suggestion_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  match_type VARCHAR(16) NOT NULL,
  match_value VARCHAR(128) NOT NULL,
  suggestion_text TEXT NOT NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_st_match (match_type, match_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS suggestion_instance (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  ref_type VARCHAR(16) NOT NULL,
  ref_id BIGINT NOT NULL,
  suggestion_text TEXT NOT NULL,
  evidence_json JSON NOT NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_si_product_time (product_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `event` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  name VARCHAR(128) NOT NULL,
  type VARCHAR(16) NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_event_product_time (product_id, start_date, end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
