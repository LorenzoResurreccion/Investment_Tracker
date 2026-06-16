-- V3__multi_user_schema.sql
-- Normalize the schema into symbols, users, and holdings tables for multi-user support.

-- Step 1: Create symbols table
CREATE TABLE symbols (
    id          BIGSERIAL PRIMARY KEY,
    ticker      VARCHAR(20) NOT NULL UNIQUE,
    name        VARCHAR(255),
    exchange    VARCHAR(50),
    asset_type  VARCHAR(20),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Step 2: Create users table
CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(50) NOT NULL UNIQUE,
    email       VARCHAR(255) NOT NULL UNIQUE,
    cognito_sub VARCHAR(255) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Step 3: Create holdings table with FK constraints and composite unique constraint
CREATE TABLE holdings (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    symbol_id    BIGINT NOT NULL REFERENCES symbols(id),
    quantity     DECIMAL(18, 8) NOT NULL,
    platform     VARCHAR(100),
    average_cost DECIMAL(18, 8),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, symbol_id, platform)
);

CREATE INDEX idx_holdings_user_id ON holdings(user_id);
CREATE INDEX idx_holdings_symbol_id ON holdings(symbol_id);
CREATE INDEX idx_symbols_ticker ON symbols(ticker);

-- Step 4: Extract distinct symbols from investments into symbols
INSERT INTO symbols (ticker, updated_at)
SELECT DISTINCT symbol, NOW() FROM investments;

-- Step 5: Create default migration user
INSERT INTO users (username, email, cognito_sub, created_at)
VALUES ('default_user', 'default@local', 'legacy-migration', NOW());

-- Step 6: Migrate all investments rows into holdings
INSERT INTO holdings (user_id, symbol_id, quantity, platform, average_cost, created_at, updated_at)
SELECT
    (SELECT id FROM users WHERE cognito_sub = 'legacy-migration'),
    s.id,
    i.quantity,
    i.platform,
    i.average_cost,
    i.created_at,
    NOW()
FROM investments i
JOIN symbols s ON s.ticker = i.symbol;

-- Step 7: Drop the old investments table
DROP TABLE investments;
