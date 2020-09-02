/**
 * Copyright (c) 2018, 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.service.auth.device;

import java.util.Objects;

import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.util.ExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Promise;


/**
 * A base class for implementing auth handlers.
 *
 * @param <T> The type of execution context this handler can authenticate.
 */
public abstract class ExecutionContextAuthHandler<T extends ExecutionContext> implements AuthHandler<T> {

    /**
     * The name of the property that contains an authenticated device's transport protocol specific
     * client identifier, e.g. the MQTT client identifier or an AMQP 1.0 container name.
     */
    public static final String PROPERTY_CLIENT_IDENTIFIER = "client-id";

    static final String AUTH_PROVIDER_CONTEXT_KEY = ExecutionContextAuthHandler.class.getName() + ".provider";

    /**
     * A logger that is shared with implementations.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final HonoClientBasedAuthProvider<?> authProvider;

    /**
     * Creates a new handler for authenticating MQTT clients.
     *
     * @param authProvider The auth provider to use for verifying a client's credentials.
     */
    protected ExecutionContextAuthHandler(final HonoClientBasedAuthProvider<?> authProvider) {
        this.authProvider = authProvider;
    }

    @Override
    public final HonoClientBasedAuthProvider<?> getAuthProvider() {
        return authProvider;
    }

    @Override
    public Future<DeviceUser> authenticateDevice(final T context) {

        Objects.requireNonNull(context);

        return parseCredentials(context)
                .compose(authInfo -> {
                    final Promise<DeviceUser> authResult = Promise.promise();
                    getAuthProvider(context).authenticate(authInfo, context, authResult);
                    return authResult.future();
                });
    }

    private HonoClientBasedAuthProvider<?> getAuthProvider(final T ctx) {

        final Object obj = ctx.get(AUTH_PROVIDER_CONTEXT_KEY);
        if (obj instanceof HonoClientBasedAuthProvider<?>) {
            log.debug("using auth provider found in context [type: {}]", obj.getClass().getName());
            // we're overruling the configured one for this request
            return (HonoClientBasedAuthProvider<?>) obj;
        } else {
            // bad type, ignore and return default
            return authProvider;
        }
    }
}
