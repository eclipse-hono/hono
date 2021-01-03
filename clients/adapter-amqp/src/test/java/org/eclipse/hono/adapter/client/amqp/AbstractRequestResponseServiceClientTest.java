/**
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.adapter.client.amqp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.impl.CachingClientFactory;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.util.CacheDirective;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.benmanes.caffeine.cache.Cache;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;


/**
 * Tests verifying the behavior of {@link AbstractRequestResponseServiceClient}.
 *
 */
class AbstractRequestResponseServiceClientTest {

    private static final int DEFAULT_CACHE_TIMEOUT_SECONDS = 100;
    private AbstractRequestResponseServiceClient<Buffer, SimpleRequestResponseResult> client;
    private Vertx vertx;
    private Cache<Object, SimpleRequestResponseResult> cache;

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {

        vertx = mock(Vertx.class);
        cache = mock(Cache.class);
        final var props = new RequestResponseClientConfigProperties();
        props.setResponseCacheDefaultTimeout(DEFAULT_CACHE_TIMEOUT_SECONDS);

        client = new AbstractRequestResponseServiceClient<>(
                AmqpClientUnitTestHelper.mockHonoConnection(vertx, props),
                SendMessageSampler.Factory.noop(),
                new ProtocolAdapterProperties(),
                new CachingClientFactory<>(vertx, v -> true),
                cache) {

            @Override
            protected SimpleRequestResponseResult getResult(
                    final int status,
                    final String contentType,
                    final Buffer payload,
                    final CacheDirective cacheDirective,
                    final ApplicationProperties applicationProperties) {
                return SimpleRequestResponseResult.from(status, payload, cacheDirective, applicationProperties);
            }

            @Override
            protected String getKey(final String tenantId) {
                return "test-" + tenantId;
            }
        };
    }

    /**
     * Verifies that the client puts a response from a service to the cache
     * using the default cache timeout if the response does not contain a
     * cache directive but has a status code that allows the response to be
     * cached using the default timeout.
     */
    @Test
    public void testCreateAndSendRequestAddsResponseWithNoCacheDirectiveToCache() {

        // GIVEN a response without a cache directive and status code 200
        final var response = SimpleRequestResponseResult.from(
                200,
                Buffer.buffer("ok"),
                null,
                null);

        // WHEN adding the response to the cache
        client.addToCache("key", response);

        // THEN the response has been put to the cache
        verify(cache).put(eq("key"), eq(response));
    }

    /**
     * Verifies that the client puts a response from a service to the cache
     * if the response contains a <em>max-age</em> cache directive.
     */
    @Test
    public void testAddToCacheConsidersMaxAge() {

        // GIVEN a response with a max-age directive
        final var response = SimpleRequestResponseResult.from(
                200,
                Buffer.buffer("ok"),
                CacheDirective.maxAgeDirective(Duration.ofMinutes(5)),
                null);

        // WHEN adding the response to the cache
        client.addToCache("key", response);

        // THEN the response has been put to the cache
        verify(cache).put(eq("key"), eq(response));
    }

    /**
     * Verifies that the client does not put a response from a service to the cache
     * if the response contains a <em>no-cache</em> cache directive.
     */
    @Test
    public void testAddToCacheDoesNotAddResponseToCache() {

        // GIVEN a response with a no-cache directive
        final var response = SimpleRequestResponseResult.from(
                200,
                Buffer.buffer("ok"),
                CacheDirective.noCacheDirective(),
                null);

        // WHEN adding the response to the cache
        client.addToCache("key", response);

        // THEN the response is not put to the cache
        verify(cache, never()).put(anyString(), any(SimpleRequestResponseResult.class));
    }

    /**
     * Verifies that the client does not put a response from a service to the cache
     * that does not contain any cache directive but has a <em>non-cacheable</em> status code.
     */
    @Test
    public void testAddToCacheDoesNotAddNonCacheableResponseToCache() {

        // GIVEN a response with no cache directive and a 404 status code
        final var response = SimpleRequestResponseResult.from(
                404,
                Buffer.buffer("ok"),
                null,
                null);

        // WHEN adding the response to the cache
        client.addToCache("key", response);

        // THEN the response is not put to the cache
        verify(cache, never()).put(anyString(), any(SimpleRequestResponseResult.class));
    }
}
