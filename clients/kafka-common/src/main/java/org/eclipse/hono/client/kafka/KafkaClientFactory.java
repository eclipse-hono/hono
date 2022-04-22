/*
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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
import java.util.function.Supplier;
import java.util.regex.Pattern;

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
import io.vertx.kafka.client.consumer.KafkaConsumer;

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
    public static final int CLIENT_CREATION_RETRY_DELAY_MILLIS = 100;

    private static final Logger LOG = LoggerFactory.getLogger(KafkaClientFactory.class);

    private static final Pattern COMMA_WITH_WHITESPACE = Pattern.compile("\\s*,\\s*");

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
     * If creation fails because the {@value CommonClientConfigs#BOOTSTRAP_SERVERS_CONFIG} config property contains a
     * (non-empty) list of URLs that are not (yet) resolvable, further creation attempts are done with a delay of
     * {@value #CLIENT_CREATION_RETRY_DELAY_MILLIS}ms in between. These retries are done until the given
     * <em>retriesTimeout</em> has elapsed.
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
        Objects.requireNonNull(clientSupplier);

        final Promise<T> resultPromise = Promise.promise();
        createClientWithRetries(clientSupplier,
                getRetriesTimeLimit(retriesTimeout),
                () -> containsValidServerEntries(bootstrapServersConfig),
                resultPromise);
        return resultPromise.future();
    }

    /**
     * Creates a new KafkaConsumer.
     * <p>
     * If creation fails because the {@value CommonClientConfigs#BOOTSTRAP_SERVERS_CONFIG} config property contains a
     * (non-empty) list of URLs that are not (yet) resolvable, further creation attempts are done with a delay of
     * {@value #CLIENT_CREATION_RETRY_DELAY_MILLIS}ms in between. These retries are done until the given
     * <em>retriesTimeout</em> has elapsed.
     *
     * @param consumerConfig The consumer configuration properties.
     * @param retriesTimeout The maximum time for which retries are done. Using a negative duration or {@code null}
     *                       here is interpreted as an unlimited timeout value.
     * @param <K> The class type for the key deserialization.
     * @param <V> The class type for the value deserialization.
     * @return A future indicating the outcome of the creation attempt.
     * @throws NullPointerException if any of the parameters except retriesTimeout is {@code null}.
     */
    public <K, V> Future<KafkaConsumer<K, V>> createKafkaConsumerWithRetries(
            final Map<String, String> consumerConfig,
            final Duration retriesTimeout) {
        Objects.requireNonNull(consumerConfig);

        final Promise<KafkaConsumer<K, V>> resultPromise = Promise.promise();
        createClientWithRetries(
                () -> KafkaConsumer.create(vertx, consumerConfig),
                getRetriesTimeLimit(retriesTimeout),
                () -> containsValidServerEntries(consumerConfig.get(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG)),
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
     * @return A future indicating the outcome of the creation attempt.
     * @throws NullPointerException if any of the parameters except retriesTimeout is {@code null}.
     */
    public Future<KafkaAdminClient> createKafkaAdminClientWithRetries(
            final Map<String, String> clientConfig,
            final Duration retriesTimeout) {
        Objects.requireNonNull(clientConfig);

        final Promise<KafkaAdminClient> resultPromise = Promise.promise();
        createClientWithRetries(
                () -> KafkaAdminClient.create(vertx, clientConfig),
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
            final Instant retriesTimeLimit,
            final Supplier<Boolean> serverEntriesValid,
            final Promise<T> resultPromise) {
        try {
            resultPromise.complete(clientSupplier.get());
        } catch (final Exception e) {
            // perform retry in case bootstrap URLs are not resolvable ("No resolvable bootstrap urls given in bootstrap.servers")
            // (see org.apache.kafka.clients.ClientUtils#parseAndValidateAddresses)
            if (!retriesTimeLimit.equals(Instant.MIN) && e instanceof KafkaException
                    && isBootstrapServersConfigException(e.getCause()) && serverEntriesValid.get()) {
                if (Instant.now(clock).isBefore(retriesTimeLimit)) {
                    LOG.debug("error creating Kafka client, will retry in {}ms: {}", CLIENT_CREATION_RETRY_DELAY_MILLIS,
                            e.getCause().getMessage());
                    vertx.setTimer(CLIENT_CREATION_RETRY_DELAY_MILLIS, tid -> {
                        createClientWithRetries(clientSupplier, retriesTimeLimit, () -> true, resultPromise);
                    });
                } else {
                    // retries time limit reached
                    LOG.warn("error creating Kafka client (no further attempts will be done, timeout for retries reached): {}",
                            e.getCause().getMessage());
                    resultPromise.fail(e);
                }
            } else {
                resultPromise.fail(e);
            }
        }
    }

    /**
     * Checks if the given client creation error is the kind of error for which the client creation methods of this
     * factory would perform a retry, i.e. whether the error is due to the {@value CommonClientConfigs#BOOTSTRAP_SERVERS_CONFIG}
     * config property containing a (non-empty) list of URLs that are not (yet) resolvable,
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
                .map(serversString -> {
                    final String trimmed = serversString.trim();
                    if (trimmed.isEmpty()) {
                        return List.<String> of();
                    }
                    return Arrays.asList(COMMA_WITH_WHITESPACE.split(trimmed, -1));
                }).orElseGet(List::of);
        return !urlList.isEmpty() && urlList.stream().allMatch(KafkaClientFactory::containsHostAndPort);
    }

    private static boolean containsHostAndPort(final String url) {
        try {
            return Utils.getHost(url) != null && Utils.getPort(url) != null;
        } catch (final IllegalArgumentException e) {
            return false;
        }
    }
}
