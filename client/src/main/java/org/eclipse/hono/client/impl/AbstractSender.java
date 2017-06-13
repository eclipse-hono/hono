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

import static org.eclipse.hono.util.MessageHelper.*;

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
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * A Vertx-Proton based client for publishing messages to a Hono server.
 */
abstract class AbstractSender extends AbstractHonoClient implements MessageSender {

    private static final Logger                LOG = LoggerFactory.getLogger(AbstractSender.class);
    private static final AtomicLong            MESSAGE_COUNTER = new AtomicLong();
    private static final Pattern               CHARSET_PATTERN = Pattern.compile("^.*;charset=(.*)$");
    private static final BiConsumer<Object, ProtonDelivery> DEFAULT_DISPOSITION_HANDLER = (messageId, delivery) -> LOG.trace("delivery state updated [message ID: {}, new remote state: {}]", messageId, delivery.getRemoteState());

    protected final String                     tenantId;
    protected final String                     targetAddress;

    private final Handler<String>              closeHook;
    private Handler<Void>                      drainHandler;
    private BiConsumer<Object, ProtonDelivery> defaultDispositionHandler = DEFAULT_DISPOSITION_HANDLER;

    AbstractSender(final ProtonSender sender, final String tenantId, final String targetAddress,
            final Context context, final Handler<String> closeHook) {
        super(context);
        this.sender = Objects.requireNonNull(sender);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.targetAddress = targetAddress;
        this.closeHook = closeHook;
    }

    @Override
    public final int getCredit() {
        if (sender == null) {
            return 0;
        } else {
            return sender.getCredit();
        }
    }

    @Override
    public final boolean sendQueueFull() {
        return sender.sendQueueFull();
    }

    @Override
    public final void sendQueueDrainHandler(final Handler<Void> handler) {
        if (this.drainHandler != null) {
            throw new IllegalStateException("already waiting for replenishment with credit");
        } else {
            this.drainHandler = Objects.requireNonNull(handler);
            sender.sendQueueDrainHandler(replenishedSender -> {
                LOG.trace("sender has received FLOW [credits: {}, queued:{}]", replenishedSender.getCredit(), replenishedSender.getQueued());
                final Handler<Void> currentHandler = this.drainHandler;
                this.drainHandler = null;
                if (currentHandler != null) {
                    currentHandler.handle(null);
                }
            });
        }
    }

    @Override
    public final void close(final Handler<AsyncResult<Void>> closeHandler) {
        Objects.requireNonNull(closeHandler);
        LOG.info("closing sender ...");
        closeLinks(closeHandler);
    }

    @Override
    public final boolean isOpen() {
        return sender.isOpen();
    }

    @Override
    public final void setErrorHandler(final Handler<AsyncResult<Void>> errorHandler) {

        sender.closeHandler(s -> {
            if (s.failed()) {
                LOG.debug("server closed link with error condition: {}", s.cause().getMessage());
                sender.close();
                if (closeHook != null) {
                    closeHook.handle(targetAddress);
                }
                errorHandler.handle(Future.failedFuture(s.cause()));
            } else {
                LOG.debug("server closed link");
                sender.close();
                if (closeHook != null) {
                    closeHook.handle(targetAddress);
                }
            }
        });
    }

    @Override
    public final void setDefaultDispositionHandler(final BiConsumer<Object, ProtonDelivery> dispositionHandler) {
        this.defaultDispositionHandler = dispositionHandler;
    }

    @Override
    public final void send(final Message rawMessage, final Handler<Void> capacityAvailableHandler, final BiConsumer<Object, ProtonDelivery> dispositionHandler) {
        Objects.requireNonNull(rawMessage);
        Objects.requireNonNull(dispositionHandler);
        if (capacityAvailableHandler == null) {
            context.runOnContext(send -> {
                sendMessage(rawMessage, dispositionHandler);
            });
        } else if (this.drainHandler != null) {
            throw new IllegalStateException("cannot send message while waiting for replenishment with credit");
        } else if (sender.isOpen()) {
            context.runOnContext(send -> {
                sendMessage(rawMessage, dispositionHandler);
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
    public final boolean send(final Message rawMessage, final BiConsumer<Object, ProtonDelivery> dispositionHandler) {
        Objects.requireNonNull(rawMessage);
        Objects.requireNonNull(dispositionHandler);
        if (sender.sendQueueFull()) {
            return false;
        } else {
            context.runOnContext(send -> {
                sendMessage(rawMessage, dispositionHandler);
            });
            return true;
        }
    }

    private void sendMessage(final Message rawMessage, final BiConsumer<Object, ProtonDelivery> dispositionHandler) {
        sender.send(rawMessage, deliveryUpdated -> dispositionHandler.accept(rawMessage.getMessageId(), deliveryUpdated));
        LOG.trace("sent message, remaining credit: {}, queued messages: {}", sender.getCredit(), sender.getQueued());
    }

    @Override
    public final void send(final Message rawMessage, final Handler<Void> capacityAvailableHandler) {
        send(rawMessage, capacityAvailableHandler, this.defaultDispositionHandler);
    }

    @Override
    public final boolean send(final Message rawMessage) {
        return send(rawMessage, this.defaultDispositionHandler);
    }

    @Override
    public final boolean send(final String deviceId, final byte[] payload, final String contentType, final String registrationAssertion) {
        return send(deviceId, null, payload, contentType, registrationAssertion);
    }

    @Override
    public final boolean send(final String deviceId, final byte[] payload, final String contentType, final String registrationAssertion,
            final BiConsumer<Object, ProtonDelivery> dispositionHandler) {
        return send(deviceId, null, payload, contentType, registrationAssertion, dispositionHandler);
    }

    @Override
    public final void send(final String deviceId, final byte[] payload, final String contentType, final String registrationAssertion,
            final Handler<Void> capacityAvailableHandler) {
        send(deviceId, null, payload, contentType, registrationAssertion, capacityAvailableHandler);
    }

    @Override
    public final boolean send(final String deviceId, final String payload, final String contentType, final String registrationAssertion) {
        return send(deviceId, null, payload, contentType, registrationAssertion);
    }

    @Override
    public final boolean send(final String deviceId, final String payload, final String contentType, final String registrationAssertion,
            final BiConsumer<Object, ProtonDelivery> dispositionHandler) {
        return send(deviceId, null, payload, contentType, registrationAssertion, dispositionHandler);
    }

    @Override
    public final void send(final String deviceId, final String payload, final String contentType, final String registrationAssertion,
            final Handler<Void> capacityAvailableHandler) {
        send(deviceId, null, payload, contentType, registrationAssertion, capacityAvailableHandler);
    }

    @Override
    public final boolean send(final String deviceId, final Map<String, ?> properties, final String payload, final String contentType,
            final String registrationAssertion) {
        Objects.requireNonNull(payload);
        final Charset charset = getCharsetForContentType(Objects.requireNonNull(contentType));
        return send(deviceId, properties, payload.getBytes(charset), contentType, registrationAssertion);
    }

    @Override
    public final boolean send(final String deviceId, final Map<String, ?> properties, final String payload, final String contentType,
                              final String registrationAssertion, final BiConsumer<Object, ProtonDelivery> dispositionHandler) {
        Objects.requireNonNull(payload);
        final Charset charset = getCharsetForContentType(Objects.requireNonNull(contentType));
        return send(deviceId, properties, payload.getBytes(charset), contentType, registrationAssertion, dispositionHandler);
    }

    @Override
    public final boolean send(final String deviceId, final Map<String, ?> properties, final byte[] payload, final String contentType,
            final String registrationAssertion) {
        return send(deviceId, properties, payload, contentType, registrationAssertion, this.defaultDispositionHandler);
    }

    @Override
    public final boolean send(final String deviceId, final Map<String, ?> properties, final byte[] payload, final String contentType,
                              final String registrationAssertion, final BiConsumer<Object, ProtonDelivery> dispositionHandler) {
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(payload);
        Objects.requireNonNull(contentType);
        Objects.requireNonNull(registrationAssertion);
        Objects.requireNonNull(dispositionHandler);
        final Message msg = ProtonHelper.message();
        msg.setBody(new Data(new Binary(payload)));
        setApplicationProperties(msg, properties);
        addProperties(msg, deviceId, contentType, registrationAssertion);
        return send(msg, dispositionHandler);
    }

    @Override
    public final void send(final String deviceId, final Map<String, ?> properties, final String payload, final String contentType,
            final String registrationAssertion, final Handler<Void> capacityAvailableHandler) {
        Objects.requireNonNull(payload);
        final Charset charset = getCharsetForContentType(Objects.requireNonNull(contentType));
        send(deviceId, properties, payload.getBytes(charset), contentType, registrationAssertion, capacityAvailableHandler);
    }

    @Override
    public final void send(final String deviceId, final Map<String, ?> properties, final byte[] payload, final String contentType,
            final String registrationAssertion, final Handler<Void> capacityAvailableHandler) {
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(payload);
        Objects.requireNonNull(contentType);
        Objects.requireNonNull(registrationAssertion);
        final Message msg = ProtonHelper.message();
        msg.setAddress(getTo(deviceId));
        msg.setBody(new Data(new Binary(payload)));
        setApplicationProperties(msg, properties);
        addProperties(msg, deviceId, contentType, registrationAssertion);
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

    private void addProperties(final Message msg, final String deviceId, final String contentType, final String registrationAssertion) {
        msg.setMessageId(String.format("%s-%d", getClass().getSimpleName(), MESSAGE_COUNTER.getAndIncrement()));
        msg.setContentType(contentType);
        addDeviceId(msg, deviceId);
        addRegistrationAssertion(msg, registrationAssertion);
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

    private Charset getCharsetForContentType(final String contentType) {

        final Matcher m = CHARSET_PATTERN.matcher(contentType);
        if (m.matches()) {
            return Charset.forName(m.group(1));
        } else {
            return StandardCharsets.UTF_8;
        }
    }

    /**
     * Creates a sender link.
     * 
     * @param ctx The vertx context to use for establishing the link.
     * @param con The connection to create the link for.
     * @param targetAddress The target address of the link.
     * @param qos The quality of service to use for the link.
     * @param closeHook The handler to invoke when the link is closed by the peer.
     * @return A future for the created link.
     */
    protected static final Future<ProtonSender> createSender(
            final Context ctx,
            final ProtonConnection con,
            final String targetAddress,
            final ProtonQoS qos,
            final Handler<String> closeHook) {

        final Future<ProtonSender> result = Future.future();

        ctx.runOnContext(create -> {
            final ProtonSender sender = con.createSender(targetAddress);
            sender.setQoS(qos);
            sender.openHandler(senderOpen -> {
                if (senderOpen.succeeded()) {
                    LOG.debug("sender open [{}]", sender.getRemoteTarget());
                    result.complete(senderOpen.result());
                } else {
                    LOG.debug("opening sender [{}] failed: {}", targetAddress, senderOpen.cause().getMessage());
                    result.fail(senderOpen.cause());
                }
            }).closeHandler(senderClosed -> {
                if (senderClosed.succeeded()) {
                    LOG.debug("sender [{}] closed", targetAddress);
                } else {
                    LOG.debug("sender [{}] closed: {}", targetAddress, senderClosed.cause().getMessage());
                }
                sender.close();
                if (closeHook != null) {
                    closeHook.handle(targetAddress);
                }
            }).open();
        });

        return result;
    }
}
