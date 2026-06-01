CREATE TABLE investments (
    id          BIGSERIAL                PRIMARY KEY,
    symbol      VARCHAR(20)              NOT NULL,
    quantity    DECIMAL(18, 8)           NOT NULL,
    platform    VARCHAR(100),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
