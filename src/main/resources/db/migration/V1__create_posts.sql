-- uuidv7() is built into PostgreSQL 18: ids sort by creation time, which keeps the primary key index
-- append-only and lets the list endpoint page by (created_at, id).
CREATE TABLE posts (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    title text NOT NULL CHECK (char_length(title) BETWEEN 1 AND 200),
    body text NOT NULL CHECK (char_length(body) BETWEEN 1 AND 10000),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX posts_created_at_id_idx ON posts (created_at DESC, id DESC);
