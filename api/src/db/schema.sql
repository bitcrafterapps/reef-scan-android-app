-- ============================================================================
-- ReefScan API Database Schema
-- Based on PRD.md Section 12: Database Schema
-- ============================================================================

-- -----------------------------------------------------------------------------
-- Devices Table
-- Stores registered device information
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS devices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_uuid     VARCHAR(255) UNIQUE NOT NULL,
    platform        VARCHAR(20) NOT NULL CHECK (platform IN ('ios', 'android')),
    app_version     VARCHAR(20) NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    last_seen_at    TIMESTAMPTZ DEFAULT NOW(),
    refresh_token   VARCHAR(512),
    token_version   INTEGER DEFAULT 1,
    tier            VARCHAR(20) DEFAULT 'free' CHECK (tier IN ('free', 'premium')),
    subscription_id VARCHAR(255),
    is_blocked      BOOLEAN DEFAULT FALSE,
    block_reason    TEXT,
    metadata        JSONB DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_devices_device_uuid ON devices(device_uuid);
CREATE INDEX IF NOT EXISTS idx_devices_last_seen ON devices(last_seen_at);
CREATE INDEX IF NOT EXISTS idx_devices_tier ON devices(tier);

-- -----------------------------------------------------------------------------
-- Request Logs Table
-- For analytics, debugging, and idempotency
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS request_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id       UUID REFERENCES devices(id) ON DELETE CASCADE,
    request_id      VARCHAR(255) UNIQUE NOT NULL,
    mode            VARCHAR(50) NOT NULL,
    image_hash      VARCHAR(64),
    provider_used   VARCHAR(50),
    api_key_id      VARCHAR(50),
    status          VARCHAR(20),
    latency_ms      INTEGER,
    tokens_input    INTEGER,
    tokens_output   INTEGER,
    error_code      VARCHAR(50),
    result          JSONB,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_logs_device_id ON request_logs(device_id);
CREATE INDEX IF NOT EXISTS idx_logs_created_at ON request_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_logs_image_hash ON request_logs(image_hash);
CREATE INDEX IF NOT EXISTS idx_logs_request_id ON request_logs(request_id);

-- -----------------------------------------------------------------------------
-- Daily Usage Table
-- Denormalized for fast rate limit queries
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS daily_usage (
    device_id       UUID REFERENCES devices(id) ON DELETE CASCADE,
    date            DATE NOT NULL,
    request_count   INTEGER DEFAULT 0,
    PRIMARY KEY (device_id, date)
);

CREATE INDEX IF NOT EXISTS idx_daily_usage_date ON daily_usage(date);

-- -----------------------------------------------------------------------------
-- Image Cache Table
-- Cost optimization through result caching
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS image_cache (
    image_hash      VARCHAR(64) PRIMARY KEY,
    mode            VARCHAR(50) NOT NULL,
    result          JSONB NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    expires_at      TIMESTAMPTZ,
    hit_count       INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_cache_expires ON image_cache(expires_at);
CREATE INDEX IF NOT EXISTS idx_cache_mode ON image_cache(mode);

-- -----------------------------------------------------------------------------
-- Prompts Table
-- Version-controlled AI prompts
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS prompts (
    id              SERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    version         INTEGER NOT NULL,
    system_prompt   TEXT NOT NULL,
    mode_prompt     TEXT NOT NULL,
    is_active       BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    created_by      VARCHAR(100),
    UNIQUE(name, version)
);

-- Only one active version per prompt name
CREATE UNIQUE INDEX IF NOT EXISTS idx_active_prompt ON prompts(name) WHERE is_active = TRUE;

-- -----------------------------------------------------------------------------
-- Species Analytics Table
-- Track identified species for business intelligence
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS species_analytics (
    id              SERIAL PRIMARY KEY,
    species_name    VARCHAR(255) NOT NULL,
    category        VARCHAR(50) NOT NULL,
    count           INTEGER DEFAULT 1,
    first_seen      TIMESTAMPTZ DEFAULT NOW(),
    last_seen       TIMESTAMPTZ DEFAULT NOW(),
    avg_confidence  DECIMAL(5,2),
    UNIQUE(species_name, category)
);

CREATE INDEX IF NOT EXISTS idx_species_category ON species_analytics(category);
CREATE INDEX IF NOT EXISTS idx_species_count ON species_analytics(count DESC);

-- -----------------------------------------------------------------------------
-- Functions
-- -----------------------------------------------------------------------------

-- Function to update species analytics on identification
CREATE OR REPLACE FUNCTION update_species_analytics(
    p_species_name VARCHAR(255),
    p_category VARCHAR(50),
    p_confidence DECIMAL(5,2)
)
RETURNS VOID AS $$
BEGIN
    INSERT INTO species_analytics (species_name, category, count, avg_confidence)
    VALUES (p_species_name, p_category, 1, p_confidence)
    ON CONFLICT (species_name, category)
    DO UPDATE SET
        count = species_analytics.count + 1,
        last_seen = NOW(),
        avg_confidence = (species_analytics.avg_confidence * species_analytics.count + p_confidence) / (species_analytics.count + 1);
END;
$$ LANGUAGE plpgsql;

-- Function to clean expired cache entries
CREATE OR REPLACE FUNCTION clean_expired_cache()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM image_cache WHERE expires_at < NOW();
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- Function to archive old request logs (move to archive or delete)
CREATE OR REPLACE FUNCTION archive_old_logs(days_to_keep INTEGER DEFAULT 30)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM request_logs
    WHERE created_at < NOW() - (days_to_keep || ' days')::INTERVAL;
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;
