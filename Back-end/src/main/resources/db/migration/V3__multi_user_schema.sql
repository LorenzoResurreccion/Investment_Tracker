-- V3__multi_user_schema.sql
-- Normalize the schema into symbols, users, and holdings tables for multi-user support.
-- Drops the old single-user investments table (data not migrated since it has no user association).

-- Step 1: Drop the old investments table
DROP TABLE IF EXISTS investments;

-- Step 2: Create symbols table
CREATE TABLE symbols (
    id          BIGSERIAL PRIMARY KEY,
    ticker      VARCHAR(20) NOT NULL UNIQUE,
    name        VARCHAR(255),
    exchange    VARCHAR(50),
    asset_type  VARCHAR(20),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Step 3: Create users table
CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(50) NOT NULL UNIQUE,
    email       VARCHAR(255) NOT NULL UNIQUE,
    cognito_sub VARCHAR(255) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Step 4: Create holdings table with FK constraints and composite unique constraint
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
