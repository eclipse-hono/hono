/**
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.deviceconnection.redis.client;

import static com.google.common.truth.Truth.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisOptions;

/**
 * Tests verifying the behavior of {@link RedisCache} against a real Redis server.
 * <p>
 * The Redis client is configured with a connection pool of size 1 so that every
 * operation reuses the same physical connection. Any operation that leaks
 * connection-level state (an armed {@code WATCH} or an open {@code MULTI})
 * therefore corrupts subsequent operations and fails the suite.
 */
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(VertxExtension.class)
@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
class RedisCacheTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379);

    private static final Pattern MGET_CALLS = Pattern.compile("cmdstat_mget:calls=(\\d+)");

    private Redis redisClient;
    private RedisAPI api;
    private RedisCache cache;

    @BeforeEach
    void setUp(final Vertx vertx) {
        redisClient = Redis.createClient(vertx, new RedisOptions()
                .setConnectionString("redis://%s:%d".formatted(REDIS.getHost(), REDIS.getMappedPort(6379)))
                .setMaxPoolSize(1)
                .setMaxPoolWaiting(32));
        api = RedisAPI.api(redisClient);
        cache = RedisCache.from(api);
    }

    @AfterEach
    void tearDown() {
        redisClient.close();
    }

    private static String randomKey(final String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    /**
     * Verifies that a value that has been put can be retrieved again.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testPutAndGetSucceeds(final VertxTestContext ctx) {
        final String key = randomKey("put-get");
        cache.put(key, "the-value")
                .compose(ok -> cache.get(key))
                .onComplete(ctx.succeeding(value -> {
                    ctx.verify(() -> assertThat(value).isEqualTo("the-value"));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that values stored via putAll can be retrieved via getAll.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testPutAllAndGetAllSucceeds(final VertxTestContext ctx) {
        final String keyA = randomKey("all-a");
        final String keyB = randomKey("all-b");
        cache.putAll(Map.of(keyA, "value-a", keyB, "value-b"))
                .compose(ok -> cache.getAll(Set.of(keyA, keyB)))
                .onComplete(ctx.succeeding(values -> {
                    ctx.verify(() -> {
                        assertThat(values).containsEntry(keyA, "value-a");
                        assertThat(values).containsEntry(keyB, "value-b");
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that getAll omits keys that do not exist, matching the behavior
     * of the Infinispan based BasicCache.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testGetAllOmitsMissingKeys(final VertxTestContext ctx) {
        final String present = randomKey("present");
        final String missing = randomKey("missing");
        cache.put(present, "here")
                .compose(ok -> cache.getAll(Set.of(present, missing)))
                .onComplete(ctx.succeeding(values -> {
                    ctx.verify(() -> {
                        assertThat(values).containsEntry(present, "here");
                        assertThat(values).doesNotContainKey(missing);
                        assertThat(values).hasSize(1);
                    });
                    ctx.completeNow();
                }));
    }

    private Future<Long> mgetCalls() {
        return api.info(List.of("commandstats"))
                .map(info -> {
                    final Matcher matcher = MGET_CALLS.matcher(info.toString(StandardCharsets.UTF_8));
                    return matcher.find() ? Long.parseLong(matcher.group(1)) : 0L;
                });
    }

    /**
     * Verifies that the values of keys belonging to different hash slots are retrieved
     * with a single MGET command if the Redis server does not run in cluster mode.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testGetAllUsesSingleCommandIfNotInClusterMode(final VertxTestContext ctx) {
        final Map<String, String> data = Map.of(
                randomKey("single-a"), "value-a",
                randomKey("single-b"), "value-b",
                randomKey("single-c"), "value-c");
        final AtomicLong callsBefore = new AtomicLong();
        cache.putAll(data)
                // determine cluster mode before counting the MGET commands
                .compose(ok -> cache.getAll(data.keySet()))
                .compose(ok -> mgetCalls())
                .compose(calls -> {
                    callsBefore.set(calls);
                    return cache.getAll(data.keySet());
                })
                .compose(values -> {
                    ctx.verify(() -> assertThat(values).containsExactlyEntriesIn(data));
                    return mgetCalls();
                })
                .onComplete(ctx.succeeding(calls -> {
                    ctx.verify(() -> assertThat(calls - callsBefore.get()).isEqualTo(1L));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that getting a key that does not exist succeeds with a null
     * value as defined by the Cache interface contract.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testGetSucceedsWithNullForMissingKey(final VertxTestContext ctx) {
        cache.get(randomKey("absent"))
                .onComplete(ctx.succeeding(value -> {
                    ctx.verify(() -> assertThat(value).isNull());
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that removing an existing key with the matching value succeeds
     * and actually deletes the entry.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testRemoveSucceedsForMatchingValue(final VertxTestContext ctx) {
        final String key = randomKey("remove-match");
        cache.put(key, "the-value")
                .compose(ok -> cache.remove(key, "the-value"))
                .compose(removed -> {
                    ctx.verify(() -> assertThat(removed).isTrue());
                    return cache.get(key);
                })
                .onComplete(ctx.succeeding(value -> {
                    ctx.verify(() -> assertThat(value).isNull());
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that removing a key with a non-matching value returns false
     * and leaves the entry intact.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testRemoveFailsForNonMatchingValue(final VertxTestContext ctx) {
        final String key = randomKey("remove-mismatch");
        cache.put(key, "the-value")
                .compose(ok -> cache.remove(key, "some-other-value"))
                .compose(removed -> {
                    ctx.verify(() -> assertThat(removed).isFalse());
                    return cache.get(key);
                })
                .onComplete(ctx.succeeding(value -> {
                    ctx.verify(() -> assertThat(value).isEqualTo("the-value"));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that removing a key that does not exist returns false.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testRemoveFailsForMissingKey(final VertxTestContext ctx) {
        cache.remove(randomKey("remove-absent"), "any-value")
                .onComplete(ctx.succeeding(removed -> {
                    ctx.verify(() -> assertThat(removed).isFalse());
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a remove attempt with a non-matching value does not leak
     * connection state that corrupts subsequent operations on the same pooled
     * connection.
     * <p>
     * With the WATCH/MULTI based implementation this sequence fails: the failed
     * remove leaves a WATCH armed on the (single) pooled connection, the
     * subsequent put touches the watched key, and the putAll transaction is
     * then silently aborted — the entry is never stored although the future
     * succeeds.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testRemoveWithNonMatchingValueDoesNotPoisonPooledConnection(final VertxTestContext ctx) {
        final String watchedKey = randomKey("poison-watched");
        final String victimKey = randomKey("poison-victim");
        cache.put(watchedKey, "value-1")
                .compose(ok -> cache.remove(watchedKey, "non-matching-value"))
                .compose(removed -> cache.put(watchedKey, "value-2"))
                .compose(ok -> cache.putAll(Map.of(victimKey, "victim-value"), 60_000, TimeUnit.MILLISECONDS))
                .compose(ok -> cache.get(victimKey))
                .onComplete(ctx.succeeding(value -> {
                    ctx.verify(() -> assertThat(value).isEqualTo("victim-value"));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that entries stored via putAll with a lifespan are readable
     * before the lifespan ends and are expired afterwards.
     *
     * @param vertx The vert.x instance.
     * @param ctx The vert.x test context.
     */
    @Test
    void testPutAllWithLifespanExpiresEntries(final Vertx vertx, final VertxTestContext ctx) {
        final String keyA = randomKey("expire-a");
        final String keyB = randomKey("expire-b");
        cache.putAll(Map.of(keyA, "value-a", keyB, "value-b"), 500, TimeUnit.MILLISECONDS)
                .compose(ok -> cache.getAll(Set.of(keyA, keyB)))
                .onComplete(ctx.succeeding(values -> {
                    ctx.verify(() -> assertThat(values).hasSize(2));
                    vertx.setTimer(1000, tid -> cache.getAll(Set.of(keyA, keyB))
                            .onComplete(ctx.succeeding(expired -> {
                                ctx.verify(() -> assertThat(expired).isEmpty());
                                ctx.completeNow();
                            })));
                }));
    }

    /**
     * Verifies that a non-positive lifespan stores entries without expiry,
     * as the Cache contract defines a negative lifespan as unlimited.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testPutAllWithNegativeLifespanStoresWithoutExpiry(final VertxTestContext ctx) {
        final String key = randomKey("no-expiry");
        cache.putAll(Map.of(key, "persistent"), -1, TimeUnit.MILLISECONDS)
                .compose(ok -> cache.get(key))
                .compose(value -> {
                    ctx.verify(() -> assertThat(value).isEqualTo("persistent"));
                    return api.pttl(key);
                })
                .onComplete(ctx.succeeding(pttl -> {
                    ctx.verify(() -> assertThat(pttl.toLong()).isEqualTo(-1L));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that putAll with an empty map succeeds without contacting Redis
     * in a way that would fail (MSET requires at least one pair).
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testPutAllWithEmptyMapSucceeds(final VertxTestContext ctx) {
        cache.putAll(Map.of())
                .compose(ok -> cache.putAll(Map.of(), 500, TimeUnit.MILLISECONDS))
                .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that start() succeeds against a Redis server that permits
     * Lua scripting.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testStartSucceedsWhenScriptingIsAllowed(final VertxTestContext ctx) {
        cache.start().onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that start() fails with a meaningful message if the configured
     * user is not allowed to run Lua scripts (denied @scripting ACL category),
     * instead of failing obscurely at runtime on the first remove/putAll.
     *
     * @param vertx The vert.x instance.
     * @param ctx The vert.x test context.
     */
    @Test
    void testStartFailsWhenScriptingIsDenied(final Vertx vertx, final VertxTestContext ctx) {
        final Redis restrictedClient = Redis.createClient(vertx, new RedisOptions()
                .setConnectionString("redis://noscript:secret@%s:%d"
                        .formatted(REDIS.getHost(), REDIS.getMappedPort(6379)))
                .setMaxPoolSize(1));
        api.acl(List.of("SETUSER", "noscript", "on", ">secret", "~*", "+@all", "-@scripting"))
                .compose(ok -> RedisCache.from(RedisAPI.api(restrictedClient)).start()
                        .onComplete(result -> restrictedClient.close())
                        .eventually(() -> api.acl(List.of("DELUSER", "noscript"))))
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> assertThat(t.getMessage()).contains("scripting"));
                    ctx.completeNow();
                }));
    }
}
