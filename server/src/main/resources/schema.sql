-- Memory: схема PostgreSQL. Применяется при каждом запуске сервера —
-- только CREATE … IF NOT EXISTS, данные не трогает.

CREATE SEQUENCE IF NOT EXISTS user_seq START 100001;
CREATE SEQUENCE IF NOT EXISTS post_seq START 10000001;

CREATE TABLE IF NOT EXISTS users (
    id              INTEGER PRIMARY KEY DEFAULT nextval('user_seq'),
    login           TEXT NOT NULL UNIQUE,
    name            TEXT NOT NULL,
    hue             REAL NOT NULL DEFAULT 0,
    pass_hash       TEXT NOT NULL,
    salt            TEXT NOT NULL,
    public_key      TEXT NOT NULL DEFAULT '',
    profile         TEXT NOT NULL DEFAULT '',
    profile_updated BIGINT NOT NULL DEFAULT 0,
    created         BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS users_name ON users (lower(name));

CREATE TABLE IF NOT EXISTS devices (
    id           TEXT PRIMARY KEY,
    user_id      INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name         TEXT NOT NULL,
    platform     TEXT NOT NULL,
    last_seen    BIGINT NOT NULL,
    refresh_hash TEXT NOT NULL,
    access_hash  TEXT NOT NULL,
    access_until BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS devices_user ON devices (user_id);
CREATE UNIQUE INDEX IF NOT EXISTS devices_access ON devices (access_hash);
CREATE UNIQUE INDEX IF NOT EXISTS devices_refresh ON devices (refresh_hash);

CREATE TABLE IF NOT EXISTS posts (
    id           BIGINT PRIMARY KEY DEFAULT nextval('post_seq'),
    author       INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created      BIGINT NOT NULL,
    body         TEXT NOT NULL,
    likes        INTEGER NOT NULL DEFAULT 0,
    comments_off BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS posts_feed ON posts (created DESC, id DESC);
CREATE INDEX IF NOT EXISTS posts_author ON posts (author, created DESC, id DESC);

CREATE TABLE IF NOT EXISTS likes (
    post_id BIGINT NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    user_id INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (post_id, user_id)
);

CREATE TABLE IF NOT EXISTS chats (
    id      INTEGER PRIMARY KEY,
    kind    TEXT NOT NULL,
    title   TEXT NOT NULL DEFAULT '',
    owner   INTEGER NOT NULL,
    created BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS members (
    chat_id  INTEGER NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    user_id  INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    admin    BOOLEAN NOT NULL DEFAULT FALSE,
    accepted BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (chat_id, user_id)
);
CREATE INDEX IF NOT EXISTS members_user ON members (user_id);

CREATE TABLE IF NOT EXISTS messages (
    id      BIGSERIAL PRIMARY KEY,
    chat_id INTEGER NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    author  INTEGER NOT NULL,
    sent_at BIGINT NOT NULL,
    body    TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS messages_chat ON messages (chat_id, id DESC);

CREATE TABLE IF NOT EXISTS uploads (
    id      TEXT PRIMARY KEY,
    owner   INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    length  BIGINT NOT NULL,
    "offset" BIGINT NOT NULL DEFAULT 0,
    meta    TEXT NOT NULL DEFAULT '',
    created BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS idem (
    user_id INTEGER NOT NULL,
    key     TEXT NOT NULL,
    status  INTEGER NOT NULL,
    body    TEXT NOT NULL,
    created BIGINT NOT NULL,
    PRIMARY KEY (user_id, key)
);
CREATE INDEX IF NOT EXISTS idem_created ON idem (created);
