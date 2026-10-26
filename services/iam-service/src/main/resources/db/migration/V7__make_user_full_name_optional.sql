-- Email/password registration leaves profile name unset; existing names are preserved.
ALTER TABLE users ALTER COLUMN full_name DROP NOT NULL;
