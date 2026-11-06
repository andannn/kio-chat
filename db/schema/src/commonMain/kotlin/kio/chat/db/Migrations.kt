package kio.chat.db

import kio.postgres.migration.Migration

val migrations
    get() = listOf(
        migration1,
        migration2,
        migration3,
    )

// Test cleanup, matching kio-note. Use only with a dedicated test database.
suspend fun kio.postgres.conn.PgConnection.dropAllTables() {
    exec("drop table if exists schema_migrations cascade")
    exec("drop table if exists messages cascade")
    exec("drop function if exists notify_chat_message()")
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
