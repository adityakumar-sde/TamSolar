CREATE TABLE IF NOT EXISTS inquiries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(254) NOT NULL,
    customer_type VARCHAR(30) NOT NULL,
    enquiry_type VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS inquiry_workflow (
    inquiry_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    internal_notes TEXT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (inquiry_id),
    CONSTRAINT fk_inquiry_workflow_inquiry FOREIGN KEY (inquiry_id)
        REFERENCES inquiries(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO inquiry_workflow (inquiry_id, status)
SELECT id, 'NEW' FROM inquiries;

CREATE TABLE IF NOT EXISTS inquiry_submission_idempotency (
    idempotency_key VARCHAR(100) NOT NULL,
    inquiry_id BIGINT NOT NULL,
    PRIMARY KEY (idempotency_key),
    UNIQUE KEY uq_inquiry_submission_id (inquiry_id),
    CONSTRAINT fk_inquiry_submission_inquiry FOREIGN KEY (inquiry_id)
        REFERENCES inquiries(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS email_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    inquiry_id BIGINT NOT NULL,
    idempotency_key VARCHAR(100) NULL,
    kind VARCHAR(30) NOT NULL,
    sender VARCHAR(254) NOT NULL,
    recipient VARCHAR(254) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    text_body MEDIUMTEXT NOT NULL,
    html_body MEDIUMTEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed_at TIMESTAMP NULL,
    last_error VARCHAR(500) NULL,
    message_id VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_email_outbox_idempotency (idempotency_key),
    KEY idx_email_outbox_pending (status, next_attempt_at, id),
    KEY idx_email_outbox_inquiry (inquiry_id, created_at),
    CONSTRAINT fk_email_outbox_inquiry FOREIGN KEY (inquiry_id)
        REFERENCES inquiries(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
