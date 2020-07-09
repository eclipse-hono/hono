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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.BasicDeviceConnectionClientFactory;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.CommandResponse;
import org.eclipse.hono.client.CommandResponseSender;
import org.eclipse.hono.client.CommandTargetMapper;
import org.eclipse.hono.client.ConnectionLifecycle;
import org.eclipse.hono.client.CredentialsClientFactory;
import org.eclipse.hono.client.DeviceConnectionClient;
import org.eclipse.hono.client.DeviceConnectionClientFactory;
import org.eclipse.hono.client.DisconnectListener;
import org.eclipse.hono.client.DownstreamSender;
import org.eclipse.hono.client.DownstreamSenderFactory;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.ProtocolAdapterCommandConsumerFactory;
import org.eclipse.hono.client.ReconnectListener;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.RegistrationClientFactory;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.client.TenantClientFactory;
import org.eclipse.hono.config.AbstractConfig;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.auth.ValidityBasedTrustOptions;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.service.limiting.ConnectionLimitManager;
import org.eclipse.hono.service.monitoring.ConnectionEventProducer;
import org.eclipse.hono.service.resourcelimits.NoopResourceLimitChecks;
import org.eclipse.hono.service.resourcelimits.ResourceLimitChecks;
import org.eclipse.hono.service.util.ServiceBaseUtils;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.Strings;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

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
import io.vertx.core.json.JsonArray;
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

    private DownstreamSenderFactory downstreamSenderFactory;
    private RegistrationClientFactory registrationClientFactory;
    private TenantClientFactory tenantClientFactory;
    private BasicDeviceConnectionClientFactory deviceConnectionClientFactory;
    private CredentialsClientFactory credentialsClientFactory;
    private ProtocolAdapterCommandConsumerFactory commandConsumerFactory;
    private CommandTargetMapper commandTargetMapper;
    private ConnectionLimitManager connectionLimitManager;

    private ConnectionEventProducer connectionEventProducer;
    private ResourceLimitChecks resourceLimitChecks = new NoopResourceLimitChecks();
    private final ConnectionEventProducer.Context connectionEventProducerContext = new ConnectionEventProducer.Context() {

        @Override
        public DownstreamSenderFactory getMessageSenderClient() {
            return AbstractProtocolAdapterBase.this.downstreamSenderFactory;
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
     * Sets the configuration by means of Spring dependency injection.
     * <p>
     * Most protocol adapters will support a single transport protocol to communicate with devices only. For those
     * adapters there will only be a single bean instance available in the application context of type <em>T</em>.
     */
    @Autowired
    @Override
    public void setConfig(final T configuration) {
        setSpecificConfig(configuration);
    }

    /**
     * Sets the factory to use for creating a client for the Tenant service.
     *
     * @param factory The factory.
     * @throws NullPointerException if the factory is {@code null}.
     */
    @Qualifier(TenantConstants.TENANT_ENDPOINT)
    @Autowired
    public final void setTenantClientFactory(final TenantClientFactory factory) {
        this.tenantClientFactory = Objects.requireNonNull(factory);
    }

    /**
     * Gets the factory used for creating a client for the Tenant service.
     *
     * @return The factory.
     */
    public final TenantClientFactory getTenantClientFactory() {
        return tenantClientFactory;
    }

    /**
     * Gets a client for interacting with the Tenant service.
     *
     * @return The client.
     */
    protected final Future<TenantClient> getTenantClient() {
        return getTenantClientFactory().getOrCreateTenantClient();
    }

    /**
     * Sets the factory to use for creating a client for the Device Connection service.
     *
     * @param factory The factory.
     * @throws NullPointerException if the factory is {@code null}.
     */
    @Qualifier(DeviceConnectionConstants.DEVICE_CONNECTION_ENDPOINT)
    @Autowired
    public final void setDeviceConnectionClientFactory(final BasicDeviceConnectionClientFactory factory) {
        this.deviceConnectionClientFactory = Objects.requireNonNull(factory);
    }

    /**
     * Gets the factory used for creating a client for the Device Connection service.
     *
     * @return The factory.
     * @deprecated Use {@link #getDeviceConnectionClient(String)} in order to access
     *             device connection information.
     */
    @Deprecated(forRemoval = true)
    public final DeviceConnectionClientFactory getDeviceConnectionClientFactory() {
        if (deviceConnectionClientFactory instanceof DeviceConnectionClientFactory) {
            return (DeviceConnectionClientFactory) deviceConnectionClientFactory;
        } else {
             return null;
        }
    }

    /**
     * Gets a client for interacting with the Device Connection service.
     *
     * @param tenantId The tenant that the client is scoped to.
     * @return The client.
     * @throws IllegalStateException if no client factory is set.
     */
    protected final Future<DeviceConnectionClient> getDeviceConnectionClient(final String tenantId) {

        if (deviceConnectionClientFactory == null) {
            throw new IllegalStateException("Device Connection client factory is not set");
        }
        return deviceConnectionClientFactory.getOrCreateDeviceConnectionClient(tenantId);
    }

    /**
     * Sets the factory to use for creating a client for the AMQP Messaging Network.
     *
     * @param factory The factory.
     * @throws NullPointerException if the factory is {@code null}.
     */
    @Qualifier(Constants.QUALIFIER_MESSAGING)
    @Autowired
    public final void setDownstreamSenderFactory(final DownstreamSenderFactory factory) {
        this.downstreamSenderFactory = Objects.requireNonNull(factory);
    }

    /**
     * Gets the factory used for creating a client for the AMQP Messaging Network.
     *
     * @return The factory.
     */
    public final DownstreamSenderFactory getDownstreamSenderFactory() {
        return downstreamSenderFactory;
    }

    /**
     * Sets the factory to use for creating a client for the Device Registration service.
     *
     * @param factory The factory.
     * @throws NullPointerException if the factory is {@code null}.
     */
    @Qualifier(RegistrationConstants.REGISTRATION_ENDPOINT)
    @Autowired
    public final void setRegistrationClientFactory(final RegistrationClientFactory factory) {
        this.registrationClientFactory = Objects.requireNonNull(factory);
    }

    /**
     * Gets the factory used for creating a client for the Device Registration service.
     *
     * @return The factory.
     */
    public final RegistrationClientFactory getRegistrationClientFactory() {
        return registrationClientFactory;
    }

    /**
     * Sets the factory to use for creating a client for the Credentials service.
     *
     * @param factory The factory.
     * @throws NullPointerException if the factory is {@code null}.
     */
    @Qualifier(CredentialsConstants.CREDENTIALS_ENDPOINT)
    @Autowired
    public final void setCredentialsClientFactory(final CredentialsClientFactory factory) {
        this.credentialsClientFactory = Objects.requireNonNull(factory);
    }

    /**
     * Gets the factory used for creating a client for the Credentials service.
     *
     * @return The factory.
     */
    public final CredentialsClientFactory getCredentialsClientFactory() {
        return credentialsClientFactory;
    }

    /**
     * Sets the producer for connections events.
     *
     * @param connectionEventProducer The instance which will handle the production of connection events. Depending on
     *            the setup this could be a simple log message or an event using the Hono Event API.
     */
    @Autowired(required = false)
    public void setConnectionEventProducer(final ConnectionEventProducer connectionEventProducer) {
        this.connectionEventProducer = connectionEventProducer;
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
    @Autowired
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
     * Sets the component for mapping an incoming command to the gateway (if applicable)
     * and protocol adapter instance that can handle it.
     *
     * @param commandTargetMapper The mapper component.
     * @throws NullPointerException if commandTargetMapper is {@code null}.
     */
    @Autowired
    public final void setCommandTargetMapper(final CommandTargetMapper commandTargetMapper) {
        this.commandTargetMapper = Objects.requireNonNull(commandTargetMapper);
    }

    /**
     * Sets the ResourceLimitChecks instance used to check if the number of connections exceeded the limit or not.
     *
     * @param resourceLimitChecks The ResourceLimitChecks instance
     * @throws NullPointerException if the resourceLimitChecks is {@code null}.
     */
    @Autowired(required = false)
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
     * Establishes the connections to the services this adapter depends on.
     * <p>
     * Note that the connections will most likely not have been established when the
     * returned future completes. The {@link #isConnected()} method can be used to
     * determine the current connection status.
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
        } else if (tenantClientFactory == null) {
            result.fail(new IllegalStateException("Tenant client factory must be set"));
        } else if (downstreamSenderFactory == null) {
            result.fail(new IllegalStateException("AMQP Messaging Network client must be set"));
        } else if (registrationClientFactory == null) {
            result.fail(new IllegalStateException("Device Registration client factory must be set"));
        } else if (credentialsClientFactory == null) {
            result.fail(new IllegalStateException("Credentials client factory must be set"));
        } else if (commandConsumerFactory == null) {
            result.fail(new IllegalStateException("Command & Control client factory must be set"));
        } else if (deviceConnectionClientFactory == null) {
            result.fail(new IllegalStateException("Device Connection client factory must be set"));
        } else {

            log.info("using ResourceLimitChecks [{}]", resourceLimitChecks.getClass().getName());

            connectToService(tenantClientFactory, "Tenant service");
            connectToService(downstreamSenderFactory, "AMQP Messaging Network");
            connectToService(registrationClientFactory, "Device Registration service");
            connectToService(credentialsClientFactory, "Credentials service");
            if (deviceConnectionClientFactory instanceof ConnectionLifecycle) {
                connectToService((ConnectionLifecycle<?>) deviceConnectionClientFactory, "Device Connection service");
            }

            connectToService(
                    commandConsumerFactory,
                    "Command & Control",
                    this::onCommandConnectionLost,
                    this::onCommandConnectionEstablished)
            .setHandler(c -> {
                if (c.succeeded()) {
                    onCommandConnectionEstablished(c.result());
                }
            });
            // initialize components dependent on the above clientFactories
            commandTargetMapper.initialize(registrationClientFactory, deviceConnectionClientFactory);
            commandConsumerFactory.initialize(commandTargetMapper, deviceConnectionClientFactory);

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
        results.add(disconnectFromService(downstreamSenderFactory));
        results.add(disconnectFromService(tenantClientFactory));
        results.add(disconnectFromService(registrationClientFactory));
        results.add(disconnectFromService(credentialsClientFactory));
        results.add(disconnectFromService(commandConsumerFactory));
        if (deviceConnectionClientFactory instanceof ConnectionLifecycle) {
            results.add(disconnectFromService((ConnectionLifecycle<?>) deviceConnectionClientFactory));
        }
        return CompositeFuture.all(results);
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
     * Checks if this adapter is enabled for a given tenant.
     * 
     * @param tenantConfig The tenant to check for.
     * @return A succeeded future if this adapter is enabled for the tenant.
     *         Otherwise the future will be failed with a {@link ClientErrorException}
     *         containing the 403 Forbidden status code.
     * @throws NullPointerException if tenant is {@code null}.
     */
    protected final Future<TenantObject> isAdapterEnabled(final TenantObject tenantConfig) {

        Objects.requireNonNull(tenantConfig);

        if (tenantConfig.isAdapterEnabled(getTypeName())) {
            log.debug("protocol adapter [{}] is enabled for tenant [{}]",
                    getTypeName(), tenantConfig.getTenantId());
            return Future.succeededFuture(tenantConfig);
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
     *         Otherwise the future will be failed with a {@link ClientErrorException}
     *         containing the 403 Forbidden status code.
     * @throws NullPointerException if tenant is {@code null}.
     */
    protected Future<Void> checkConnectionLimit(final TenantObject tenantConfig, final SpanContext spanContext) {

        Objects.requireNonNull(tenantConfig);

        final Future<Void> connectionLimitCheckResult = resourceLimitChecks
                .isConnectionLimitReached(tenantConfig, spanContext)
                .recover(t -> Future.succeededFuture(Boolean.FALSE))
                .compose(isExceeded -> {
                    if (isExceeded) {
                        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN));
                    } else {
                        return Future.succeededFuture();
                    }
                });
        final Future<Void> messageLimitCheckResult = checkMessageLimit(tenantConfig, 1, spanContext)
                .recover(t -> {
                    if (t instanceof ClientErrorException) {
                        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN));
                    }
                    return Future.failedFuture(t);
                });

        return CompositeFuture
                .all(connectionLimitCheckResult, checkConnectionDurationLimit(tenantConfig, spanContext),
                        messageLimitCheckResult)
                .map(ok -> null);
    }

    /**
     * Checks if this adapter may accept another telemetry or event message from a device.
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
     *         Otherwise, the future will be failed with a {@link ClientErrorException}
     *         containing the 403 Forbidden status code.
     * @throws NullPointerException if tenantConfig is {@code null}.
     */
    protected Future<Void> checkConnectionDurationLimit(final TenantObject tenantConfig,
            final SpanContext spanContext) {

        Objects.requireNonNull(tenantConfig);

        return resourceLimitChecks.isConnectionDurationLimitReached(tenantConfig, spanContext)
                .recover(t -> Future.succeededFuture(Boolean.FALSE))
                .compose(isExceeded -> {
                    if (isExceeded) {
                        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN));
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
     *         Otherwise, the future will be failed with a {@link ServiceInvocationException}.
     */
    protected final Future<Void> checkDeviceRegistration(final Device device, final SpanContext context) {

        Objects.requireNonNull(device);

        return getRegistrationAssertion(
                device.getTenantId(),
                device.getDeviceId(),
                null,
                context).map(assertion -> null);
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
     * Checks if this adapter is connected to the services it depends on.
     * <p>
     * Subclasses may override this method in order to add checks or omit checks for
     * connection to services that are not used/needed by the adapter.
     *
     * @return A future indicating the outcome of the check. The future will succeed if this adapter is currently
     *         connected to
     *         <ul>
     *         <li>a Tenant service</li>
     *         <li>a Device Registration service</li>
     *         <li>a Credentials service</li>
     *         <li>a service implementing the south bound Telemetry &amp; Event APIs</li>
     *         <li>a service implementing the south bound Command &amp; Control API</li>
     *         </ul>
     *         Otherwise, the future will fail.
     */
    protected Future<Void> isConnected() {

        @SuppressWarnings("rawtypes")
        final List<Future> connections = new ArrayList<>();
        connections.add(Optional.ofNullable(tenantClientFactory)
                .map(client -> client.isConnected())
                .orElse(Future.failedFuture(new ServerErrorException(
                        HttpURLConnection.HTTP_UNAVAILABLE, "Tenant client factory is not set"))));
        connections.add(Optional.ofNullable(registrationClientFactory)
                .map(client -> client.isConnected())
                .orElse(Future
                        .failedFuture(new ServerErrorException(
                                HttpURLConnection.HTTP_UNAVAILABLE, "Device Registration client factory is not set"))));
        connections.add(Optional.ofNullable(credentialsClientFactory)
                .map(client -> client.isConnected())
                .orElse(Future.failedFuture(new ServerErrorException(
                        HttpURLConnection.HTTP_UNAVAILABLE, "Credentials client factory is not set"))));
        connections.add(Optional.ofNullable(downstreamSenderFactory)
                .map(client -> client.isConnected())
                .orElse(Future.failedFuture(new ServerErrorException(
                        HttpURLConnection.HTTP_UNAVAILABLE, "Messaging client is not set"))));
        connections.add(Optional.ofNullable(commandConsumerFactory)
                .map(client -> client.isConnected())
                .orElse(Future.failedFuture(new ServerErrorException(
                        HttpURLConnection.HTTP_UNAVAILABLE, "Command & Control client factory is not set"))));
        connections.add(Optional.ofNullable(deviceConnectionClientFactory)
                .map(client -> {
                    if (deviceConnectionClientFactory instanceof ConnectionLifecycle) {
                        return ((ConnectionLifecycle<?>) client).isConnected();
                    } else {
                        return Future.succeededFuture();
                    }
                })
                .orElse(Future.failedFuture(new ServerErrorException(
                        HttpURLConnection.HTTP_UNAVAILABLE, "Device Connection client factory is not set"))));
        return CompositeFuture.all(connections).mapEmpty();
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
    protected final Future<MessageConsumer> createCommandConsumer(
            final String tenantId,
            final String deviceId,
            final Handler<CommandContext> commandConsumer,
            final SpanContext context) {

        return commandConsumerFactory.createCommandConsumer(
                tenantId,
                deviceId,
                commandContext -> {
                    Tags.COMPONENT.set(commandContext.getCurrentSpan(), getTypeName());
                    commandConsumer.handle(commandContext);
                },
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
     * Gets a client for sending telemetry data for a tenant.
     *
     * @param tenantId The tenant to send the telemetry data for.
     * @return The client.
     */
    protected final Future<DownstreamSender> getTelemetrySender(final String tenantId) {
        return getDownstreamSenderFactory().getOrCreateTelemetrySender(tenantId);
    }

    /**
     * Gets a client for sending events for a tenant.
     *
     * @param tenantId The tenant to send the events for.
     * @return The client.
     */
    protected final Future<DownstreamSender> getEventSender(final String tenantId) {
        return getDownstreamSenderFactory().getOrCreateEventSender(tenantId);
    }

    /**
     * Gets a client for interacting with the Device Registration service.
     *
     * @param tenantId The tenant that the client is scoped to.
     * @return The client.
     */
    protected final Future<RegistrationClient> getRegistrationClient(final String tenantId) {
        return getRegistrationClientFactory().getOrCreateRegistrationClient(tenantId);
    }

    /**
     * Gets an assertion for a device's registration status.
     * <p>
     * The returned JSON object may include <em>default</em>
     * values for properties to set on messages published by the device under
     * property {@link RegistrationConstants#FIELD_PAYLOAD_DEFAULTS}.
     * <p>
     * Note that this method will also update the last gateway associated with the given device (if applicable).
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
     * @return The assertion.
     * @throws NullPointerException if any of tenant or device ID are {@code null}.
     */
    protected final Future<JsonObject> getRegistrationAssertion(
            final String tenantId,
            final String deviceId,
            final Device authenticatedDevice,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        final Future<String> gatewayId = getGatewayId(tenantId, deviceId, authenticatedDevice);

        return gatewayId
                .compose(gwId -> getRegistrationClient(tenantId))
                .compose(client -> client.assertRegistration(deviceId, gatewayId.result(), context))
                .compose(registrationAssertion -> {
                    // the updateLastGateway invocation shouldn't delay or possibly fail the surrounding operation
                    // so don't wait for the outcome here
                    updateLastGateway(registrationAssertion, tenantId, deviceId, authenticatedDevice, context)
                            .otherwise(t -> {
                                log.warn("failed to update last gateway [tenantId: {}, deviceId: {}]", tenantId, deviceId, t);
                                return null;
                            });
                    return Future.succeededFuture(registrationAssertion);
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
     */
    protected final Future<JsonObject> updateLastGateway(
            final JsonObject registrationAssertion,
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
                .compose(gwId -> getDeviceConnectionClient(tenantId))
                .compose(client -> client.setLastKnownGatewayForDevice(deviceId,
                        Optional.ofNullable(gatewayIdFuture.result()).orElse(deviceId), context))
                .map(registrationAssertion);
    }

    private boolean isGatewaySupportedForDevice(final JsonObject registrationAssertion) {
        final Object viaObj = registrationAssertion.getValue(RegistrationConstants.FIELD_VIA);
        return viaObj instanceof JsonArray && !((JsonArray) viaObj).isEmpty();
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
        return getTenantClient().compose(client -> client.get(tenantId, context));
    }

    /**
     * Creates a new AMQP 1.0 message.
     * <p>
     * Subclasses are encouraged to use this method for creating {@code Message} instances to be sent downstream in
     * order to have required Hono specific properties being set on the message automatically.
     * <p>
     * This method simply delegates to {@link #newMessage(ResourceIdentifier, String, String, Buffer, TenantObject,
     * JsonObject, Integer, Duration)}.
     *
     * @param target The target address of the message or {@code null} if the message's
     *               <em>to</em> property contains the target address. The target
     *               address is used to determine if the message represents an event or not.
     *               Determining this information from the <em>to</em> property
     *               requires additional parsing which can be prevented by passing in the
     *               target address as a {@code ResourceIdentifier} instead.
     * @param publishAddress The address that the message has been published to originally by the device or
     *            {@code null} if unknown.
     *            <p>
     *            This address will be transport protocol specific, e.g. an HTTP based adapter will probably use URIs
     *            here whereas an MQTT based adapter might use the MQTT message's topic.
     * @param contentType The content type describing the message's payload or {@code null} if no content type
     *                    should be set.
     * @param payload The message payload or {@code null} if the message has no payload.
     * @param tenant The information registered for the tenant that the device belongs to or {@code null}
     *               if no information about the tenant is available.
     * @param registrationInfo The device's registration information as retrieved by the <em>Device Registration</em>
     *            service's <em>assert Device Registration</em> operation.
     * @param timeUntilDisconnect The number of seconds until the device that has published the message will disconnect
     *            from the protocol adapter or {@code null} if unknown.
     * @return The newly created message.
     * @throws NullPointerException if target or registration info are {@code null}.
     */
    protected final Message newMessage(
            final ResourceIdentifier target,
            final String publishAddress,
            final String contentType,
            final Buffer payload,
            final TenantObject tenant,
            final JsonObject registrationInfo,
            final Integer timeUntilDisconnect) {

        return newMessage(target, publishAddress, contentType, payload, tenant, registrationInfo, timeUntilDisconnect,
                null);
    }

    /**
     * Creates a new AMQP 1.0 message.
     * <p>
     * Subclasses are encouraged to use this method for creating {@code Message} instances to be sent downstream in
     * order to have required Hono specific properties being set on the message automatically.
     * <p>
     * This method simply delegates to {@link MessageHelper#newMessage(ResourceIdentifier, String, String, Buffer,
     * TenantObject, JsonObject, Integer, Duration, String, boolean, boolean)}.
     *
     * @param target The target address of the message or {@code null} if the message's
     *               <em>to</em> property contains the target address. The target
     *               address is used to determine if the message represents an event or not.
     *               Determining this information from the <em>to</em> property
     *               requires additional parsing which can be prevented by passing in the
     *               target address as a {@code ResourceIdentifier} instead.
     * @param publishAddress The address that the message has been published to originally by the device or
     *            {@code null} if unknown.
     *            <p>
     *            This address will be transport protocol specific, e.g. an HTTP based adapter will probably use URIs
     *            here whereas an MQTT based adapter might use the MQTT message's topic.
     * @param contentType The content type describing the message's payload or {@code null} if no content type
     *                    should be set.
     * @param payload The message payload or {@code null} if the message has no payload.
     * @param tenant The information registered for the tenant that the device belongs to or {@code null}
     *               if no information about the tenant is available.
     * @param registrationInfo The device's registration information as retrieved by the <em>Device Registration</em>
     *            service's <em>assert Device Registration</em> operation.
     * @param timeUntilDisconnect The number of seconds until the device that has published the message will disconnect
     *            from the protocol adapter or {@code null} if unknown.
     * @param timeToLive The message's <em>time-to-live</em> as provided by the device or {@code null} if the
     *                   device did not provide any TTL.
     * @return The newly created message.
     * @throws NullPointerException if registration info is {@code null}.
     */
    protected final Message newMessage(
            final ResourceIdentifier target,
            final String publishAddress,
            final String contentType,
            final Buffer payload,
            final TenantObject tenant,
            final JsonObject registrationInfo,
            final Integer timeUntilDisconnect,
            final Duration timeToLive) {

        Objects.requireNonNull(registrationInfo);

        return MessageHelper.newMessage(
                target,
                publishAddress,
                contentType,
                payload,
                tenant,
                registrationInfo.getJsonObject(RegistrationConstants.FIELD_PAYLOAD_DEFAULTS),
                timeUntilDisconnect,
                timeToLive,
                getTypeName(),
                getConfig().isDefaultsEnabled(),
                getConfig().isJmsVendorPropsEnabled());
    }

    /**
     * Adds Hono specific properties to an AMQP 1.0 message.
     * <p>
     * This method simply delegates to {@link #addProperties(Message, ResourceIdentifier, String, TenantObject, JsonObject, Integer, Duration)}.
     * 
     * @param msg The message to add the properties to.
     * @param target The target address of the message or {@code null} if the message's
     *               <em>to</em> property contains the target address. The target
     *               address is used to determine if the message represents an event or not.
     *               Determining this information from the <em>to</em> property
     *               requires additional parsing which can be prevented by passing in the
     *               target address as a {@code ResourceIdentifier} instead.
     * @param publishAddress The address that the message has been published to originally by the device or
     *            {@code null} if unknown.
     *            <p>
     *            This address will be transport protocol specific, e.g. an HTTP based adapter will probably use URIs
     *            here whereas an MQTT based adapter might use the MQTT message's topic.
     * @param tenant The information registered for the tenant that the device belongs to or {@code null}
     *               if no information about the tenant is available.
     * @param registrationInfo The device's registration information as retrieved by the <em>Device Registration</em>
     *            service's <em>assert Device Registration</em> operation.
     * @param timeUntilDisconnect The number of seconds until the device that has published the message will disconnect
     *            from the protocol adapter or {@code null} if unknown.
     * @return The message with its properties set.
     * @throws NullPointerException if message or registration info are {@code null}.
     */
    protected final Message addProperties(
            final Message msg,
            final ResourceIdentifier target,
            final String publishAddress,
            final TenantObject tenant,
            final JsonObject registrationInfo,
            final Integer timeUntilDisconnect) {

        return addProperties(msg, target, publishAddress, tenant, registrationInfo, timeUntilDisconnect, null);
    }

    /**
     * Adds Hono specific properties to an AMQP 1.0 message.
     * <p>
     * This method simply delegates to {@link MessageHelper#addProperties(Message, ResourceIdentifier,
     * String, TenantObject, JsonObject, Integer, Duration, String, boolean, boolean)}.
     * 
     * @param msg The message to add the properties to.
     * @param target The target address of the message or {@code null} if the message's
     *               <em>to</em> property contains the target address. The target
     *               address is used to determine if the message represents an event or not.
     *               Determining this information from the <em>to</em> property
     *               requires additional parsing which can be prevented by passing in the
     *               target address as a {@code ResourceIdentifier} instead.
     * @param publishAddress The address that the message has been published to originally by the device or
     *            {@code null} if unknown.
     *            <p>
     *            This address will be transport protocol specific, e.g. an HTTP based adapter will probably use URIs
     *            here whereas an MQTT based adapter might use the MQTT message's topic.
     * @param tenant The information registered for the tenant that the device belongs to or {@code null}
     *               if no information about the tenant is available.
     * @param registrationInfo The device's registration information as retrieved by the <em>Device Registration</em>
     *            service's <em>assert Device Registration</em> operation.
     * @param timeUntilDisconnect The number of seconds until the device that has published the message will disconnect
     *            from the protocol adapter or {@code null} if unknown.
     * @param timeToLive The message's <em>time-to-live</em> as provided by the device or {@code null} if the
     *                   device did not provide any TTL.
     * @return The message with its properties set.
     * @throws NullPointerException if message or registration info are {@code null}.
     */
    protected final Message addProperties(
            final Message msg,
            final ResourceIdentifier target,
            final String publishAddress,
            final TenantObject tenant,
            final JsonObject registrationInfo,
            final Integer timeUntilDisconnect,
            final Duration timeToLive) {

        Objects.requireNonNull(msg);
        Objects.requireNonNull(target);
        Objects.requireNonNull(registrationInfo);

        return MessageHelper.addProperties(
                msg,
                target,
                publishAddress,
                tenant,
                registrationInfo.getJsonObject(RegistrationConstants.FIELD_PAYLOAD_DEFAULTS),
                timeUntilDisconnect,
                timeToLive,
                getTypeName(),
                getConfig().isDefaultsEnabled(),
                getConfig().isJmsVendorPropsEnabled());
    }

    /**
     * Registers a check that succeeds if this component is connected to the services it depends on.
     * 
     * @see #isConnected()
     */
    @Override
    public void registerReadinessChecks(final HealthCheckHandler handler) {
        handler.register("connection-to-services", 2000L, status -> {
            isConnected().map(connected -> {
                status.tryComplete(Status.OK());
                return null;
            }).otherwise(t -> {
                status.tryComplete(Status.KO());
                return null;
            });
        });
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
    protected final Future<ProtonDelivery> sendConnectedTtdEvent(
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
    protected final Future<ProtonDelivery> sendDisconnectedTtdEvent(
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
    protected final Future<ProtonDelivery> sendTtdEvent(
            final String tenant,
            final String deviceId,
            final Device authenticatedDevice,
            final Integer ttd,
            final SpanContext context) {

        Objects.requireNonNull(tenant);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(ttd);

        final Future<JsonObject> tokenTracker = getRegistrationAssertion(tenant, deviceId, authenticatedDevice, context);
        final Future<TenantObject> tenantConfigTracker = getTenantConfiguration(tenant, context);
        final Future<DownstreamSender> senderTracker = getEventSender(tenant);

        return CompositeFuture.all(tokenTracker, tenantConfigTracker, senderTracker).compose(ok -> {
            if (tenantConfigTracker.result().isAdapterEnabled(getTypeName())) {
                final DownstreamSender sender = senderTracker.result();
                final Message msg = newMessage(
                        ResourceIdentifier.from(EventConstants.EVENT_ENDPOINT, tenant, deviceId),
                        EventConstants.EVENT_ENDPOINT,
                        EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION,
                        null,
                        tenantConfigTracker.result(),
                        tokenTracker.result(),
                        ttd);
                return sender.sendAndWaitForOutcome(msg, context);
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
     * This method may be set as the close handler of the command consumer.
     * <p>
     * This default implementation simply logs that the link was closed and does
     * not try to reopen it.
     *
     * @param tenant The tenant of the device for that a command may be received.
     * @param deviceId The id of the device for that a command may be received.
     * @param commandMessageConsumer The Handler that will be called for each command to the device.
     */
    protected void onCloseCommandConsumer(
            final String tenant,
            final String deviceId,
            final BiConsumer<ProtonDelivery, Message> commandMessageConsumer) {

        log.debug("command consumer closed [tenantId: {}, deviceId: {}] - no command will be received for this device anymore",
                tenant, deviceId);
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
     * This implementation returns the options returned by {@link AbstractConfig#getTrustOptions()} if not {@code null}.
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
     * Non {@link ServiceInvocationException} instances are mapped to {@link AmqpError#PRECONDITION_FAILED}.
     *
     * @param t The throwable to map to an error condition.
     * @return The error condition.
     */
    protected final ErrorCondition getErrorCondition(final Throwable t) {
        if (ServiceInvocationException.class.isInstance(t)) {
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
     * Gets the manager to use for connection limits.
     * 
     * @return The manager. May be {@code null}.
     */
    public final ConnectionLimitManager getConnectionLimitManager() {
        return connectionLimitManager;
    }

    /**
     * Sets the manager to use for connection limits.
     * 
     * @param connectionLimitManager The implementation that manages the connection limit.
     */
    public final void setConnectionLimitManager(final ConnectionLimitManager connectionLimitManager) {
        this.connectionLimitManager = connectionLimitManager;
    }
}
