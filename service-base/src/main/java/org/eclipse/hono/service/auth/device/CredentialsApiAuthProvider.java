/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *    Red Hat Inc
 */

package org.eclipse.hono.service.auth.device;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.CredentialsClient;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;


/**
 * A base class for implementing authentication providers that verify credentials provided by devices
 * against information on record retrieved using Hono's <em>Credentials</em> API.
 *
 */
public abstract class CredentialsApiAuthProvider implements HonoClientBasedAuthProvider {

    /**
     * A logger to be used by subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());
    private final Vertx vertx;
    private HonoClient credentialsClient;
    private AtomicBoolean shutdownInProgress = new AtomicBoolean(false);

    /**
     * Creates a new authentication provider for a vert.x instance.
     * 
     * @param vertx The vert.x instance to use for scheduling re-connection attempts.
     * @throws NullPointerException if vertx is {@code null}
     */
    protected CredentialsApiAuthProvider(final Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
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
        this.credentialsClient = Objects.requireNonNull(credentialsServiceClient);
    }

    /**
     * Registers a check that verifies connection to Hono's <em>Credentials</em>
     * service.
     */
    @Override
    public void registerReadinessChecks(final HealthCheckHandler readinessHandler) {
        readinessHandler.register("connected-to-credentials-service", status -> {
            Future<Boolean> checkResult = Optional.ofNullable(credentialsClient)
                    .map(client -> client.isConnected()).orElse(Future.succeededFuture(Boolean.FALSE));
            checkResult.map(connected -> {
                if (connected) {
                    status.tryComplete(Status.OK());
                } else {
                    status.tryComplete(Status.KO());
                }
                return null;
            });
        });
    }

    /**
     * Does not do anything.
     */
    @Override
    public void registerLivenessChecks(final HealthCheckHandler livenessHandler) {
        // nothing to do
    }

    /**
     * Connects to the Credentials service using the configured client.
     */
    @Override
    public final Future<Void> start() {

        if (credentialsClient == null) {
            return Future.failedFuture(new IllegalStateException("Credentials service client is not set"));
        } else {
            return credentialsClient
                    .connect(createClientOptions(), this::onDisconnectCredentialsService)
                    .map(connectedClient -> {
                        log.info("connected to Credentials service");
                        return (Void) null;
                    }).recover(t -> {
                        log.warn("connection to Credentials service failed", t);
                        return Future.failedFuture(t);
                    });
        }
    }

    /**
     * Attempts a reconnect for the Hono Credentials client after {@link Constants#DEFAULT_RECONNECT_INTERVAL_MILLIS} milliseconds.
     *
     * @param con The connection that was disconnected.
     */
    private void onDisconnectCredentialsService(final ProtonConnection con) {

        if (shutdownInProgress.get()) {
            log.debug("shut down in progress, not trying to re-connect to Credentials service");
        } else {
            vertx.setTimer(Constants.DEFAULT_RECONNECT_INTERVAL_MILLIS, reconnect -> {
                log.info("attempting to reconnect to Credentials service");
                credentialsClient.connect(createClientOptions(), this::onDisconnectCredentialsService).setHandler(connectAttempt -> {
                    if (connectAttempt.succeeded()) {
                        log.info("reconnected to Credentials service");
                    } else {
                        log.debug("cannot reconnect to Credentials service");
                    }
                });
            });
        }
    }

    /**
     * Closes the connection to the Credentials service.
     */
    @Override
    public final Future<Void> stop() {

        shutdownInProgress.set(true);
        Future<Void> result = Future.future();
        if (credentialsClient == null) {
            result.complete();
        } else {
            credentialsClient.shutdown(result.completer());
        }
        return result;
    }

    private ProtonClientOptions createClientOptions() {
        return new ProtonClientOptions()
                .setConnectTimeout(200)
                .setReconnectAttempts(1)
                .setReconnectInterval(Constants.DEFAULT_RECONNECT_INTERVAL_MILLIS);
    }

    /**
     * Gets a client for the Credentials service.
     * 
     * @param tenantId The tenant to get the client for.
     * @return A future containing the client.
     */
    protected final Future<CredentialsClient> getCredentialsServiceClient(final String tenantId) {
        if (credentialsClient == null) {
            return Future.failedFuture(new IllegalStateException("no credentials client set"));
        } else {
            return credentialsClient.getOrCreateCredentialsClient(tenantId);
        }
    }

    /**
     * Retrieves credentials from the Credentials service.
     * 
     * @param deviceCredentials The credentials provided by the device.
     * @return A future containing the credentials on record as retrieved from
     *         Hono's <em>Credentials</em> API.
     * @throws NullPointerException if device credentials is {@code null}.
     */
    protected final Future<CredentialsObject> getCredentialsForDevice(final DeviceCredentials deviceCredentials) {

        Objects.requireNonNull(deviceCredentials);
        if (credentialsClient == null) {
            return Future.failedFuture(new IllegalStateException("Credentials API client is not set"));
        } else {
            return getCredentialsServiceClient(deviceCredentials.getTenantId()).compose(client ->
                client.get(deviceCredentials.getType(), deviceCredentials.getAuthId()));
        }
    }

    @Override
    public final void authenticate(
            final DeviceCredentials deviceCredentials,
            final Handler<AsyncResult<Device>> resultHandler) {

        Objects.requireNonNull(deviceCredentials);
        Objects.requireNonNull(resultHandler);
        Future<Device> validationResult = Future.future();
        validationResult.setHandler(resultHandler);

        getCredentialsForDevice(deviceCredentials).recover(t -> {
            final ServiceInvocationException e = (ServiceInvocationException) t;
            if (e.getErrorCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED, "bad credentials"));
            } else {
                return Future.failedFuture(t);
            }
        }).map(credentialsOnRecord -> {
            if (deviceCredentials.validate(credentialsOnRecord)) {
                return new Device(deviceCredentials.getTenantId(), credentialsOnRecord.getDeviceId());
            } else {
                 throw new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED, "invalid credentials");
            }
        }).setHandler(resultHandler);
    }

    @Override
    public final void authenticate(final JsonObject authInfo, final Handler<AsyncResult<User>> resultHandler) {

        final DeviceCredentials credentials = getCredentials(Objects.requireNonNull(authInfo));
        if (credentials == null) {
            resultHandler.handle(Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED, "malformed credentials")));
        } else {
            authenticate(credentials, s -> {
                if (s.succeeded()) {
                    resultHandler.handle(Future.succeededFuture(s.result()));
                } else {
                    resultHandler.handle(Future.failedFuture(s.cause()));
                }
            });
        }
    }

    /**
     * Creates device credentials from authentication information provided by a
     * device.
     * <p>
     * Subclasses need to create a concrete {@code DeviceCredentials} instance based on
     * the information contained in the JSON object.
     * 
     * @param authInfo The credentials provided by the device.
     * @return The device credentials or {@code null} if the auth info does not contain
     *         the required information.
     * @throws NullPointerException if auth info is {@code null}.
     */
    protected abstract DeviceCredentials getCredentials(JsonObject authInfo);

}
