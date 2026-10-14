# Redis Device Connection Client

A Redis based implementation of Hono's `Cache` interface for storing device
connection information, used by the Redis variant of the Command Router
(`services/command-router-redis`).

> **Experimental:** This is a recent addition that has not yet undergone
> extensive real-world validation. It is considered experimental and its
> configuration and behavior may change in future releases. Using it in
> production environments is not recommended at this stage.

## Requirements

| Requirement | Details |
| --- | --- |
| Redis server | Version 2.6 or later (`EVAL` support); tested against Redis 8 |
| Lua scripting | The `EVAL` command must be permitted for the configured user. When using Redis ACLs, the `@scripting` command category must not be denied. The cache verifies this at startup; if scripting is not permitted, the startup future fails and a descriptive error is logged. |
| `INFO` command | The cache runs `INFO cluster` to find out whether the server is part of a Redis Cluster. `INFO` belongs to the `@dangerous` command category, so an ACL that denies `@dangerous` must allow `INFO` explicitly (e.g. `+@all -@dangerous +info`). If `INFO` is not permitted, the cache treats the server as a Redis Cluster and repeats the check before each operation on multiple keys. |

Atomic operations (compare-and-delete, bulk put with time-to-live) are
implemented as server-side Lua scripts instead of `WATCH`/`MULTI`
transactions. The vert.x Redis client recycles pooled connections without
resetting per-connection state, so transaction based implementations leak
`WATCH`/`MULTI` state into the connection pool and corrupt unrelated
operations. Scripts execute atomically server-side and keep connections
stateless.
