/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.service.tenant;

import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;

import java.util.Objects;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.amqp.RequestResponseEndpoint;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TenantConstants;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * An {@code AmqpEndpoint} for managing tenant information.
 * <p>
 * This endpoint implements Hono's <a href="https://www.eclipse.org/hono/api/tenant-api/">Tenant API</a>. It receives AMQP 1.0
 * messages representing requests and sends them to an address on the vertx event bus for processing. The outcome is
 * then returned to the peer in a response message.
 */
public final class TenantAmqpEndpoint extends RequestResponseEndpoint<ServiceConfigProperties> {

    /**
     * Creates a new tenant endpoint for a vertx instance.
     *
     * @param vertx The vertx instance to use.
     */
    @Autowired
    public TenantAmqpEndpoint(final Vertx vertx) {
        super(Objects.requireNonNull(vertx));
    }

    @Override
    public String getName() {
        return TenantConstants.TENANT_ENDPOINT;
    }

    @Override
    public void processRequest(final Message msg, final ResourceIdentifier targetAddress,
            final HonoUser clientPrincipal) {

        final JsonObject tenantMsg = TenantConstants.getTenantMsg(msg);
        vertx.eventBus().send(TenantConstants.EVENT_BUS_ADDRESS_TENANT_IN, tenantMsg,
                result -> {
                    JsonObject response = null;
                    if (result.succeeded()) {
                        response = (JsonObject) result.result().body();
                    } else {
                        logger.debug("failed to process tenant management request [msg ID: {}] due to {}",
                                msg.getMessageId(), result.cause());
                        response = TenantConstants.getServiceReplyAsJson(HTTP_INTERNAL_ERROR,
                                MessageHelper.getTenantIdAnnotation(msg), null, null);
                    }
                    addHeadersToResponse(msg, response);
                    vertx.eventBus().send(msg.getReplyTo(), response);
                });
    }

    @Override
    protected boolean passesFormalVerification(final ResourceIdentifier linkTarget, final Message msg) {
        return TenantMessageFilter.verify(linkTarget, msg);
    }

    @Override
    protected Message getAmqpReply(final io.vertx.core.eventbus.Message<JsonObject> message) {
        return TenantConstants.getAmqpReply(TenantConstants.TENANT_ENDPOINT, message.body());
    }
}
