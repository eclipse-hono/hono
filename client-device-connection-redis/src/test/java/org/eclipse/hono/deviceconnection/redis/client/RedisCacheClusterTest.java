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

import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
import io.vertx.redis.client.RedisClientType;
import io.vertx.redis.client.RedisOptions;
import io.vertx.redis.client.Response;

/**
 * Tests verifying the behavior of {@link RedisCache} against a Redis server running in cluster mode.
 * <p>
 * The server is a single node cluster that serves all hash slots. That is sufficient for verifying
 * the cache's behavior because the Redis cluster client splits multi-key commands by hash slot,
 * regardless of the number of nodes.
 */
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(VertxExtension.class)
@Timeout(value = 20, timeUnit = TimeUnit.SECONDS)
class RedisCacheClusterTest {

    private static final int REDIS_PORT = 6379;

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withCommand("redis-server", "--cluster-enabled", "yes", "--appendonly", "no")
            .withExposedPorts(REDIS_PORT);

    private Redis redisClient;
    private RedisAPI api;
    private RedisCache cache;

    /**
     * Makes the node serve all hash slots and announce the address that it can be reached at from the test.
     *
     * @throws Exception if the cluster cannot be set up.
     */
    @BeforeAll
    static void setUpCluster() throws Exception {
        final String announcedIp = InetAddress.getByName(REDIS.getHost()).getHostAddress();
        REDIS.execInContainer("redis-cli", "CONFIG", "SET", "cluster-announce-ip", announcedIp);
        REDIS.execInContainer("redis-cli", "CONFIG", "SET", "cluster-announce-port",
                String.valueOf(REDIS.getMappedPort(REDIS_PORT)));
        REDIS.execInContainer("redis-cli", "CLUSTER", "ADDSLOTSRANGE", "0", "16383");
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!REDIS.execInContainer("redis-cli", "CLUSTER", "INFO").getStdout().contains("cluster_state:ok")) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("Redis cluster did not become ready");
            }
            Thread.sleep(100);
        }
    }

    @BeforeEach
    void setUp(final Vertx vertx) {
        redisClient = Redis.createClient(vertx, new RedisOptions()
                .setType(RedisClientType.CLUSTER)
                .setConnectionString("redis://%s:%d".formatted(REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT))));
        api = RedisAPI.api(redisClient);
        cache = RedisCache.from(api);
    }

    @AfterEach
    void tearDown() {
        redisClient.close();
    }

    /**
     * Verifies that values are returned under their keys if the keys belong to different hash slots.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testGetAllReturnsValuesUnderTheirKeysAcrossHashSlots(final VertxTestContext ctx) {
        final String prefix = "cluster-" + UUID.randomUUID() + "-";
        final Map<String, String> data = new LinkedHashMap<>();
        for (int i = 0; i < 20; i++) {
            data.put(prefix + i, "value-" + i);
        }
        cache.putAll(data)
                .compose(ok -> cache.getAll(data.keySet()))
                .onComplete(ctx.succeeding(values -> {
                    ctx.verify(() -> assertThat(values).containsExactlyEntriesIn(data));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that keys that are not mapped are omitted from the result if the keys belong to
     * different hash slots.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testGetAllOmitsMissingKeysAcrossHashSlots(final VertxTestContext ctx) {
        final String prefix = "cluster-" + UUID.randomUUID() + "-";
        final Map<String, String> data = Map.of(prefix + "a", "value-a", prefix + "c", "value-c");
        cache.putAll(data)
                .compose(ok -> cache.getAll(Set.of(prefix + "a", prefix + "b", prefix + "c", prefix + "d")))
                .onComplete(ctx.succeeding(values -> {
                    ctx.verify(() -> assertThat(values).containsExactlyEntriesIn(data));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that values of keys belonging to different hash slots are stored with the given lifespan.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testPutAllWithLifespanStoresValuesAcrossHashSlots(final VertxTestContext ctx) {
        final String prefix = "cluster-" + UUID.randomUUID() + "-";
        final Map<String, String> data = new LinkedHashMap<>();
        for (int i = 0; i < 10; i++) {
            data.put(prefix + i, "value-" + i);
        }
        cache.putAll(data, 60, TimeUnit.SECONDS)
                .compose(ok -> cache.getAll(data.keySet()))
                .compose(values -> {
                    ctx.verify(() -> assertThat(values).containsExactlyEntriesIn(data));
                    return Future.all(data.keySet().stream().map(api::pttl).toList());
                })
                .onComplete(ctx.succeeding(ttls -> {
                    ctx.verify(() -> {
                        for (int i = 0; i < ttls.size(); i++) {
                            assertThat(ttls.<Response>resultAt(i).toLong()).isGreaterThan(0L);
                        }
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the hash slots calculated by the cache match the ones that the Redis server calculates.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testHashSlotsMatchRedisServer(final VertxTestContext ctx) {
        final List<String> keys = List.of(
                "123456789", "ai@@DEFAULT_TENANT@@4711", "gw@@DEFAULT_TENANT@@4711", "{coap:node}:0",
                "{user1000}.following", "foo{}{bar}", "foo{{bar}}zap", "foo{bar}{zap}", "{}", "x{", "ö-ümlaut");
        Future.all(keys.stream().map(key -> api.cluster(List.of("KEYSLOT", key))).toList())
                .onComplete(ctx.succeeding(slots -> {
                    ctx.verify(() -> {
                        for (int i = 0; i < keys.size(); i++) {
                            assertThat(RedisHashSlot.of(keys.get(i)))
                                    .isEqualTo(slots.<Response>resultAt(i).toInteger());
                        }
                    });
                    ctx.completeNow();
                }));
    }
}
