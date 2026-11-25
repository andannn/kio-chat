package kio.chat.db

import kio.postgres.migration.Migration

val migrations
    get() = listOf(
        migration1,
        migration2,
        migration3,
        migration4,
        migration5,
        migration6,
        migration7,
        migration8,
        migration9,
        migration10,
        migration11,
        migration12,
        migration13,
    )

// Test cleanup, matching kio-note. Use only with a dedicated test database.
suspend fun kio.postgres.conn.PgConnection.dropAllTables() {
    exec("drop table if exists schema_migrations cascade")
    exec("drop table if exists messages cascade")
    exec("drop function if exists notify_chat_message()")
    exec("DROP TABLE IF EXISTS room_join_requests CASCADE")
    exec("DROP FUNCTION IF EXISTS notify_room_join_request()")
    exec("DROP TABLE IF EXISTS room_members CASCADE")
    exec("DROP FUNCTION IF EXISTS notify_room_member()")
    exec("DROP FUNCTION IF EXISTS create_user_room() CASCADE")
    exec("DROP TABLE IF EXISTS rooms CASCADE")
    exec("DROP FUNCTION IF EXISTS notify_room()")
    exec("drop table if exists sessions cascade")
    exec("drop table if exists users cascade")
}

private val migration1 = Migration(
    version = 1,
    name = "create_auth_tables",
    sql = """
create table users (
    id bigserial primary key,
    username text not null unique,
    password_hash text not null,
    create_at timestamptz not null default now()
);

create table sessions (
    id text primary key,
    user_id bigint not null
        references users(id)
        on delete cascade,
    create_at timestamptz not null default now()
);
        """.trimIndent()
)

private val migration2 = Migration(
    version = 2,
    name = "add message table",
    sql = """
create table messages (
    id bigserial primary key,
    user_id bigint not null
        references users(id)
        on delete cascade,
    text text not null,
    create_at timestamptz not null default now()
);
        """.trimIndent()
)

private val migration3 = Migration(
    version = 3,
    name = "notify message changes",
    sql = """
create function notify_chat_message() returns trigger
language plpgsql as $$
declare
    message_row messages%rowtype;
begin
    if TG_OP = 'DELETE' then
        message_row := OLD;
    else
        message_row := NEW;
    end if;

    perform pg_notify('chat_messages', json_build_object(
        'operation', TG_OP,
        'message_id', message_row.id
    )::text);
    return null;
end;
$$;

create trigger messages_notify
    after insert or update or delete on messages
    for each row execute function notify_chat_message();
        """.trimIndent()
)

private val migration4 = Migration(
    version = 4,
    name = "add chat rooms",
    sql = """
CREATE TABLE rooms (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    create_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO rooms (name) VALUES ('公共聊天室');

ALTER TABLE messages
    ADD COLUMN room_id BIGINT NOT NULL DEFAULT 1 REFERENCES rooms(id);

CREATE INDEX messages_room_id_id_idx ON messages(room_id, id);

CREATE OR REPLACE FUNCTION notify_chat_message() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    message_row messages%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        message_row := OLD;
    ELSE
        message_row := NEW;
    END IF;

    PERFORM pg_notify('chat_messages', json_build_object(
        'operation', TG_OP,
        'message_id', message_row.id,
        'room_id', message_row.room_id
    )::TEXT);
    RETURN NULL;
END;
$$;
        """.trimIndent()
)

private val migration5 = Migration(
    version = 5,
    name = "add room members and user rooms",
    sql = """
CREATE TABLE room_members (
    room_id BIGINT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (room_id, user_id)
);

CREATE INDEX room_members_user_id_idx ON room_members(user_id);

DO $$
DECLARE
    existing_user RECORD;
    new_room_id BIGINT;
BEGIN
    FOR existing_user IN SELECT id, username FROM users LOOP
        INSERT INTO rooms(name) VALUES (existing_user.username || '的房间')
            RETURNING id INTO new_room_id;
        INSERT INTO room_members(room_id, user_id) VALUES (new_room_id, existing_user.id);
    END LOOP;
END;
$$;

CREATE FUNCTION create_user_room() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    new_room_id BIGINT;
BEGIN
    INSERT INTO rooms(name) VALUES (NEW.username || '的房间')
        RETURNING id INTO new_room_id;
    INSERT INTO room_members(room_id, user_id) VALUES (new_room_id, NEW.id);
    RETURN NEW;
END;
$$;

CREATE TRIGGER users_create_room
    AFTER INSERT ON users
    FOR EACH ROW EXECUTE FUNCTION create_user_room();
        """.trimIndent()
)

private val migration6 = Migration(
    version = 6,
    name = "add room member roles",
    sql = """
ALTER TABLE room_members
    ADD COLUMN role TEXT NOT NULL DEFAULT 'member'
        CHECK (role IN ('owner', 'member'));

UPDATE room_members
SET role = 'owner'
WHERE room_id IN (
    SELECT room_id FROM room_members GROUP BY room_id HAVING COUNT(*) = 1
);

CREATE OR REPLACE FUNCTION create_user_room() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    new_room_id BIGINT;
BEGIN
    INSERT INTO rooms(name) VALUES (NEW.username || '的房间')
        RETURNING id INTO new_room_id;
    INSERT INTO room_members(room_id, user_id, role) VALUES (new_room_id, NEW.id, 'owner');
    RETURN NEW;
END;
$$;
        """.trimIndent()
)

private val migration7 = Migration(
    version = 7,
    name = "add room join requests",
    sql = """
CREATE TABLE room_join_requests (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    create_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (room_id, user_id)
);
        """.trimIndent()
)

private val migration8 = Migration(
    version = 8,
    name = "notify room join request changes",
    sql = """
CREATE FUNCTION notify_room_join_request() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    request_row room_join_requests%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        request_row := OLD;
    ELSE
        request_row := NEW;
    END IF;

    PERFORM pg_notify('room_join_requests', json_build_object(
        'operation', TG_OP,
        'request_id', request_row.id,
        'request_room_id', request_row.room_id,
        'request_user_id', request_row.user_id
    )::TEXT);
    RETURN NULL;
END;
$$;

CREATE TRIGGER room_join_requests_notify
    AFTER INSERT OR DELETE ON room_join_requests
    FOR EACH ROW EXECUTE FUNCTION notify_room_join_request();
        """.trimIndent()
)

private val migration9 = Migration(
    version = 9,
    name = "notify room member changes",
    sql = """
CREATE FUNCTION notify_room_member() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    member_row room_members%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        member_row := OLD;
    ELSE
        member_row := NEW;
    END IF;

    PERFORM pg_notify('room_members', json_build_object(
        'operation', TG_OP,
        'room_id', member_row.room_id,
        'user_id', member_row.user_id
    )::TEXT);
    RETURN NULL;
END;
$$;

CREATE TRIGGER room_members_notify
    AFTER INSERT OR DELETE ON room_members
    FOR EACH ROW EXECUTE FUNCTION notify_room_member();
        """.trimIndent()
)

private val migration10 = Migration(
    version = 10,
    name = "notify room name changes",
    sql = """
CREATE FUNCTION notify_room() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_notify('rooms', json_build_object(
        'operation', TG_OP,
        'room_id', NEW.id
    )::TEXT);
    RETURN NULL;
END;
$$;

CREATE TRIGGER rooms_notify
    AFTER UPDATE OF name ON rooms
    FOR EACH ROW EXECUTE FUNCTION notify_room();
        """.trimIndent()
)

private val migration11 = Migration(
    version = 11,
    name = "cascade messages on room deletion",
    sql = """
ALTER TABLE messages
    DROP CONSTRAINT messages_room_id_fkey,
    ADD CONSTRAINT messages_room_id_fkey
        FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE;
        """.trimIndent()
)

private val migration12 = Migration(
    version = 12,
    name = "add user nickname",
    sql = """
ALTER TABLE users ADD COLUMN nickname TEXT;
UPDATE users SET nickname = username;
ALTER TABLE users ALTER COLUMN nickname SET NOT NULL;
        """.trimIndent()
)

private val migration13 = Migration(
    version = 13,
    name = "add message type and extra",
    sql = """
ALTER TABLE messages
    ADD COLUMN msg_type TEXT NOT NULL DEFAULT 'text',
    ADD COLUMN extra JSONB;
        """.trimIndent()
)
