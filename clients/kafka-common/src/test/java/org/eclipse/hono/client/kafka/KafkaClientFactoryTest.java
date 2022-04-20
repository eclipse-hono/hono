/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.kafka;

import static org.mockito.Mockito.mock;

import static com.google.common.truth.Truth.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.common.config.ConfigException;
import org.eclipse.hono.test.VertxMockSupport;
import org.junit.jupiter.api.Test;

import io.vertx.core.Vertx;

/**
 * Verifies the behavior of {@link KafkaClientFactory}.
 */
public class KafkaClientFactoryTest {

    private final Vertx vertx = mock(Vertx.class);

    /**
     * Verifies that retries are performed if client creation fails because of an unresolvable URL.
     */
    @Test
    public void testClientCreationIsDoneWithRetries() {
        VertxMockSupport.runTimersImmediately(vertx);
        final KafkaClientFactory kafkaClientFactory = new KafkaClientFactory(vertx);

        final String bootstrapServers = "some.hostname.that.is.invalid:9094";
        final Map<String, String> clientConfig = Map.of(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        final AtomicInteger creationAttempts = new AtomicInteger();
        final int expectedCreationAttempts = 3;
        final Object client = new Object();
        final Supplier<Object> clientSupplier = () -> {
            if (creationAttempts.incrementAndGet() < expectedCreationAttempts) {
                Admin.create(new HashMap<>(clientConfig));
                throw new AssertionError("admin client creation should have thrown exception");
            }
            // let the 3rd attempt succeed (regardless of the config)
            return client;
        };
        kafkaClientFactory.createClientWithRetries(clientSupplier, bootstrapServers, Duration.ofSeconds(20))
            .onComplete(ar -> {
                assertThat(ar.succeeded()).isTrue();
                assertThat(ar.result()).isEqualTo(client);
                assertThat(creationAttempts.get()).isEqualTo(expectedCreationAttempts);
            });
    }

    /**
     * Verifies that client creation fails if the <em>bootstrap.servers</em> config entry is invalid.
     */
    @Test
    public void testClientCreationFailsForInvalidServersEntry() {
        VertxMockSupport.runTimersImmediately(vertx);
        final KafkaClientFactory kafkaClientFactory = new KafkaClientFactory(vertx);

        // contains entry with missing port
        final String invalidBootstrapServers = "some.hostname.that.is.invalid, some.hostname.that.is.invalid:9094";
        final var clientConfig = Map.of(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, invalidBootstrapServers);

        final AtomicInteger creationAttempts = new AtomicInteger();
        final Supplier<Object> clientSupplier = () -> {
            creationAttempts.incrementAndGet();
            Admin.create(new HashMap<>(clientConfig));
            throw new AssertionError("admin client creation should have thrown exception");
        };
        kafkaClientFactory.createClientWithRetries(clientSupplier, invalidBootstrapServers, Duration.ofSeconds(20))
                .onComplete(ar -> {
                    assertThat(ar.succeeded()).isFalse();
                    assertThat(ar.cause().getCause()).isInstanceOf(ConfigException.class);
                    assertThat(ar.cause().getCause().getMessage()).contains(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG);
                    // no retries should have been done here
                    assertThat(creationAttempts.get()).isEqualTo(1);
                });
    }

    /**
     * Verifies that trying to create a client with an unresolvable URL fails after the retry-timeout has been reached.
     */
    @Test
    public void testClientCreationFailsAfterTimeoutReached() {
        VertxMockSupport.runTimersImmediately(vertx);
        final KafkaClientFactory kafkaClientFactory = new KafkaClientFactory(vertx);

        final String bootstrapServers = "some.hostname.that.is.invalid:9094";
        final Map<String, String> clientConfig = Map.of(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        final AtomicInteger creationAttempts = new AtomicInteger();
        final int expectedCreationAttempts = 3;
        final Supplier<Object> clientSupplier = () -> {
            if (creationAttempts.incrementAndGet() == expectedCreationAttempts) {
                // let following retries be skipped by letting the retry-timeout be reached
                kafkaClientFactory.setClock(Clock.offset(Clock.systemUTC(), Duration.ofSeconds(60)));
            }
            Admin.create(new HashMap<>(clientConfig));
            throw new AssertionError("admin client creation should have thrown exception");
        };
        kafkaClientFactory.createClientWithRetries(clientSupplier, bootstrapServers, Duration.ofSeconds(20))
                .onComplete(ar -> {
                    assertThat(ar.succeeded()).isFalse();
                    assertThat(ar.cause().getCause()).isInstanceOf(ConfigException.class);
                    assertThat(ar.cause().getCause().getMessage()).contains(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG);
                    // only the number of retries before changing the clock should have been done here
                    assertThat(creationAttempts.get()).isEqualTo(expectedCreationAttempts);
                });
    }

    /**
     * Verifies that trying to create a client fails after client code has canceled further attempts.
     */
    @Test
    public void testClientCreationFailsAfterCanceling() {
        VertxMockSupport.runTimersImmediately(vertx);
        final KafkaClientFactory kafkaClientFactory = new KafkaClientFactory(vertx);

        final String bootstrapServers = "some.hostname.that.is.invalid:9094";
        final var clientConfig = Map.of(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        final var keepTrying = new AtomicBoolean(true);
        final var creationAttempts = new AtomicInteger();
        final int expectedCreationAttempts = 3;
        final Supplier<Object> clientSupplier = () -> {
            if (creationAttempts.incrementAndGet() == expectedCreationAttempts) {
                // let following retries be skipped by canceling further attempts
                keepTrying.set(false);
            }
            Admin.create(new HashMap<>(clientConfig));
            throw new AssertionError("admin client creation should have thrown exception");
        };
        kafkaClientFactory.createClientWithRetries(
                clientSupplier,
                keepTrying::get,
                bootstrapServers,
                KafkaClientFactory.UNLIMITED_RETRIES_DURATION)
            .onComplete(ar -> {
                assertThat(ar.succeeded()).isFalse();
                // only the number of retries before changing the clock should have been done here
                assertThat(creationAttempts.get()).isEqualTo(expectedCreationAttempts);
            });
    }

    /**
     * Verifies that trying to create a client with an unresolvable URL fails if the retry timeout is set to zero.
     */
    @Test
    public void testClientCreationFailsForZeroRetryTimeout() {
        VertxMockSupport.runTimersImmediately(vertx);
        final KafkaClientFactory kafkaClientFactory = new KafkaClientFactory(vertx);

        final String bootstrapServers = "some.hostname.that.is.invalid:9094";
        final Map<String, String> clientConfig = Map.of(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        final AtomicInteger creationAttempts = new AtomicInteger();
        final Supplier<Object> clientSupplier = () -> {
            creationAttempts.incrementAndGet();
            Admin.create(new HashMap<>(clientConfig));
            throw new AssertionError("admin client creation should have thrown exception");
        };
        kafkaClientFactory.createClientWithRetries(clientSupplier, bootstrapServers, Duration.ZERO)
                .onComplete(ar -> {
                    assertThat(ar.succeeded()).isFalse();
                    assertThat(ar.cause().getCause()).isInstanceOf(ConfigException.class);
                    assertThat(ar.cause().getCause().getMessage()).contains(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG);
                    // no retries should have been done here
                    assertThat(creationAttempts.get()).isEqualTo(1);
                });
    }
}
