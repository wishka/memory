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

-- Раунд 25: подписки, комментарии, истории, пуши

ALTER TABLE users ADD COLUMN IF NOT EXISTS closed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS push_token TEXT NOT NULL DEFAULT '';
CREATE INDEX IF NOT EXISTS devices_push ON devices (push_token);

CREATE TABLE IF NOT EXISTS follows (
    follower INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    followee INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    accepted BOOLEAN NOT NULL,
    created  BIGINT NOT NULL,
    PRIMARY KEY (follower, followee)
);
CREATE INDEX IF NOT EXISTS follows_followee ON follows (followee, accepted);

-- Номера комментариев и историй — с 10 000 001, как у записей: у телефона свои, меньше
CREATE SEQUENCE IF NOT EXISTS comment_seq START 10000001;
CREATE SEQUENCE IF NOT EXISTS story_seq START 10000001;

CREATE TABLE IF NOT EXISTS comments (
    id      BIGINT PRIMARY KEY DEFAULT nextval('comment_seq'),
    post_id BIGINT NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    author  INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created BIGINT NOT NULL,
    body    TEXT NOT NULL,
    likes   INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS comments_post ON comments (post_id, id);

CREATE TABLE IF NOT EXISTS comment_likes (
    comment_id BIGINT NOT NULL REFERENCES comments (id) ON DELETE CASCADE,
    user_id    INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (comment_id, user_id)
);

CREATE TABLE IF NOT EXISTS stories (
    id            BIGINT PRIMARY KEY DEFAULT nextval('story_seq'),
    author        INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created       BIGINT NOT NULL,
    expires       BIGINT NOT NULL,
    body          TEXT NOT NULL,
    close_friends BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS stories_author ON stories (author, expires);

CREATE TABLE IF NOT EXISTS story_views (
    story_id BIGINT NOT NULL REFERENCES stories (id) ON DELETE CASCADE,
    user_id  INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    at       BIGINT NOT NULL,
    liked    BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (story_id, user_id)
);

CREATE TABLE IF NOT EXISTS close_friends (
    owner  INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    friend INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (owner, friend)
);

-- Раунд 26: модерация — блокировки, жалобы, бан

ALTER TABLE users ADD COLUMN IF NOT EXISTS banned BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS blocks (
    blocker INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    blocked INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (blocker, blocked)
);

CREATE TABLE IF NOT EXISTS reports (
    id       BIGSERIAL PRIMARY KEY,
    reporter INTEGER NOT NULL,
    kind     TEXT NOT NULL,
    target   BIGINT NOT NULL,
    author   INTEGER NOT NULL,
    reason   TEXT NOT NULL,
    note     TEXT NOT NULL DEFAULT '',
    preview  TEXT NOT NULL DEFAULT '',
    created  BIGINT NOT NULL,
    status   TEXT NOT NULL DEFAULT 'open'
);
CREATE INDEX IF NOT EXISTS reports_status ON reports (status, id DESC);
