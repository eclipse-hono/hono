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

import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonConnection;
import org.eclipse.hono.client.CredentialsClient;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.credentials.CredentialsSecretsValidator;
import org.eclipse.hono.service.credentials.CredentialsUtils;
import org.eclipse.hono.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.proton.ProtonClientOptions;

/**
 * A base class for implementing protocol adapters.
 * <p>
 * Provides connections to device registration and telemetry and event service endpoints.
 * 
 * @param <T> The type of configuration properties used by this service.
 */
public abstract class AbstractProtocolAdapterBase<T extends ServiceConfigProperties> extends AbstractServiceBase<T> {

    private HonoClient messaging;
    private HonoClient registration;
    private HonoClient credentials;

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
     * Sets the client to use for connecting to the Credentials API (which may be offered by the Device Registry component).
     * If no credentials client is configured, the registration client will be used for accessing the Credentials API.
     *
     * @param credentialsServiceClient The client.
     * @throws NullPointerException if the client is {@code null}.
     */
    @Qualifier(CredentialsConstants.CREDENTIALS_ENDPOINT)
    @Autowired(required = false)
    public final void setCredentialsServiceClient(final HonoClient credentialsServiceClient) {
        this.credentials = Objects.requireNonNull(credentialsServiceClient);
    }

    /**
     * Gets the client used for connecting to the Credentials API.
     *
     * @return The client.
     */
    public final HonoClient getCredentialsServiceClient() {
        if (credentials == null) {
            return registration;
        } else {
            return credentials;
        }
    }

    @Override
    public final Future<Void> startInternal() {
        Future<Void> result = Future.future();
        if (messaging == null) {
            result.fail("Hono Messaging client must be set");
        } else if (registration == null) {
            result.fail("Device Registration client must be set");
        } else {
            if (credentials == null) {
                LOG.info("Credentials client not configured, using Device Registration client instead.");
            }
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
            LOG.debug("already connected to Hono Messaging");
            if (connectHandler != null) {
                connectHandler.handle(Future.succeededFuture(messaging));
            }
        } else {
            messaging.connect(createClientOptions(), connectAttempt -> {
                if (connectHandler != null) {
                    connectHandler.handle(connectAttempt);
                } else {
                    LOG.debug("connected to Hono Messaging");
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
                    LOG.debug("reconnected to Hono Messaging");
                } else {
                    LOG.debug("cannot reconnect to Hono Messaging");
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
            LOG.debug("already connected to Device Registration service");
            if (connectHandler != null) {
                connectHandler.handle(Future.succeededFuture(registration));
            }
        } else {
            registration.connect(createClientOptions(), connectAttempt -> {
                if (connectHandler != null) {
                    connectHandler.handle(connectAttempt);
                } else {
                    LOG.debug("connected to Device Registration service");
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
                    LOG.debug("reconnected to Device Registration service");
                } else {
                    LOG.debug("cannot reconnect to Device Registration service");
                }
            }, this::onDisconnectDeviceRegistry);
        });
    }

    /**
     * Connects to the Credentials service using the configured client.
     *
     * @param connectHandler The handler to invoke with the outcome of the connection attempt.
     *                       If {@code null} and the connection attempt failed, this method
     *                       tries to re-connect until a connection is established.
     */
    protected final void connectToCredentialsService(final Handler<AsyncResult<HonoClient>> connectHandler) {

        if (credentials == null) {
            if (connectHandler != null) {
                if (registration != null) {
                    // give back registration client if credentials client is not configured
                    connectHandler.handle(Future.succeededFuture(registration));
                } else {
                    connectHandler.handle(Future.failedFuture("Neither Credentials client nor Device Registration client is set"));
                }
            }
        } else if (credentials.isConnected()) {
            LOG.debug("already connected to Credentials service");
            if (connectHandler != null) {
                connectHandler.handle(Future.succeededFuture(credentials));
            }
        } else {
            credentials.connect(createClientOptions(), connectAttempt -> {
                if (connectHandler != null) {
                    connectHandler.handle(connectAttempt);
                } else {
                    LOG.debug("connected to Credentials service");
                }
            }, this::onDisconnectCredentialsService);
        }
    }

    /**
     * Attempts a reconnect for the Hono Credentials client after {@link Constants#DEFAULT_RECONNECT_INTERVAL_MILLIS} milliseconds.
     *
     * @param con The connection that was disonnected.
     */
    private void onDisconnectCredentialsService(final ProtonConnection con) {

        vertx.setTimer(Constants.DEFAULT_RECONNECT_INTERVAL_MILLIS, reconnect -> {
            LOG.info("attempting to reconnect to Credentials service");
            credentials.connect(createClientOptions(), connectAttempt -> {
                if (connectAttempt.succeeded()) {
                    LOG.debug("reconnected to Credentials service");
                } else {
                    LOG.debug("cannot reconnect to Credentials service");
                }
            }, this::onDisconnectCredentialsService);
        });
    }

    private ProtonClientOptions createClientOptions() {
        return new ProtonClientOptions()
                .setConnectTimeout(200)
                .setReconnectAttempts(1)
                .setReconnectInterval(Constants.DEFAULT_RECONNECT_INTERVAL_MILLIS);
    }

    /**
     * Checks if this adapter is connected to both <em>Hono Messaging</em> and the Device Registration service.
     * 
     * @return {@code true} if this adapter is connected.
     */
    protected final boolean isConnected() {
        boolean result = messaging != null && messaging.isConnected() &&
                registration != null && registration.isConnected();
        if (credentials != null) {
            result &= credentials.isConnected();
        }
        return result;
    }

    /**
     * Closes the connections to the Hono Messaging component and the Device Registration service.
     * 
     * @param closeHandler The handler to notify about the result.
     */
    protected final void closeClients(final Handler<AsyncResult<Void>> closeHandler) {

        Future<Void> messagingTracker = Future.future();
        Future<Void> registrationTracker = Future.future();
        Future<Void> credentialsTracker = Future.future();

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

        if (credentials == null) {
            credentialsTracker.complete();
        } else {
            credentials.shutdown(credentialsTracker.completer());
        }

        CompositeFuture.all(messagingTracker, registrationTracker, credentialsTracker).setHandler(s -> {
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
     * Gets a client for interacting with the Credentials service.
     *
     * @param tenantId The tenant that the client is scoped to.
     * @return The client.
     */
    protected final Future<CredentialsClient> getCredentialsClient(final String tenantId) {
        Future<CredentialsClient> result = Future.future();
        getCredentialsServiceClient().getOrCreateCredentialsClient(tenantId, result.completer());
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
            Future<RegistrationResult> tokenTracker = Future.future();
            client.assertRegistration(deviceId, tokenTracker.completer());
            return tokenTracker;
        }).compose(regResult -> {
            if (regResult.getStatus() == HttpURLConnection.HTTP_OK) {
                result.complete(regResult.getPayload().getString(RegistrationConstants.FIELD_ASSERTION));
            } else {
                result.fail("cannot assert device registration status");
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
                status.complete(Status.OK());
            } else {
                status.complete(Status.KO());
            }
        });
    }

    /**
     * Registers a check that always succeeds.
     */
    @Override
    public void registerLivenessChecks(final HealthCheckHandler handler) {
        handler.register("ping", status -> {
            status.complete(Status.OK());
        });
    }
    private Future<CredentialsObject> getCredentialsForDevice(final String tenantId, final String type, final String authId) {

        Future<CredentialsObject> result = Future.future();
        getCredentialsClient(tenantId).compose(client -> {
            Future<CredentialsResult<CredentialsObject>> credResultFuture = Future.future();
            client.get(type, authId, credResultFuture.completer());
            return credResultFuture;
        }).compose(credResult -> {
            if (credResult.getStatus() == HttpURLConnection.HTTP_OK) {
                CredentialsObject payload = credResult.getPayload();
                result.complete(payload);
            } else if (credResult.getStatus() == HttpURLConnection.HTTP_NOT_FOUND) {
                result.fail(String.format("cannot retrieve credentials (not found for type <%s>, authId <%s>)",type,authId));
            } else {
                result.fail("cannot retrieve credentials");
            }
        }, result);

        return result;
    }

    /**
     * Validates an authentication object against credentials secrets available by the get operation of the
     *  <a href="https://www.eclipse.org/hono/api/Credentials-API/">Credentials API</a>.
     * <p>The credentials are first retrieved from the credentials service, and then all matching validators are
     * invoked until one is successful or all failed. The authentication object is validated iff at least
     * one validator was successful.
     *
     * @param tenantId The tenantId to which the device belongs.
     * @param type The type of credentials that are to be used for validation.
     * @param authId The authId of the credentials that are to be used for validation.
     * @param authenticationObject The authentication object to be validated, e.g. a password, a preshared-key, etc.

     * @return Future The future object carrying the payload of the credentials get operation, if successful.
     */
    protected Future<String> validateCredentialsForDevice(final String tenantId, final String type, final String authId,
                                                              final Object authenticationObject) {
        return getCredentialsForDevice(tenantId, type, authId).compose(payload -> {
            Future<String> resultDeviceId = Future.future();
            CredentialsSecretsValidator validator = CredentialsUtils.findAppropriateValidators(type);
            try {
                if (validator != null && validator.validate(payload, authenticationObject)) {
                    resultDeviceId.complete(payload.getDeviceId());
                } else {
                    resultDeviceId.fail("credentials invalid - not validated");
                }
            }
            catch (IllegalArgumentException e) {
                resultDeviceId.fail(String.format("credentials invalid : %s", e.getMessage()));
            }

            return resultDeviceId;
        });
    }
}
