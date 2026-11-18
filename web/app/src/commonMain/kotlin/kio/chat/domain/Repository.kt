package kio.chat.domain

import kio.postgres.conn.PgConnectionPool

interface Repository : ChatDBRepository, WsSessionRepository

fun Repository(pgPool: PgConnectionPool): Repository = object : Repository, ChatDBRepository by ChatDBRepositoryImpl(pgPool), WsSessionRepository by InMemoryWsSessionRepository() {}
