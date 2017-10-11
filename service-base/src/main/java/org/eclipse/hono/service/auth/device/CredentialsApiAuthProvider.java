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

package org.eclipse.hono.service.auth.device;

import java.net.HttpURLConnection;
import java.util.Objects;

import javax.annotation.PostConstruct;

import org.eclipse.hono.client.CredentialsClient;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.CredentialsResult;
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
            if (credentialsClient != null && credentialsClient.isConnected()) {
                status.tryComplete(Status.OK());
            } else {
                status.tryComplete(Status.KO());
            }
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
    @PostConstruct
    protected final void connectToCredentialsService() {

        if (credentialsClient == null) {
            log.error("Credentials service client is not set");
        } else if (credentialsClient.isConnected()) {
            log.debug("already connected to Credentials service");
        } else {
            credentialsClient.connect(
                    createClientOptions(),
                    connectAttempt -> log.debug("connected to Credentials service"),
                    this::onDisconnectCredentialsService);
        }
    }

    /**
     * Attempts a reconnect for the Hono Credentials client after {@link Constants#DEFAULT_RECONNECT_INTERVAL_MILLIS} milliseconds.
     *
     * @param con The connection that was disonnected.
     */
    private void onDisconnectCredentialsService(final ProtonConnection con) {

        vertx.setTimer(Constants.DEFAULT_RECONNECT_INTERVAL_MILLIS, reconnect -> {
            log.debug("attempting to reconnect to Credentials service");
            credentialsClient.connect(createClientOptions(), connectAttempt -> {
                if (connectAttempt.succeeded()) {
                    log.debug("reconnected to Credentials service");
                } else {
                    log.debug("cannot reconnect to Credentials service");
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
     * Gets a client for the Credentials service.
     * 
     * @param tenantId The tenant to get the client for.
     * @return A future containing the client.
     */
    protected final Future<CredentialsClient> getCredentialsServiceClient(final String tenantId) {
        Future<CredentialsClient> result = Future.future();
        credentialsClient.getOrCreateCredentialsClient(tenantId, result.completer());
        return result;
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
        Future<CredentialsObject> result = Future.future();
        if (credentialsClient == null) {
            result.fail(new IllegalStateException("Credentials API client is not set"));
        } else {
            getCredentialsServiceClient(deviceCredentials.getTenantId()).compose(client -> {
                Future<CredentialsResult<CredentialsObject>> credResultFuture = Future.future();
                client.get(deviceCredentials.getType(), deviceCredentials.getAuthId(), credResultFuture.completer());
                return credResultFuture;
            }).compose(credResult -> {
                if (credResult.getStatus() == HttpURLConnection.HTTP_OK) {
                    CredentialsObject payload = credResult.getPayload();
                    result.complete(payload);
                } else if (credResult.getStatus() == HttpURLConnection.HTTP_NOT_FOUND) {
                    result.fail(String.format("no credentials found for device [tenant: %s, type: %s, authId: %s]",
                            deviceCredentials.getTenantId(), deviceCredentials.getType(), deviceCredentials.getAuthId()));
                } else {
                    result.fail(String.format("cannot retrieve credentials [status: %d]", credResult.getStatus()));
                }
            }, result);
        }

        return result;
    }

    @Override
    public final void authenticate(
            final DeviceCredentials deviceCredentials,
            final Handler<AsyncResult<Device>> resultHandler) {

        Objects.requireNonNull(deviceCredentials);
        Objects.requireNonNull(resultHandler);
        Future<Device> validationResult = Future.future();
        validationResult.setHandler(resultHandler);

        getCredentialsForDevice(deviceCredentials).compose(credentialsOnRecord -> {
            if (deviceCredentials.validate(credentialsOnRecord)) {
                validationResult.complete(new Device(deviceCredentials.getTenantId(), credentialsOnRecord.getDeviceId()));
            } else {
                validationResult.fail("credentials invalid - not validated");
            }
        }, validationResult);
    }

    @Override
    public final void authenticate(final JsonObject authInfo, final Handler<AsyncResult<User>> resultHandler) {

        DeviceCredentials credentials = getCredentials(Objects.requireNonNull(authInfo));
        if (credentials == null) {
            resultHandler.handle(Future.failedFuture("bad credentials"));
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
