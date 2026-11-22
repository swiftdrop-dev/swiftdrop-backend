-- ==============================================================
-- SwiftDrop Pricing & Zones Schema (Member 3 - M3)
-- ==============================================================

CREATE TABLE IF NOT EXISTS zones (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    code VARCHAR(20) NOT NULL UNIQUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS zone_rates (
    id BIGSERIAL PRIMARY KEY,
    from_zone_id BIGINT NOT NULL REFERENCES zones(id) ON DELETE RESTRICT,
    to_zone_id BIGINT NOT NULL REFERENCES zones(id) ON DELETE RESTRICT,
    base_fee NUMERIC(10, 2) NOT NULL CHECK (base_fee >= 0),
    per_kg_fee NUMERIC(10, 2) NOT NULL CHECK (per_kg_fee >= 0),
    express_multiplier NUMERIC(4, 2) NOT NULL DEFAULT 1.50 CHECK (express_multiplier >= 1.0),
    estimated_delivery_days INT NOT NULL DEFAULT 2 CHECK (estimated_delivery_days >= 1),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_from_to_zone UNIQUE (from_zone_id, to_zone_id)
);

CREATE INDEX IF NOT EXISTS idx_zone_rates_from_to ON zone_rates(from_zone_id, to_zone_id);

-- Seed Initial Zones
INSERT INTO zones (id, name, code, is_active) VALUES
(1, 'Western Province (Colombo)', 'ZONE_WP_COLOMBO', TRUE),
(2, 'Western Province (Gampaha/Kalutara)', 'ZONE_WP_SUBURB', TRUE),
(3, 'Central Province (Kandy)', 'ZONE_CP_KANDY', TRUE),
(4, 'Southern Province (Galle)', 'ZONE_SP_GALLE', TRUE)
ON CONFLICT (id) DO NOTHING;

SELECT setval('zones_id_seq', (SELECT MAX(id) FROM zones));

-- Seed Default Rates (Intra-zone & Inter-zone)
INSERT INTO zone_rates (from_zone_id, to_zone_id, base_fee, per_kg_fee, express_multiplier, estimated_delivery_days) VALUES
(1, 1, 350.00, 50.00, 1.40, 1),
(1, 2, 400.00, 60.00, 1.50, 1),
(2, 1, 400.00, 60.00, 1.50, 1),
(1, 3, 500.00, 80.00, 1.50, 2), -- Colombo <-> Kandy (Demo Route)
(3, 1, 500.00, 80.00, 1.50, 2),
(1, 4, 450.00, 70.00, 1.50, 2),
(4, 1, 450.00, 70.00, 1.50, 2),
(2, 2, 350.00, 50.00, 1.40, 1),
(3, 3, 350.00, 50.00, 1.40, 1),
(4, 4, 350.00, 50.00, 1.40, 1)
ON CONFLICT (from_zone_id, to_zone_id) DO NOTHING;
