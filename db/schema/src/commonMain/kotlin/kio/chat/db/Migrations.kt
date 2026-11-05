package kio.chat.db

import kio.postgres.migration.Migration

val migrations get() = listOf(
    Migration(
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
)
