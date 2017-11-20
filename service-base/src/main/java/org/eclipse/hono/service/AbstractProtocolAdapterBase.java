/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.service;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.service.auth.device.HonoClientBasedAuthProvider;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.eclipse.hono.util.ResourceIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;

/**
 * A base class for implementing protocol adapters.
 * <p>
 * Provides connections to device registration and telemetry and event service endpoints.
 * 
 * @param <T> The type of configuration properties used by this service.
 */
public abstract class AbstractProtocolAdapterBase<T extends ServiceConfigProperties> extends AbstractServiceBase<T> {

    /**
     * The name of the AMQP 1.0 application property that is used to convey the
     * address that a message has been originally published to by a device.
     */
    public static final String PROPERTY_HONO_ORIG_ADDRESS = "hono-orig-address";
    /**
     * The <em>telemetry</em> endpoint name.
     */
    public static final String TELEMETRY_ENDPOINT = "telemetry";
    /**
     * The <em>event</em> endpoint name.
     */
    public static final String EVENT_ENDPOINT = "event";
    /**
     * The <em>application/octet-stream</em> content type.
     */
    protected static final String CONTENT_TYPE_OCTET_STREAM = "application/octet-stream";

    private HonoClient messaging;
    private HonoClient registration;
    private HonoClientBasedAuthProvider credentialsAuthProvider;

    /**
     * Sets the configuration by means of Spring dependency injection.
     * <p>
     * Most protocol adapters will support a single transport protocol to communicate with
     * devices only. For those adapters there will only be a single bean instance available
     * in the application context of type <em>T</em>.
     */
    @Autowired
    @Override
    public void setConfig(final T configuration) {
        setSpecificConfig(configuration);
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
        this.messaging = Objects.requireNonNull(honoClient);
    }

    /**
     * Gets the client used for connecting to the Hono Messaging component.
     * 
     * @return The client.
     */
    public final HonoClient getHonoMessagingClient() {
        return messaging;
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
        this.registration = Objects.requireNonNull(registrationServiceClient);
    }

    /**
     * Gets the client used for connecting to the Device Registration service.
     * 
     * @return The client.
     */
    public final HonoClient getRegistrationServiceClient() {
        return registration;
    }

    /**
     * Sets the authentication provider to use for verifying device credentials.
     *
     * @param credentialsAuthProvider The provider.
     * @throws NullPointerException if the provider is {@code null}.
     */
    @Autowired(required = false)
    public final void setCredentialsAuthProvider(final HonoClientBasedAuthProvider credentialsAuthProvider) {
        this.credentialsAuthProvider = Objects.requireNonNull(credentialsAuthProvider);
    }

    /**
     * Gets the authentication provider used for verifying device credentials.
     *
     * @return The provider.
     */
    public final HonoClientBasedAuthProvider getCredentialsAuthProvider() {
        return credentialsAuthProvider;
    }

    @Override
    public final Future<Void> startInternal() {
        Future<Void> result = Future.future();
        if (messaging == null) {
            result.fail("Hono Messaging client must be set");
        } else if (registration == null) {
            result.fail("Device Registration client must be set");
        } else if (credentialsAuthProvider == null) {
            result.fail(new IllegalStateException("Credentials Authentication Provider must be set"));
        } else {
            doStart(result);
        }
        return result;
    }

    /**
     * Subclasses should override this method to perform any work required on start-up of this protocol adapter.
     * <p>
     * This method is invoked by {@link #start()} as part of the startup process.
     *
     * @param startFuture The future to complete once start up is complete.
     */
    protected void doStart(final Future<Void> startFuture) {
        // should be overridden by subclasses
        startFuture.complete();
    }

    @Override
    public final Future<Void> stopInternal() {
        Future<Void> result = Future.future();
        doStop(result);
        return result;
    }

    /**
     * Subclasses should override this method to perform any work required before shutting down this protocol adapter.
     * <p>
     * This method is invoked by {@link #stop()} as part of the shutdown process.
     *
     * @param stopFuture The future to complete once shutdown is complete.
     */
    protected void doStop(final Future<Void> stopFuture) {
        // to be overridden by subclasses
        stopFuture.complete();
    }

    /**
     * Connects to the Hono Messaging component using the configured client.
     * 
     * @param connectHandler The handler to invoke with the outcome of the connection attempt.
     *                       If {@code null} and the connection attempt failed, this method
     *                       tries to re-connect until a connection is established.
     */
    protected final void connectToMessaging(final Handler<AsyncResult<HonoClient>> connectHandler) {

        if (messaging == null) {
            if (connectHandler != null) {
                connectHandler.handle(Future.failedFuture("Hono Messaging client not set"));
            }
        } else if (messaging.isConnected()) {
            LOG.info("already connected to Hono Messaging");
            if (connectHandler != null) {
                connectHandler.handle(Future.succeededFuture(messaging));
            }
        } else {
            messaging.connect(createClientOptions(), connectAttempt -> {
                if (connectHandler != null) {
                    connectHandler.handle(connectAttempt);
                } else {
                    LOG.info("connected to Hono Messaging");
                }
            }, this::onDisconnectMessaging
            );
        }
    }

    /**
     * Attempts a reconnect for the Hono Messaging client after {@link Constants#DEFAULT_RECONNECT_INTERVAL_MILLIS} milliseconds.
     *
     * @param con The connection that was disonnected.
     */
    private void onDisconnectMessaging(final ProtonConnection con) {

        vertx.setTimer(Constants.DEFAULT_RECONNECT_INTERVAL_MILLIS, reconnect -> {
            LOG.info("attempting to reconnect to Hono Messaging");
            messaging.connect(createClientOptions(), connectAttempt -> {
                if (connectAttempt.succeeded()) {
                    LOG.info("reconnected to Hono Messaging");
                } else {
                    LOG.debug("cannot reconnect to Hono Messaging: {}", connectAttempt.cause().getMessage());
                }
            }, this::onDisconnectMessaging);
        });
    }

    /**
     * Connects to the Device Registration service using the configured client.
     * 
     * @param connectHandler The handler to invoke with the outcome of the connection attempt.
     *                       If {@code null} and the connection attempt failed, this method
     *                       tries to re-connect until a connection is established.
     */
    protected final void connectToDeviceRegistration(final Handler<AsyncResult<HonoClient>> connectHandler) {

        if (registration == null) {
            if (connectHandler != null) {
                connectHandler.handle(Future.failedFuture("Device Registration client not set"));
            }
        } else if (registration.isConnected()) {
            LOG.info("already connected to Device Registration service");
            if (connectHandler != null) {
                connectHandler.handle(Future.succeededFuture(registration));
            }
        } else {
            registration.connect(createClientOptions(), connectAttempt -> {
                if (connectHandler != null) {
                    connectHandler.handle(connectAttempt);
                } else {
                    LOG.info("connected to Device Registration service");
                }
            }, this::onDisconnectDeviceRegistry);
        }
    }

    /**
     * Attempts a reconnect for the Hono Device Registration client after {@link Constants#DEFAULT_RECONNECT_INTERVAL_MILLIS} milliseconds.
     *
     * @param con The connection that was disonnected.
     */
    private void onDisconnectDeviceRegistry(final ProtonConnection con) {

        vertx.setTimer(Constants.DEFAULT_RECONNECT_INTERVAL_MILLIS, reconnect -> {
            LOG.info("attempting to reconnect to Device Registration service");
            registration.connect(createClientOptions(), connectAttempt -> {
                if (connectAttempt.succeeded()) {
                    LOG.info("reconnected to Device Registration service");
                } else {
                    LOG.debug("cannot reconnect to Device Registration service: {}", connectAttempt.cause().getMessage());
                }
            }, this::onDisconnectDeviceRegistry);
        });
    }

    private ProtonClientOptions createClientOptions() {
        return new ProtonClientOptions()
                .setConnectTimeout(200)
                .setReconnectAttempts(1)
                .setReconnectInterval(Constants.DEFAULT_RECONNECT_INTERVAL_MILLIS);
    }

    /**
     * Checks if this adapter is connected to <em>Hono Messaging</em>, <em>Device Registration</em>
     * and the <em>Credentials</em> service.
     * 
     * @return {@code true} if this adapter is connected.
     */
    protected final boolean isConnected() {
        return messaging != null && messaging.isConnected() &&
                registration != null && registration.isConnected();
    }

    /**
     * Closes the connections to the Hono Messaging component and the Device Registration service.
     * 
     * @param closeHandler The handler to notify about the result.
     */
    protected final void closeClients(final Handler<AsyncResult<Void>> closeHandler) {

        Future<Void> messagingTracker = Future.future();
        Future<Void> registrationTracker = Future.future();

        if (messaging == null) {
            messagingTracker.complete();
        } else {
            messaging.shutdown(messagingTracker.completer());
        }

        if (registration == null) {
            registrationTracker.complete();
        } else {
            registration.shutdown(registrationTracker.completer());
        }

        CompositeFuture.all(messagingTracker, registrationTracker).setHandler(s -> {
            if (closeHandler != null) {
                if (s.succeeded()) {
                    closeHandler.handle(Future.succeededFuture());
                } else {
                    closeHandler.handle(Future.failedFuture(s.cause()));
                }
            }
        });
    }

    /**
     * Gets a client for sending telemetry data for a tenant.
     * 
     * @param tenantId The tenant to send the telemetry data for.
     * @return The client.
     */
    protected final Future<MessageSender> getTelemetrySender(final String tenantId) {
        Future<MessageSender> result = Future.future();
        messaging.getOrCreateTelemetrySender(tenantId, result.completer());
        return result;
    }

    /**
     * Gets a client for sending events for a tenant.
     * 
     * @param tenantId The tenant to send the events for.
     * @return The client.
     */
    protected final Future<MessageSender> getEventSender(final String tenantId) {
        Future<MessageSender> result = Future.future();
        messaging.getOrCreateEventSender(tenantId, result.completer());
        return result;
    }

    /**
     * Gets a client for interacting with the Device Registration service.
     * 
     * @param tenantId The tenant that the client is scoped to.
     * @return The client.
     */
    protected final Future<RegistrationClient> getRegistrationClient(final String tenantId) {
        Future<RegistrationClient> result = Future.future();
        getRegistrationServiceClient().getOrCreateRegistrationClient(tenantId, result.completer());
        return result;
    }

    /**
     * Gets a registration status assertion for a device.
     * 
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to get the assertion for.
     * @return The assertion.
     */
    protected final Future<String> getRegistrationAssertion(final String tenantId, final String deviceId) {

        Future<String> result = Future.future();
        getRegistrationClient(tenantId).compose(client -> {
            Future<RegistrationResult> regResult = Future.future();
            client.assertRegistration(deviceId, regResult.completer());
            return regResult;
        }).compose(response -> {
            switch(response.getStatus()) {
            case HttpURLConnection.HTTP_OK:
                final String assertion = response.getPayload().getString(RegistrationConstants.FIELD_ASSERTION);
                result.complete(assertion);
                break;
            case HttpURLConnection.HTTP_NOT_FOUND:
                result.fail(new ClientErrorException(response.getStatus(), "device unknown or disabled"));
                break;
            default:
                result.fail(new ServiceInvocationException(response.getStatus(), "failed to assert registration"));
            }
        }, result);
        return result;
    }

    /**
     * Registers a check that succeeds if this component is connected to Hono Messaging,
     * the Device Registration and the Credentials service.
     */
    @Override
    public void registerReadinessChecks(final HealthCheckHandler handler) {
        handler.register("connection-to-services", status -> {
            if (isConnected()) {
                status.tryComplete(Status.OK());
            } else {
                status.tryComplete(Status.KO());
            }
        });
        if (credentialsAuthProvider != null) {
            credentialsAuthProvider.registerReadinessChecks(handler);
        }
    }

    /**
     * Registers a check that always succeeds.
     */
    @Override
    public void registerLivenessChecks(final HealthCheckHandler handler) {
        if (credentialsAuthProvider != null) {
            credentialsAuthProvider.registerLivenessChecks(handler);
        }
    }

    /**
     * Checks if the tenant and device from a given resource identifier match an authenticated device's identity.
     *
     * @param resource The resource to match.
     * @param authenticatedDevice The authenticated device identity.
     * 
     * @return {@code true} if tenantId and deviceId are {@code null} or (if not null) match the authenticated
     *         device identity.
     */
    protected final boolean validateCredentialsWithTopicStructure(final ResourceIdentifier resource, final Device authenticatedDevice) {
        return validateCredentialsWithTopicStructure(resource.getTenantId(), resource.getResourceId(),
                authenticatedDevice.getTenantId(), authenticatedDevice.getDeviceId());
    }

    /**
     * Checks if the tenant and device from a given resource identifier match an authenticated device's identity.
     *
     * @param resource The resource to match.
     * @param authenticatedTenantId The identifier of the tenant that the authenticated device belongs to.
     * @param authenticatedDeviceId The authenticated device's identifier.
     * 
     * @return {@code true} if tenantId and deviceId are {@code null} or (if not null) match the authenticated
     *         device's tenant and device identifier.
     */
    protected final boolean validateCredentialsWithTopicStructure(final ResourceIdentifier resource,
            final String authenticatedTenantId, final String authenticatedDeviceId) {
        return validateCredentialsWithTopicStructure(resource.getTenantId(), resource.getResourceId(),
                authenticatedTenantId, authenticatedDeviceId);
    }

    /**
     * Checks if a given tenant and device identifier match an authenticated device's identity.
     *
     * @param tenantId The tenant identifier to match.
     * @param deviceId The device identifier to match.
     * @param authenticatedTenantId The identifier of the tenant that the authenticated device belongs to.
     * @param authenticatedDeviceId The authenticated device's identifier.
     * 
     * @return {@code true} if tenantId and deviceId are {@code null} or (if not null) match the authenticated
     *         device's tenant and device identifier.
     */
    protected final boolean validateCredentialsWithTopicStructure(final String tenantId, final String deviceId,
            final String authenticatedTenantId, final String authenticatedDeviceId) {
        if (tenantId != null && !tenantId.equals(authenticatedTenantId)) {
            return false;
        }
        if (deviceId != null && !deviceId.equals(authenticatedDeviceId)) {
            return false;
        }
        return true;
    }
}
