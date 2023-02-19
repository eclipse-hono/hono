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

package org.eclipse.hono.adapter.auth.device.usernamepassword;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

import org.eclipse.hono.adapter.auth.device.AuthHandler;
import org.eclipse.hono.adapter.auth.device.CredentialsApiAuthProvider;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.registry.CredentialsClient;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.service.auth.HonoPasswordEncoder;
import org.eclipse.hono.service.auth.SpringBasedHonoPasswordEncoder;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.JsonHelper;

import io.opentracing.Tracer;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * An authentication provider that verifies username/password credentials using
 * Hono's <em>Credentials</em> API.
 */
public final class UsernamePasswordAuthProvider extends CredentialsApiAuthProvider<UsernamePasswordCredentials> {

    private final HonoPasswordEncoder pwdEncoder;

    /**
     * Creates a new provider for a given configuration.
     *
     * @param credentialsClient The client to use for accessing the Credentials service.
     * @param tracer The tracer instance.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public UsernamePasswordAuthProvider(final CredentialsClient credentialsClient, final Tracer tracer) {
        this(credentialsClient, new SpringBasedHonoPasswordEncoder(), tracer);
    }

    /**
     * Creates a new provider for a given configuration.
     *
     * @param credentialsClient The client to use for accessing the Credentials service.
     * @param pwdEncoder The object to use for validating hashed passwords.
     * @param tracer The tracer instance.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public UsernamePasswordAuthProvider(
            final CredentialsClient credentialsClient,
            final HonoPasswordEncoder pwdEncoder,
            final Tracer tracer) {

        super(credentialsClient, tracer);
        this.pwdEncoder = Objects.requireNonNull(pwdEncoder);
    }

    /**
     * Creates a {@link UsernamePasswordCredentials} instance from auth info provided by a
     * device.
     * <p>
     * The JSON object passed in is required to contain a <em>username</em> and a
     * <em>password</em> property.
     *
     * @param authInfo The credentials provided by the device. These usually get assembled via
     *            {@link AuthHandler#parseCredentials(org.eclipse.hono.util.ExecutionContext)}.
     * @return The {@link UsernamePasswordCredentials} instance created from the auth info or
     *         {@code null} if the auth info does not contain the required information.
     * @throws NullPointerException if the auth info is {@code null}.
     */
    @Override
    public UsernamePasswordCredentials getCredentials(final JsonObject authInfo) {

        final String username = JsonHelper.getValue(authInfo, CredentialsConstants.FIELD_USERNAME, String.class, null);
        final String password = JsonHelper.getValue(authInfo, CredentialsConstants.FIELD_PASSWORD, String.class, null);
        if (username == null || password == null) {
            return null;
        }
        final JsonObject clientContext = authInfo.copy();
        clientContext.remove(CredentialsConstants.FIELD_USERNAME);
        clientContext.remove(CredentialsConstants.FIELD_PASSWORD);

        if (password.isEmpty()) {
            return tryGetCredentialsEncodedInUsername(username, clientContext);
        } else {
            return UsernamePasswordCredentials.create(username, password, clientContext);
        }
    }

    private UsernamePasswordCredentials tryGetCredentialsEncodedInUsername(
            final String username,
            final JsonObject clientContext) {

        try {
            final String decoded = new String(Base64.getDecoder().decode(username), StandardCharsets.UTF_8);
            final int colonIdx = decoded.indexOf(":");
            if (colonIdx > -1) {
                final String user = decoded.substring(0, colonIdx);
                final String pass = decoded.substring(colonIdx + 1);
                return UsernamePasswordCredentials.create(user, pass, clientContext);
            } else {
                return null;
            }
        } catch (final IllegalArgumentException ex) {
            log.debug("error extracting username/password from username field", ex);
            return null;
        }
    }

    @Override
    protected Future<DeviceUser> doValidateCredentials(
            final UsernamePasswordCredentials deviceCredentials,
            final CredentialsObject credentialsOnRecord) {

        final Context currentContext = Vertx.currentContext();
        if (currentContext == null) {
            return Future.failedFuture(new IllegalStateException("not running on vert.x Context"));
        } else {
            final Promise<DeviceUser> result = Promise.promise();
            currentContext.executeBlocking(blockingCodeHandler -> {
                log.debug("validating password hash on vert.x worker thread [{}]", Thread.currentThread().getName());
                final boolean isValid = credentialsOnRecord.getCandidateSecrets().stream()
                        .anyMatch(candidateSecret -> pwdEncoder.matches(deviceCredentials.getPassword(), candidateSecret));
                if (isValid) {
                    blockingCodeHandler.complete(new DeviceUser(deviceCredentials.getTenantId(), credentialsOnRecord.getDeviceId()));
                } else {
                    blockingCodeHandler.fail(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED, "bad credentials"));
                }
            }, false, result);
            return result.future();
        }
    }
}
