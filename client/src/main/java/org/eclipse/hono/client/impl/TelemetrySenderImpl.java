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
 */

package org.eclipse.hono.client.impl;

import static org.eclipse.hono.util.MessageHelper.*;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.TelemetrySender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * A Vertx-Proton based client for uploading telemtry data to a Hono server.
 */
public class TelemetrySenderImpl extends AbstractHonoClient implements TelemetrySender {

    private static final String     TELEMETRY_ADDRESS_TEMPLATE  = "telemetry/%s";
    private static final Logger     LOG = LoggerFactory.getLogger(TelemetrySenderImpl.class);
    private static final AtomicLong messageCounter = new AtomicLong();

    private TelemetrySenderImpl(final ProtonSender sender) {
        this.sender = sender;
    }

    public static void create(
            final ProtonConnection con,
            final String tenantId,
            final Handler<AsyncResult<TelemetrySender>> creationHandler) {

        Objects.requireNonNull(con);
        Objects.requireNonNull(tenantId);
        createSender(con, tenantId).setHandler(created -> {
            if (created.succeeded()) {
                creationHandler.handle(Future.succeededFuture(
                        new TelemetrySenderImpl(created.result())));
            } else {
                creationHandler.handle(Future.failedFuture(created.cause()));
            }
        });
    }

    private static Future<ProtonSender> createSender(
            final ProtonConnection con,
            final String tenantId) {

        Future<ProtonSender> result = Future.future();
        final String targetAddress = String.format(TELEMETRY_ADDRESS_TEMPLATE, tenantId);

        final ProtonSender sender = con.createSender(targetAddress);
        sender.setQoS(ProtonQoS.AT_MOST_ONCE);
        sender.openHandler(senderOpen -> {
            if (senderOpen.succeeded()) {
                LOG.debug("telemetry sender for [{}] open", senderOpen.result().getRemoteTarget());
                result.complete(senderOpen.result());
            } else {
                result.fail(senderOpen.cause());
            }
        }).open();

        return result;
    }

    @Override
    public void close(final Handler<AsyncResult<Void>> closeHandler) {
        closeLinks(closeHandler);
    }

    @Override
    public void setErrorHandler(Handler<AsyncResult<Void>> errorHandler) {
        sender.closeHandler(s -> {
            if (s.failed()) {
                LOG.debug("server closed link: {}", s.cause().getMessage());
                sender.close();
                errorHandler.handle(Future.failedFuture(s.cause()));
            } else {
                LOG.debug("server wants to close telemetry sender");
                sender.close();
            }
        });
    }

    @Override
    public boolean send(final Message rawMessage) {
        if (sender.getCredit() <= 0) {
            return false;
        } else {
            sender.send(Objects.requireNonNull(rawMessage));
            return true;
        }
    }

    @Override
    public boolean send(final String deviceId, final byte[] payload, final String contentType) {
        final Message msg = ProtonHelper.message();
        msg.setBody(new Data(new Binary(payload)));
        msg.setContentType(contentType);
        return addPropertiesAndSend(deviceId, msg);
    }

    @Override
    public boolean send(final String deviceId, final String payload, final String contentType) {
        final Message msg = ProtonHelper.message(payload);
        msg.setContentType(contentType);
        return addPropertiesAndSend(deviceId, msg);
    }

    private boolean addPropertiesAndSend(final String deviceId, final Message msg) {
        msg.setMessageId(String.format("TelemetryClientImpl-%d", messageCounter.getAndIncrement()));
        addDeviceId(msg, deviceId);
        return send(msg);
    }
}
