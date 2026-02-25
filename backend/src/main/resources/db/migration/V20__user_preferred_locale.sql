ALTER TABLE users ADD COLUMN preferred_locale VARCHAR(5) DEFAULT 'ja'
    CHECK (preferred_locale IN ('en', 'ja'));
