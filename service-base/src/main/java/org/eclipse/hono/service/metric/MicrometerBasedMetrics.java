/*******************************************************************************
 * Copyright (c) 2018, 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.service.metric;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.eclipse.hono.client.amqp.connection.SendMessageSampler;
import org.eclipse.hono.service.metric.MetricsTags.Direction;
import org.eclipse.hono.service.metric.MetricsTags.ProcessingOutcome;
import org.eclipse.hono.service.util.ServiceBaseUtils;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.tracing.TracingPolicy;

/**
 * Micrometer based metrics implementation.
 * <p>
 * This implementation monitors the {@code TenantIdleTimeout}, if configured to a non-zero duration.
 * If the tenant has not interacted with the Hono component for the configured time period (i.e. there were no changes
 * in the connection, telemetry and command metrics values related to that tenant), the metrics meters for that tenant
 * will be removed and an event will be published on the Vert.x event bus. The event bus address is
 * {@value Constants#EVENT_BUS_ADDRESS_TENANT_TIMED_OUT} and the body of the message consists of the tenant identifier.
 */
public class MicrometerBasedMetrics implements Metrics, SendMessageSampler.Factory {

    /**
     * The name of the meter for authenticated connections.
     */
    public static final String METER_CONNECTIONS_AUTHENTICATED = "hono.connections.authenticated";
    /**
     * This metric is used to keep track of the connection duration of the authenticated devices to Hono.
     */
    public static final String METER_CONNECTIONS_AUTHENTICATED_DURATION = "hono.connections.authenticated.duration";
    /**
     * The name of the meter for unauthenticated connections.
     */
    public static final String METER_CONNECTIONS_UNAUTHENTICATED = "hono.connections.unauthenticated";
    /**
     * The name of the meter for connection attempts. The outcome is signaled by the accordingly named tag.
     */
    public static final String METER_CONNECTIONS_ATTEMPTS = "hono.connections.attempts";
    /**
     * The name of the meter for recording the telemetry/event message payload size.
     */
    public static final String METER_TELEMETRY_PAYLOAD = "hono.telemetry.payload";
    /**
     * The name of the meter for tracking the processing duration of telemetry/event messages received from devices.
     */
    public static final String METER_TELEMETRY_PROCESSING_DURATION = "hono.telemetry.processing.duration";
    /**
     * The name of the meter for recording the command payload size.
     */
    public static final String METER_COMMAND_PAYLOAD = "hono.command.payload";
    /**
     * The name of the meter for tracking the processing duration of command messages.
     */
    public static final String METER_COMMAND_PROCESSING_DURATION = "hono.command.processing.duration";
    /**
     * The name of the meter for counting the messages that could not be sent because there was no credit.
     */
    public static final String METER_AMQP_NOCREDIT = "hono.amqp.nocredit";
    /**
     * The name of the meter for tracking the duration of AMQP message deliveries.
     */
    public static final String METER_AMQP_DELIVERY_DURATION = "hono.amqp.delivery.duration";
    /**
     * The name of the meter for counting sent AMQP messages for which the sender did not receive a disposition update
     * in time.
     */
    public static final String METER_AMQP_TIMEOUT = "hono.amqp.timeout";

    private static final long DEFAULT_TENANT_IDLE_TIMEOUT = Duration.ZERO.toMillis();
    private static final long DEVICE_CONNECTION_DURATION_RECORDING_INTERVAL_IN_MS = TimeUnit.SECONDS.toMillis(10);

    /**
     * A logger to be shared with subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * The meter registry.
     */
    protected final MeterRegistry registry;

    private final Map<String, AtomicLong> authenticatedConnections = new ConcurrentHashMap<>();
    private final Map<String, DeviceConnectionDurationTracker> connectionDurationTrackers = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSeenTimestampPerTenant = new ConcurrentHashMap<>();
    private final AtomicLong unauthenticatedConnections;
    private final AtomicInteger totalCurrentConnections = new AtomicInteger();
    private final Vertx vertx;
    private long tenantIdleTimeout = DEFAULT_TENANT_IDLE_TIMEOUT;

    /**
     * Creates a new metrics instance.
     *
     * @param registry The meter registry to use.
     * @param vertx The Vert.x instance to use.
     * @throws NullPointerException if registry or vertx is {@code null}.
     */
    protected MicrometerBasedMetrics(final MeterRegistry registry, final Vertx vertx) {

        Objects.requireNonNull(registry);
        Objects.requireNonNull(vertx);

        log.info("using Metrics Registry implementation [{}]", registry.getClass().getName());
        this.registry = registry;
        this.vertx = vertx;

        this.registry.config().onMeterRemoved(meter -> {
            // execution is synchronized in MeterRegistry#remove(Meter)
            if (METER_CONNECTIONS_AUTHENTICATED.equals(meter.getId().getName())) {
                authenticatedConnections.remove(meter.getId().getTag(MetricsTags.TAG_TENANT));
            }
        });
        this.unauthenticatedConnections = registry.gauge(METER_CONNECTIONS_UNAUTHENTICATED, new AtomicLong());
    }

    /**
     * Sets the tenant idle timeout.
     *
     * @param tenantIdleTimeout The timeout in milliseconds
     * @throws NullPointerException If tenantIdleTimeout is {@code null}.
     * @throws IllegalArgumentException If tenantIdleTimeout is negative.
     */
    public void setTenantIdleTimeout(final Duration tenantIdleTimeout) {
        Objects.requireNonNull(tenantIdleTimeout);
        if (tenantIdleTimeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        this.tenantIdleTimeout = tenantIdleTimeout.toMillis();
    }

    @Override
    public final void incrementConnections(final String tenantId) {

        Objects.requireNonNull(tenantId);
        final long tenantSpecificAuthenticatedConnections = gaugeForTenant(METER_CONNECTIONS_AUTHENTICATED,
                this.authenticatedConnections, tenantId, AtomicLong::new)
                        .incrementAndGet();
        this.totalCurrentConnections.incrementAndGet();
        trackDeviceConnectionDuration(tenantId, tenantSpecificAuthenticatedConnections);
        updateLastSeenTimestamp(tenantId);

    }

    @Override
    public final void decrementConnections(final String tenantId) {

        Objects.requireNonNull(tenantId);
        final long tenantSpecificAuthenticatedConnections = gaugeForTenant(METER_CONNECTIONS_AUTHENTICATED,
                this.authenticatedConnections, tenantId, AtomicLong::new)
                        .decrementAndGet();
        this.totalCurrentConnections.decrementAndGet();
        trackDeviceConnectionDuration(tenantId, tenantSpecificAuthenticatedConnections);
        updateLastSeenTimestamp(tenantId);

    }

    @Override
    public final void incrementUnauthenticatedConnections() {
        this.unauthenticatedConnections.incrementAndGet();
        this.totalCurrentConnections.incrementAndGet();
    }

    @Override
    public final void decrementUnauthenticatedConnections() {
        this.unauthenticatedConnections.decrementAndGet();
        this.totalCurrentConnections.decrementAndGet();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Invokes {@link #reportConnectionAttempt(org.eclipse.hono.service.metric.MetricsTags.ConnectionAttemptOutcome, String, String)}
     * for the given tenant ID and {@code null} as the cipher suite.
     */
    @Override
    public void reportConnectionAttempt(
            final MetricsTags.ConnectionAttemptOutcome outcome,
            final String tenantId) {
        reportConnectionAttempt(outcome, tenantId, null);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The tenant tag will be set to value <em>UNKNOWN</em> if the
     * given tenant ID is {@code null}.
     * The cipher suite tag will be set to value <em>UNKNOWN</em> if
     * the given cipher suite is {@code null}. 
     */
    @Override
    public void reportConnectionAttempt(
            final MetricsTags.ConnectionAttemptOutcome outcome,
            final String tenantId,
            final String cipherSuite) {

        Objects.requireNonNull(outcome);

        final Tags tags = Tags.of(outcome.asTag())
                .and(MetricsTags.getTenantTag(tenantId))
                .and(MetricsTags.getCipherSuiteTag(cipherSuite));

        Counter.builder(METER_CONNECTIONS_ATTEMPTS)
            .tags(tags)
            .register(this.registry)
            .increment();
    }

    @Override
    public int getNumberOfConnections() {
        return this.totalCurrentConnections.get();
    }

    @Override
    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    @Override
    public void reportTelemetry(
            final MetricsTags.EndpointType type,
            final String tenantId,
            final TenantObject tenantObject,
            final ProcessingOutcome outcome,
            final MetricsTags.QoS qos,
            final int payloadSize,
            final Timer.Sample timer) {
        reportTelemetry(type, tenantId, tenantObject, outcome, qos, payloadSize, MetricsTags.TtdStatus.NONE, timer);
    }

    @Override
    public final void reportTelemetry(
            final MetricsTags.EndpointType type,
            final String tenantId,
            final TenantObject tenantObject,
            final MetricsTags.ProcessingOutcome outcome,
            final MetricsTags.QoS qos,
            final int payloadSize,
            final MetricsTags.TtdStatus ttdStatus,
            final Timer.Sample timer) {

        Objects.requireNonNull(type);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(outcome);
        Objects.requireNonNull(qos);
        Objects.requireNonNull(ttdStatus);
        Objects.requireNonNull(timer);

        if (type != MetricsTags.EndpointType.TELEMETRY && type != MetricsTags.EndpointType.EVENT) {
            throw new IllegalArgumentException("invalid type, must be either telemetry or event");
        } else if (payloadSize < 0) {
            throw new IllegalArgumentException("payload size must not be negative");
        }

        final Tags tags = Tags.of(type.asTag())
                .and(MetricsTags.getTenantTag(tenantId))
                .and(outcome.asTag())
                .and(qos.asTag())
                .and(ttdStatus.asTag());

        timer.stop(this.registry.timer(METER_TELEMETRY_PROCESSING_DURATION, tags));

        // record payload size
        DistributionSummary.builder(METER_TELEMETRY_PAYLOAD)
            .baseUnit("bytes")
            .minimumExpectedValue(0.01)
            .tags(tags)
            .register(this.registry)
            .record(ServiceBaseUtils.calculatePayloadSize(payloadSize, tenantObject));

        updateLastSeenTimestamp(tenantId);
    }

    @Override
    public void reportCommand(
            final Direction direction,
            final String tenantId,
            final TenantObject tenantObject,
            final ProcessingOutcome outcome,
            final int payloadSize,
            final Timer.Sample timer) {

        Objects.requireNonNull(direction);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(outcome);
        Objects.requireNonNull(timer);

        if (payloadSize < 0) {
            throw new IllegalArgumentException("payload size must not be negative");
        }

        final Tags tags = Tags.of(direction.asTag())
                .and(MetricsTags.getTenantTag(tenantId))
                .and(outcome.asTag());

        timer.stop(this.registry.timer(METER_COMMAND_PROCESSING_DURATION, tags));

        // record payload size
        DistributionSummary.builder(METER_COMMAND_PAYLOAD)
            .baseUnit("bytes")
            .minimumExpectedValue(0.01)
            .tags(tags)
            .register(this.registry)
            .record((double) ServiceBaseUtils.calculatePayloadSize(payloadSize, tenantObject));

        updateLastSeenTimestamp(tenantId);
    }

    /**
     * Gets a gauge value for a specific key.
     * <p>
     * If no gauge value exists for the given key yet, a new value
     * is created using the given name and tags.
     *
     * @param <K> The type of the key.
     * @param <V> The type of the gauge's value.
     * @param name The metric name.
     * @param map The map that holds the metric's gauge values.
     * @param key The key to use for looking up the gauge value.
     * @param tags The tags to use for creating the gauge if it does not exist yet.
     * @param instanceSupplier A supplier for creating a new instance of the gauge's value.
     * @return The gauge value.
     */
    protected <K, V extends Number> V gaugeForKey(final String name, final Map<K, V> map, final K key,
            final Tags tags, final Supplier<V> instanceSupplier) {

        return map.computeIfAbsent(key, a -> {

            return this.registry.gauge(name, tags, instanceSupplier.get());

        });
    }

    /**
     * Gets a gauge value for a tenant.
     * <p>
     * If no gauge value exists for the given tenant yet, a new value
     * is created using the given name and a tag for the tenant.
     *
     * @param <V> The type of the gauge's value.
     * @param name The metric name.
     * @param map The map that holds the metric's gauge values.
     * @param tenant The tenant to use for looking up the gauge value.
     * @param instanceSupplier A supplier for creating a new instance of the gauge's value.
     * @return The gauge value.
     */
    protected <V extends Number> V gaugeForTenant(final String name, final Map<String, V> map, final String tenant,
            final Supplier<V> instanceSupplier) {

        return gaugeForKey(name, map, tenant, Tags.of(MetricsTags.getTenantTag(tenant)), instanceSupplier);
    }

    // visible for testing
    Map<String, Long> getLastSeenTimestampPerTenant() {
        return lastSeenTimestampPerTenant;
    }

    private void updateLastSeenTimestamp(final String tenantId) {
        if (tenantIdleTimeout == DEFAULT_TENANT_IDLE_TIMEOUT) {
            return;
        }

        final Long previousVal = lastSeenTimestampPerTenant.put(tenantId, System.currentTimeMillis());
        if (previousVal == null) {
            newTenantTimeoutTimer(tenantId, tenantIdleTimeout);
        }
    }

    private void newTenantTimeoutTimer(final String tenantId, final long delay) {
        vertx.setTimer(delay, id -> {
            final Long lastSeen = lastSeenTimestampPerTenant.get(tenantId);
            final long remaining = tenantIdleTimeout - (System.currentTimeMillis() - lastSeen);

            if (remaining > 0) {
                newTenantTimeoutTimer(tenantId, remaining);
            } else {
                if (!isConnected(tenantId) && lastSeenTimestampPerTenant.remove(tenantId, lastSeen)) {
                    handleTenantTimeout(tenantId);
                } else { // not ready -> reset timeout
                    newTenantTimeoutTimer(tenantId, tenantIdleTimeout);
                }
            }
        });
    }

    private boolean isConnected(final String tenantId) {
        final AtomicLong count = authenticatedConnections.get(tenantId);
        return count != null && count.get() > 0;
    }

    /**
     * <b> On synchronization: </b>
     * <p>
     * Each remove operation ({@link MeterRegistry#remove(io.micrometer.core.instrument.Meter)}) is synchronized
     * internally. The removal operations are not synchronized together.
     * </p>
     * <b>Rationale:</b>
     * <p>
     * It is a) unlikely that a tenant connects during the cleanup (timeout is rather in hours than in very small time
     * units). And b) if it happens, a metrics would temporarily be not 100% accurate. This does not justify additional
     * locking for every message that is send to Hono.
     */
    private void handleTenantTimeout(final String tenantId) {
        final Tags tenantTag = Tags.of(MetricsTags.getTenantTag(tenantId));

        // the onMeterRemoved() handler removes it also from this.authenticatedConnections
        registry.find(METER_CONNECTIONS_AUTHENTICATED).tags(tenantTag).meters().forEach(registry::remove);
        registry.find(METER_CONNECTIONS_AUTHENTICATED_DURATION).tags(tenantTag).meters().forEach(registry::remove);

        registry.find(METER_TELEMETRY_PAYLOAD).tags(tenantTag).meters().forEach(registry::remove);
        registry.find(METER_TELEMETRY_PROCESSING_DURATION).tags(tenantTag).meters().forEach(registry::remove);
        registry.find(METER_COMMAND_PAYLOAD).tags(tenantTag).meters().forEach(registry::remove);
        registry.find(METER_COMMAND_PROCESSING_DURATION).tags(tenantTag).meters().forEach(registry::remove);

        registry.find(METER_AMQP_NOCREDIT).tags(tenantTag).meters().forEach(registry::remove);
        registry.find(METER_AMQP_DELIVERY_DURATION).tags(tenantTag).meters().forEach(registry::remove);
        registry.find(METER_AMQP_TIMEOUT).tags(tenantTag).meters().forEach(registry::remove);

        final DeliveryOptions options = new DeliveryOptions();
        options.setTracingPolicy(TracingPolicy.IGNORE);
        vertx.eventBus().publish(Constants.EVENT_BUS_ADDRESS_TENANT_TIMED_OUT, tenantId, options);
    }

    /**
     * Tracks the device connection duration for the given tenant and records the tracked value.
     *
     * @param tenantId The tenant identifier.
     * @param deviceConnectionsCount The number of device connections.
     */
    private void trackDeviceConnectionDuration(final String tenantId, final long deviceConnectionsCount) {

        connectionDurationTrackers.compute(tenantId,
                (tenant, deviceConnectionDurationTracker) -> Optional.ofNullable(deviceConnectionDurationTracker)
                        .map(tracker -> tracker.updateNoOfDeviceConnections(deviceConnectionsCount))
                        .orElseGet(() -> {
                            if (deviceConnectionsCount > 0) {
                                return DeviceConnectionDurationTracker.Builder
                                        .forTenant(tenant)
                                        .withVertx(vertx)
                                        .withNumberOfDeviceConnections(deviceConnectionsCount)
                                        .withRecordingInterval(DEVICE_CONNECTION_DURATION_RECORDING_INTERVAL_IN_MS)
                                        .recordUsing(connectionDuration -> registry
                                                .timer(METER_CONNECTIONS_AUTHENTICATED_DURATION,
                                                        Tags.of(MetricsTags.getTenantTag(tenantId)))
                                                .record(connectionDuration, TimeUnit.MILLISECONDS))
                                        .start();
                            }
                            return null;
                        }));
    }

    @Override
    public SendMessageSampler create(final String messageType) {

        Objects.requireNonNull(messageType);

        return new SendMessageSampler() {
            @Override
            public SendMessageSampler.Sample start(final String tenantId) {

                final Timer.Sample sample = Timer.start(registry);

                return new SendMessageSampler.Sample() {

                    @Override
                    public void completed(final String outcome) {

                        final Tags tags = Tags.of(
                                Tag.of(MetricsTags.TAG_TYPE, messageType),
                                MetricsTags.getTenantTag(tenantId),
                                Tag.of("outcome", outcome));
                        sample.stop(registry.timer(METER_AMQP_DELIVERY_DURATION, tags));

                    }

                    @Override
                    public void timeout() {

                        /*
                         * We report timeouts with a different meter, since the message might still be
                         * accepted by the remote peer, at a time after the timeout expired. And so we
                         * can still track those times.
                         */

                        final Tags tags = Tags.of(
                                Tag.of(MetricsTags.TAG_TYPE, messageType),
                                MetricsTags.getTenantTag(tenantId));
                        registry.counter(METER_AMQP_TIMEOUT, tags).increment();

                    }
                };

            }

            @Override
            public void noCredit(final String tenantId) {

                final Tags tags = Tags.of(
                        Tag.of(MetricsTags.TAG_TYPE, messageType),
                        MetricsTags.getTenantTag(tenantId));
                registry.counter(METER_AMQP_NOCREDIT, tags).increment();

            }
        };
    }
}
