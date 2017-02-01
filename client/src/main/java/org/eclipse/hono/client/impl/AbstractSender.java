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

import static org.eclipse.hono.util.MessageHelper.addDeviceId;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonSender;

/**
 * A Vertx-Proton based client for publishing messages to a Hono server.
 */
abstract class AbstractSender extends AbstractHonoClient implements MessageSender {

    private static final Logger                LOG = LoggerFactory.getLogger(AbstractSender.class);
    private static final AtomicLong            MESSAGE_COUNTER = new AtomicLong();
    private static final Pattern               CHARSET_PATTERN = Pattern.compile("^.*;charset=(.*)$");

    protected final String                     tenantId;

    private Handler<Void>                      drainHandler;
    private BiConsumer<Object, ProtonDelivery> dispositionHandler = (messageId, delivery) -> LOG.trace("delivery state updated [message ID: {}, new remote state: {}]", messageId, delivery.getRemoteState());

    AbstractSender(final ProtonSender sender, final String tenantId, final Context context) {
        super(context);
        this.sender = Objects.requireNonNull(sender);
        this.tenantId = Objects.requireNonNull(tenantId);
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
                LOG.trace("sender has been replenished with {} credits", replenishedSender.getCredit());
                final Handler<Void> currentHandler = this.drainHandler;
                this.drainHandler = null;
                if (currentHandler != null) {
                    currentHandler.handle(null);
                }
            });
        }
    }

    @Override
    public void close(final Handler<AsyncResult<Void>> closeHandler) {
        Objects.requireNonNull(closeHandler);
        LOG.info("closing sender ...");
        closeLinks(closeHandler);
    }

    @Override
    public boolean isOpen() {
        return sender.isOpen();
    }

    @Override
    public void setErrorHandler(final Handler<AsyncResult<Void>> errorHandler) {
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
    public void setDispositionHandler(final BiConsumer<Object, ProtonDelivery> dispositionHandler) {
        this.dispositionHandler = dispositionHandler;
    }

    @Override
    public void send(final Message rawMessage, final Handler<Void> capacityAvailableHandler) {
        Objects.requireNonNull(rawMessage);
        if (capacityAvailableHandler == null) {
            context.runOnContext(send -> {
                sendMessage(rawMessage);
            });
        } else if (this.drainHandler != null) {
            throw new IllegalStateException("cannot send message while waiting for replenishment with credit");
        } else if (sender.isOpen()) {
            context.runOnContext(send -> {
                sendMessage(rawMessage);
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
        Objects.requireNonNull(rawMessage);
        if (sender.sendQueueFull()) {
            return false;
        } else {
            context.runOnContext(send -> {
                sendMessage(rawMessage);
            });
            return true;
        }
    }

    private void sendMessage(final Message rawMessage) {
        sender.send(rawMessage, deliveryUpdated -> dispositionHandler.accept(rawMessage.getMessageId(), deliveryUpdated));
    }

    @Override
    public boolean send(final String deviceId, final byte[] payload, final String contentType) {
        return send(deviceId, null, payload, contentType);
    }

    @Override
    public void send(final String deviceId, final byte[] payload, final String contentType, final Handler<Void> capacityAvailableHandler) {
        send(deviceId, null, payload, contentType, capacityAvailableHandler);
    }

    @Override
    public boolean send(final String deviceId, final String payload, final String contentType) {
        return send(deviceId, null, payload, contentType);
    }

    @Override
    public void send(final String deviceId, final String payload, final String contentType, final Handler<Void> capacityAvailableHandler) {
        send(deviceId, null, payload, contentType, capacityAvailableHandler);
    }

    @Override
    public boolean send(final String deviceId, final Map<String, ?> properties, final String payload, final String contentType) {
        Objects.requireNonNull(payload);
        final Charset charset = getCharsetForContentType(Objects.requireNonNull(contentType));
        return send(deviceId, properties, payload.getBytes(charset), contentType);
    }

    @Override
    public boolean send(final String deviceId, final Map<String, ?> properties, final byte[] payload, final String contentType) {
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(payload);
        Objects.requireNonNull(contentType);
        final Message msg = ProtonHelper.message();
        msg.setBody(new Data(new Binary(payload)));
        setApplicationProperties(msg, properties);
        addProperties(msg, deviceId, contentType);
        return send(msg);
    }

    @Override
    public void send(final String deviceId, final Map<String, ?> properties, final String payload, final String contentType, final Handler<Void> capacityAvailableHandler) {
        Objects.requireNonNull(payload);
        final Charset charset = getCharsetForContentType(Objects.requireNonNull(contentType));
        send(deviceId, properties, payload.getBytes(charset), contentType, capacityAvailableHandler);
    }

    @Override
    public void send(final String deviceId, final Map<String, ?> properties, final byte[] payload, final String contentType, final Handler<Void> capacityAvailableHandler) {
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(payload);
        Objects.requireNonNull(contentType);
        final Message msg = ProtonHelper.message();
        msg.setAddress(getTo(deviceId));
        msg.setBody(new Data(new Binary(payload)));
        setApplicationProperties(msg, properties);
        addProperties(msg, deviceId, contentType);
        addEndpointSpecificProperties(msg, deviceId);
        send(msg, capacityAvailableHandler);
    }

    /**
     * Gets the value of the <em>to</em> property to be used for messages produced by this sender.
     * 
     * @param deviceId The identifier of the device that the message's content originates from.
     * @return The address.
     */
    protected abstract String getTo(final String deviceId);

    private void addProperties(final Message msg, final String deviceId, final String contentType) {
        msg.setMessageId(String.format("%s-%d", getClass().getSimpleName(), MESSAGE_COUNTER.getAndIncrement()));
        msg.setContentType(contentType);
        addDeviceId(msg, deviceId);
    }

    /**
     * Sets additional properties on the message to be sent.
     * <p>
     * Subclasses should override this method to set any properties on messages
     * that are specific to the particular endpoint the message is to be sent to.
     * <p>
     * This method does nothing by default.
     * 
     * @param msg The message to be sent.
     * @param deviceId The ID of the device that the message's content originates from.
     */
    protected void addEndpointSpecificProperties(final Message msg, final String deviceId) {
        // empty
    }

    private void setApplicationProperties(final Message msg, final Map<String, ?> properties) {
        if (properties != null) {

            // check the three types not allowed by AMQP 1.0 spec for application properties (list, map and array)
            for (final Map.Entry<String, ?> entry: properties.entrySet()) {
                if (entry.getValue() instanceof  List) {
                    throw new IllegalArgumentException(String.format("Application property %s can't be a List", entry.getKey()));
                } else if (entry.getValue() instanceof Map) {
                    throw new IllegalArgumentException(String.format("Application property %s can't be a Map", entry.getKey()));
                } else if (entry.getValue().getClass().isArray()) {
                    throw new IllegalArgumentException(String.format("Application property %s can't be an Array", entry.getKey()));
                }
            }

            final ApplicationProperties applicationProperties = new ApplicationProperties(properties);
            msg.setApplicationProperties(applicationProperties);
        }
    }

    private Charset getCharsetForContentType(final String contentType) {

        final Matcher m = CHARSET_PATTERN.matcher(contentType);
        if (m.matches()) {
            return Charset.forName(m.group(1));
        } else {
            return StandardCharsets.UTF_8;
        }
    }
}
