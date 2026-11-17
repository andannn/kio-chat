package kio.chat.domain

import kio.postgres.conn.PgConnectionPool

interface Repository : ChatDBRepository, VoiceSignaling

fun Repository(pgPool: PgConnectionPool): Repository = object : Repository, ChatDBRepository by ChatDBRepositoryImpl(pgPool), VoiceSignaling by InMemoryVoiceSignaling() {}
