/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *
 */

package org.eclipse.hono.client.impl;

import java.util.Objects;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * A Vertx-Proton based client for publishing event messages to a Hono server.
 */
public class EventSenderImpl extends AbstractSender {

    private static final String EVENT_ENDPOINT_NAME = "event/";
    private static final Logger LOG = LoggerFactory.getLogger(EventSenderImpl.class);

    private EventSenderImpl(final ProtonSender sender, final String tenantId, final Context context) {
        super(sender, tenantId, context);
    }

    /**
     * Gets the AMQP <em>target</em> address to use for sending messages to Hono's event endpoint.
     * 
     * @param tenantId The tenant to send events for.
     * @param deviceId The device to send events for. If {@code null}, the target address can be used
     *                 to send events for arbitrary devices belonging to the tenant.
     * @return The target address.
     * @throws NullPointerException if tenant is {@code null}.
     */
    public static String getTargetAddress(final String tenantId, final String deviceId) {
        StringBuilder address = new StringBuilder(EVENT_ENDPOINT_NAME).append(tenantId);
        if (deviceId != null && deviceId.length() > 0) {
            address.append("/").append(deviceId);
        }
        return address.toString();
    }

    @Override
    protected String getTo(final String deviceId) {
        return getTargetAddress(tenantId, deviceId);
    }

    public static void create(
            final Context context,
            final ProtonConnection con,
            final String tenantId,
            final String deviceId,
            final Handler<AsyncResult<MessageSender>> creationHandler) {

        Objects.requireNonNull(context);
        Objects.requireNonNull(con);
        Objects.requireNonNull(tenantId);
        final String targetAddress = getTargetAddress(tenantId, deviceId);
        createSender(con, targetAddress).setHandler(created -> {
            if (created.succeeded()) {
                creationHandler.handle(Future.succeededFuture(
                        new EventSenderImpl(created.result(), tenantId, context)));
            } else {
                creationHandler.handle(Future.failedFuture(created.cause()));
            }
        });
    }

    private static Future<ProtonSender> createSender(
            final ProtonConnection con,
            final String targetAddress) {

        final Future<ProtonSender> result = Future.future();

        final ProtonSender sender = con.createSender(targetAddress);
        sender.setQoS(ProtonQoS.AT_LEAST_ONCE);
        sender.openHandler(senderOpen -> {
            if (senderOpen.succeeded()) {
                LOG.debug("event sender for [{}] open", senderOpen.result().getRemoteTarget());
                result.complete(senderOpen.result());
            } else {
                LOG.debug("event sender open failed [{}]", senderOpen.cause().getMessage());
                result.fail(senderOpen.cause());
            }
        }).closeHandler(senderClosed -> {
            if (senderClosed.succeeded()) {
                LOG.debug("event sender for [{}] closed", targetAddress);
            } else {
                LOG.debug("event sender for [{}] closed: {}", targetAddress, senderClosed.cause().getMessage());
            }
        }).open();

        return result;
    }

    @Override
    protected void addEndpointSpecificProperties(final Message msg, final String deviceId) {
        msg.setDurable(true);
    }
}
