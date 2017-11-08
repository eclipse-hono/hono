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

import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.eclipse.hono.util.CredentialsConstants.OPERATION_GET;

import java.io.IOException;
import java.util.UUID;

import org.eclipse.hono.client.CredentialsClient;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.CredentialsResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonConnection;

/**
 * A Vertx-Proton based client for Hono's Credentials API.
 *
 */
public final class CredentialsClientImpl extends AbstractRequestResponseClient<CredentialsResult<CredentialsObject>> implements CredentialsClient {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private CredentialsClientImpl(final Context context, final String tenantId) {
        super(context, tenantId);
    }

    @Override
    protected String getName() {

        return CredentialsConstants.CREDENTIALS_ENDPOINT;
    }

    @Override
    protected String createMessageId() {

        return String.format("cred-client-%s", UUID.randomUUID());
    }

    @Override
    protected CredentialsResult<CredentialsObject> getResult(final int status, final String payload) {
        try {
            if (status == HTTP_OK) {
                return CredentialsResult.from(status, objectMapper.readValue(payload, CredentialsObject.class));
            } else {
                return CredentialsResult.from(status);
            }
        } catch (IOException e) {
            return CredentialsResult.from(HTTP_INTERNAL_ERROR);
        }
    }

    /**
     * Creates a new credentials client for a tenant.
     *
     * @param context The vert.x context to run all interactions with the server on.
     * @param con The AMQP connection to the server.
     * @param tenantId The tenant for which credentials are handled.
     * @param receiverPrefetchCredits Number of credits, given initially from receiver to sender.
     * @param waitForInitialCredits Milliseconds to wait after link creation if there are no credits.
     * @param senderCloseHook A handler to invoke if the peer closes the sender link unexpectedly.
     * @param receiverCloseHook A handler to invoke if the peer closes the receiver link unexpectedly.
     * @param creationHandler The handler to invoke with the outcome of the creation attempt.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if receiverPrefetchCredits is {@code < 0}.
     * @throws IllegalArgumentException if waitForInitialCredits is {@code < 1}.
     */
    public static void create(
            final Context context,
            final ProtonConnection con,
            final String tenantId,
            final int receiverPrefetchCredits,
            final long waitForInitialCredits,
            final Handler<String> senderCloseHook,
            final Handler<String> receiverCloseHook,
            final Handler<AsyncResult<CredentialsClient>> creationHandler) {

        final CredentialsClientImpl client = new CredentialsClientImpl(context, tenantId);
        client.createLinks(con, receiverPrefetchCredits, waitForInitialCredits, senderCloseHook, receiverCloseHook).setHandler(s -> {
            if (s.succeeded()) {
                creationHandler.handle(Future.succeededFuture(client));
            } else {
                creationHandler.handle(Future.failedFuture(s.cause()));
            }
        });
    }

    @Override
    public final void get(final String type, final String authId, final Handler<AsyncResult<CredentialsResult<CredentialsObject>>> resultHandler) {
        JsonObject specification = new JsonObject().put(CredentialsConstants.FIELD_TYPE, type).put(CredentialsConstants.FIELD_AUTH_ID, authId);
        createAndSendRequest(OPERATION_GET, specification, resultHandler);
    }
}
