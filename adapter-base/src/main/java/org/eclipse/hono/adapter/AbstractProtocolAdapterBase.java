/*******************************************************************************
 * Copyright (c) 2016, 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.adapter.limiting.ConnectionLimitManager;
import org.eclipse.hono.adapter.monitoring.ConnectionEventProducer;
import org.eclipse.hono.adapter.resourcelimits.NoopResourceLimitChecks;
import org.eclipse.hono.adapter.resourcelimits.ResourceLimitChecks;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.client.command.CommandResponse;
import org.eclipse.hono.client.command.CommandResponseSender;
import org.eclipse.hono.client.command.CommandRouterClient;
import org.eclipse.hono.client.command.ProtocolAdapterCommandConsumerFactory;
import org.eclipse.hono.client.registry.CredentialsClient;
import org.eclipse.hono.client.registry.DeviceDisabledOrNotRegisteredException;
import org.eclipse.hono.client.registry.DeviceRegistrationClient;
import org.eclipse.hono.client.registry.GatewayDisabledOrNotRegisteredException;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.client.registry.TenantDisabledOrNotRegisteredException;
import org.eclipse.hono.client.telemetry.EventSender;
import org.eclipse.hono.client.telemetry.TelemetrySender;
import org.eclipse.hono.client.util.ServiceClient;
import org.eclipse.hono.service.AbstractServiceBase;
import org.eclipse.hono.service.auth.ValidityBasedTrustOptions;
import org.eclipse.hono.service.metric.MetricsTags.ConnectionAttemptOutcome;
import org.eclipse.hono.service.util.ServiceBaseUtils;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.Strings;
import org.eclipse.hono.util.TenantObject;

import io.micrometer.core.instrument.Timer.Sample;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.opentracing.SpanContext;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.TrustOptions;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;

/**
 * A base class for implementing protocol adapters.
 * <p>
 * Provides connections to device registration and telemetry and event service endpoints.
 *
 * @param <T> The type of configuration properties used by this service.
 */
public abstract class AbstractProtocolAdapterBase<T extends ProtocolAdapterProperties> extends AbstractServiceBase<T> implements ProtocolAdapter {

    /**
     * The <em>application/octet-stream</em> content type.
     */
    protected static final String CONTENT_TYPE_OCTET_STREAM = MessageHelper.CONTENT_TYPE_OCTET_STREAM;
    /**
     * The key used for storing a Micrometer {@code Sample} in an
     * execution context.
     */
    protected static final String KEY_MICROMETER_SAMPLE = "micrometer.sample";

    private ProtocolAdapterCommandConsumerFactory commandConsumerFactory;
    private CommandRouterClient commandRouterClient;
    private ConnectionLimitManager connectionLimitManager;
    private ConnectionEventProducer connectionEventProducer;
    private CredentialsClient credentialsClient;
    private DeviceRegistrationClient registrationClient;
    private ResourceLimitChecks resourceLimitChecks = new NoopResourceLimitChecks();
    private TenantClient tenantClient;
    private MessagingClientProviders messagingClientProviders;

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
    @Override
    public final TenantClient getTenantClient() {
        return tenantClient;
    }

    /**
     * Sets the client to use for accessing the Command Router service.
     *
     * @param client The client.
     * @throws NullPointerException if the client is {@code null}.
     */
    public final void setCommandRouterClient(final CommandRouterClient client) {
        this.commandRouterClient = Objects.requireNonNull(client);
        log.info("using Command Router client [{}]", client.getClass().getName());
    }

    /**
     * Sets the client providers to use for messaging.
     *
     * @param messagingClientProviders The messaging client providers.
     * @throws NullPointerException if messagingClientProviders is {@code null}.
     */
    public void setMessagingClientProviders(final MessagingClientProviders messagingClientProviders) {
        Objects.requireNonNull(messagingClientProviders);
        this.messagingClientProviders = messagingClientProviders;
    }

    @Override
    public final TelemetrySender getTelemetrySender(final TenantObject tenant) {
        return messagingClientProviders.getTelemetrySender(tenant);
    }

    @Override
    public final EventSender getEventSender(final TenantObject tenant) {
        return messagingClientProviders.getEventSender(tenant);
    }

    @Override
    public final CommandResponseSender getCommandResponseSender(final MessagingType messagingType, final TenantObject tenant) {
        return messagingClientProviders.getCommandResponseSender(messagingType, tenant);
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

    @Override
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
     * Sets the factory to use for creating clients to receive commands.
     *
     * @param factory The factory.
     * @throws NullPointerException if factory is {@code null}.
     */
    public final void setCommandConsumerFactory(final ProtocolAdapterCommandConsumerFactory factory) {
        this.commandConsumerFactory = Objects.requireNonNull(factory);
    }

    @Override
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
        } else if (messagingClientProviders == null) {
            result.fail(new IllegalStateException("Downstream messaging client providers must be set"));
        } else if (registrationClient == null) {
            result.fail(new IllegalStateException("Device Registration client must be set"));
        } else if (credentialsClient == null) {
            result.fail(new IllegalStateException("Credentials client must be set"));
        } else if (commandConsumerFactory == null) {
            result.fail(new IllegalStateException("Command & Control consumer factory must be set"));
        } else if (commandRouterClient == null) {
            result.fail(new IllegalStateException("Command Router client must be set"));
        } else {

            log.info("using ResourceLimitChecks [{}]", resourceLimitChecks.getClass().getName());

            messagingClientProviders.start();
            tenantClient.start();
            registrationClient.start();
            credentialsClient.start();
            commandConsumerFactory.start();
            commandRouterClient.start();
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

    private Future<Void> closeServiceClients() {

        final List<Future<Void>> results = new ArrayList<>();
        results.add(tenantClient.stop());
        results.add(registrationClient.stop());
        results.add(credentialsClient.stop());
        results.add(commandConsumerFactory.stop());
        results.add(commandRouterClient.stop());
        results.add(messagingClientProviders.stop());
        return Future.all(results).mapEmpty();
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

    @Override
    public final Future<TenantObject> isAdapterEnabled(final TenantObject tenantConfig) {

        Objects.requireNonNull(tenantConfig);

        if (tenantConfig.isAdapterEnabled(getTypeName())) {
            log.debug("protocol adapter [{}] is enabled for tenant [{}]",
                    getTypeName(), tenantConfig.getTenantId());
            return Future.succeededFuture(tenantConfig);
        } else if (!tenantConfig.isEnabled()) {
            log.debug("tenant [{}] is disabled", tenantConfig.getTenantId());
            return Future.failedFuture(
                    new TenantDisabledOrNotRegisteredException(tenantConfig.getTenantId(), HttpURLConnection.HTTP_FORBIDDEN));
        } else {
            log.debug("protocol adapter [{}] is disabled for tenant [{}]",
                    getTypeName(), tenantConfig.getTenantId());
            return Future.failedFuture(
                    new AdapterDisabledException(tenantConfig.getTenantId()));
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
                        return Future.failedFuture(new TenantConnectionsExceededException(
                                tenantConfig.getTenantId(),
                                null));
                    } else {
                        return Future.succeededFuture();
                    }
                });
        final Future<Void> messageLimitCheckResult = checkMessageLimit(tenantConfig, 1, spanContext)
                .recover(t -> {
                    if (ServiceInvocationException.extractStatusCode(t) ==  HttpResponseStatus.TOO_MANY_REQUESTS.code()) {
                        return Future.failedFuture(new DataVolumeExceededException(
                                tenantConfig.getTenantId(),
                                t.getCause()));
                    } else {
                        return Future.failedFuture(t);
                    }
                });

        return Future.all(
                connectionLimitCheckResult,
                checkConnectionDurationLimit(tenantConfig, spanContext),
                messageLimitCheckResult).mapEmpty();
    }

    @Override
    public Future<Void> checkMessageLimit(final TenantObject tenantConfig, final long payloadSize,
            final SpanContext spanContext) {

        Objects.requireNonNull(tenantConfig);

        return resourceLimitChecks
                .isMessageLimitReached(tenantConfig,
                        ServiceBaseUtils.calculatePayloadSize(payloadSize, tenantConfig),
                        spanContext)
                .recover(t -> Future.succeededFuture(Boolean.FALSE))
                .compose(isExceeded -> {
                    if (isExceeded) {
                        return Future.failedFuture(new ClientErrorException(
                                tenantConfig.getTenantId(),
                                HttpResponseStatus.TOO_MANY_REQUESTS.code(),
                                "tenant's accumulated message data volume exceeds configured maximum value"));
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
                        return Future.failedFuture(new ConnectionDurationExceededException(
                                tenantConfig.getTenantId(),
                                null));
                    } else {
                        return Future.succeededFuture();
                    }
                });
    }

    /**
     * Validates a message's target address for consistency with Hono's addressing rules.
     * <p>
     * It is ensured that the returned address contains tenant and device identifiers, adopting them from the
     * given device information if not already set.
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
     * Forwards a response message that has been sent by a device in reply to a
     * command to the sender of the command.
     * <p>
     * This method opens a new link for sending the response, tries to send the
     * response message and then closes the link again.
     *
     * @param tenant The tenant to send the response for.
     * @param device The registration assertion for the device that the data originates from.
     * @param response The response message.
     * @param context The currently active OpenTracing span. An implementation
     *         should use this as the parent for any span it creates for tracing
     *         the execution of this operation.
     * @return A future indicating the outcome of the attempt to send
     *         the message. The link will be closed in any case.
     * @throws NullPointerException if any of the parameters other than context are {@code null}.
     */
    protected final Future<Void> sendCommandResponse(
            final TenantObject tenant,
            final RegistrationAssertion device,
            final CommandResponse response,
            final SpanContext context) {

        Objects.requireNonNull(response);
        Objects.requireNonNull(tenant);
        Objects.requireNonNull(device);

        return messagingClientProviders.getCommandResponseSender(response.getMessagingType(), tenant)
                .sendCommandResponse(tenant, device, response, context);
    }

    @Override
    public final Future<RegistrationAssertion> getRegistrationAssertion(
            final String tenantId,
            final String deviceId,
            final Device authenticatedDevice,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        final Future<String> gatewayId = getGatewayId(tenantId, deviceId, authenticatedDevice);

        return gatewayId
                .compose(gwId -> getRegistrationClient().assertRegistration(tenantId, deviceId, gwId, context))
                // the updateLastGateway invocation shouldn't delay or possibly fail the surrounding operation
                // so don't wait for the outcome here
                .onSuccess(assertion -> updateLastGateway(assertion, tenantId, deviceId, authenticatedDevice, context)
                            .onFailure(t -> log.warn("failed to update last gateway [tenantId: {}, deviceId: {}]",
                                    tenantId, deviceId, t)))
                .recover(error -> {
                    final int errorCode = ServiceInvocationException.extractStatusCode(error);
                    if (errorCode == HttpURLConnection.HTTP_NOT_FOUND) {
                        return Future.failedFuture(new DeviceDisabledOrNotRegisteredException(tenantId, errorCode));
                    } else if (errorCode == HttpURLConnection.HTTP_FORBIDDEN) {
                        return Future.failedFuture(new GatewayDisabledOrNotRegisteredException(tenantId, errorCode));
                    } else {
                        return Future.failedFuture(error);
                    }
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
    protected final Future<RegistrationAssertion> updateLastGateway(
            final RegistrationAssertion registrationAssertion,
            final String tenantId,
            final String deviceId,
            final Device authenticatedDevice,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        if (!isDeviceWithMultipleViaGateways(registrationAssertion)) {
            return Future.succeededFuture(registrationAssertion);
        }

        return getGatewayId(tenantId, deviceId, authenticatedDevice)
            .compose(gwId -> commandRouterClient.setLastKnownGatewayForDevice(
                        tenantId,
                        deviceId,
                        Optional.ofNullable(gwId).orElse(deviceId),
                        context))
            .map(registrationAssertion);
    }

    private boolean isDeviceWithMultipleViaGateways(final RegistrationAssertion registrationAssertion) {
        return registrationAssertion.getAuthorizedGateways().size() > 1;
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

        return getTenantClient().get(tenantId, context)
                .recover(error -> Future.failedFuture(
                        ServiceInvocationException.extractStatusCode(error) == HttpURLConnection.HTTP_NOT_FOUND
                                ? new TenantDisabledOrNotRegisteredException(tenantId, HttpURLConnection.HTTP_NOT_FOUND)
                                : error));
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned properties are the properties returned by
     * {@link TelemetryExecutionContext#getDownstreamMessageProperties()} plus
     * this {@linkplain #getTypeName() adapter's type name}.
     *
     * @param context The execution context for processing the downstream message.
     * @return The properties.
     */
    @Override
    public final Map<String, Object> getDownstreamMessageProperties(final TelemetryExecutionContext context) {
        final Map<String, Object> props = Objects.requireNonNull(context).getDownstreamMessageProperties();
        props.put(MessageHelper.APP_PROPERTY_ORIG_ADAPTER, getTypeName());
        return props;
    }

    /**
     * Registers checks which verify that this component is connected to the services it depends on.
     */
    @Override
    public void registerReadinessChecks(final HealthCheckHandler handler) {

        if (commandConsumerFactory instanceof ServiceClient client) {
            client.registerReadinessChecks(handler);
        }
        if (tenantClient instanceof ServiceClient client) {
            client.registerReadinessChecks(handler);
        }
        if (registrationClient instanceof ServiceClient client) {
            client.registerReadinessChecks(handler);
        }
        if (credentialsClient instanceof ServiceClient client) {
            client.registerReadinessChecks(handler);
        }
        if (commandRouterClient instanceof ServiceClient client) {
            client.registerReadinessChecks(handler);
        }
        messagingClientProviders.registerReadinessChecks(handler);
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

        if (commandConsumerFactory instanceof ServiceClient client) {
            client.registerLivenessChecks(handler);
        }
        if (tenantClient instanceof ServiceClient client) {
            client.registerLivenessChecks(handler);
        }
        if (registrationClient instanceof ServiceClient client) {
            client.registerLivenessChecks(handler);
        }
        if (credentialsClient instanceof ServiceClient client) {
            client.registerLivenessChecks(handler);
        }
        if (commandRouterClient instanceof ServiceClient client) {
            client.registerLivenessChecks(handler);
        }
        messagingClientProviders.registerLivenessChecks(handler);
    }

    /**
     * Triggers the creation of a <em>connected</em> event.
     *
     * @param remoteId The remote ID.
     * @param authenticatedDevice The (optional) authenticated device.
     * @param context The currently active OpenTracing span context or {@code null}.
     * @return A failed future if an event producer is set but the event could not be published. Otherwise, a succeeded
     *         event.
     * @see ConnectionEventProducer#connected(ConnectionEventProducer.Context, String, String, Device, io.vertx.core.json.JsonObject, SpanContext)
     */
    protected Future<Void> sendConnectedEvent(final String remoteId, final Device authenticatedDevice, final SpanContext context) {
        if (this.connectionEventProducer != null) {
            return Optional.ofNullable(authenticatedDevice)
                    .map(device -> getTenantClient().get(device.getTenantId(), context)
                            .map(this::getEventSender))
                    .orElseGet(() -> Future.succeededFuture(null))
                    .compose(es -> this.connectionEventProducer.connected(
                            new ConnectionEventProducer.Context() {
                                @Override
                                public EventSender getMessageSenderClient() {
                                    return es;
                                }

                                @Override
                                public TenantClient getTenantClient() {
                                    return AbstractProtocolAdapterBase.this.getTenantClient();
                                }
                            },
                            remoteId, 
                            getTypeName(), 
                            authenticatedDevice, 
                            null,
                            context));
        } else {
            return Future.succeededFuture();
        }
    }

    /**
     * Triggers the creation of a <em>disconnected</em> event.
     *
     * @param remoteId The remote ID.
     * @param authenticatedDevice The (optional) authenticated device.
     * @param context The currently active OpenTracing span context or {@code null}.
     * @return A failed future if an event producer is set but the event could not be published. Otherwise, a succeeded
     *         event.
     * @see ConnectionEventProducer#disconnected(ConnectionEventProducer.Context, String, String, Device, io.vertx.core.json.JsonObject, SpanContext)
     */
    protected Future<Void> sendDisconnectedEvent(final String remoteId, final Device authenticatedDevice, final SpanContext context) {
        if (this.connectionEventProducer != null) {
            return Optional.ofNullable(authenticatedDevice)
                    .map(device -> getTenantClient().get(device.getTenantId(), context)
                            .map(this::getEventSender))
                    .orElseGet(() -> Future.succeededFuture(null))
                    .compose(es -> this.connectionEventProducer.disconnected(
                            new ConnectionEventProducer.Context() {
                                @Override
                                public EventSender getMessageSenderClient() {
                                    return es;
                                }

                                @Override
                                public TenantClient getTenantClient() {
                                    return AbstractProtocolAdapterBase.this.getTenantClient();
                                }
                            },
                            remoteId,
                            getTypeName(),
                            authenticatedDevice,
                            null,
                            context));
        } else {
            return Future.succeededFuture();
        }
    }

    @Override
    public final Future<Void> sendTtdEvent(
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

        return Future.all(tokenTracker, tenantConfigTracker).compose(ok -> {
            if (tenantConfigTracker.result().isAdapterEnabled(getTypeName())) {
                final Map<String, Object> props = new HashMap<>();
                props.put(MessageHelper.APP_PROPERTY_ORIG_ADAPTER, getTypeName());
                props.put(MessageHelper.APP_PROPERTY_QOS, QoS.AT_LEAST_ONCE.ordinal());
                props.put(CommandConstants.MSG_PROPERTY_DEVICE_TTD, ttd);
                return getEventSender(tenantConfigTracker.result()).sendEvent(
                        tenantConfigTracker.result(),
                        tokenTracker.result(),
                        EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION,
                        null,
                        props,
                        context)
                    .onSuccess(s -> log.debug(
                            "successfully sent TTD notification [tenant: {}, device-id: {}, TTD: {}]",
                            tenant, deviceId, ttd))
                    .onFailure(t -> log.debug(
                            "failed to send TTD notification [tenant: {}, device-id: {}, TTD: {}]",
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
     * @return {@code true} if the payload is empty and the content type is not empty, or else
     *         if the content type is not {@link EventConstants#CONTENT_TYPE_EMPTY_NOTIFICATION}.
     */
    public static boolean isPayloadOfIndicatedType(final Buffer payload, final String contentType) {
        if (payload == null || payload.length() == 0) {
            return !Strings.isNullOrEmpty(contentType);
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
                        context.runOnContext(action -> procedure.tryComplete(Status.OK()));
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
     * Maps an error that occurred during a device's connection attempt to a
     * corresponding outcome.
     *
     * @param e The error that has occurred.
     * @return The outcome.
     */
    public static ConnectionAttemptOutcome getOutcome(final Throwable e) {

        if (e instanceof AuthorizationException) {
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
        } else if (e instanceof AdapterDisabledException) {
            return ConnectionAttemptOutcome.ADAPTER_DISABLED;
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

    /**
     * Checks if the given error is terminal or not.
     * <p>
     * The errors that are classified as terminal are listed below.
     * <ul>
     * <li>The adapter is disabled for the tenant that the client belongs to.</li>
     * <li>The authenticated device or gateway is disabled or not registered.</li>
     * <li>The tenant is disabled or does not exist.</li>
     * <li>The authenticated device is not authorized anymore (e.g. device credentials expired).</li>
     * </ul>
     *
     * @param error The error to be checked.
     * @param deviceId The device identifier or {@code null}.
     * @param authenticatedDevice The authenticated device or {@code null}.
     * @param spanContext The OpenTracing context to use for tracking the operation.
     * @return A future indicating the outcome of the check.
     * @throws NullPointerException if error is {@code null}.
     */
    protected Future<Boolean> isTerminalError(final Throwable error, final String deviceId,
            final Device authenticatedDevice, final SpanContext spanContext) {

        Objects.requireNonNull(error);

        if (authenticatedDevice == null) {
            // If the device is unauthenticated then the error is classified as non-terminal.
            return Future.succeededFuture(false);
        } else {
            // if the device is not registered or disabled
            if (error instanceof DeviceDisabledOrNotRegisteredException) {
                // and if the device is connected via a gateway
                if (deviceId != null && !authenticatedDevice.getDeviceId().equals(deviceId)) {
                    return getRegistrationAssertion(authenticatedDevice.getTenantId(),
                            authenticatedDevice.getDeviceId(), null, spanContext)
                                    .map(ok -> false)
                                    // and if the gateway is not registered then it is a terminal error
                                    .recover(e -> Future.succeededFuture(e instanceof DeviceDisabledOrNotRegisteredException));
                } else {
                    return Future.succeededFuture(true);
                }
            }

            return Future.succeededFuture(error instanceof AdapterDisabledException
                    || error instanceof GatewayDisabledOrNotRegisteredException
                    || error instanceof TenantDisabledOrNotRegisteredException
                    || error instanceof AuthorizationException);
        }
    }
}
