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

import static org.eclipse.hono.util.MessageHelper.addDeviceId;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.TelemetrySender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
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
    private Handler<Void> drainHandler;

    private TelemetrySenderImpl(final Context context, final ProtonSender sender) {
        super(context);
        this.sender = sender;
    }

    public static void create(
            final Context context,
            final ProtonConnection con,
            final String tenantId,
            final Handler<AsyncResult<TelemetrySender>> creationHandler) {

        Objects.requireNonNull(context);
        Objects.requireNonNull(con);
        Objects.requireNonNull(tenantId);
        createSender(con, tenantId).setHandler(created -> {
            if (created.succeeded()) {
                creationHandler.handle(Future.succeededFuture(
                        new TelemetrySenderImpl(context, created.result())));
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
    public boolean sendQueueFull() {
        return sender.sendQueueFull();
    }

    @Override
    public void sendQueueDrainHandler(final Handler<Void> handler) {
        if (this.drainHandler != null) {
            throw new IllegalStateException("already waiting for replenishment with credit");
        } else {
            this.drainHandler = Objects.requireNonNull(handler);
            sender.sendQueueDrainHandler(replenishedSender -> {
                LOG.trace("telemetry sender has been replenished with {} credits", replenishedSender.getCredit());
                Handler<Void> currentHandler = this.drainHandler;
                this.drainHandler = null;
                if (currentHandler != null) {
                    currentHandler.handle(null);
                }
            });
        }
    }

    @Override
    public void close(final Handler<AsyncResult<Void>> closeHandler) {
        closeLinks(closeHandler);
    }

    @Override
    public void setErrorHandler(Handler<AsyncResult<Void>> errorHandler) {
        sender.closeHandler(s -> {
            if (s.failed()) {
                LOG.debug("server closed link with error condition: {}", s.cause().getMessage());
                sender.close();
                errorHandler.handle(Future.failedFuture(s.cause()));
            } else {
                LOG.debug("server closed link");
                sender.close();
            }
        });
    }

    @Override
    public void send(final Message rawMessage, final Handler<Void> capacityAvailableHandler) {
        Objects.requireNonNull(rawMessage);
        if (capacityAvailableHandler == null) {
            context.runOnContext(send -> {
                sender.send(rawMessage);
            });
        } else if (this.drainHandler != null) {
            throw new IllegalStateException("cannot send message while waiting for replenishment with credit");
        } else if (sender.isOpen()) {
            context.runOnContext(send -> {
                sender.send(rawMessage);
                if (sender.sendQueueFull()) {
                    sendQueueDrainHandler(capacityAvailableHandler);
                } else {
                    capacityAvailableHandler.handle(null);
                }
            });
        } else {
            throw new IllegalStateException("sender is not open");
        }
    }

    @Override
    public boolean send(final Message rawMessage) {
        if (sender.sendQueueFull()) {
            return false;
        } else {
            context.runOnContext(send -> {
                sender.send(Objects.requireNonNull(rawMessage));
            });
            return true;
        }
    }

    @Override
    public boolean send(final String deviceId, final byte[] payload, final String contentType) {
        final Message msg = ProtonHelper.message();
        msg.setBody(new Data(new Binary(payload)));
        addPropterties(msg, deviceId, contentType);
        return send(msg);
    }

    @Override
    public void send(final String deviceId, final byte[] payload, final String contentType, final Handler<Void> capacityAvailableHandler) {
        final Message msg = ProtonHelper.message();
        msg.setBody(new Data(new Binary(payload)));
        addPropterties(msg, deviceId, contentType);
        send(msg, capacityAvailableHandler);
    }

    @Override
    public boolean send(final String deviceId, final String payload, final String contentType) {
        final Message msg = ProtonHelper.message(payload);
        addPropterties(msg, deviceId, contentType);
        return send(msg);
    }

    @Override
    public void send(final String deviceId, final String payload, final String contentType, final Handler<Void> capacityAvailableHandler) {
        final Message msg = ProtonHelper.message(payload);
        addPropterties(msg, deviceId, contentType);
        send(msg, capacityAvailableHandler);
    }

    private void addPropterties(final Message msg, final String deviceId, final String contentType) {
        msg.setMessageId(String.format("TelemetryClientImpl-%d", messageCounter.getAndIncrement()));
        msg.setContentType(contentType);
        addDeviceId(msg, deviceId);
    }
}
