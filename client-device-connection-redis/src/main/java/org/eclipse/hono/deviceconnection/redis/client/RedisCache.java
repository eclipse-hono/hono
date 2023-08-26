/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.deviceconnection.common.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * TODO.
 * @param <K> TODO
 * @param <V> TODO
 */
public class RedisCache<K, V> implements Cache<K, V> {

    private static final Logger LOG = LoggerFactory.getLogger(RedisCache.class);

    private JedisPool pool;

    /**
     * TODO.
     */
    public RedisCache() {
        LOG.info("Initializing REDIS cache!");
        try {
            pool = new JedisPool("redis", 6379);
            try (Jedis jedis = pool.getResource()) {
                final var response = jedis.ping();
                LOG.info("Got {} from redis server", response);
            }
        } catch (Exception e) {
            LOG.error("something went wrong", e);
        }
    }

    @Override
    public Future<JsonObject> checkForCacheAvailability() {
        LOG.info("REDIS: checking for cache availability");
        try (Jedis jedis = pool.getResource()) {
            final var response = jedis.ping();
            LOG.info("Got {} from redis server", response);
            return Future.succeededFuture(new JsonObject());
        } catch (Exception e) {
            return Future.failedFuture(e);
        }
    }

    @Override
    public Future<Void> put(final K key, final V value) {
        LOG.info("REDIS: put {}={}", key, value);
        try (Jedis jedis = pool.getResource()) {
            jedis.set(key.toString(), value.toString());
        }
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> put(final K key, final V value, final long lifespan, final TimeUnit lifespanUnit) {
        LOG.info("REDIS: put {}={} ({} {})", key, value, lifespan, lifespanUnit);
        try (Jedis jedis = pool.getResource()) {
            jedis.psetex(key.toString(), lifespanUnit.toMillis(lifespan), value.toString());
            return Future.succeededFuture();
        } catch (Exception e) {
            return Future.failedFuture(e);
        }
    }

    @Override
    public Future<Void> putAll(final Map<? extends K, ? extends V> data) {
        LOG.info("REDIS: putAll ({})", data.size());
        try (Jedis jedis = pool.getResource()) {
            for (K k : data.keySet()) {
                jedis.set(k.toString(), data.get(k).toString());
            }
            return Future.succeededFuture();
        } catch (Exception e) {
            return Future.failedFuture(e);
        }
    }

    @Override
    public Future<Void> putAll(final Map<? extends K, ? extends V> data, final long lifespan, final TimeUnit lifespanUnit) {
        LOG.info("REDIS: putAll ({}) ({} {})", data.size(), lifespan, lifespanUnit);
        try (Jedis jedis = pool.getResource()) {
            for (K k : data.keySet()) {
                jedis.psetex(k.toString(), lifespanUnit.toMillis(lifespan), data.get(k).toString());
            }
            return Future.succeededFuture();
        } catch (Exception e) {
            return Future.failedFuture(e);
        }
    }

    @Override
    public Future<V> get(final K key) {
        LOG.info("REDIS: get {}", key);
        try (Jedis jedis = pool.getResource()) {
            return Future.succeededFuture((V) jedis.get(key.toString()));
        } catch (Exception e) {
            return Future.failedFuture(e);
        }
    }

    @Override
    public Future<Boolean> remove(final K key, final V value) {
        LOG.info("REDIS: remove {}={}", key, value);
        try (Jedis jedis = pool.getResource()) {
            jedis.del(key.toString());
            return Future.succeededFuture(true);
        } catch (Exception e) {
            return Future.failedFuture(e);
        }
    }

    @Override
    public Future<Map<K, V>> getAll(final Set<? extends K> keys) {
        LOG.warn("getAll() ({}) called but that has not been implemented!!!", keys.size());
        try (Jedis jedis = pool.getResource()) {
            return Future.succeededFuture(null);
        } catch (Exception e) {
            return Future.failedFuture(e);
        }
    }
}
