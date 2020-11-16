/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.eclipse.hono.adapter.client.command.DeviceConnectionClient;
import org.eclipse.hono.adapter.client.registry.CredentialsClient;
import org.eclipse.hono.adapter.client.registry.DeviceRegistrationClient;
import org.eclipse.hono.adapter.client.registry.TenantClient;
import org.eclipse.hono.adapter.client.telemetry.EventSender;
import org.eclipse.hono.adapter.client.telemetry.TelemetrySender;
import org.eclipse.hono.adapter.client.util.ServiceClient;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.CommandResponse;
import org.eclipse.hono.client.CommandResponseSender;
import org.eclipse.hono.client.ConnectionLifecycle;
import org.eclipse.hono.client.DisconnectListener;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ProtocolAdapterCommandConsumer;
import org.eclipse.hono.client.ProtocolAdapterCommandConsumerFactory;
import org.eclipse.hono.client.ReconnectListener;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.auth.ValidityBasedTrustOptions;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.service.limiting.ConnectionLimitManager;
import org.eclipse.hono.service.metric.MetricsTags.ConnectionAttemptOutcome;
import org.eclipse.hono.service.monitoring.ConnectionEventProducer;
import org.eclipse.hono.service.resourcelimits.NoopResourceLimitChecks;
import org.eclipse.hono.service.resourcelimits.ResourceLimitChecks;
import org.eclipse.hono.service.util.ServiceBaseUtils;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.Strings;
import org.eclipse.hono.util.TelemetryExecutionContext;
import org.eclipse.hono.util.TenantObject;

import io.micrometer.core.instrument.Timer.Sample;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tags;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.TrustOptions;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;

/**
 * A base class for implementing protocol adapters.
 * <p>
 * Provides connections to device registration and telemetry and event service endpoints.
 *
 * @param <T> The type of configuration properties used by this service.
 */
public abstract class AbstractProtocolAdapterBase<T extends ProtocolAdapterProperties> extends AbstractServiceBase<T> {

    /**
     * The <em>application/octet-stream</em> content type.
     */
    protected static final String CONTENT_TYPE_OCTET_STREAM = MessageHelper.CONTENT_TYPE_OCTET_STREAM;
    /**
     * The key used for storing a Micrometer {@code Sample} in an
     * execution context.
     */
    protected static final String KEY_MICROMETER_SAMPLE = "micrometer.sample";

    private TelemetrySender telemetrySender;
    private EventSender eventSender;
    private DeviceRegistrationClient registrationClient;
    private TenantClient tenantClient;
    private DeviceConnectionClient deviceConnectionClient;
    private CredentialsClient credentialsClient;
    private ProtocolAdapterCommandConsumerFactory commandConsumerFactory;
    private ConnectionLimitManager connectionLimitManager;

    private ConnectionEventProducer connectionEventProducer;
    private ResourceLimitChecks resourceLimitChecks = new NoopResourceLimitChecks();
    private final ConnectionEventProducer.Context connectionEventProducerContext = new ConnectionEventProducer.Context() {

        @Override
        public EventSender getMessageSenderClient() {
            return AbstractProtocolAdapterBase.this.eventSender;
        }

        @Override
        public TenantClient getTenantClient() {
            return AbstractProtocolAdapterBase.this.tenantClient;
        }

    };

    /**
     * Adds a Micrometer sample to a command context.
     *
     * @param ctx The context to add the sample to.
     * @param sample The sample.
     * @throws NullPointerException if ctx is {@code null}.
     */
    protected static final void addMicrometerSample(final CommandContext ctx, final Sample sample) {
        Objects.requireNonNull(ctx);
        ctx.put(KEY_MICROMETER_SAMPLE, sample);
    }

    /**
     * Gets the timer used to track the processing of a command message.
     *
     * @param ctx The command context to extract the sample from.
     * @return The sample or {@code null} if the context does not
     *         contain a sample.
     * @throws NullPointerException if ctx is {@code null}.
     */
    protected static final Sample getMicrometerSample(final CommandContext ctx) {
        Objects.requireNonNull(ctx);
        return ctx.get(KEY_MICROMETER_SAMPLE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void setConfig(final T configuration) {
        setSpecificConfig(configuration);
    }

    /**
     * Sets the client to use for accessing the Tenant service.
     *
     * @param client The client.
     * @throws NullPointerException if the client is {@code null}.
     */
    public final void setTenantClient(final TenantClient client) {
        this.tenantClient = Objects.requireNonNull(client);
    }

    /**
     * Gets the client used for accessing the Tenant service.
     *
     * @return The client.
     */
    public final TenantClient getTenantClient() {
        return tenantClient;
    }

    /**
     * Sets the client to use for accessing the Device Connection service.
     *
     * @param client The client.
     * @throws NullPointerException if the client is {@code null}.
     */
    public final void setDeviceConnectionClient(final DeviceConnectionClient client) {
        this.deviceConnectionClient = Objects.requireNonNull(client);
    }

    /**
     * Sets the client to use for sending telemetry messages downstream.
     *
     * @param sender The sender.
     * @throws NullPointerException if the sender is {@code null}.
     */
    public final void setTelemetrySender(final TelemetrySender sender) {
        this.telemetrySender = Objects.requireNonNull(sender);
    }

    /**
     * Gets the client being used for sending telemetry messages downstream.
     *
     * @return The sender.
     */
    public final TelemetrySender getTelemetrySender() {
        return telemetrySender;
    }

    /**
     * Sets the client to use for sending events downstream.
     *
     * @param sender The sender.
     * @throws NullPointerException if the sender is {@code null}.
     */
    public final void setEventSender(final EventSender sender) {
        this.eventSender = Objects.requireNonNull(sender);
    }

    /**
     * Gets the client being used for sending events downstream.
     *
     * @return The sender.
     */
    public final EventSender getEventSender() {
        return eventSender;
    }

    /**
     * Sets the client to use for accessing the Device Registration service.
     *
     * @param client The client.
     * @throws NullPointerException if the client is {@code null}.
     */
    public final void setRegistrationClient(final DeviceRegistrationClient client) {
        this.registrationClient = Objects.requireNonNull(client);
    }

    /**
     * Gets the client used for accessing the Device Registration service.
     *
     * @return The client.
     */
    public final DeviceRegistrationClient getRegistrationClient() {
        return registrationClient;
    }

    /**
     * Sets the client to use for accessing the Credentials service.
     *
     * @param client The client.
     * @throws NullPointerException if the client is {@code null}.
     */
    public final void setCredentialsClient(final CredentialsClient client) {
        this.credentialsClient = Objects.requireNonNull(client);
    }

    /**
     * Gets the client used for accessing the Credentials service.
     *
     * @return The client.
     */
    public final CredentialsClient getCredentialsClient() {
        return credentialsClient;
    }

    /**
     * Sets the producer for connections events.
     * <p>
     * Note that subclasses are not required to actually emit connection events.
     * In particular, adapters for connection-less protocols like e.g. HTTP will
     * most likely not emit such events.
     *
     * @param connectionEventProducer The instance which will handle the production of connection events. Depending on
     *            the setup this could be a simple log message or an event using the Hono Event API.
     * @throws NullPointerException if the producer is {@code null}.
     */
    public void setConnectionEventProducer(final ConnectionEventProducer connectionEventProducer) {
        this.connectionEventProducer = Objects.requireNonNull(connectionEventProducer);
        log.info("using [{}] for reporting connection events, if applicable for device protocol", connectionEventProducer);
    }

    /**
     * Gets the producer of connection events.
     *
     * @return The implementation for producing connection events. May be {@code null}.
     */
    public ConnectionEventProducer getConnectionEventProducer() {
        return this.connectionEventProducer;
    }

    /**
     * Gets this adapter's type name.
     * <p>
     * The name should be unique among all protocol adapters that are part of a Hono installation. There is no specific
     * scheme to follow but it is recommended to include the adapter's origin and the protocol that the adapter supports
     * in the name and to use lower case letters only.
     * <p>
     * Based on this recommendation, Hono's standard HTTP adapter for instance might report <em>hono-http</em> as its
     * type name.
     * <p>
     * The name returned by this method is added to message that are forwarded to downstream consumers.
     *
     * @return The adapter's name.
     */
    protected abstract String getTypeName();

    /**
     * Gets the number of seconds after which this protocol adapter should give up waiting for an upstream command for a
     * device of a given tenant.
     * <p>
     * Protocol adapters may override this method to e.g. use a static value for all tenants.
     *
     * @param tenant The tenant that the device belongs to.
     * @param deviceTtd The TTD value provided by the device in seconds.
     * @return A succeeded future that contains {@code null} if device TTD is {@code null}, or otherwise the lesser of
     *         device TTD and the value returned by {@link TenantObject#getMaxTimeUntilDisconnect(String)}.
     * @throws NullPointerException if tenant is {@code null}.
     */
    protected Future<Integer> getTimeUntilDisconnect(final TenantObject tenant, final Integer deviceTtd) {

        Objects.requireNonNull(tenant);

        if (deviceTtd == null) {
            return Future.succeededFuture();
        } else {
            return Future.succeededFuture(Math.min(tenant.getMaxTimeUntilDisconnect(getTypeName()), deviceTtd));
        }
    }

    /**
     * Sets the factory to use for creating clients to receive commands via the AMQP Messaging Network.
     *
     * @param factory The factory.
     * @throws NullPointerException if factory is {@code null}.
     */
    public final void setCommandConsumerFactory(final ProtocolAdapterCommandConsumerFactory factory) {
        this.commandConsumerFactory = Objects.requireNonNull(factory);
    }

    /**
     * Gets the factory used for creating clients to receive commands via the AMQP Messaging Network.
     *
     * @return The factory.
     */
    public final ProtocolAdapterCommandConsumerFactory getCommandConsumerFactory() {
        return this.commandConsumerFactory;
    }

    /**
     * Sets the ResourceLimitChecks instance used to check if the number of connections exceeded the limit or not.
     *
     * @param resourceLimitChecks The ResourceLimitChecks instance
     * @throws NullPointerException if the resourceLimitChecks is {@code null}.
     */
    public final void setResourceLimitChecks(final ResourceLimitChecks resourceLimitChecks) {
        this.resourceLimitChecks = Objects.requireNonNull(resourceLimitChecks);
    }

    /**
     * Gets the ResourceLimitChecks instance used to check if the number of connections exceeded the limit or not.
     *
     * @return The ResourceLimitChecks instance.
     */
    protected final ResourceLimitChecks getResourceLimitChecks() {
        return this.resourceLimitChecks;
    }

    /**
     * Sets the manager to use for connection limits.
     *
     * @param connectionLimitManager The implementation that manages the connection limit.
     */
    public final void setConnectionLimitManager(final ConnectionLimitManager connectionLimitManager) {
        this.connectionLimitManager = connectionLimitManager;
    }

    /**
     * Gets the manager to use for connection limits.
     *
     * @return The manager. May be {@code null}.
     */
    protected final ConnectionLimitManager getConnectionLimitManager() {
        return connectionLimitManager;
    }

    /**
     * Establishes the connections to the services this adapter depends on.
     * <p>
     * Note that the connections will most likely not have been established yet, when the
     * returned future completes.
     *
     * @return A future indicating the outcome of the startup process. the future will
     *         fail if the {@link #getTypeName()} method returns {@code null} or an empty string
     *         or if any of the service clients are not set. Otherwise the future will succeed.
     */
    @Override
    protected final Future<Void> startInternal() {

        final Promise<Void> result = Promise.promise();

        if (Strings.isNullOrEmpty(getTypeName())) {
            result.fail(new IllegalStateException("adapter does not define a typeName"));
        } else if (tenantClient == null) {
            result.fail(new IllegalStateException("Tenant client must be set"));
        } else if (telemetrySender == null) {
            result.fail(new IllegalStateException("Telemetry message sender must be set"));
        } else if (eventSender == null) {
            result.fail(new IllegalStateException("Event sender must be set"));
        } else if (registrationClient == null) {
            result.fail(new IllegalStateException("Device Registration client must be set"));
        } else if (credentialsClient == null) {
            result.fail(new IllegalStateException("Credentials client must be set"));
        } else if (commandConsumerFactory == null) {
            result.fail(new IllegalStateException("Command & Control client factory must be set"));
        } else if (deviceConnectionClient == null) {
            result.fail(new IllegalStateException("Device Connection client must be set"));
        } else {

            log.info("using ResourceLimitChecks [{}]", resourceLimitChecks.getClass().getName());

            startServiceClient(telemetrySender, "Telemetry");
            startServiceClient(eventSender, "Event");
            startServiceClient(tenantClient, "Tenant service");
            startServiceClient(registrationClient, "Device Registration service");
            startServiceClient(credentialsClient, "Credentials service");
            startServiceClient(deviceConnectionClient, "Device Connection service");

            connectToService(
                    commandConsumerFactory,
                    "Command & Control",
                    this::onCommandConnectionLost,
                    this::onCommandConnectionEstablished)
            .onComplete(c -> {
                if (c.succeeded()) {
                    onCommandConnectionEstablished(c.result());
                }
            });

            doStart(result);
        }
        return result.future();
    }

    /**
     * Invoked after the adapter has started up.
     * <p>
     * This default implementation simply completes the promise.
     * <p>
     * Subclasses should override this method to perform any work required on start-up of this protocol adapter.
     *
     * @param startPromise The promise to complete once start up is complete.
     */
    protected void doStart(final Promise<Void> startPromise) {
        startPromise.complete();
    }

    @Override
    protected final Future<Void> stopInternal() {

        log.info("stopping protocol adapter");
        final Promise<Void> result = Promise.promise();
        doStop(result);
        return result.future()
                .compose(s -> closeServiceClients())
                .recover(t -> {
                    log.info("error while stopping protocol adapter", t);
                    return Future.failedFuture(t);
                })
                .map(ok -> {
                    log.info("successfully stopped protocol adapter");
                    return null;
                });
    }

    private Future<?> closeServiceClients() {

        @SuppressWarnings("rawtypes")
        final List<Future> results = new ArrayList<>();
        results.add(stopServiceClient(tenantClient));
        results.add(stopServiceClient(registrationClient));
        results.add(stopServiceClient(credentialsClient));
        results.add(disconnectFromService(commandConsumerFactory));
        results.add(stopServiceClient(deviceConnectionClient));
        results.add(stopServiceClient(eventSender));
        results.add(stopServiceClient(telemetrySender));
        return CompositeFuture.all(results);
    }

    /**
     * Stops a service client.
     * <p>
     * This method invokes the client's {@link Lifecycle#stop()} method.
     *
     * @param client The client to stop.
     * @return A future indicating the outcome of stopping the client.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected final Future<Void> stopServiceClient(final Lifecycle client) {

        Objects.requireNonNull(client);
        return client.stop();
    }

    private Future<Void> disconnectFromService(final ConnectionLifecycle<?> connection) {

        final Promise<Void> disconnectTracker = Promise.promise();
        if (connection == null) {
            disconnectTracker.complete();
        } else {
            connection.disconnect(disconnectTracker);
        }
        return disconnectTracker.future();
    }

    /**
     * Invoked directly before the adapter is shut down.
     * <p>
     * This default implementation always completes the promise.
     * <p>
     * Subclasses should override this method to perform any work required before shutting down this protocol adapter.
     *
     * @param stopPromise The promise to complete once all work is done and shut down should commence.
     */
    protected void doStop(final Promise<Void> stopPromise) {
        // to be overridden by subclasses
        stopPromise.complete();
    }

    /**
     * Checks if this adapter is enabled for a given tenant, requiring the tenant itself to be enabled as well.
     *
     * @param tenantConfig The tenant to check for.
     * @return A succeeded future if the given tenant and this adapter are enabled.
     *         Otherwise the future will be failed with a {@link ClientErrorException}
     *         containing the 403 Forbidden status code.
     * @throws NullPointerException if tenant config is {@code null}.
     */
    protected final Future<TenantObject> isAdapterEnabled(final TenantObject tenantConfig) {

        Objects.requireNonNull(tenantConfig);

        if (tenantConfig.isAdapterEnabled(getTypeName())) {
            log.debug("protocol adapter [{}] is enabled for tenant [{}]",
                    getTypeName(), tenantConfig.getTenantId());
            return Future.succeededFuture(tenantConfig);
        } else if (!tenantConfig.isEnabled()) {
            log.debug("tenant [{}] is disabled", tenantConfig.getTenantId());
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN,
                    "tenant is disabled"));
        } else {
            log.debug("protocol adapter [{}] is disabled for tenant [{}]",
                    getTypeName(), tenantConfig.getTenantId());
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN,
                    "adapter disabled for tenant"));
        }
    }

    /**
     * Checks if the maximum number of concurrent connections across all protocol
     * adapters from devices of a particular tenant has been reached.
     * <p>
     * This default implementation uses the
     * {@link ResourceLimitChecks#isConnectionLimitReached(TenantObject, SpanContext)} method
     * to verify if the tenant's overall connection limit across all adapters
     * has been reached and also invokes {@link #checkMessageLimit(TenantObject, long, SpanContext)}
     * and  {@link #checkConnectionDurationLimit(TenantObject, SpanContext)} to check 
     * if the tenant's message and connection duration limits have been exceeded or not.
     *
     * @param tenantConfig The tenant to check the connection limit for.
     * @param spanContext The currently active OpenTracing span context that is used to
     *                    trace the limits verification or {@code null}
     *                    if no span is currently active.
     * @return A succeeded future if the connection and message limits have not been reached yet
     *         or if the limits could not be checked.
     *         Otherwise the future will be failed with a {@link AuthorizationException}.
     * @throws NullPointerException if tenant is {@code null}.
     */
    protected Future<Void> checkConnectionLimit(final TenantObject tenantConfig, final SpanContext spanContext) {

        Objects.requireNonNull(tenantConfig);

        final Future<Void> connectionLimitCheckResult = resourceLimitChecks.isConnectionLimitReached(tenantConfig, spanContext)
                .recover(t -> Future.succeededFuture(Boolean.FALSE))
                .compose(isExceeded -> {
                    if (isExceeded) {
                        return Future.failedFuture(new TenantConnectionsExceededException(tenantConfig.getTenantId(), null, null));
                    } else {
                        return Future.succeededFuture();
                    }
                });
        final Future<Void> messageLimitCheckResult = checkMessageLimit(tenantConfig, 1, spanContext)
                .recover(t -> {
                    if (t instanceof ClientErrorException) {
                        return Future.failedFuture(new DataVolumeExceededException(tenantConfig.getTenantId(), null, null));
                    }
                    return Future.failedFuture(t);
                });

        return CompositeFuture.all(
                connectionLimitCheckResult,
                checkConnectionDurationLimit(tenantConfig, spanContext),
                messageLimitCheckResult).mapEmpty();
    }

    /**
     * Checks if a tenant's message limit will be exceeded by a given payload.
     * <p>
     * This default implementation uses the
     * {@link ResourceLimitChecks#isMessageLimitReached(TenantObject, long, SpanContext)} method
     * to verify if the tenant's message limit has been reached.
     *
     * @param tenantConfig The tenant to check the message limit for.
     * @param payloadSize  The size of the message payload in bytes.
     * @param spanContext The currently active OpenTracing span context that is used to
     *                    trace the limits verification or {@code null}
     *                    if no span is currently active.
     * @return A succeeded future if the message limit has not been reached yet
     *         or if the limits could not be checked.
     *         Otherwise the future will be failed with a {@link ClientErrorException}
     *         containing the 429 Too many requests status code.
     * @throws NullPointerException if tenant is {@code null}.
     */
    protected Future<Void> checkMessageLimit(final TenantObject tenantConfig, final long payloadSize,
            final SpanContext spanContext) {

        Objects.requireNonNull(tenantConfig);

        return resourceLimitChecks
                .isMessageLimitReached(tenantConfig,
                        ServiceBaseUtils.calculatePayloadSize(payloadSize, tenantConfig),
                        spanContext)
                .recover(t -> Future.succeededFuture(Boolean.FALSE))
                .compose(isExceeded -> {
                    if (isExceeded) {
                        return Future.failedFuture(
                                new ClientErrorException(HttpResponseStatus.TOO_MANY_REQUESTS.code()));
                    } else {
                        return Future.succeededFuture();
                    }
                });
    }

    /**
     * Checks if the maximum connection duration across all protocol adapters
     * for a particular tenant has been reached.
     * <p>
     * This default implementation uses the
     * {@link ResourceLimitChecks#isConnectionDurationLimitReached(TenantObject, SpanContext)} 
     * method to verify if the tenant's overall connection duration across all adapters
     * has been reached.
     *
     * @param tenantConfig The tenant to check the connection duration limit for.
     * @param spanContext The currently active OpenTracing span context that is used to
     *                    trace the limits verification or {@code null}
     *                    if no span is currently active.
     * @return A succeeded future if the connection duration limit has not yet been reached
     *         or if the limit could not be checked.
     *         Otherwise, the future will be failed with a {@link AuthorizationException}.
     * @throws NullPointerException if tenantConfig is {@code null}.
     */
    protected Future<Void> checkConnectionDurationLimit(final TenantObject tenantConfig,
            final SpanContext spanContext) {

        Objects.requireNonNull(tenantConfig);

        return resourceLimitChecks.isConnectionDurationLimitReached(tenantConfig, spanContext)
                .recover(t -> Future.succeededFuture(Boolean.FALSE))
                .compose(isExceeded -> {
                    if (isExceeded) {
                        return Future.failedFuture(new ConnectionDurationExceededException(tenantConfig.getTenantId(), null, null));
                    } else {
                        return Future.succeededFuture();
                    }
                });
    }

    /**
     * Validates a message's target address for consistency with Hono's addressing rules.
     *
     * @param address The address to validate.
     * @param authenticatedDevice The device that has uploaded the message.
     * @return A future indicating the outcome of the check.
     *         <p>
     *         The future will be completed with the validated target address if all
     *         checks succeed. Otherwise the future will be failed with a
     *         {@link ClientErrorException}.
     * @throws NullPointerException if address is {@code null}.
     */
    protected final Future<ResourceIdentifier> validateAddress(final ResourceIdentifier address, final Device authenticatedDevice) {

        Objects.requireNonNull(address);
        final Promise<ResourceIdentifier> result = Promise.promise();

        if (authenticatedDevice == null) {
            if (Strings.isNullOrEmpty(address.getTenantId()) || Strings.isNullOrEmpty(address.getResourceId())) {
                result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                        "unauthenticated client must provide tenant and device ID in message address"));
            } else {
                result.complete(address);
            }
        } else {
            if (!Strings.isNullOrEmpty(address.getTenantId()) && Strings.isNullOrEmpty(address.getResourceId())) {
                result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                        "message address must not contain tenant ID only"));
            } else if (!Strings.isNullOrEmpty(address.getTenantId()) && !address.getTenantId().equals(authenticatedDevice.getTenantId())) {
                result.fail(new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN, "can only publish for device of same tenant"));
            } else if (Strings.isNullOrEmpty(address.getTenantId()) && Strings.isNullOrEmpty(address.getResourceId())) {
                // use authenticated device's tenant and device ID
                final ResourceIdentifier resource = ResourceIdentifier.from(address,
                        authenticatedDevice.getTenantId(), authenticatedDevice.getDeviceId());
                result.complete(resource);
            } else if (Strings.isNullOrEmpty(address.getTenantId())) {
                // use authenticated device's tenant ID
                final ResourceIdentifier resource = ResourceIdentifier.from(address,
                        authenticatedDevice.getTenantId(), address.getResourceId());
                result.complete(resource);
            } else {
                result.complete(address);
            }
        }
        return result.future().recover(t -> {
            log.debug("validation failed for address [{}], device [{}]: {}", address, authenticatedDevice, t.getMessage());
            return Future.failedFuture(t);
        });
    }

    /**
     * Checks whether a given device is registered and enabled.
     *
     * @param device The device to check.
     * @param context The currently active OpenTracing span that is used to
     *                    trace the retrieval of the assertion or {@code null}
     *                    if no span is currently active.
     * @return A future indicating the outcome.
     *         The future will be succeeded if the device is registered and enabled.
     *         Otherwise, the future will be failed with a {@link RegistrationAssertionException}
     *         containing the root cause of the failure to assert the registration.
     * @throws NullPointerException if device is {@code null}.
     */
    protected final Future<Void> checkDeviceRegistration(final Device device, final SpanContext context) {

        Objects.requireNonNull(device);

        return getRegistrationAssertion(
                device.getTenantId(),
                device.getDeviceId(),
                null,
                context)
                .recover(t -> Future.failedFuture(new RegistrationAssertionException(
                        device.getTenantId(),
                        "failed to assert registration status of " + device, t)))
                .mapEmpty();
    }

    /**
     * Starts a service client.
     * <p>
     * This method invokes the given client's {@link Lifecycle#start()} method.
     *
     * @param serviceClient The client to start.
     * @param serviceName The name of the service that the client is for (used for logging).
     * @return A future indicating the outcome of starting the client.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected final Future<Void> startServiceClient(final Lifecycle serviceClient, final String serviceName) {

        Objects.requireNonNull(serviceClient);
        Objects.requireNonNull(serviceName);

        return serviceClient.start().map(c -> {
            log.info("{} client [{}] successfully connected", serviceName, serviceClient);
            return c;
        }).recover(t -> {
            log.warn("{} client [{}] failed to connect", serviceName, serviceClient, t);
            return Future.failedFuture(t);
        });
    }

    /**
     * Establishes a connection to a Hono Service component.
     *
     * @param factory The client factory for the service that is to be connected.
     * @param serviceName The name of the service that is to be connected (used for logging).
     * @return A future that will succeed once the connection has been established. The future will fail if the
     *         connection cannot be established.
     * @throws NullPointerException if serviceName is {@code null}.
     * @throws IllegalArgumentException if factory is {@code null}.
     * @param <C> The type of connection that the factory uses.
     */
    protected final <C> Future<C> connectToService(final ConnectionLifecycle<C> factory, final String serviceName) {
        return connectToService(factory, serviceName, null, null);
    }

    /**
     * Establishes a connection to a Hono Service component.
     *
     * @param factory The client factory for the service that is to be connected.
     * @param serviceName The name of the service that is to be connected (used for logging).
     * @param disconnectListener A listener to invoke when the connection is lost unexpectedly
     *                           or {@code null} if no listener should be invoked. 
     * @param reconnectListener A listener to invoke when the connection has been re-established
     *                          after it had been lost unexpectedly or {@code null} if no listener
     *                          should be invoked. 
     * @return A future that will succeed once the connection has been established. The future will fail if the
     *         connection cannot be established.
     * @throws NullPointerException if serviceName is {@code null}.
     * @throws IllegalArgumentException if factory is {@code null}.
     * @param <C> The type of connection that the factory uses.
     */
    protected final <C> Future<C> connectToService(
            final ConnectionLifecycle<C> factory,
            final String serviceName,
            final DisconnectListener<C> disconnectListener,
            final ReconnectListener<C> reconnectListener) {

        Objects.requireNonNull(factory);
        factory.addDisconnectListener(c -> {
            log.info("lost connection to {}", serviceName);
            if (disconnectListener != null) {
                disconnectListener.onDisconnect(c);
            }
        });
        factory.addReconnectListener(c -> {
            log.info("connection to {} re-established", serviceName);
            if (reconnectListener != null) {
                reconnectListener.onReconnect(c);
            }
        });
        return factory.connect().map(c -> {
            log.info("connected to {}", serviceName);
            return c;
        }).recover(t -> {
            log.warn("failed to connect to {}", serviceName, t);
            return Future.failedFuture(t);
        });
    }

    /**
     * Invoked when a connection for receiving commands and sending responses has been
     * unexpectedly lost.
     * <p>
     * Subclasses may override this method in order to perform housekeeping and/or clear
     * state that is associated with the connection. Implementors <em>must not</em> try
     * to re-establish the connection, the adapter will try to re-establish the connection
     * by default.
     * <p>
     * This default implementation does nothing.
     *
     * @param commandConnection The lost connection.
     */
    protected void onCommandConnectionLost(final HonoConnection commandConnection) {
        // empty by default
    }

    /**
     * Invoked when a connection for receiving commands and sending responses has been
     * established.
     * <p>
     * Note that this method is invoked once the initial connection has been established
     * but also when the connection has been re-established after a connection loss.
     * <p>
     * Subclasses may override this method in order to e.g. re-establish device specific
     * links for receiving commands or to create a permanent link for receiving commands
     * for all devices.
     * <p>
     * This default implementation does nothing.
     *
     * @param commandConnection The (re-)established connection.
     */
    protected void onCommandConnectionEstablished(final HonoConnection commandConnection) {
        // empty by default
    }

    /**
     * Creates a command consumer for a specific device.
     *
     * @param tenantId The tenant of the command receiver.
     * @param deviceId The device of the command receiver.
     * @param commandConsumer The handler to invoke for each command destined to the device.
     * @param context The currently active OpenTracing span context or {@code null} if no span is currently active.
     * @return Result of the receiver creation.
     */
    protected final Future<ProtocolAdapterCommandConsumer> createCommandConsumer(
            final String tenantId,
            final String deviceId,
            final Handler<CommandContext> commandConsumer,
            final SpanContext context) {

        return commandConsumerFactory.createCommandConsumer(
                tenantId,
                deviceId,
                commandContext -> {
                    Tags.COMPONENT.set(commandContext.getTracingSpan(), getTypeName());
                    commandConsumer.handle(commandContext);
                },
                null,
                context);
    }

    /**
     * Creates a link for sending a command response downstream.
     *
     * @param tenantId The tenant that the device belongs to from which
     *                 the response has been received.
     * @param replyId The command's reply-to-id.
     * @return The sender.
     */
    protected final Future<CommandResponseSender> createCommandResponseSender(
            final String tenantId,
            final String replyId) {
        return commandConsumerFactory.getCommandResponseSender(tenantId, replyId);
    }

    /**
     * Forwards a response message that has been sent by a device in reply to a
     * command to the sender of the command.
     * <p>
     * This method opens a new link for sending the response, tries to send the
     * response message and then closes the link again.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param response The response message.
     * @param context The currently active OpenTracing span. An implementation
     *         should use this as the parent for any span it creates for tracing
     *         the execution of this operation.
     * @return A future indicating the outcome of the attempt to send
     *         the message. The link will be closed in any case.
     * @throws NullPointerException if any of the parameters other than context are {@code null}.
     */
    protected final Future<ProtonDelivery> sendCommandResponse(
            final String tenantId,
            final CommandResponse response,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(response);

        final Future<CommandResponseSender> senderTracker = createCommandResponseSender(tenantId,
                response.getReplyToId());
        return senderTracker
                .compose(sender -> sender.sendCommandResponse(response, context))
                .map(delivery -> {
                    senderTracker.result().close(c -> {});
                    return delivery;
                }).recover(t -> {
                    if (senderTracker.succeeded()) {
                        senderTracker.result().close(c -> {});
                    }
                    return Future.failedFuture(t);
                });
    }

    /**
     * Gets an assertion of a device's registration status.
     * <p>
     * Note that this method will also update the last gateway associated with
     * the given device (if applicable).
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to get the assertion for.
     * @param authenticatedDevice The device that has authenticated to this protocol adapter.
     *            <p>
     *            If not {@code null} then the authenticated device is compared to the given tenant and device ID. If
     *            they differ in the device identifier, then the authenticated device is considered to be a gateway
     *            acting on behalf of the device.
     * @param context The currently active OpenTracing span that is used to
     *                trace the retrieval of the assertion.
     * @return A succeeded future containing the assertion or a future
     *         failed with a {@link ServiceInvocationException} if the
     *         device's registration status could not be asserted.
     * @throws NullPointerException if any of tenant or device ID are {@code null}.
     */
    protected final Future<RegistrationAssertion> getRegistrationAssertion(
            final String tenantId,
            final String deviceId,
            final Device authenticatedDevice,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        final Future<String> gatewayId = getGatewayId(tenantId, deviceId, authenticatedDevice);

        return gatewayId
                .compose(gwId -> getRegistrationClient().assertRegistration(tenantId, deviceId, gwId, context))
                .onSuccess(assertion -> {
                    // the updateLastGateway invocation shouldn't delay or possibly fail the surrounding operation
                    // so don't wait for the outcome here
                    updateLastGateway(assertion, tenantId, deviceId, authenticatedDevice, context)
                            .onFailure(t -> {
                                log.warn("failed to update last gateway [tenantId: {}, deviceId: {}]", tenantId, deviceId, t);
                            });
                });
    }

    /**
     * Updates the last known gateway associated with the given device.
     *
     * @param registrationAssertion The registration assertion JSON object as returned by
     *            {@link #getRegistrationAssertion(String, String, Device, SpanContext)}.
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to update the last known gateway for.
     * @param authenticatedDevice The device that has authenticated to this protocol adapter.
     *            <p>
     *            If not {@code null} then the authenticated device is compared to the given tenant and device ID. If
     *            they differ in the device identifier, then the authenticated device is considered to be a gateway
     *            acting on behalf of the device.
     * @param context The currently active OpenTracing span that is used to trace the operation.
     * @return The registration assertion.
     * @throws NullPointerException if any of tenant or device ID are {@code null}.
     * @deprecated Use {@link #updateLastGateway(RegistrationAssertion, String, String, Device, SpanContext)}
     *             instead.
     */
    @Deprecated
    protected final Future<JsonObject> updateLastGateway(
            final JsonObject registrationAssertion,
            final String tenantId,
            final String deviceId,
            final Device authenticatedDevice,
            final SpanContext context) {
        try {
            return updateLastGateway(
                    registrationAssertion.mapTo(RegistrationAssertion.class),
                    tenantId,
                    deviceId,
                    authenticatedDevice,
                    context)
                .map(registrationAssertion);
        } catch (final DecodeException e) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, e));
        }
    }

    /**
     * Updates the last known gateway associated with the given device.
     *
     * @param registrationAssertion The registration assertion JSON object as returned by
     *            {@link #getRegistrationAssertion(String, String, Device, SpanContext)}.
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to update the last known gateway for.
     * @param authenticatedDevice The device that has authenticated to this protocol adapter.
     *            <p>
     *            If not {@code null} then the authenticated device is compared to the given tenant and device ID. If
     *            they differ in the device identifier, then the authenticated device is considered to be a gateway
     *            acting on behalf of the device.
     * @param context The currently active OpenTracing span that is used to trace the operation.
     * @return The registration assertion.
     * @throws NullPointerException if any of tenant or device ID are {@code null}.
     */
    protected final Future<RegistrationAssertion> updateLastGateway(
            final RegistrationAssertion registrationAssertion,
            final String tenantId,
            final String deviceId,
            final Device authenticatedDevice,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        if (!isGatewaySupportedForDevice(registrationAssertion)) {
            return Future.succeededFuture(registrationAssertion);
        }

        final Future<String> gatewayIdFuture = getGatewayId(tenantId, deviceId, authenticatedDevice);
        return gatewayIdFuture
                .compose(gwId -> deviceConnectionClient.setLastKnownGatewayForDevice(
                        tenantId,
                        deviceId,
                        Optional.ofNullable(gatewayIdFuture.result()).orElse(deviceId),
                        context))
                .map(registrationAssertion);
    }

    private boolean isGatewaySupportedForDevice(final RegistrationAssertion registrationAssertion) {
        return !registrationAssertion.getAuthorizedGateways().isEmpty();
    }

    private Future<String> getGatewayId(
            final String tenantId,
            final String deviceId,
            final Device authenticatedDevice) {

        final Promise<String> result = Promise.promise();
        if (authenticatedDevice == null) {
            result.complete(null);
        } else if (tenantId.equals(authenticatedDevice.getTenantId())) {
            if (deviceId.equals(authenticatedDevice.getDeviceId())) {
                result.complete(null);
            } else {
                result.complete(authenticatedDevice.getDeviceId());
            }
        } else {
            result.fail(new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN,
                    "cannot publish data for device of other tenant"));
        }
        return result.future();
    }

    /**
     * Gets configuration information for a tenant.
     * <p>
     * The returned JSON object contains information as defined by Hono's
     * <a href="https://www.eclipse.org/hono/docs/api/tenant/#get-tenant-information">Tenant API</a>.
     *
     * @param tenantId The tenant to retrieve information for.
     * @param context The currently active OpenTracing span that is used to
     *                trace the retrieval of the tenant configuration.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will fail if the information cannot be retrieved. The cause will be a
     *         {@link ServiceInvocationException} containing a corresponding error code.
     *         <p>
     *         Otherwise the future will contain the configuration information.
     * @throws NullPointerException if tenant ID is {@code null}.
     */
    protected final Future<TenantObject> getTenantConfiguration(final String tenantId, final SpanContext context) {

        Objects.requireNonNull(tenantId);
        return getTenantClient().get(tenantId, context);
    }

    /**
     * Gets default properties for downstream telemetry and event messages.
     * <p>
     * The returned properties are the properties returned by
     * {@link TelemetryExecutionContext#getDownstreamMessageProperties()} plus
     * this {@linkplain #getTypeName() adapter's type name}.
     *
     * @param context The execution context for processing the downstream message.
     * @return The properties.
     */
    protected final Map<String, Object> getDownstreamMessageProperties(final TelemetryExecutionContext context) {
        final Map<String, Object> props = Objects.requireNonNull(context).getDownstreamMessageProperties();
        props.put(MessageHelper.APP_PROPERTY_ORIG_ADAPTER, getTypeName());
        return props;
    }

    /**
     * Registers checks which verify that this component is connected to the services it depends on.
     */
    @Override
    public void registerReadinessChecks(final HealthCheckHandler handler) {

        Optional.ofNullable(commandConsumerFactory)
            .ifPresent(factory -> {
                handler.register("connected-to-command-endpoint", 2000L, status -> {
                    factory.isConnected()
                        .onSuccess(connected -> status.tryComplete(Status.OK()))
                        .onFailure(t -> status.tryComplete(Status.KO()));
                });
            });

        if (tenantClient instanceof ServiceClient) {
            ((ServiceClient) tenantClient).registerReadinessChecks(handler);
        }
        if (registrationClient instanceof ServiceClient) {
            ((ServiceClient) registrationClient).registerReadinessChecks(handler);
        }
        if (credentialsClient instanceof ServiceClient) {
            ((ServiceClient) credentialsClient).registerReadinessChecks(handler);
        }
        if (deviceConnectionClient instanceof ServiceClient) {
            ((ServiceClient) deviceConnectionClient).registerReadinessChecks(handler);
        }
        if (telemetrySender instanceof ServiceClient) {
            ((ServiceClient) telemetrySender).registerReadinessChecks(handler);
        }
        if (eventSender instanceof ServiceClient) {
            ((ServiceClient ) eventSender).registerReadinessChecks(handler);
        }
    }

    /**
     * Registers a liveness check which succeeds if
     * the vert.x event loop of this protocol adapter is not blocked.
     *
     * @see #registerEventLoopBlockedCheck(HealthCheckHandler)
     */
    @Override
    public void registerLivenessChecks(final HealthCheckHandler handler) {
        registerEventLoopBlockedCheck(handler);
        if (tenantClient instanceof ServiceClient) {
            ((ServiceClient) tenantClient).registerLivenessChecks(handler);
        }
        if (registrationClient instanceof ServiceClient) {
            ((ServiceClient) registrationClient).registerLivenessChecks(handler);
        }
        if (credentialsClient instanceof ServiceClient) {
            ((ServiceClient) credentialsClient).registerLivenessChecks(handler);
        }
        if (deviceConnectionClient instanceof ServiceClient) {
            ((ServiceClient) deviceConnectionClient).registerLivenessChecks(handler);
        }
        if (telemetrySender instanceof ServiceClient) {
            ((ServiceClient) telemetrySender).registerLivenessChecks(handler);
        }
        if (eventSender instanceof ServiceClient) {
            ((ServiceClient ) eventSender).registerLivenessChecks(handler);
        }
    }

    /**
     * Triggers the creation of a <em>connected</em> event.
     *
     * @param remoteId The remote ID.
     * @param authenticatedDevice The (optional) authenticated device.
     * @return A failed future if an event producer is set but the event could not be published. Otherwise, a succeeded
     *         event.
     * @see ConnectionEventProducer#connected(ConnectionEventProducer.Context, String, String, Device, JsonObject)
     */
    protected Future<?> sendConnectedEvent(final String remoteId, final Device authenticatedDevice) {
        if (this.connectionEventProducer != null) {
            return this.connectionEventProducer.connected(connectionEventProducerContext, remoteId, getTypeName(),
                    authenticatedDevice, null);
        } else {
            return Future.succeededFuture();
        }
    }

    /**
     * Triggers the creation of a <em>disconnected</em> event.
     *
     * @param remoteId The remote ID.
     * @param authenticatedDevice The (optional) authenticated device.
     * @return A failed future if an event producer is set but the event could not be published. Otherwise, a succeeded
     *         event.
     * @see ConnectionEventProducer#disconnected(ConnectionEventProducer.Context, String, String, Device, JsonObject)
     */
    protected Future<?> sendDisconnectedEvent(final String remoteId, final Device authenticatedDevice) {
        if (this.connectionEventProducer != null) {
            return this.connectionEventProducer.disconnected(connectionEventProducerContext, remoteId, getTypeName(),
                    authenticatedDevice, null);
        } else {
            return Future.succeededFuture();
        }
    }

    /**
     * Sends an <em>empty notification</em> event for a device that will remain
     * connected for an indeterminate amount of time.
     * <p>
     * This method invokes {@link #sendTtdEvent(String, String, Device, Integer, SpanContext)}
     * with a TTD of {@code -1}.
     *
     * @param tenant The tenant that the device belongs to, who owns the device.
     * @param deviceId The device for which the TTD is reported.
     * @param authenticatedDevice The authenticated device or {@code null}.
     * @param context The currently active OpenTracing span that is used to
     *                trace the sending of the event.
     * @return A future indicating the outcome of the operation. The future will be
     *         succeeded if the TTD event has been sent downstream successfully.
     *         Otherwise, it will be failed with a {@link ServiceInvocationException}.
     * @throws NullPointerException if any of tenant or device ID are {@code null}.
     */
    protected final Future<?> sendConnectedTtdEvent(
            final String tenant,
            final String deviceId,
            final Device authenticatedDevice,
            final SpanContext context) {

        return sendTtdEvent(tenant, deviceId, authenticatedDevice, MessageHelper.TTD_VALUE_UNLIMITED, context);
    }

    /**
     * Sends an <em>empty notification</em> event for a device that has disconnected
     * from a protocol adapter.
     * <p>
     * This method invokes {@link #sendTtdEvent(String, String, Device, Integer, SpanContext)}
     * with a TTD of {@code 0}.
     *
     * @param tenant The tenant that the device belongs to, who owns the device.
     * @param deviceId The device for which the TTD is reported.
     * @param authenticatedDevice The authenticated device or {@code null}.
     * @param context The currently active OpenTracing span that is used to
     *                trace the sending of the event.
     * @return A future indicating the outcome of the operation. The future will be
     *         succeeded if the TTD event has been sent downstream successfully.
     *         Otherwise, it will be failed with a {@link ServiceInvocationException}.
     * @throws NullPointerException if any of tenant or device ID are {@code null}.
     */
    protected final Future<?> sendDisconnectedTtdEvent(
            final String tenant,
            final String deviceId,
            final Device authenticatedDevice,
            final SpanContext context) {

        return sendTtdEvent(tenant, deviceId, authenticatedDevice, 0, context);
    }

    /**
     * Sends an <em>empty notification</em> containing a given <em>time until disconnect</em> for
     * a device.
     *
     * @param tenant The tenant that the device belongs to, who owns the device.
     * @param deviceId The device for which the TTD is reported.
     * @param authenticatedDevice The authenticated device or {@code null}.
     * @param ttd The time until disconnect (seconds).
     * @param context The currently active OpenTracing span that is used to
     *                trace the sending of the event.
     * @return A future indicating the outcome of the operation. The future will be
     *         succeeded if the TTD event has been sent downstream successfully.
     *         Otherwise, it will be failed with a {@link ServiceInvocationException}.
     * @throws NullPointerException if any of tenant, device ID or TTD are {@code null}.
     */
    protected final Future<?> sendTtdEvent(
            final String tenant,
            final String deviceId,
            final Device authenticatedDevice,
            final Integer ttd,
            final SpanContext context) {

        Objects.requireNonNull(tenant);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(ttd);

        final Future<RegistrationAssertion> tokenTracker = getRegistrationAssertion(
                tenant,
                deviceId,
                authenticatedDevice,
                context);
        final Future<TenantObject> tenantConfigTracker = getTenantConfiguration(tenant, context);

        return CompositeFuture.all(tokenTracker, tenantConfigTracker).compose(ok -> {
            if (tenantConfigTracker.result().isAdapterEnabled(getTypeName())) {
                final Map<String, Object> props = new HashMap<>();
                props.put(MessageHelper.APP_PROPERTY_ORIG_ADAPTER, getTypeName());
                props.put(MessageHelper.APP_PROPERTY_QOS, QoS.AT_LEAST_ONCE.ordinal());
                props.put(MessageHelper.APP_PROPERTY_DEVICE_TTD, ttd);
                return getEventSender().sendEvent(
                        tenantConfigTracker.result(),
                        tokenTracker.result(),
                        EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION,
                        null,
                        props,
                        context)
                    .onSuccess(s -> log.debug(
                            "successfully sent TTD notification [tenant: {}, device-id: {}, TTD: {}",
                            tenant, deviceId, ttd))
                    .onFailure(t -> log.debug(
                            "failed to send TTD notification [tenant: {}, device-id: {}, TTD: {}",
                            tenant, deviceId, ttd, t));
            } else {
                // this adapter is not enabled for the tenant
                return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN));
            }
        });
    }

    /**
     * Checks if the payload conveyed in the body of a request is consistent with the indicated content type.
     *
     * @param contentType The indicated content type.
     * @param payload The payload from the request body.
     * @return {@code true} if the payload is empty and the content type is
     *         {@link EventConstants#CONTENT_TYPE_EMPTY_NOTIFICATION} or else
     *         if the content type is not {@link EventConstants#CONTENT_TYPE_EMPTY_NOTIFICATION}.
     */
    protected boolean isPayloadOfIndicatedType(final Buffer payload, final String contentType) {
        if (payload == null || payload.length() == 0) {
            return EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION.equals(contentType);
        } else {
            return !EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION.equals(contentType);
        }
    }

    /**
     * Registers a health check which tries to run an action on the protocol adapter context.
     * <p>
     * If the protocol adapter vert.x event loop is blocked, the health check procedure will not complete
     * with OK status within the defined timeout.
     *
     * @param handler The health check handler to register the checks with.
     */
    protected void registerEventLoopBlockedCheck(final HealthCheckHandler handler) {

        handler.register(
                "event-loop-blocked-check",
                getConfig().getEventLoopBlockedCheckTimeout(),
                procedure -> {
                    final Context currentContext = Vertx.currentContext();

                    if (currentContext != context) {
                        context.runOnContext(action -> {
                            procedure.tryComplete(Status.OK());
                        });
                    } else {
                        log.debug("Protocol Adapter - HealthCheck Server context match. Assume protocol adapter is alive.");
                        procedure.tryComplete(Status.OK());
                    }
                });
    }

    /**
     * Gets the options for configuring the server side trust anchor.
     * <p>
     * This implementation returns the options returned by
     * {@link org.eclipse.hono.config.AbstractConfig#getTrustOptions()} if not {@code null}.
     * Otherwise, it returns trust options for verifying a client certificate's validity period.
     *
     * @return The trust options.
     */
    @Override
    protected TrustOptions getServerTrustOptions() {

        return Optional.ofNullable(getConfig().getTrustOptions())
                .orElseGet(() -> {
                    if (getConfig().isAuthenticationRequired()) {
                        return new ValidityBasedTrustOptions();
                    } else {
                        return null;
                    }
                });
    }

    /**
     * Creates an AMQP error condition for an throwable.
     * <p>
     * Unknown error types are mapped to {@link AmqpError#PRECONDITION_FAILED}.
     *
     * @param t The throwable to map to an error condition.
     * @return The error condition.
     */
    public static ErrorCondition getErrorCondition(final Throwable t) {

        if (t instanceof AuthorizationException) {
            return ProtonHelper.condition(AmqpError.UNAUTHORIZED_ACCESS, t.getMessage());
        } else if (ServiceInvocationException.class.isInstance(t)) {
            final ServiceInvocationException error = (ServiceInvocationException) t;
            switch (error.getErrorCode()) {
            case HttpURLConnection.HTTP_BAD_REQUEST:
                return ProtonHelper.condition(Constants.AMQP_BAD_REQUEST, error.getMessage());
            case HttpURLConnection.HTTP_FORBIDDEN:
                return ProtonHelper.condition(AmqpError.UNAUTHORIZED_ACCESS, error.getMessage());
            case HttpUtils.HTTP_TOO_MANY_REQUESTS:
                return ProtonHelper.condition(AmqpError.RESOURCE_LIMIT_EXCEEDED, error.getMessage());
            default:
                return ProtonHelper.condition(AmqpError.PRECONDITION_FAILED, error.getMessage());
            }
        } else {
            return ProtonHelper.condition(AmqpError.PRECONDITION_FAILED, t.getMessage());
        }
    }

    /**
     * Maps an error that occurred during a device's connection attempt to a
     * corresponding outcome.
     *
     * @param e The error that has occurred.
     * @return The outcome.
     */
    public static ConnectionAttemptOutcome getOutcome(final Throwable e) {

        if (e instanceof AuthorizationException) {
            if (e instanceof AdapterDisabledException) {
                return ConnectionAttemptOutcome.ADAPTER_DISABLED;
            }
            if (e instanceof AdapterConnectionsExceededException) {
                return ConnectionAttemptOutcome.ADAPTER_CONNECTIONS_EXCEEDED;
            }
            if (e instanceof ConnectionDurationExceededException) {
                return ConnectionAttemptOutcome.CONNECTION_DURATION_EXCEEDED;
            }
            if (e instanceof DataVolumeExceededException) {
                return ConnectionAttemptOutcome.DATA_VOLUME_EXCEEDED;
            }
            if (e instanceof RegistrationAssertionException) {
                return ConnectionAttemptOutcome.REGISTRATION_ASSERTION_FAILURE;
            }
            if (e instanceof TenantConnectionsExceededException) {
                return ConnectionAttemptOutcome.TENANT_CONNECTIONS_EXCEEDED;
            }
            return ConnectionAttemptOutcome.UNAUTHORIZED;
        } else if (e instanceof ServiceInvocationException) {
            switch (((ServiceInvocationException) e).getErrorCode()) {
            case HttpURLConnection.HTTP_UNAUTHORIZED:
                return ConnectionAttemptOutcome.UNAUTHORIZED;
            case HttpURLConnection.HTTP_UNAVAILABLE:
                return ConnectionAttemptOutcome.UNAVAILABLE;
            default:
                return ConnectionAttemptOutcome.UNKNOWN;
            }
        } else {
            return ConnectionAttemptOutcome.UNKNOWN;
        }
    }
}
