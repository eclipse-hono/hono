/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.auth.device;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.CredentialsClient;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.CredentialsObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;


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
    private final HonoClient credentialsServiceClient;

    /**
     * Creates a new authentication provider for a credentials service client.
     * 
     * @param credentialsServiceClient The client.
     * @throws NullPointerException if the client is {@code null}
     */
    public CredentialsApiAuthProvider(final HonoClient credentialsServiceClient) {
        this.credentialsServiceClient = Objects.requireNonNull(credentialsServiceClient);
    }

    /**
     * Gets a client for the Credentials service.
     * 
     * @param tenantId The tenant to get the client for.
     * @return A future containing the client.
     */
    protected final Future<CredentialsClient> getCredentialsClient(final String tenantId) {
        if (credentialsServiceClient == null) {
            return Future.failedFuture(new IllegalStateException("no credentials client set"));
        } else {
            return credentialsServiceClient.getOrCreateCredentialsClient(tenantId);
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
        if (credentialsServiceClient == null) {
            return Future.failedFuture(new IllegalStateException("Credentials API client is not set"));
        } else {
            return getCredentialsClient(deviceCredentials.getTenantId()).compose(client ->
                client.get(deviceCredentials.getType(), deviceCredentials.getAuthId()));
        }
    }

    @Override
    public final void authenticate(
            final DeviceCredentials deviceCredentials,
            final Handler<AsyncResult<Device>> resultHandler) {

        Objects.requireNonNull(deviceCredentials);
        Objects.requireNonNull(resultHandler);

        getCredentialsForDevice(deviceCredentials)
        .recover(t -> {

            if (!(t instanceof ServiceInvocationException)) {
                return Future.failedFuture(t);
            }

            final ServiceInvocationException e = (ServiceInvocationException) t;

            if (e.getErrorCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED, "bad credentials"));
            } else {
                return Future.failedFuture(t);
            }
        }).compose(credentialsOnRecord -> validateCredentials(deviceCredentials, credentialsOnRecord))
        .setHandler(resultHandler);
    }

    /**
     * Verifies that the credentials provided by a device during the authentication
     * process match the credentials on record for that device.
     * <p>
     * This default implementation simply invokes the {@link DeviceCredentials#validate(CredentialsObject)}
     * method in order to validate the credentials.
     * <p>
     * Subclasses may override this method in order to perform more specific checks
     * for certain types of credentials.
     * 
     * @param deviceCredentials The credentials provided by the device.
     * @param credentialsOnRecord The credentials on record.
     * @return A future that is succeeded with the authenticated device if the
     *         credentials have been validated successfully. Otherwise, the
     *         future is failed with a {@link ServiceInvocationException}.
     */
    protected Future<Device> validateCredentials(
            final DeviceCredentials deviceCredentials,
            final CredentialsObject credentialsOnRecord) {

        final Future<Device> result = Future.future();
        if (deviceCredentials.validate(credentialsOnRecord)) {
            result.complete(new Device(deviceCredentials.getTenantId(), credentialsOnRecord.getDeviceId()));
        } else {
            result.fail(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED, "bad credentials"));
        }
        return result;
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
