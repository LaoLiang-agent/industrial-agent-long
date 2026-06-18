CREATE TABLE IF NOT EXISTS work_orders (
    id            VARCHAR(20) PRIMARY KEY,
    device_id     VARCHAR(64)  NOT NULL,
    type          VARCHAR(20)  NOT NULL,
    priority      VARCHAR(10)  NOT NULL,
    description   TEXT,
    assignee      VARCHAR(64)  DEFAULT '值班工程师',
    status        VARCHAR(20)  DEFAULT 'PENDING',
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    completed_at  TIMESTAMP,
    closed_at     TIMESTAMP
);
