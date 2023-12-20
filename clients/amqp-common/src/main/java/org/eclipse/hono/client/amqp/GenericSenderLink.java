/*******************************************************************************
 * Copyright (c) 2016 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.client.amqp;

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Modified;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.MessageNotProcessedException;
import org.eclipse.hono.client.MessageUndeliverableException;
import org.eclipse.hono.client.NoConsumerException;
import org.eclipse.hono.client.SendMessageTimeoutException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.amqp.config.AddressHelper;
import org.eclipse.hono.client.amqp.config.ClientConfigProperties;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.client.amqp.connection.ErrorConverter;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.connection.HonoProtonHelper;
import org.eclipse.hono.client.amqp.connection.SendMessageSampler;
import org.eclipse.hono.tracing.TracingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.log.Fields;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * A wrapper around a vertx-proton based AMQP sender link.
 * <p>
 * Exposes methods for sending messages using {@linkplain #send(Message, Span) AT_MOST_ONCE}
 * and {@linkplain #sendAndWaitForOutcome(Message, Span) AT_LEAST_ONCE} delivery semantics.
 */
public class GenericSenderLink extends AbstractHonoClient {

    private static final AtomicLong MESSAGE_COUNTER = new AtomicLong();
    private static final Logger log = LoggerFactory.getLogger(GenericSenderLink.class);

    /**
     * Associated tenant identifier. May be {@code null} if the link is independent of a particular tenant.
     */
    private final String tenantId;
    private final String targetAddress;
    private final SendMessageSampler sampler;

    private boolean errorInfoLoggingEnabled;

    /**
     * Creates a new sender.
     *
     * @param connection The connection to use for interacting with the server.
     * @param sender The sender link to send messages over.
     * @param tenantId The identifier of the tenant that the
     *           devices belong to which have published the messages
     *           that this sender is used to send downstream.
     * @param sampler The sampler for sending messages.
     * @param targetAddress The target address to send the messages to.
     * @throws NullPointerException if connection, sender or sampler is {@code null}.
     */
    protected GenericSenderLink(
            final HonoConnection connection,
            final ProtonSender sender,
            final String tenantId,
            final String targetAddress,
            final SendMessageSampler sampler) {

        super(connection);
        this.sender = Objects.requireNonNull(sender);
        this.tenantId = tenantId;
        this.targetAddress = targetAddress;
        this.sampler = Objects.requireNonNull(sampler);
        if (sender.isOpen()) {
            this.offeredCapabilities = Optional.ofNullable(sender.getRemoteOfferedCapabilities())
                    .map(List::of)
                    .orElse(Collections.emptyList());
        }
    }

    /**
     * Creates a new AMQP sender link for publishing messages on the anonymous link address.
     * <p>
     * This method is to be used for creating links that are independent of a particular tenant.
     *
     * @param con The connection to the Hono server.
     * @param remoteCloseHook The handler to invoke when the link is closed by the peer (may be {@code null}). The
     *            sender's target address is provided as an argument to the handler.
     * @return A future indicating the outcome.
     * @throws NullPointerException if any of the parameters except remoteCloseHook is {@code null}.
     */
    public static Future<GenericSenderLink> create(
            final HonoConnection con,
            final Handler<String> remoteCloseHook) {

        Objects.requireNonNull(con);

        return con.createSender(null, ProtonQoS.AT_LEAST_ONCE, remoteCloseHook)
                .map(sender -> new GenericSenderLink(con, sender, null, null, SendMessageSampler.noop()));
    }

    /**
     * Creates a new AMQP sender link for publishing messages to Hono's south bound API endpoints.
     * <p>
     * This method is to be used for creating links that are independent of a particular tenant.
     *
     * @param con The connection to the Hono server.
     * @param address The target address of the link.
     * @param remoteCloseHook The handler to invoke when the link is closed by the peer (may be {@code null}). The
     *            sender's target address is provided as an argument to the handler.
     * @return A future indicating the outcome.
     * @throws NullPointerException if any of the parameters except remoteCloseHook is {@code null}.
     */
    public static Future<GenericSenderLink> create(
            final HonoConnection con,
            final String address,
            final Handler<String> remoteCloseHook) {

        Objects.requireNonNull(con);
        Objects.requireNonNull(address);

        final String targetAddress = AddressHelper.rewrite(address, con.getConfig());
        return con.createSender(targetAddress, ProtonQoS.AT_LEAST_ONCE, remoteCloseHook)
                .map(sender -> new GenericSenderLink(con, sender, null, targetAddress, SendMessageSampler.noop()));
    }

    /**
     * Creates a new AMQP sender link for publishing messages to Hono's south bound API endpoints.
     *
     * @param con The connection to the Hono server.
     * @param endpoint The endpoint to send messages to.
     * @param tenantId The tenant that the events will be published for.
     * @param sampler The sampler to use.
     * @param remoteCloseHook The handler to invoke when the link is closed by the peer (may be {@code null}). The
     *            sender's target address is provided as an argument to the handler.
     * @return A future indicating the outcome.
     * @throws NullPointerException if any of the parameters except remoteCloseHook is {@code null}.
     */
    public static Future<GenericSenderLink> create(
            final HonoConnection con,
            final String endpoint,
            final String tenantId,
            final SendMessageSampler sampler,
            final Handler<String> remoteCloseHook) {

        Objects.requireNonNull(con);
        Objects.requireNonNull(endpoint);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(sampler);

        return create(con, endpoint, tenantId, null, sampler, remoteCloseHook);
    }

    /**
     * Creates a new AMQP sender link for publishing messages to Hono's south bound API endpoints.
     *
     * @param con The connection to the Hono server.
     * @param endpoint The endpoint to send messages to.
     * @param tenantId The tenant that the messages' origin device belongs to.
     * @param resourceId The resource ID to include in a downstream message's address (may be {@code null}).
     * @param sampler The sampler to use.
     * @param remoteCloseHook The handler to invoke when the link is closed by the peer (may be {@code null}). The
     *            sender's target address is provided as an argument to the handler.
     * @return A future indicating the outcome.
     * @throws NullPointerException if any of the parameters except remoteCloseHook and resource ID are {@code null}.
     */
    public static Future<GenericSenderLink> create(
            final HonoConnection con,
            final String endpoint,
            final String tenantId,
            final String resourceId,
            final SendMessageSampler sampler,
            final Handler<String> remoteCloseHook) {

        Objects.requireNonNull(con);
        Objects.requireNonNull(endpoint);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(sampler);

        final String targetAddress = AddressHelper.getTargetAddress(endpoint, tenantId, resourceId, con.getConfig());
        return con.createSender(targetAddress, ProtonQoS.AT_LEAST_ONCE, remoteCloseHook)
                .map(sender -> new GenericSenderLink(con, sender, tenantId, targetAddress, sampler));
    }

    /**
     * Closes the link.
     *
     * @return A succeeded future indicating that the link has been closed.
     */
    public final Future<Void> close() {
        log.debug("closing sender ...");
        return closeLinks();
    }

    /**
     * Checks if the wrapped sender link is open.
     *
     * @return {@code true} if the link is open and can be used to send messages.
     */
    public final boolean isOpen() {
        return HonoProtonHelper.isLinkOpenAndConnected(sender);
    }

    /**
     * Sends an AMQP 1.0 message to the endpoint configured for this link.
     *
     * @param message The message to send.
     * @param currentSpan The <em>OpenTracing</em> span used to trace the sending of the message.
     *              The span will be finished by this method and will contain an error log if
     *              the message has not been accepted by the peer.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the message has been sent to the endpoint.
     *         The delivery contained in the future represents the delivery state at the time
     *         the future has been succeeded, i.e. for telemetry data it will be locally
     *         <em>unsettled</em> without any outcome yet. For events it will be locally
     *         and remotely <em>settled</em> and will contain the <em>accepted</em> outcome.
     *         <p>
     *         The future will be failed with a {@link ServerErrorException} if the message
     *         could not be sent due to a lack of credit.
     *         If a message is sent which cannot be processed by the peer, the future will
     *         be failed with either a {@link ServerErrorException} or a {@link ClientErrorException}
     *         depending on the reason for the failure to process the message.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public final Future<ProtonDelivery> send(final Message message, final Span currentSpan) {

        return checkForCreditAndSend(message, currentSpan, () -> sendMessage(message, currentSpan));
    }

    /**
     * Sends an AMQP 1.0 message to the endpoint configured for this link and waits for the
     * disposition indicating the outcome of the transfer.
     * <p>
     * A not-accepted outcome will cause the returned future to be failed.
     *
     * @param message The message to send.
     * @param currentSpan The <em>OpenTracing</em> span used to trace the sending of the message.
     *              The span will be finished by this method and will contain an error log if
     *              the message has not been accepted by the peer.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the message has been accepted (and settled)
     *         by the peer.
     *         <p>
     *         The future will be failed with a {@link ServerErrorException} if the message
     *         could not be sent due to a lack of credit.
     *         If a message is sent which cannot be processed by the peer, the future will
     *         be failed with either a {@link ServerErrorException} or a {@link ClientErrorException}
     *         depending on the reason for the failure to process the message.
     *         If no delivery update was received from the peer within the configured timeout period
     *         (see {@link ClientConfigProperties#getSendMessageTimeout()}), the future will
     *         be failed with a {@link ServerErrorException}.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public Future<ProtonDelivery> sendAndWaitForOutcome(final Message message, final Span currentSpan) {

        return checkForCreditAndSend(message, currentSpan,
                () -> sendMessageAndWaitForOutcome(message, currentSpan, true));
    }

    /**
     * Sends an AMQP 1.0 message to the endpoint configured for this link and waits for the
     * disposition indicating the outcome of the transfer.
     * <p>
     * A not-accepted outcome will be returned in a succeeded future result.
     *
     * @param message The message to send.
     * @param currentSpan The <em>OpenTracing</em> span used to trace the sending of the message.
     *              The span will be finished by this method and will contain an error log if
     *              the message has not been accepted by the peer.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the message has been settled by the peer.
     *         <p>
     *         The future will be failed with a {@link ServerErrorException} if the message
     *         could not be sent due to a lack of credit or if no delivery update was received
     *         from the peer within the configured timeout period
     *         (see {@link ClientConfigProperties#getSendMessageTimeout()}).
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public Future<ProtonDelivery> sendAndWaitForRawOutcome(final Message message, final Span currentSpan) {

        return checkForCreditAndSend(message, currentSpan,
                () -> sendMessageAndWaitForOutcome(message, currentSpan, false));
    }

    /**
     * Sets whether message sending errors should be logged on INFO level.
     *
     * @param errorInfoLoggingEnabled {@code true} if errors should be logged on INFO level.
     */
    public void setErrorInfoLoggingEnabled(final boolean errorInfoLoggingEnabled) {
        this.errorInfoLoggingEnabled = errorInfoLoggingEnabled;
    }

    private Future<ProtonDelivery> checkForCreditAndSend(
            final Message message,
            final Span currentSpan,
            final Supplier<Future<ProtonDelivery>> sendOperation) {

        Objects.requireNonNull(message);
        Objects.requireNonNull(currentSpan);
        Objects.requireNonNull(sendOperation);

        Tags.MESSAGE_BUS_DESTINATION.set(currentSpan, getMessageAddress(message));
        TracingHelper.TAG_QOS.set(currentSpan, sender.getQoS().toString());
        Tags.SPAN_KIND.set(currentSpan, Tags.SPAN_KIND_PRODUCER);
        TracingHelper.setDeviceTags(currentSpan, tenantId, AmqpUtils.getDeviceId(message));
        AmqpUtils.injectSpanContext(connection.getTracer(), currentSpan.context(), message,
                connection.getConfig().isUseLegacyTraceContextFormat());

        return connection.executeOnContext(result -> {
            if (sender.sendQueueFull()) {
                final ServerErrorException e = new NoConsumerException("no credit available");
                logMessageSendingError("error sending message [ID: {}, address: {}], no credit available (drain={})",
                        message.getMessageId(), getMessageAddress(message), sender.getDrain());
                TracingHelper.TAG_CREDIT.set(currentSpan, 0);
                logError(currentSpan, e);
                currentSpan.finish();
                result.fail(e);
                sampler.noCredit(tenantId);
            } else {
                sendOperation.get().onComplete(result);
            }
        });
    }

    /**
     * Sends an AMQP 1.0 message to the peer this client is configured for.
     *
     * @param message The message to send.
     * @param currentSpan The <em>OpenTracing</em> span used to trace the sending of the message.
     *              The span will be finished by this method and will contain an error log if
     *              the message has not been accepted by the peer.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the message has been sent to the peer.
     *         The delivery contained in the future will represent the delivery
     *         state at the time the future has been succeeded, i.e. it will be
     *         locally <em>unsettled</em> without any outcome yet.
     *         <p>
     *         The future will be failed with a {@link ServiceInvocationException} if the
     *         message could not be sent.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    private Future<ProtonDelivery> sendMessage(final Message message, final Span currentSpan) {

        Objects.requireNonNull(message);
        Objects.requireNonNull(currentSpan);

        final String messageId = String.format("%s-%d", getClass().getSimpleName(), MESSAGE_COUNTER.getAndIncrement());
        message.setMessageId(messageId);
        logMessageIdAndSenderInfo(currentSpan, messageId);

        final SendMessageSampler.Sample sample = sampler.start(tenantId);

        final AtomicReference<ProtonDelivery> deliveryRef = new AtomicReference<>();
        final ClientConfigProperties config = connection.getConfig();
        final AtomicBoolean timeoutReached = new AtomicBoolean(false);
        final Long timerId = config.getSendMessageTimeout() > 0
                ? connection.getVertx().setTimer(config.getSendMessageTimeout(), id -> {
                    if (timeoutReached.compareAndSet(false, true)) {
                        handleSendMessageTimeout(message, config.getSendMessageTimeout(), deliveryRef.get(), sample,
                                null, currentSpan);
                    }
                })
                : null;

        final ProtonDelivery delivery = sender.send(message, deliveryUpdated -> {
            if (timerId != null) {
                connection.getVertx().cancelTimer(timerId);
            }
            final DeliveryState remoteState = deliveryUpdated.getRemoteState();
            sample.completed(remoteState);
            if (timeoutReached.get()) {
                log.debug("ignoring received delivery update for message [ID: {}, address: {}]: waiting for the update has already timed out",
                        messageId, getMessageAddress(message));
            } else if (deliveryUpdated.remotelySettled()) {
                logUpdatedDeliveryState(currentSpan, message, deliveryUpdated);
            } else {
                logMessageSendingError("peer did not settle message [ID: {}, address: {}, remote state: {}], failing delivery",
                        messageId, getMessageAddress(message), remoteState.getClass().getSimpleName());
                TracingHelper.logError(currentSpan, new ServerErrorException(
                        HttpURLConnection.HTTP_INTERNAL_ERROR,
                        "peer did not settle message, failing delivery"));
            }
            currentSpan.finish();
        });
        deliveryRef.set(delivery);
        log.trace("sent AT_MOST_ONCE message [ID: {}, address: {}], remaining credit: {}, queued messages: {}",
                messageId, getMessageAddress(message), sender.getCredit(), sender.getQueued());

        return Future.succeededFuture(delivery);
    }

    private Future<ProtonDelivery> sendMessageAndWaitForOutcome(final Message message, final Span currentSpan,
            final boolean mapUnacceptedOutcomeToErrorResult) {

        Objects.requireNonNull(message);
        Objects.requireNonNull(currentSpan);

        final AtomicReference<ProtonDelivery> deliveryRef = new AtomicReference<>();
        final Promise<ProtonDelivery> result = Promise.promise();
        final String messageId = String.format("%s-%d", getClass().getSimpleName(), MESSAGE_COUNTER.getAndIncrement());
        message.setMessageId(messageId);
        logMessageIdAndSenderInfo(currentSpan, messageId);

        final SendMessageSampler.Sample sample = sampler.start(tenantId);

        final ClientConfigProperties config = connection.getConfig();
        final Long timerId = config.getSendMessageTimeout() > 0
                ? connection.getVertx().setTimer(config.getSendMessageTimeout(), id -> {
                    if (!result.future().isComplete()) {
                        handleSendMessageTimeout(message, config.getSendMessageTimeout(), deliveryRef.get(), sample,
                                result, null);
                    }
                })
                : null;

        deliveryRef.set(sender.send(message, deliveryUpdated -> {
            if (timerId != null) {
                connection.getVertx().cancelTimer(timerId);
            }
            final DeliveryState remoteState = deliveryUpdated.getRemoteState();
            if (result.future().isComplete()) {
                log.debug("ignoring received delivery update for message [ID: {}, address: {}]: waiting for the update has already timed out",
                        messageId, getMessageAddress(message));
            } else if (deliveryUpdated.remotelySettled()) {
                logUpdatedDeliveryState(currentSpan, message, deliveryUpdated);
                sample.completed(remoteState);
                if (Accepted.class.isInstance(remoteState)) {
                    result.complete(deliveryUpdated);
                } else {
                    if (mapUnacceptedOutcomeToErrorResult) {
                        result.handle(mapUnacceptedOutcomeToErrorResult(deliveryUpdated));
                    } else {
                        result.complete(deliveryUpdated);
                    }
                }
            } else {
                logMessageSendingError("peer did not settle message [ID: {}, address: {}, remote state: {}], failing delivery",
                        messageId, getMessageAddress(message), remoteState.getClass().getSimpleName());
                final ServiceInvocationException e = new ServerErrorException(
                        HttpURLConnection.HTTP_INTERNAL_ERROR,
                        "peer did not settle message, failing delivery");
                result.fail(e);
            }
        }));
        log.trace("sent AT_LEAST_ONCE message [ID: {}, address: {}], remaining credit: {}, queued messages: {}",
                messageId, getMessageAddress(message), sender.getCredit(), sender.getQueued());

        return result.future()
                .onSuccess(delivery -> Tags.HTTP_STATUS.set(currentSpan, HttpURLConnection.HTTP_ACCEPTED))
                .onFailure(t -> {
                    TracingHelper.logError(currentSpan, t);
                    Tags.HTTP_STATUS.set(currentSpan, ServiceInvocationException.extractStatusCode(t));
                })
                .onComplete(r -> currentSpan.finish());
    }

    /**
     * Handles a timeout waiting for a delivery update after sending a message.
     *
     * @param message The message for which the timeout occurred.
     * @param sendMessageTimeout The timeout value.
     * @param delivery The message delivery to release (may be {@code null}).
     * @param sample The sample to log a timeout on (may be {@code null}).
     * @param resultPromise The promise to fail with an exception (may be {@code null}).
     * @param spanToLogToAndFinish The span to log the error to and finish (may be {@code null})
     * @throws NullPointerException if message is {@code null}.
     */
    protected void handleSendMessageTimeout(
            final Message message,
            final long sendMessageTimeout,
            final ProtonDelivery delivery,
            final SendMessageSampler.Sample sample,
            final Promise<ProtonDelivery> resultPromise,
            final Span spanToLogToAndFinish) {
        Objects.requireNonNull(message);

        final String linkOrConnectionClosedInfo = HonoProtonHelper.isLinkOpenAndConnected(sender)
                ? "" : " (link or connection already closed)";
        final String exceptionMsg = "waiting for delivery update timed out after " + sendMessageTimeout + "ms";

        final ServerErrorException exception = HonoProtonHelper.isLinkOpenAndConnected(sender)
                ? new SendMessageTimeoutException(exceptionMsg)
                : new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, exceptionMsg + linkOrConnectionClosedInfo);
        logMessageSendingError("waiting for delivery update timed out for message [ID: {}, address: {}] after {}ms{}",
                message.getMessageId(), getMessageAddress(message), sendMessageTimeout, linkOrConnectionClosedInfo);
        // settle and release the delivery - this ensures that the message isn't considered "in flight"
        // anymore in the AMQP messaging network and that it doesn't count towards the link capacity
        // (it would be enough to just settle the delivery without an outcome but that cannot be done with proton-j as of now)
        if (delivery != null) {
            ProtonHelper.released(delivery, true);
        }
        if (spanToLogToAndFinish != null) {
            TracingHelper.logError(spanToLogToAndFinish, exception.getMessage());
            Tags.HTTP_STATUS.set(spanToLogToAndFinish, HttpURLConnection.HTTP_UNAVAILABLE);
            spanToLogToAndFinish.finish();
        }
        if (resultPromise != null) {
            resultPromise.fail(exception);
        }
        if (sample != null) {
            sample.timeout();
        }
    }

    private Future<ProtonDelivery> mapUnacceptedOutcomeToErrorResult(final ProtonDelivery delivery) {
        final DeliveryState remoteState = delivery.getRemoteState();
        if (remoteState instanceof Accepted) {
            throw new IllegalStateException("delivery is expected to be rejected, released or modified here, not accepted");
        }
        ServiceInvocationException e = null;
        if (remoteState instanceof Rejected rejected) {
            e = Optional.ofNullable(rejected.getError())
                    .map(ErrorConverter::fromTransferError)
                    .orElseGet(() -> new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else if (remoteState instanceof Released) {
            e = new MessageNotProcessedException();
        } else if (remoteState instanceof Modified modified) {
            if (modified.getUndeliverableHere()) {
                e = new MessageUndeliverableException();
            } else {
                e = new MessageNotProcessedException();
            }
        }
        return Future.failedFuture(e);
    }

    /**
     * Creates a log entry in the given span with the message id and information about the sender (credits, QOS).
     *
     * @param currentSpan The current span to log to.
     * @param messageId The message id.
     * @throws NullPointerException if currentSpan is {@code null}.
     */
    protected final void logMessageIdAndSenderInfo(final Span currentSpan, final String messageId) {
        final Map<String, Object> details = new HashMap<>(2);
        details.put(TracingHelper.TAG_MESSAGE_ID.getKey(), messageId);
        details.put(TracingHelper.TAG_CREDIT.getKey(), sender.getCredit());
        currentSpan.log(details);
    }

    /**
     * Creates a log entry in the given span with information about the message delivery outcome given in the delivery
     * parameter. Sets the {@link Tags#HTTP_STATUS} as well.
     * <p>
     * Also corresponding log output is created.
     *
     * @param currentSpan The current span to log to.
     * @param message The message.
     * @param delivery The updated delivery.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected final void logUpdatedDeliveryState(final Span currentSpan, final Message message, final ProtonDelivery delivery) {
        Objects.requireNonNull(currentSpan);
        final String messageId = message.getMessageId() != null ? message.getMessageId().toString() : "";
        final String messageAddress = getMessageAddress(message);
        final DeliveryState remoteState = delivery.getRemoteState();
        if (remoteState instanceof Accepted) {
            log.trace("message [ID: {}, address: {}] accepted by peer", messageId, messageAddress);
            currentSpan.log("message accepted by peer");
            Tags.HTTP_STATUS.set(currentSpan, HttpURLConnection.HTTP_ACCEPTED);
        } else {
            final Map<String, Object> events = new HashMap<>();
            if (remoteState instanceof Rejected rejected) {
                Tags.HTTP_STATUS.set(currentSpan, HttpURLConnection.HTTP_BAD_REQUEST);
                if (rejected.getError() == null) {
                    logMessageSendingError("message [ID: {}, address: {}] rejected by peer", messageId, messageAddress);
                    events.put(Fields.MESSAGE, "message rejected by peer");
                } else {
                    logMessageSendingError("message [ID: {}, address: {}] rejected by peer: {}, {}", messageId,
                            messageAddress, rejected.getError().getCondition(), rejected.getError().getDescription());
                    events.put(Fields.MESSAGE, String.format("message rejected by peer: %s, %s",
                            rejected.getError().getCondition(), rejected.getError().getDescription()));
                }
            } else if (remoteState instanceof Released) {
                logMessageSendingError("message [ID: {}, address: {}] not accepted by peer, remote state: {}",
                        messageId, messageAddress, remoteState.getClass().getSimpleName());
                Tags.HTTP_STATUS.set(currentSpan, HttpURLConnection.HTTP_UNAVAILABLE);
                events.put(Fields.MESSAGE, "message not accepted by peer, remote state: " + remoteState);
            } else if (remoteState instanceof Modified modified) {
                logMessageSendingError("message [ID: {}, address: {}] not accepted by peer, remote state: {}",
                        messageId, messageAddress, modified);
                Tags.HTTP_STATUS.set(currentSpan, modified.getUndeliverableHere() ? HttpURLConnection.HTTP_NOT_FOUND
                        : HttpURLConnection.HTTP_UNAVAILABLE);
                events.put(Fields.MESSAGE, "message not accepted by peer, remote state: " + remoteState);
            }
            TracingHelper.logError(currentSpan, events);
        }
    }

    /**
     * Gets the address that a message is targeted at.
     *
     * @param message The message.
     * @return The message's address or the targetAddress passed in to the constructor
     *         if the message has no address.
     * @throws NullPointerException if message is {@code null}.
     */
    private String getMessageAddress(final Message message) {
        Objects.requireNonNull(message);
        return Optional.ofNullable(message.getAddress()).orElse(targetAddress);
    }

    private void logMessageSendingError(final String format, final Object... arguments) {
        if (errorInfoLoggingEnabled) {
            log.info(format, arguments);
        } else {
            log.debug(format, arguments);
        }
    }
}
