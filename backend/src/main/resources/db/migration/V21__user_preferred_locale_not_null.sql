-- Ensure preferred_locale is never null (backfill + constraint)
UPDATE users SET preferred_locale = 'ja' WHERE preferred_locale IS NULL;
ALTER TABLE users ALTER COLUMN preferred_locale SET NOT NULL;
