/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
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

import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.MessageHelper;
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

    private static final Pattern CHARSET_PATTERN = Pattern.compile("^.*;charset=(.*)$");

    /**
     * A logger to be shared with subclasses.
     */
    protected final Logger LOG = LoggerFactory.getLogger(getClass());

    protected final String tenantId;
    protected final String targetAddress;

    private Handler<Void> drainHandler;

    AbstractSender(final ClientConfigProperties config, final ProtonSender sender, final String tenantId, final String targetAddress,
            final Context context) {
        super(context, config);
        this.sender = Objects.requireNonNull(sender);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.targetAddress = targetAddress;
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
    public final Future<ProtonDelivery> send(final Message rawMessage, final Handler<Void> capacityAvailableHandler) {

        Objects.requireNonNull(rawMessage);

        if (capacityAvailableHandler == null) {
            final Future<ProtonDelivery> result = Future.future();
            context.runOnContext(send -> {
                sendMessage(rawMessage).setHandler(result.completer());
            });
            return result;
        } else if (this.drainHandler != null) {
            throw new IllegalStateException("cannot send message while waiting for replenishment with credit");
        } else if (sender.isOpen()) {
            final Future<ProtonDelivery> result = Future.future();
            context.runOnContext(send -> {
                sendMessage(rawMessage).setHandler(result.completer());
                if (sender.sendQueueFull()) {
                    sendQueueDrainHandler(capacityAvailableHandler);
                } else {
                    capacityAvailableHandler.handle(null);
                }
            });
            return result;
        } else {
            throw new IllegalStateException("sender is not open");
        }
    }

    @Override
    public final Future<ProtonDelivery> send(final Message rawMessage) {

        Objects.requireNonNull(rawMessage);

        final Future<ProtonDelivery> result = Future.future();
        context.runOnContext(send -> {
            if (sender.sendQueueFull()) {
                result.fail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "no credit available"));
            } else {
                sendMessage(rawMessage).setHandler(result.completer());
            }
        });
        return result;
    }

    @Override
    public final Future<ProtonDelivery> send(final String deviceId, final byte[] payload, final String contentType, final String registrationAssertion) {
        return send(deviceId, null, payload, contentType, registrationAssertion);
    }

    @Override
    public final Future<ProtonDelivery> send(final String deviceId, final byte[] payload, final String contentType, final String registrationAssertion,
            final Handler<Void> capacityAvailableHandler) {
        return send(deviceId, null, payload, contentType, registrationAssertion, capacityAvailableHandler);
    }

    @Override
    public final Future<ProtonDelivery> send(final String deviceId, final String payload, final String contentType, final String registrationAssertion) {
        return send(deviceId, null, payload, contentType, registrationAssertion);
    }

    @Override
    public final Future<ProtonDelivery> send(final String deviceId, final String payload, final String contentType, final String registrationAssertion,
            final Handler<Void> capacityAvailableHandler) {
        return send(deviceId, null, payload, contentType, registrationAssertion, capacityAvailableHandler);
    }

    @Override
    public final Future<ProtonDelivery> send(final String deviceId, final Map<String, ?> properties, final String payload, final String contentType,
            final String registrationAssertion) {
        Objects.requireNonNull(payload);
        final Charset charset = getCharsetForContentType(Objects.requireNonNull(contentType));
        return send(deviceId, properties, payload.getBytes(charset), contentType, registrationAssertion);
    }

    @Override
    public final Future<ProtonDelivery> send(final String deviceId, final Map<String, ?> properties, final byte[] payload, final String contentType,
                              final String registrationAssertion) {
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
        return send(msg);
    }

    @Override
    public final Future<ProtonDelivery> send(String deviceId, Map<String, ?> properties, String payload, String contentType,
            String registrationAssertion, Handler<Void> capacityAvailableHandler) {
        Objects.requireNonNull(payload);
        final Charset charset = getCharsetForContentType(Objects.requireNonNull(contentType));
        return send(deviceId, properties, payload.getBytes(charset), contentType, registrationAssertion, capacityAvailableHandler);
    }

    @Override
    public final Future<ProtonDelivery> send(String deviceId, Map<String, ?> properties, byte[] payload, String contentType,
            String registrationAssertion, Handler<Void> capacityAvailableHandler) {

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
        return send(msg, capacityAvailableHandler);
    }

    /**
     * Sends an AMQP 1.0 message to the peer this client is configured for.
     * <p>
     * The message is sent according to the delivery semantics defined by
     * the Hono API this client interacts with.
     * 
     * @param message The message to send.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the message has been sent to the endpoint.
     *         The delivery contained in the future represents the delivery state at the time
     *         the future has been succeeded, i.e. for telemetry data it will be locally
     *         <em>unsettled</em> without any outcome yet. For events it will be locally
     *         and remotely <em>settled</em> and will contain the <em>accepted</em> outcome.
     *         <p>
     *         The future will be failed with a {@link ServiceInvocationException} if the
     *         message could not be sent.
     * @throws NullPointerException if the message is {@code null}.
     */
    protected abstract Future<ProtonDelivery> sendMessage(final Message message);

    /**
     * Gets the value of the <em>to</em> property to be used for messages produced by this sender.
     * 
     * @param deviceId The identifier of the device that the message's content originates from.
     * @return The address.
     */
    protected abstract String getTo(final String deviceId);

    private void addProperties(final Message msg, final String deviceId, final String contentType, final String registrationAssertion) {
        msg.setContentType(contentType);
        MessageHelper.addDeviceId(msg, deviceId);
        MessageHelper.addRegistrationAssertion(msg, registrationAssertion);
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
}
