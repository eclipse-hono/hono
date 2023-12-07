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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.kafka.admin.KafkaAdminClient;

/**
 * A factory to create Kafka clients.
 */
public class KafkaClientFactory {

    /**
     * Duration value that may be used for the {@code retriesTimeout} parameter of the factory methods,
     * signifying unlimited retries.
     */
    public static final Duration UNLIMITED_RETRIES_DURATION = Duration.ofSeconds(-1);
    /**
     * The number of milliseconds to wait before retrying to create a client.
     */
    public static final int CLIENT_CREATION_RETRY_DELAY_MILLIS = 1000;

    private static final Logger LOG = LoggerFactory.getLogger(KafkaClientFactory.class);

    private final Vertx vertx;
    private Clock clock = Clock.systemUTC();

    /**
     * Creates a new KafkaClientFactory.
     *
     * @param vertx The Vert.x instance to use.
     * @throws NullPointerException if vertx is {@code null}.
     */
    public KafkaClientFactory(final Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
    }

    /**
     * Sets a clock to use for determining the current system time.
     * <p>
     * The default value of this property is {@link Clock#systemUTC()}.
     * <p>
     * This property should only be set for running tests expecting the current
     * time to be a certain value.
     *
     * @param clock The clock to use.
     * @throws NullPointerException if clock is {@code null}.
     */
    void setClock(final Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Creates a new Kafka client.
     * <p>
     * Simply invokes {@link #createClientWithRetries(Supplier, BooleanSupplier, String, Duration)} with a keep retrying
     * condition that always returns {@code true}.
     *
     * @param <T> The type of client.
     * @param clientSupplier The action that will create the client.
     * @param bootstrapServersConfig The {@value CommonClientConfigs#BOOTSTRAP_SERVERS_CONFIG} config property value.
     *                               A {@code null} value will lead to a failed result future.
     * @param retriesTimeout The maximum time for which retries are done. Using a negative duration or {@code null}
     *                       here is interpreted as an unlimited timeout value.
     * @return A future indicating the outcome of the creation attempt.
     * @throws NullPointerException if clientSupplier is {@code null}.
     */
    public <T> Future<T> createClientWithRetries(
            final Supplier<T> clientSupplier,
            final String bootstrapServersConfig,
            final Duration retriesTimeout) {
        return createClientWithRetries(clientSupplier, () -> true, bootstrapServersConfig, retriesTimeout);
    }

    /**
     * Creates a new Kafka client.
     * <p>
     * If creation fails because the {@value CommonClientConfigs#BOOTSTRAP_SERVERS_CONFIG} config property contains a
     * (non-empty) list of URLs that are not (yet) resolvable, further creation attempts are done with a delay of
     * {@value #CLIENT_CREATION_RETRY_DELAY_MILLIS}ms in between. These retries are done until the given
     * <em>retriesTimeout</em> has elapsed.
     *
     * @param <T> The type of client.
     * @param clientSupplier The action that will create the client.
     * @param keepTrying A guard condition that controls whether another attempt to create the client should be
     *                     started. Client code can set this to {@code false} in order to prevent any further attempts.
     * @param bootstrapServersConfig The {@value CommonClientConfigs#BOOTSTRAP_SERVERS_CONFIG} config property value.
     *                               A {@code null} value will lead to a failed result future.
     * @param retriesTimeout The maximum time for which retries are done. Using a negative duration or {@code null}
     *                       here is interpreted as an unlimited timeout value.
     * @return A future indicating the outcome of the creation attempt.
     * @throws NullPointerException if clientSupplier or keepTrying are {@code null}.
     */
    public <T> Future<T> createClientWithRetries(
            final Supplier<T> clientSupplier,
            final BooleanSupplier keepTrying,
            final String bootstrapServersConfig,
            final Duration retriesTimeout) {
        Objects.requireNonNull(clientSupplier);
        Objects.requireNonNull(keepTrying);

        final Promise<T> resultPromise = Promise.promise();
        createClientWithRetries(
                clientSupplier,
                keepTrying,
                getRetriesTimeLimit(retriesTimeout),
                () -> containsValidServerEntries(bootstrapServersConfig),
                resultPromise);
        return resultPromise.future();
    }

    /**
     * Creates a new KafkaAdminClient.
     * <p>
     * If creation fails because the {@value CommonClientConfigs#BOOTSTRAP_SERVERS_CONFIG} config property contains a
     * (non-empty) list of URLs that are not (yet) resolvable, further creation attempts are done with a delay of
     * {@value #CLIENT_CREATION_RETRY_DELAY_MILLIS}ms in between. These retries are done until the given
     * <em>retriesTimeout</em> has elapsed.
     *
     * @param clientConfig The admin client configuration properties.
     * @param retriesTimeout The maximum time for which retries are done. Using a negative duration or {@code null}
     *                       here is interpreted as an unlimited timeout value.
     * @param keepTrying A guard condition that controls whether another attempt to create the client should be
     *                     started. Client code can set this to {@code false} in order to prevent any further attempts.
     * @return A future indicating the outcome of the creation attempt.
     * @throws NullPointerException if any of the parameters except retriesTimeout is {@code null}.
     */
    public Future<KafkaAdminClient> createKafkaAdminClientWithRetries(
            final Map<String, String> clientConfig,
            final BooleanSupplier keepTrying,
            final Duration retriesTimeout) {
        Objects.requireNonNull(clientConfig);
        Objects.requireNonNull(keepTrying);

        final Promise<KafkaAdminClient> resultPromise = Promise.promise();
        createClientWithRetries(
                () -> KafkaAdminClient.create(vertx, clientConfig),
                keepTrying,
                getRetriesTimeLimit(retriesTimeout),
                () -> containsValidServerEntries(clientConfig.get(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG)),
                resultPromise);
        return resultPromise.future();
    }

    private Instant getRetriesTimeLimit(final Duration retriesTimeout) {
        if (retriesTimeout == null || retriesTimeout.isNegative()) {
            return Instant.MAX;
        } else if (retriesTimeout.isZero()) {
            return Instant.MIN;
        }
        return Instant.now(clock).plus(retriesTimeout);
    }

    private <T> void createClientWithRetries(
            final Supplier<T> clientSupplier,
            final BooleanSupplier keepTrying,
            final Instant retriesTimeLimit,
            final BooleanSupplier serverEntriesValid,
            final Promise<T> resultPromise) {

        if (!keepTrying.getAsBoolean()) {
            resultPromise.fail("client code has canceled further attempts to create Kafka client");
            return;
        }

        try {
            final var client = clientSupplier.get();
            LOG.debug("successfully created client [type: {}]", client.getClass().getName());
            resultPromise.complete(client);
        } catch (final Exception e) {
            // perform retry in case bootstrap URLs are not resolvable ("No resolvable bootstrap urls given in
            // bootstrap.servers")
            // (see org.apache.kafka.clients.ClientUtils#parseAndValidateAddresses)
            if (!retriesTimeLimit.equals(Instant.MIN) && e instanceof KafkaException
                    && isBootstrapServersConfigException(e.getCause()) && serverEntriesValid.getAsBoolean()) {

                if (!keepTrying.getAsBoolean()) {
                    // client code has canceled further attempts
                    LOG.debug("client code has canceled further attempts to create Kafka client");
                    resultPromise.fail(e);
                } else if (Instant.now(clock).isBefore(retriesTimeLimit)) {
                    LOG.debug("error creating Kafka client, will retry in {}ms: {}", CLIENT_CREATION_RETRY_DELAY_MILLIS,
                            e.getCause().getMessage());
                    vertx.setTimer(CLIENT_CREATION_RETRY_DELAY_MILLIS, tid -> {
                        createClientWithRetries(clientSupplier, keepTrying, retriesTimeLimit, () -> true, resultPromise);
                    });
                } else {
                    // retries time limit reached
                    LOG.warn("""
                            error creating Kafka client \
                            (no further attempts will be done, timeout for retries reached): {}\
                            """,
                            e.getCause().getMessage());
                    resultPromise.fail(e);
                }
            } else {
                LOG.warn("failed to create client due to terminal error (won't retry)", e);
                resultPromise.fail(e);
            }
        }
    }

    /**
     * Checks if the given client creation error is the kind of error for which the client creation methods of this
     * factory would perform a retry, i.e. whether the error is due to the {@value CommonClientConfigs#BOOTSTRAP_SERVERS_CONFIG}
     * configuration property containing a (non-empty) list of URLs that are not (yet) resolvable,
     *
     * @param exception The error to check.
     * @param bootstrapServersConfig The {@value CommonClientConfigs#BOOTSTRAP_SERVERS_CONFIG} config property value.
     * @return {@code true} if client creation may be retried.
     */
    public static boolean isRetriableClientCreationError(final Throwable exception, final String bootstrapServersConfig) {
        return exception instanceof KafkaException
                && isBootstrapServersConfigException(exception.getCause())
                && containsValidServerEntries(bootstrapServersConfig);
    }

    private static boolean isBootstrapServersConfigException(final Throwable ex) {
        return ex instanceof ConfigException
                && ex.getMessage() != null
                && ex.getMessage().contains(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG);
    }

    private static boolean containsValidServerEntries(final String bootstrapServersConfig) {
        final List<String> urlList = Optional.ofNullable(bootstrapServersConfig)
                .map(String::trim)
                .map(serversString -> {
                    if (serversString.isEmpty()) {
                        return List.<String> of();
                    } else {
                        return Arrays.asList(serversString.split(","));
                    }
                }).orElseGet(List::of);
        return !urlList.isEmpty() && urlList.stream().allMatch(KafkaClientFactory::containsHostAndPort);
    }

    private static boolean containsHostAndPort(final String url) {
        try {
            final var trimmedUrl = url.trim();
            return Utils.getHost(trimmedUrl) != null && Utils.getPort(trimmedUrl) != null;
        } catch (final IllegalArgumentException e) {
            return false;
        }
    }
}
