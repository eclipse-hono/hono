/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.CommandConnection;
import org.eclipse.hono.client.CommandConsumer;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.CommandResponse;
import org.eclipse.hono.client.CommandResponseSender;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.config.AbstractConfig;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.auth.ValidityBasedTrustOptions;
import org.eclipse.hono.service.monitoring.ConnectionEventProducer;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
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
import io.opentracing.SpanContext;
import io.opentracing.tag.Tags;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.TrustOptions;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.proton.ProtonConnection;
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
    protected static final String CONTENT_TYPE_OCTET_STREAM = "application/octet-stream";
    /**
     * The key used for storing a Micrometer {@code Sample} in an
     * execution context.
     */
    protected static final String KEY_MICROMETER_SAMPLE = "micrometer.sample";

    private HonoClient messagingClient;
    private HonoClient registrationServiceClient;
    private HonoClient tenantServiceClient;
    private HonoClient credentialsServiceClient;
    private CommandConnection commandConnection;

    private ConnectionEventProducer connectionEventProducer;

    private final ConnectionEventProducer.Context connectionEventProducerContext = new ConnectionEventProducer.Context() {

        @Override
        public HonoClient getDeviceRegistryClient() {
            return AbstractProtocolAdapterBase.this.registrationServiceClient;
        }

        @Override
        public HonoClient getMessageSenderClient() {
            return AbstractProtocolAdapterBase.this.messagingClient;
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
     * Gets the Micrometer used to track the processing
     * of a command message.
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
     * Sets the client to use for connecting to the Tenant service.
     *
     * @param tenantClient The client.
     * @throws NullPointerException if the client is {@code null}.
     */
    @Qualifier(TenantConstants.TENANT_ENDPOINT)
    @Autowired
    public final void setTenantServiceClient(final HonoClient tenantClient) {
        this.tenantServiceClient = Objects.requireNonNull(tenantClient);
    }

    /**
     * Gets the client used for connecting to the Tenant service.
     *
     * @return The client.
     */
    public final HonoClient getTenantServiceClient() {
        return tenantServiceClient;
    }

    /**
     * Gets a client for interacting with the Tenant service.
     *
     * @return The client.
     */
    protected final Future<TenantClient> getTenantClient() {
        return getTenantServiceClient().getOrCreateTenantClient();
    }

    /**
     * Sets the client to use for connecting to the Hono Messaging component.
     *
     * @param honoClient The client.
     * @throws NullPointerException if hono client is {@code null}.
     */
    @Qualifier(Constants.QUALIFIER_MESSAGING)
    @Autowired
    public final void setHonoMessagingClient(final HonoClient honoClient) {
        this.messagingClient = Objects.requireNonNull(honoClient);
    }

    /**
     * Gets the client used for connecting to the Hono Messaging component.
     *
     * @return The client.
     */
    public final HonoClient getHonoMessagingClient() {
        return messagingClient;
    }

    /**
     * Sets the client to use for connecting to the Device Registration service.
     *
     * @param registrationServiceClient The client.
     * @throws NullPointerException if the client is {@code null}.
     */
    @Qualifier(RegistrationConstants.REGISTRATION_ENDPOINT)
    @Autowired
    public final void setRegistrationServiceClient(final HonoClient registrationServiceClient) {
        this.registrationServiceClient = Objects.requireNonNull(registrationServiceClient);
    }

    /**
     * Gets the client used for connecting to the Device Registration service.
     *
     * @return The client.
     */
    public final HonoClient getRegistrationServiceClient() {
        return registrationServiceClient;
    }

    /**
     * Sets the client to use for connecting to the Credentials service.
     *
     * @param credentialsServiceClient The client.
     * @throws NullPointerException if the client is {@code null}.
     */
    @Qualifier(CredentialsConstants.CREDENTIALS_ENDPOINT)
    @Autowired
    public final void setCredentialsServiceClient(final HonoClient credentialsServiceClient) {
        this.credentialsServiceClient = Objects.requireNonNull(credentialsServiceClient);
    }

    /**
     * Gets the client used for connecting to the Credentials service.
     *
     * @return The client.
     */
    public final HonoClient getCredentialsServiceClient() {
        return credentialsServiceClient;
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
     * @return The implementation for producing connection events. Maybe {@code null}.
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
     * The name returned by this method is added to a downstream message by the
     * {@link #addProperties(Message, JsonObject, boolean)} method.
     *
     * @return The adapter's name.
     */
    protected abstract String getTypeName();

    /**
     * Gets the number of seconds after which this protocol adapter should
     * give up waiting for an upstream command for a device of a given tenant.
     * <p>
     * Protocol adapters may override this method to e.g. use a static value
     * for all tenants.
     * 
     * @param tenant The tenant that the device belongs to.
     * @param deviceTtd The TTD value provided by the device.
     * @return A succeeded future that contains {@code null} if device TTD is {@code null},
     *         or otherwise the lesser of device TTD and the value returned by
     *         {@link TenantObject#getMaxTimeUntilDisconnect(String)}.
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
     * Sets the client to use for connecting to the AMQP 1.0 network to receive commands.
     *
     * @param commandConnection The command connection.
     * @throws NullPointerException if the connection is {@code null}.
     */
    @Autowired
    public final void setCommandConnection(final CommandConnection commandConnection) {
        this.commandConnection = Objects.requireNonNull(commandConnection);
    }

    /**
     * Gets the client used for connecting to the AMQP 1.0 network to receive commands.
     *
     * @return The command connection.
     */
    public final CommandConnection getCommandConnection() {
        return this.commandConnection;
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

        final Future<Void> result = Future.future();

        if (Strings.isNullOrEmpty(getTypeName())) {
            result.fail(new IllegalStateException("adapter does not define a typeName"));
        } else if (tenantServiceClient == null) {
            result.fail(new IllegalStateException("Tenant service client must be set"));
        } else if (messagingClient == null) {
            result.fail(new IllegalStateException("AMQP Messaging Network client must be set"));
        } else if (registrationServiceClient == null) {
            result.fail(new IllegalStateException("Device Registration service client must be set"));
        } else if (credentialsServiceClient == null) {
            result.fail(new IllegalStateException("Credentials service client must be set"));
        } else if (commandConnection == null) {
            result.fail(new IllegalStateException("Command & Control service client must be set"));
        } else {
            connectToService(tenantServiceClient, "Tenant service");
            connectToService(messagingClient, "AMQP Messaging Network");
            connectToService(registrationServiceClient, "Device Registration service");
            connectToService(credentialsServiceClient, "Credentials service");
            connectToService(
                    commandConnection,
                    "Command and Control service",
                    this::onCommandConnectionEstablished,
                    this::onCommandConnectionLost);
            doStart(result);
        }
        return result;
    }

    /**
     * Invoked after the adapter has started up.
     * <p>
     * This default implementation simply completes the future.
     * <p>
     * Subclasses should override this method to perform any work required on start-up of this protocol adapter.
     *
     * @param startFuture The future to complete once start up is complete.
     */
    protected void doStart(final Future<Void> startFuture) {
        startFuture.complete();
    }

    @Override
    protected final Future<Void> stopInternal() {

        LOG.info("stopping protocol adapter");
        final Future<Void> result = Future.future();
        final Future<Void> doStopResult = Future.future();
        doStop(doStopResult);
        doStopResult
                .compose(s -> closeServiceClients())
                .recover(t -> {
                    LOG.info("error while stopping protocol adapter", t);
                    return Future.failedFuture(t);
                }).compose(s -> {
                    result.complete();
                    LOG.info("successfully stopped protocol adapter");
                }, result);
        return result;
    }

    private Future<?> closeServiceClients() {

        return CompositeFuture.all(
                closeServiceClient(messagingClient),
                closeServiceClient(commandConnection),
                closeServiceClient(tenantServiceClient),
                closeServiceClient(registrationServiceClient),
                closeServiceClient(credentialsServiceClient));
    }

    private Future<Void> closeServiceClient(final HonoClient client) {

        final Future<Void> shutdownTracker = Future.future();
        if (client == null) {
            shutdownTracker.complete();
        } else {
            client.shutdown(shutdownTracker.completer());
        }

        return shutdownTracker;
    }

    /**
     * Invoked directly before the adapter is shut down.
     * <p>
     * Subclasses should override this method to perform any work required before shutting down this protocol adapter.
     *
     * @param stopFuture The future to complete once all work is done and shut down should commence.
     */
    protected void doStop(final Future<Void> stopFuture) {
        // to be overridden by subclasses
        stopFuture.complete();
    }

    /**
     * Checks if this adapter is enabled for a given tenant.
     * 
     * @param tenantConfig The tenant to check for.
     * @return A succeeded future if this adapter is enabled for the tenant.
     *         Otherwise the future will be failed with a {@link ClientErrorException}.
     */
    protected final Future<TenantObject> isAdapterEnabled(final TenantObject tenantConfig) {
        if (tenantConfig.isAdapterEnabled(getTypeName())) {
            LOG.debug("protocol adapter [{}] is enabled for tenant [{}]",
                    getTypeName(), tenantConfig.getTenantId());
            return Future.succeededFuture(tenantConfig);
        } else {
            LOG.debug("protocol adapter [{}] is disabled for tenant [{}]",
                    getTypeName(), tenantConfig.getTenantId());
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN,
                    "adapter disabled for tenant"));
        }
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
        final Future<ResourceIdentifier> result = Future.future();

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
            } else {
                result.complete(address);
            }
        }
        return result;
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
     * Connects to a Hono Service component using the configured client.
     *
     * @param client The Hono client for the service that is to be connected.
     * @param serviceName The name of the service that is to be connected (used for logging).
     * @return A future that will succeed once the connection has been established. The future will fail if the
     *         connection cannot be established.
     * @throws NullPointerException if serviceName is {@code null}.
     * @throws IllegalArgumentException if client is {@code null}.
     */
    protected final Future<HonoClient> connectToService(final HonoClient client, final String serviceName) {
        return connectToService(client, serviceName, onConnect -> {}, onConnectionLost -> {});
    }

    /**
     * Connects to a Hono Service component using the configured client.
     *
     * @param client The Hono client for the service that is to be connected.
     * @param serviceName The name of the service that is to be connected (used for logging).
     * @param connectionEstablishedHandler A handler to invoke once the connection is established.
     * @param connectionLostHandler A handler to invoke when the connection is lost unexpectedly.
     * @return A future that will succeed once the connection has been established. The future will fail if the
     *         connection cannot be established.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected final Future<HonoClient> connectToService(
            final HonoClient client,
            final String serviceName,
            final Handler<HonoClient> connectionEstablishedHandler,
            final Handler<HonoClient> connectionLostHandler) {

        Objects.requireNonNull(client);
        Objects.requireNonNull(serviceName);
        Objects.requireNonNull(connectionEstablishedHandler);
        Objects.requireNonNull(connectionLostHandler);

        final Handler<ProtonConnection> disconnectHandler = getHandlerForDisconnectHonoService(client, serviceName,
                connectionEstablishedHandler, connectionLostHandler);

        return client.connect(disconnectHandler).map(connectedClient -> {
            LOG.info("connected to {}", serviceName);
            connectionEstablishedHandler.handle(connectedClient);
            return connectedClient;
        }).recover(t -> {
            LOG.warn("failed to connect to {}", serviceName, t);
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
    protected void onCommandConnectionLost(final HonoClient commandConnection) {
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
    protected void onCommandConnectionEstablished(final HonoClient commandConnection) {
        // empty by default
    }

    private Handler<ProtonConnection> getHandlerForDisconnectHonoService(
            final HonoClient client,
            final String serviceName,
            final Handler<HonoClient> connectHandler,
            final Handler<HonoClient> connectionLostHandler) {

        return (connection) -> {
            connectionLostHandler.handle(client);
            vertx.setTimer(Constants.DEFAULT_RECONNECT_INTERVAL_MILLIS, reconnect -> {
                LOG.info("attempting to reconnect to {}", serviceName);
                client.connect(getHandlerForDisconnectHonoService(client, serviceName, connectHandler, connectionLostHandler)).setHandler(connectAttempt -> {
                    if (connectAttempt.succeeded()) {
                        LOG.info("reconnected to {}", serviceName);
                        connectHandler.handle(connectAttempt.result());
                    } else {
                        LOG.debug("cannot reconnect to {}: {}", serviceName, connectAttempt.cause().getMessage());
                    }
                });
            });
        };
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

        final Future<Void> tenantCheck = Optional.ofNullable(tenantServiceClient)
                .map(client -> client.isConnected())
                .orElse(Future.failedFuture(new ServerErrorException(
                        HttpURLConnection.HTTP_UNAVAILABLE, "Tenant service client is not set")));
        final Future<Void> registrationCheck = Optional.ofNullable(registrationServiceClient)
                .map(client -> client.isConnected())
                .orElse(Future
                        .failedFuture(new ServerErrorException(
                                HttpURLConnection.HTTP_UNAVAILABLE, "Device Registration service client is not set")));
        final Future<Void> credentialsCheck = Optional.ofNullable(credentialsServiceClient)
                .map(client -> client.isConnected())
                .orElse(Future.failedFuture(new ServerErrorException(
                        HttpURLConnection.HTTP_UNAVAILABLE, "Credentials service client is not set")));
        final Future<Void> messagingCheck = Optional.ofNullable(messagingClient)
                .map(client -> client.isConnected())
                .orElse(Future.failedFuture(new ServerErrorException(
                        HttpURLConnection.HTTP_UNAVAILABLE, "Messaging client is not set")));
        final Future<Void> commandCheck = Optional.ofNullable(commandConnection)
                .map(client -> client.isConnected())
                .orElse(Future.failedFuture(new ServerErrorException(
                        HttpURLConnection.HTTP_UNAVAILABLE, "Command & Control client is not set")));
        return CompositeFuture.all(tenantCheck, registrationCheck, credentialsCheck, messagingCheck, commandCheck).map(ok -> null);
    }

    /**
     * Creates a command consumer for a specific device.
     *
     * @param tenantId The tenant of the command receiver.
     * @param deviceId The device of the command receiver.
     * @param commandConsumer The handler to invoke for each command destined to the device.
     * @param closeHandler Called when the peer detaches the link.
     * @return Result of the receiver creation.
     */
    protected final Future<MessageConsumer> createCommandConsumer(
            final String tenantId,
            final String deviceId,
            final Handler<CommandContext> commandConsumer,
            final Handler<Void> closeHandler) {

        return commandConnection.createCommandConsumer(
                tenantId,
                deviceId,
                commandContext -> {
                    Tags.COMPONENT.set(commandContext.getCurrentSpan(), getTypeName());
                    commandConsumer.handle(commandContext);
                },
                closeHandler);
    }

    /**
     * Closes a command consumer for a device.
     * <p>
     * If no command consumer for the device is open, this method does nothing.
     * 
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     */
    protected final void closeCommandConsumer(final String tenantId, final String deviceId) {

        getCommandConnection().closeCommandConsumer(tenantId, deviceId).otherwise(t -> {
            LOG.warn("cannot close command consumer [tenant-id: {}, device-id: {}]: {}",
                    tenantId, deviceId, t.getMessage());
            return null;
        });
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
        return commandConnection.getCommandResponseSender(tenantId, replyId);
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

        final Future<CommandResponseSender> senderTracker = createCommandResponseSender(tenantId, response.getReplyToId());
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
    protected final Future<MessageSender> getTelemetrySender(final String tenantId) {
        return getHonoMessagingClient().getOrCreateTelemetrySender(tenantId);
    }

    /**
     * Gets a client for sending events for a tenant.
     *
     * @param tenantId The tenant to send the events for.
     * @return The client.
     */
    protected final Future<MessageSender> getEventSender(final String tenantId) {
        return getHonoMessagingClient().getOrCreateEventSender(tenantId);
    }

    /**
     * Gets a client for interacting with the Device Registration service.
     *
     * @param tenantId The tenant that the client is scoped to.
     * @return The client.
     */
    protected final Future<RegistrationClient> getRegistrationClient(final String tenantId) {
        return getRegistrationServiceClient().getOrCreateRegistrationClient(tenantId);
    }

    /**
     * Gets an assertion for a device's registration status.
     * <p>
     * The returned JSON object contains the assertion for the device under property
     * {@link RegistrationConstants#FIELD_ASSERTION}.
     * <p>
     * In addition to the assertion the returned object may include <em>default</em> values for properties to set on
     * messages published by the device under property {@link RegistrationConstants#FIELD_DEFAULTS}.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to get the assertion for.
     * @param authenticatedDevice The device that has authenticated to this protocol adapter.
     *            <p>
     *            If not {@code null} then the authenticated device is compared to the given tenant and device ID. If
     *            they differ in the device identifier, then the authenticated device is considered to be a gateway
     *            acting on behalf of the device.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will fail if the assertion cannot be retrieved. The cause will be a
     *         {@link ServiceInvocationException} containing a corresponding error code.
     *         <p>
     *         Otherwise the future will contain the assertion.
     * @throws NullPointerException if tenant ID or device ID are {@code null}.
     * @deprecated Use {@link #getRegistrationAssertion(String, String, Device, SpanContext)} instead.
     */
    @Deprecated
    protected final Future<JsonObject> getRegistrationAssertion(final String tenantId, final String deviceId,
            final Device authenticatedDevice) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        final Future<String> gatewayId = getGatewayId(tenantId, deviceId, authenticatedDevice);

        return gatewayId
                .compose(gwId -> getRegistrationClient(tenantId))
                .compose(client -> client.assertRegistration(deviceId, gatewayId.result()));
    }

    /**
     * Gets an assertion for a device's registration status.
     * <p>
     * The returned JSON object contains the assertion for the device
     * under property {@link RegistrationConstants#FIELD_ASSERTION}.
     * <p>
     * In addition to the assertion the returned object may include <em>default</em>
     * values for properties to set on messages published by the device under
     * property {@link RegistrationConstants#FIELD_DEFAULTS}.
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
                .compose(client -> client.assertRegistration(deviceId, gatewayId.result(), context));
    }

    private Future<String> getGatewayId(final String tenantId, final String deviceId,
            final Device authenticatedDevice) {

        final Future<String> result = Future.future();
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
        return result;
    }

    /**
     * Gets configuration information for a tenant.
     * <p>
     * The returned JSON object contains information as defined by Hono's
     * <a href="https://www.eclipse.org/hono/api/tenant-api/#response-payload">Tenant API</a>.
     *
     * @param tenantId The tenant to retrieve information for.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will fail if the information cannot be retrieved. The cause will be a
     *         {@link ServiceInvocationException} containing a corresponding error code.
     *         <p>
     *         Otherwise the future will contain the configuration information.
     * @throws NullPointerException if tenant ID is {@code null}.
     * @deprecated Use {@link #getTenantConfiguration(String, SpanContext)} instead.
     */
    @Deprecated
    protected final Future<TenantObject> getTenantConfiguration(final String tenantId) {
        Objects.requireNonNull(tenantId);
        return getTenantClient().compose(client -> client.get(tenantId));
    }

    /**
     * Gets configuration information for a tenant.
     * <p>
     * The returned JSON object contains information as defined by Hono's
     * <a href="https://www.eclipse.org/hono/api/tenant-api/#response-payload">Tenant API</a>.
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
     * This method creates a new {@code Message}, sets its content type and payload as an AMQP <em>Data</em> section
     * and then invokes {@link #addProperties(Message, ResourceIdentifier, boolean, String, JsonObject, Integer)}.
     * 
     * @param target The resource that the message is targeted at.
     * @param regAssertionRequired {@code true} if the downstream peer requires the registration assertion to
     *            be included in the message.
     * @param publishAddress The address that the message has been published to originally by the device. (may be
     *            {@code null}).
     *            <p>
     *            This address will be transport protocol specific, e.g. an HTTP based adapter will probably use URIs
     *            here whereas an MQTT based adapter might use the MQTT message's topic.
     * @param contentType The content type describing the message's payload (may be {@code null}).
     * @param payload The message payload.
     * @param registrationInfo The device's registration information as retrieved by the <em>Device Registration</em>
     *            service's <em>assert Device Registration</em> operation.
     * @param timeUntilDisconnect The number of milliseconds until the device that has published the message
     *            will disconnect from the protocol adapter (may be {@code null}).
     * @return The message.
     * @throws NullPointerException if target or registration info are {@code null}.
     */
    protected final Message newMessage(
            final ResourceIdentifier target,
            final boolean regAssertionRequired,
            final String publishAddress,
            final String contentType,
            final Buffer payload,
            final JsonObject registrationInfo,
            final Integer timeUntilDisconnect) {

        Objects.requireNonNull(target);
        Objects.requireNonNull(registrationInfo);

        final Message msg = ProtonHelper.message();
        MessageHelper.setPayload(msg, contentType, payload);
        msg.setContentType(contentType);

        return addProperties(msg, target, regAssertionRequired, publishAddress, registrationInfo, timeUntilDisconnect);
    }

    /**
     * Sets Hono specific properties on an AMQP 1.0 message.
     * <p>
     * The following properties are set:
     * <ul>
     * <li><em>to</em> will be set to the address consisting of the target's endpoint and tenant</li>
     * <li><em>creation-time</em> will be set to the current number of milliseconds since 1970-01-01T00:00:00Z</li>
     * <li>application property <em>device_id</em> will be set to the target's resourceId property</li>
     * <li>application property <em>orig_address</em> will be set to the given publish address</li>
     * <li>application property <em>ttd</em> will be set to the given time til disconnect</li>
     * <li>additional properties set by {@link #addProperties(Message, JsonObject, boolean)}</li>
     * </ul>
     *
     * @param msg The message to add the properties to.
     * @param target The resource that the message is targeted at.
     * @param regAssertionRequired {@code true} if the downstream peer requires the registration assertion to
     *            be included in the message.
     * @param publishAddress The address that the message has been published to originally by the device. (may be
     *            {@code null}).
     *            <p>
     *            This address will be transport protocol specific, e.g. an HTTP based adapter will probably use URIs
     *            here whereas an MQTT based adapter might use the MQTT message's topic.
     * @param registrationInfo The device's registration information as retrieved by the <em>Device Registration</em>
     *            service's <em>assert Device Registration</em> operation.
     * @param timeUntilDisconnect The number of seconds until the device that has published the message
     *            will disconnect from the protocol adapter (may be {@code null}).
     * @return The message with its properties set.
     * @throws NullPointerException if message, target or registration info are {@code null}.
     */
    protected final Message addProperties(
            final Message msg,
            final ResourceIdentifier target,
            final boolean regAssertionRequired,
            final String publishAddress,
            final JsonObject registrationInfo,
            final Integer timeUntilDisconnect) {

        Objects.requireNonNull(msg);
        Objects.requireNonNull(target);
        Objects.requireNonNull(registrationInfo);

        msg.setAddress(target.getBasePath());
        MessageHelper.addDeviceId(msg, target.getResourceId());
        if (!regAssertionRequired) {
            // this adapter is not connected to Hono Messaging
            // so we need to add the annotations for tenant and
            // device ID
            MessageHelper.annotate(msg, target);
        }
        if (publishAddress != null) {
            MessageHelper.addProperty(msg, MessageHelper.APP_PROPERTY_ORIG_ADDRESS, publishAddress);
        }
        if (timeUntilDisconnect != null) {
            MessageHelper.addTimeUntilDisconnect(msg, timeUntilDisconnect);
        }

        MessageHelper.setCreationTime(msg);
        addProperties(msg, registrationInfo, regAssertionRequired);
        return msg;
    }

    /**
     * Adds message properties based on a device's registration information.
     * <p>
     * This methods simply invokes {@link #addProperties(Message, JsonObject, boolean)} with
     * with {@code true} as the value for the regAssertionRequired parameter.
     *
     * @param message The message to set the properties on.
     * @param registrationInfo The values to set.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected final void addProperties(
            final Message message,
            final JsonObject registrationInfo) {

        addProperties(message, registrationInfo, true);
    }

    /**
     * Adds message properties based on a device's registration information.
     * <p>
     * Sets the following properties on the message:
     * <ul>
     * <li>Adds the registration assertion found in the {@link RegistrationConstants#FIELD_ASSERTION} property of the
     * given registration information (if required by downstream peer).</li>
     * <li>Adds {@linkplain #getTypeName() the adapter's name} to the message in application property
     * {@link MessageHelper#APP_PROPERTY_ORIG_ADAPTER}</li>
     * <li>Augments the message with missing (application) properties corresponding to the
     * {@link RegistrationConstants#FIELD_DEFAULTS} contained in the registration information.</li>
     * <li>Adds JMS vendor properties if configuration property <em>jmsVendorPropertiesEnabled</em> is set to
     * {@code true}.</li>
     * </ul>
     *
     * @param message The message to set the properties on.
     * @param registrationInfo The values to set.
     * @param regAssertionRequired {@code true} if the downstream peer requires the registration assertion to
     *            be included in the message.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected final void addProperties(
            final Message message,
            final JsonObject registrationInfo,
            final boolean regAssertionRequired) {

        if (regAssertionRequired) {
            final String registrationAssertion = registrationInfo.getString(RegistrationConstants.FIELD_ASSERTION);
            MessageHelper.addRegistrationAssertion(message, registrationAssertion);
        }
        MessageHelper.addProperty(message, MessageHelper.APP_PROPERTY_ORIG_ADAPTER, getTypeName());
        if (getConfig().isDefaultsEnabled()) {
            final JsonObject defaults = registrationInfo.getJsonObject(RegistrationConstants.FIELD_DEFAULTS);
            if (defaults != null) {
                addDefaults(message, defaults);
            }
        }
        if (Strings.isNullOrEmpty(message.getContentType())) {
            // set default content type if none has been specified when creating the
            // message nor a default content type is available
            message.setContentType(CONTENT_TYPE_OCTET_STREAM);
        }
        if (getConfig().isJmsVendorPropsEnabled()) {
            MessageHelper.addJmsVendorProperties(message);
        }
    }

    private void addDefaults(final Message message, final JsonObject defaults) {

        defaults.forEach(prop -> {

            switch (prop.getKey()) {
            case MessageHelper.SYS_PROPERTY_CONTENT_TYPE:
                if (Strings.isNullOrEmpty(message.getContentType()) && String.class.isInstance(prop.getValue())) {
                    // set to default type registered for device or fall back to default content type
                    message.setContentType((String) prop.getValue());
                }
                break;
            case MessageHelper.SYS_PROPERTY_CONTENT_ENCODING:
                if (Strings.isNullOrEmpty(message.getContentEncoding()) && String.class.isInstance(prop.getValue())) {
                    message.setContentEncoding((String) prop.getValue());
                }
                break;
            case MessageHelper.SYS_PROPERTY_ABSOLUTE_EXPIRY_TIME:
            case MessageHelper.SYS_PROPERTY_CORRELATION_ID:
            case MessageHelper.SYS_PROPERTY_CREATION_TIME:
            case MessageHelper.SYS_PROPERTY_GROUP_ID:
            case MessageHelper.SYS_PROPERTY_GROUP_SEQUENCE:
            case MessageHelper.SYS_PROPERTY_MESSAGE_ID:
            case MessageHelper.SYS_PROPERTY_REPLY_TO:
            case MessageHelper.SYS_PROPERTY_REPLY_TO_GROUP_ID:
            case MessageHelper.SYS_PROPERTY_SUBJECT:
            case MessageHelper.SYS_PROPERTY_TO:
            case MessageHelper.SYS_PROPERTY_USER_ID:
                // these standard properties cannot be set using defaults
                LOG.debug("ignoring default property [{}] registered for device", prop.getKey());
                break;
            default:
                // add all other defaults as application properties
                MessageHelper.addProperty(message, prop.getKey(), prop.getValue());
            }
        });
    }

    /**
     * Registers a check that succeeds if this component is connected to the services it depends on.
     * 
     * @see #isConnected()
     */
    @Override
    public void registerReadinessChecks(final HealthCheckHandler handler) {
        handler.register("connection-to-services", status -> {
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
     * Register a liveness check procedure which succeeds if
     * the vert.x event loop of this protocol adapter is not blocked.
     *
     * @param handler The health check handler to register the checks with.
     * @see #registerEventLoopBlockedCheck(HealthCheckHandler)
     */
    @Override
    public void registerLivenessChecks(final HealthCheckHandler handler) {
        registerEventLoopBlockedCheck(handler);
    }

    /**
     * Trigger the creation of a <em>connected</em> event.
     * 
     * @param remoteId The remote ID.
     * @param authenticatedDevice The (optional) authenticated device.
     * @return A failed future if an event producer is set but the event could not be published. Otherwise, a succeeded
     *         event.
     * @see ConnectionEventProducer
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
     * Trigger the creation of a <em>disconnected</em> event.
     *
     * @param remoteId The remote ID.
     * @param authenticatedDevice The (optional) authenticated device.
     * @return A failed future if an event producer is set but the event could not be published. Otherwise, a succeeded
     *         event.
     * @see ConnectionEventProducer
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
     * @return A future indicating the outcome of the operation. The future will be
     *         succeeded if the TTD event has been sent downstream successfully.
     *         Otherwise, it will be failed with a {@link ServiceInvocationException}.
     * @throws NullPointerException if any of tenant or device ID are {@code null}.
     * @deprecated Use {@link #sendConnectedTtdEvent(String, String, Device, SpanContext)} instead.
     */
    @Deprecated
    protected final Future<ProtonDelivery> sendConnectedTtdEvent(
            final String tenant,
            final String deviceId,
            final Device authenticatedDevice) {

        return sendConnectedTtdEvent(tenant, deviceId, authenticatedDevice, null);
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

        return sendTtdEvent(tenant, deviceId, authenticatedDevice, -1, context);
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
     * @return A future indicating the outcome of the operation. The future will be
     *         succeeded if the TTD event has been sent downstream successfully.
     *         Otherwise, it will be failed with a {@link ServiceInvocationException}.
     * @throws NullPointerException if any of tenant or device ID are {@code null}.
     * @deprecated Use {@link #sendDisconnectedTtdEvent(String, String, Device, SpanContext)} instead.
     */
    @Deprecated
    protected final Future<ProtonDelivery> sendDisconnectedTtdEvent(
            final String tenant,
            final String deviceId,
            final Device authenticatedDevice) {

        return sendDisconnectedTtdEvent(tenant, deviceId, authenticatedDevice, null);
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
     * @return A future indicating the outcome of the operation. The future will be
     *         succeeded if the TTD event has been sent downstream successfully.
     *         Otherwise, it will be failed with a {@link ServiceInvocationException}.
     * @throws NullPointerException if any of tenant, device ID or TTD are {@code null}.
     * @deprecated Use {@link #sendTtdEvent(String, String, Device, Integer, SpanContext)} instead.
     */
    @Deprecated
    protected final Future<ProtonDelivery> sendTtdEvent(
            final String tenant,
            final String deviceId,
            final Device authenticatedDevice,
            final Integer ttd) {

        return sendTtdEvent(tenant, deviceId, authenticatedDevice, ttd, null);
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
        final Future<MessageSender> senderTracker = getEventSender(tenant);

        return CompositeFuture.all(tokenTracker, tenantConfigTracker, senderTracker).compose(ok -> {
            if (tenantConfigTracker.result().isAdapterEnabled(getTypeName())) {
                final MessageSender sender = senderTracker.result();
                final Message msg = newMessage(
                        ResourceIdentifier.from(EventConstants.EVENT_ENDPOINT, tenant, deviceId),
                        senderTracker.result().isRegistrationAssertionRequired(),
                        EventConstants.EVENT_ENDPOINT,
                        EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION,
                        null,
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
     * This method may be set as the close handler of the {@link CommandConsumer}.
     * <p>
     * The implementation only logs that the link was closed and does not try to reopen it. Any other functionality must be
     * implemented by overwriting the method in a subclass.
     *
     * @param tenant The tenant of the device for that a command may be received.
     * @param deviceId The id of the device for that a command may be received.
     * @param commandMessageConsumer The Handler that will be called for each command to the device.
     */
    protected void onCloseCommandConsumer(
            final String tenant,
            final String deviceId,
            final BiConsumer<ProtonDelivery, Message> commandMessageConsumer) {

        LOG.debug("command consumer closed [tenantId: {}, deviceId: {}] - no command will be received for this device anymore",
                tenant, deviceId);
    }

    /**
     * Registers a health check procedure which tries to run an action on the protocol adapter context.
     * If the protocol adapter vert.x event loop is blocked, the health check procedure will not complete
     * with OK status within the defined timeout.
     *
     * @param handler The health check handler to register the checks with.
     */
    protected void registerEventLoopBlockedCheck(final HealthCheckHandler handler) {
        handler.register("event-loop-blocked-check", getConfig().getEventLoopBlockedCheckTimeout(), procedure -> {
            final Context currentContext = Vertx.currentContext();

            if (currentContext != context) {
                context.runOnContext(action -> {
                    procedure.complete(Status.OK());
                });
            } else {
                LOG.info("Protocol Adapter - HealthCheck Server context match. Assume protocol adapter is alive.");
                procedure.complete(Status.OK());
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

}
