/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * <p>
 * Contributors:
 * Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.client.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.*;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.CredentialsClient;
import org.eclipse.hono.client.RequestResponseClient;
import org.eclipse.hono.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.eclipse.hono.util.CredentialsConstants.OPERATION_GET;
import static org.eclipse.hono.util.MessageHelper.APP_PROPERTY_STATUS;

/**
 * A Vertx-Proton based client for Hono's Credentials API.
 *
 */
public final class CredentialsClientImpl extends AbstractRequestResponseClient<CredentialsClient, CredentialsResult> implements CredentialsClient {

    private static final String                  CREDENTIALS_NAME = "credentials";

    private static final Logger                  LOG = LoggerFactory.getLogger(CredentialsClientImpl.class);

    private CredentialsClientImpl(final Context context, final ProtonConnection con, final String tenantId,
                                  final Handler<AsyncResult<CredentialsClient>> creationHandler) {
        super(context, con, tenantId, creationHandler);
    }

    @Override
    protected String getName() {

        return CREDENTIALS_NAME;
    }

    @Override
    protected String createMessageId() {

        return String.format("cred-client-%s", UUID.randomUUID());
    }

    @Override
    protected CredentialsResult getResult(final int status, final JsonObject payload) {

        return CredentialsResult.from(status, payload);
    }

    /**
     * Creates a new credentials client for a tenant.
     *
     * @param context The vert.x context to run all interactions with the server on.
     * @param con The AMQP connection to the server.
     * @param tenantId The tenant for which credentials are handled.
     * @param creationHandler The handler to invoke with the outcome of the creation attempt.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static void create(final Context context, final ProtonConnection con, final String tenantId,
                              final Handler<AsyncResult<CredentialsClient>> creationHandler) {
        new CredentialsClientImpl(
                Objects.requireNonNull(context),
                Objects.requireNonNull(con),
                Objects.requireNonNull(tenantId),
                Objects.requireNonNull(creationHandler));
    }

    @Override
    public final void get(final String type, final String authId, final Handler<AsyncResult<CredentialsResult>> resultHandler) {
        JsonObject specification = new JsonObject().put(CredentialsConstants.FIELD_TYPE, type).put(CredentialsConstants.FIELD_AUTH_ID, authId);
        createAndSendRequest(OPERATION_GET, specification, resultHandler);
    }
}
