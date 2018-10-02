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
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.CredentialsObject;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;


/**
 * An authentication provider that verifies username/password credentials using
 * Hono's <em>Credentials</em> API.
 *
 */
public final class UsernamePasswordAuthProvider extends CredentialsApiAuthProvider {

    private final ServiceConfigProperties config;

    /**
     * Creates a new provider for a given configuration.
     * 
     * @param credentialsServiceClient The client.
     * @param config The configuration.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    @Autowired
    public UsernamePasswordAuthProvider(final HonoClient credentialsServiceClient, final ServiceConfigProperties config) {
        super(credentialsServiceClient);
        this.config = Objects.requireNonNull(config);
    }

    /**
     * Creates a {@link UsernamePasswordCredentials} instance from auth info provided by a
     * device.
     * <p>
     * The JSON object passed in is required to contain a <em>username</em> and a
     * <em>password</em> property.
     * 
     * @param authInfo The credentials provided by the device.
     * @return The {@link UsernamePasswordCredentials} instance created from the auth info or
     *         {@code null} if the auth info does not contain the required information.
     * @throws NullPointerException if the auth info is {@code null}.
     */
    @Override
    protected DeviceCredentials getCredentials(final JsonObject authInfo) {

        try {
            final String username = authInfo.getString("username");
            final String password = authInfo.getString("password");
            if (username == null || password == null) {
                return null;
            } else {
                return UsernamePasswordCredentials.create(username, password, config.isSingleTenant());
            }
        } catch (ClassCastException e) {
            return null;
        }
    }

    @Override
    protected Future<Device> validateCredentials(
            final DeviceCredentials deviceCredentials,
            final CredentialsObject credentialsOnRecord) {

        final Context currentContext = Vertx.currentContext();
        if (currentContext == null) {
            return Future.failedFuture(new IllegalStateException("not running on vert.x Context"));
        } else {
            final Future<Device> resultHandler = Future.future();
            currentContext.executeBlocking(blockingCodeHandler -> {
                log.debug("validating password hash on vert.x worker thread [{}]", Thread.currentThread().getName());
                if (deviceCredentials.validate(credentialsOnRecord)) {
                    blockingCodeHandler.complete(new Device(deviceCredentials.getTenantId(), credentialsOnRecord.getDeviceId()));
                } else {
                    blockingCodeHandler.fail(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED, "bad credentials"));
                }
            }, false, resultHandler);
            return resultHandler;
        }
    }
}
