/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.impl;

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.HonoProtonHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.opentracing.tag.Tags;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonLink;
import io.vertx.proton.ProtonMessageHandler;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * A base class for implementing Hono API clients.
 * <p>
 * Holds a sender and a receiver to an AMQP 1.0 server and provides
 * support for closing these links gracefully.
 */
public abstract class AbstractHonoClient {

    /**
     * The key under which the link status is stored in the link's attachments.
     */
    protected static final String KEY_LINK_ESTABLISHED = "linkEstablished";

    private static final Logger LOG = LoggerFactory.getLogger(AbstractHonoClient.class);

    /**
     * The vertx context to run all interactions with the server on.
     */
    protected final Context                context;
    /**
     * The configuration properties for this client.
     */
    protected final ClientConfigProperties config;

    /**
     * The vertx-proton object used for sending messages to the server.
     */
    protected ProtonSender   sender;
    /**
     * The vertx-proton object used for receiving messages from the server.
     */
    protected ProtonReceiver receiver;
    /**
     * The capabilities offered by the peer.
     */
    protected List<Symbol> offeredCapabilities = Collections.emptyList();
    /**
     * The <em>OpenTracing</em> tracer to use for tracking request processing
     * across process boundaries.
     */
    protected Tracer tracer;

    /**
     * Creates a client for a vert.x context.
     * 
     * @param context The context to run all interactions with the server on.
     * @param config The configuration properties to use.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected AbstractHonoClient(final Context context, final ClientConfigProperties config) {
        this(context, config, null);
    }

    /**
     * Creates a client for a vert.x context.
     * 
     * @param context The context to run all interactions with the server on.
     * @param config The configuration properties to use.
     * @param tracer The tracer to use for tracking request processing
     *               across process boundaries.
     * @throws NullPointerException if context or config are {@code null}.
     */
    protected AbstractHonoClient(final Context context, final ClientConfigProperties config, final Tracer tracer) {
        this.context = Objects.requireNonNull(context);
        this.config = Objects.requireNonNull(config);
        this.tracer = Optional.ofNullable(tracer).orElse(NoopTracerFactory.create());
    }

    /**
     * Executes some code on the vert.x Context that has been used to establish the
     * connection to the peer.
     * 
     * @param <T> The type of the result that the code produces.
     * @param codeToRun The code to execute. The code is required to either complete or
     *                  fail the future that is passed into the handler.
     * @return The future passed into the handler for executing the code. The future
     *         thus indicates the outcome of executing the code.
     */
    protected final <T> Future<T> executeOrRunOnContext(
            final Handler<Future<T>> codeToRun) {

        return HonoProtonHelper.executeOrRunOnContext(context, codeToRun);
    }

    /**
     * Marks an <em>OpenTracing</em> span as erroneous and logs an exception.
     * <p>
     * This method does <em>not</em> finish the span.
     * 
     * @param span The span to mark.
     * @param error The exception that has occurred. If the exception is a
     *              {@link ServiceInvocationException} then a {@link Tags#HTTP_STATUS}
     *              tag is added containing the exception's error code property value.
     * @throws NullPointerException if error is {@code null}.
     */
    protected final void logError(final Span span, final Throwable error) {
        if (span != null) {
            if (ServiceInvocationException.class.isInstance(error)) {
                final ServiceInvocationException e = (ServiceInvocationException) error;
                Tags.HTTP_STATUS.set(span, e.getErrorCode());
            }
            TracingHelper.logError(span, error);
        }
    }

    /**
     * Creates a new <em>OpenTracing</em> span for tracing the execution of a service invocation.
     * <p>
     * The returned span will already contain the following tags:
     * <ul>
     * <li>{@link Tags#COMPONENT} - set to <em>hono-client</em></li>
     * <li>{@link Tags#PEER_HOSTNAME} - set to {@link ClientConfigProperties#getHost()}</li>
     * <li>{@link Tags#PEER_PORT} - set to {@link ClientConfigProperties#getPort()}</li>
     * </ul>
     * 
     * @param parent The existing span. If not {@code null} then the new span will have a
     *                     {@link References#CHILD_OF} reference to the existing span.
     * @param operationName The operation name that the span should be created for.
     * @return The new span.
     */
    protected final Span newChildSpan(final SpanContext parent, final String operationName) {

        return newSpan(parent, References.CHILD_OF, operationName);
    }

    /**
     * Creates a new <em>OpenTracing</em> span for tracing the execution of a service invocation.
     * <p>
     * The returned span will already contain the following tags:
     * <ul>
     * <li>{@link Tags#COMPONENT} - set to <em>hono-client</em></li>
     * <li>{@link Tags#PEER_HOSTNAME} - set to {@link ClientConfigProperties#getHost()}</li>
     * <li>{@link Tags#PEER_PORT} - set to {@link ClientConfigProperties#getPort()}</li>
     * </ul>
     * 
     * @param parent The existing span. If not {@code null} then the new span will have a
     *                     {@link References#FOLLOWS_FROM} reference to the existing span.
     * @param operationName The operation name that the span should be created for.
     * @return The new span.
     */
    protected final Span newFollowingSpan(final SpanContext parent, final String operationName) {

        return newSpan(parent, References.FOLLOWS_FROM, operationName);
    }

    private Span newSpan(final SpanContext parent, final String referenceType, final String operationName) {

        return tracer.buildSpan(operationName)
                    .addReference(referenceType, parent)
                    .withTag(Tags.COMPONENT.getKey(), "hono-client")
                    .withTag(Tags.PEER_HOSTNAME.getKey(), config.getHost())
                    .withTag(Tags.PEER_PORT.getKey(), config.getPort())
                    .start();
    }

    /**
     * Checks if this client supports a certain capability.
     * <p>
     * The result of this method should only be considered reliable
     * if this client is open.
     * 
     * @param capability The capability to check support for.
     * @return {@code true} if the capability is included in the list of
     *         capabilities that the peer has offered during link
     *         establishment, {@code false} otherwise.
     */
    public final boolean supportsCapability(final Symbol capability) {
        if (capability == null) {
            return false;
        } else {
            return offeredCapabilities.contains(capability);
        }
    }

    /**
     * Closes this client's sender and receiver links to Hono.
     * Link resources will be freed after the links are closed.
     *
     * @param closeHandler the handler to be notified about the outcome.
     * @throws NullPointerException if the given handler is {@code null}.
     */
    protected final void closeLinks(final Handler<AsyncResult<Void>> closeHandler) {

        Objects.requireNonNull(closeHandler);

        final Future<Void> res = executeOrRunOnContext(result -> {

            final Future<Void> senderCloseHandler = Future.future();
            if (sender == null) {
                senderCloseHandler.complete();
            } else {
                final Handler<AsyncResult<ProtonSender>> wrappedHandler = HonoProtonHelper.setCloseHandler(sender, closeAttempt -> {
                    LOG.debug("closed message sender for [{}]", sender.getTarget().getAddress());
                    senderCloseHandler.complete();
                });
                if (sender.isOpen()) {
                    // wait for peer's detach frame to trigger the handler
                    sender.close();
                } else {
                    // trigger handler manually to make sure that
                    // resources are freed up
                    wrappedHandler.handle(Future.succeededFuture());
                }
            }

            senderCloseHandler.compose(closedSender -> {

                if (receiver == null) {
                    result.complete((Void) null);
                } else {
                    final Handler<AsyncResult<ProtonReceiver>> wrappedHandler = HonoProtonHelper.setCloseHandler(receiver, closeAttempt -> {
                        LOG.debug("closed message consumer for [{}]", receiver.getSource().getAddress());
                        result.complete((Void) null);
                    });
                    if (receiver.isOpen()) {
                        // wait for peer's detach frame to trigger the handler
                        receiver.close();
                    } else {
                        // trigger handler manually to make sure that
                        // resources are freed up
                        wrappedHandler.handle(Future.succeededFuture());
                    }
                }

            }, result);
        });
        res.setHandler(closeHandler);
    }

    /**
     * Set the application properties for a Proton Message but do a check for all properties first if they only contain
     * values that the AMQP 1.0 spec allows.
     *
     * @param msg The Proton message. Must not be null.
     * @param properties The map containing application properties.
     * @throws NullPointerException if the message passed in is null.
     * @throws IllegalArgumentException if the properties contain any value that AMQP 1.0 disallows.
     */
    protected static final void setApplicationProperties(final Message msg, final Map<String, ?> properties) {
        if (properties != null) {
            final Map<String, Object> propsToAdd = new HashMap<>();
            // check the three types not allowed by AMQP 1.0 spec for application properties (list, map and array)
            for (final Map.Entry<String, ?> entry: properties.entrySet()) {
                if (entry.getValue() != null) {
                    if (entry.getValue() instanceof List) {
                        throw new IllegalArgumentException(String.format("Application property %s can't be a List", entry.getKey()));
                    } else if (entry.getValue() instanceof Map) {
                        throw new IllegalArgumentException(String.format("Application property %s can't be a Map", entry.getKey()));
                    } else if (entry.getValue().getClass().isArray()) {
                        throw new IllegalArgumentException(String.format("Application property %s can't be an Array", entry.getKey()));
                    }
                }
                propsToAdd.put(entry.getKey(), entry.getValue());
            }

            final ApplicationProperties applicationProperties = new ApplicationProperties(propsToAdd);
            msg.setApplicationProperties(applicationProperties);
        }
    }

    /**
     * Creates a sender link.
     * 
     * @param ctx The vert.x context to use for establishing the link.
     * @param clientConfig The configuration properties to use.
     * @param con The connection to create the link for.
     * @param targetAddress The target address of the link.
     * @param qos The quality of service to use for the link.
     * @param closeHook The handler to invoke when the link is closed by the peer (may be {@code null}).
     * @return A future for the created link. The future will be completed once the link is open.
     *         The future will fail with a {@link ServiceInvocationException} if the link cannot be opened.
     * @throws NullPointerException if any of the arguments other than close hook is {@code null}.
     */
    protected static final Future<ProtonSender> createSender(
            final Context ctx,
            final ClientConfigProperties clientConfig,
            final ProtonConnection con,
            final String targetAddress,
            final ProtonQoS qos,
            final Handler<String> closeHook) {

        Objects.requireNonNull(ctx);
        Objects.requireNonNull(clientConfig);
        Objects.requireNonNull(con);
        Objects.requireNonNull(targetAddress);
        Objects.requireNonNull(qos);

        return HonoProtonHelper.executeOrRunOnContext(ctx, result -> {

            final ProtonSender sender = con.createSender(targetAddress);
            sender.attachments().set(KEY_LINK_ESTABLISHED, Boolean.class, Boolean.FALSE);
            sender.setQoS(qos);
            sender.setAutoSettle(true);
            sender.openHandler(senderOpen -> {
                if (senderOpen.succeeded()) {
                    LOG.debug("sender open [target: {}, sendQueueFull: {}]", targetAddress, sender.sendQueueFull());
                    sender.attachments().set(KEY_LINK_ESTABLISHED, Boolean.class, Boolean.TRUE);
                    // wait on credits a little time, if not already given
                    if (sender.sendQueueFull()) {
                        ctx.owner().setTimer(clientConfig.getFlowLatency(), timerID -> {
                            LOG.debug("sender [target: {}] has {} credits after grace period of {}ms", targetAddress,
                                    sender.getCredit(), clientConfig.getFlowLatency());
                            result.complete(sender);
                        });
                    } else {
                        result.complete(sender);
                    }
                } else {
                    final ErrorCondition error = sender.getRemoteCondition();
                    if (error == null) {
                        LOG.debug("opening sender [{}] failed", targetAddress, senderOpen.cause());
                        result.fail(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND, "cannot open sender", senderOpen.cause()));
                    } else {
                        LOG.debug("opening sender [{}] failed: {} - {}", targetAddress, error.getCondition(), error.getDescription());
                        result.fail(StatusCodeMapper.from(error));
                    }
                }
            });
            HonoProtonHelper.setDetachHandler(sender, remoteDetached -> onRemoteDetach(sender, con.getRemoteContainer(), false, closeHook));
            HonoProtonHelper.setCloseHandler(sender, remoteClosed -> onRemoteDetach(sender, con.getRemoteContainer(), true, closeHook));
            sender.open();
        });
    }

    /**
     * Creates a receiver link.
     * <p>
     * The receiver will be created with its <em>autoAccept</em> property set to {@code true}.
     *
     * @param ctx The vert.x context to use for establishing the link.
     * @param clientConfig The configuration properties to use.
     * @param con The connection to create the link for.
     * @param sourceAddress The address to receive messages from.
     * @param qos The quality of service to use for the link.
     * @param messageHandler The handler to invoke with every message received.
     * @param closeHook The handler to invoke when the link is closed by the peer (may be {@code null}).
     * @return A future for the created link. The future will be completed once the link is open.
     *         The future will fail with a {@link ServiceInvocationException} if the link cannot be opened.
     * @throws NullPointerException if any of the arguments other than close hook is {@code null}.
     */
    protected static final Future<ProtonReceiver> createReceiver(
            final Context ctx,
            final ClientConfigProperties clientConfig,
            final ProtonConnection con,
            final String sourceAddress,
            final ProtonQoS qos,
            final ProtonMessageHandler messageHandler,
            final Handler<String> closeHook) {

        Objects.requireNonNull(ctx);
        Objects.requireNonNull(clientConfig);
        Objects.requireNonNull(con);
        Objects.requireNonNull(sourceAddress);
        Objects.requireNonNull(qos);
        Objects.requireNonNull(messageHandler);

        return HonoProtonHelper.executeOrRunOnContext(ctx, result -> {
            final ProtonReceiver receiver = con.createReceiver(sourceAddress);
            receiver.attachments().set(KEY_LINK_ESTABLISHED, Boolean.class, Boolean.FALSE);
            receiver.setAutoAccept(true);
            receiver.setQoS(qos);
            receiver.setPrefetch(clientConfig.getInitialCredits());
            receiver.handler((delivery, message) -> {
                messageHandler.handle(delivery, message);
                if (LOG.isTraceEnabled()) {
                    final int remainingCredits = receiver.getCredit() - receiver.getQueued();
                    LOG.trace("handling message [remotely settled: {}, queued messages: {}, remaining credit: {}]",
                            delivery.remotelySettled(), receiver.getQueued(), remainingCredits);
                }
            });
            receiver.openHandler(recvOpen -> {
                if (recvOpen.succeeded()) {
                    LOG.debug("receiver open [source: {}]", sourceAddress);
                    receiver.attachments().set(KEY_LINK_ESTABLISHED, Boolean.class, Boolean.TRUE);
                    result.complete(recvOpen.result());
                } else {
                    final ErrorCondition error = receiver.getRemoteCondition();
                    if (error == null) {
                        LOG.debug("opening receiver [{}] failed", sourceAddress, recvOpen.cause());
                        result.fail(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND, "cannot open receiver", recvOpen.cause()));
                    } else {
                        LOG.debug("opening receiver [{}] failed: {} - {}", sourceAddress, error.getCondition(), error.getDescription());
                        result.fail(StatusCodeMapper.from(error));
                    }
                }
            });
            HonoProtonHelper.setDetachHandler(receiver, remoteDetached -> onRemoteDetach(receiver, con.getRemoteContainer(), false, closeHook));
            HonoProtonHelper.setCloseHandler(receiver, remoteClosed -> onRemoteDetach(receiver, con.getRemoteContainer(), true, closeHook));
            receiver.open();
        });
    }

    private static void onRemoteDetach(
            final ProtonLink<?> link,
            final String remoteContainer,
            final boolean closed,
            final Handler<String> closeHook) {

        final ErrorCondition error = link.getRemoteCondition();
        final String type = link instanceof ProtonSender ? "sender" : "receiver";
        final String address = link instanceof ProtonSender ? link.getTarget().getAddress() :
            link.getSource().getAddress();
        if (error == null) {
            LOG.debug("{} [{}] detached (with closed={}) by peer [{}]",
                    type, address, closed, remoteContainer);
        } else {
            LOG.debug("{} [{}] detached (with closed={}) by peer [{}]: {} - {}",
                    type, address, closed, remoteContainer, error.getCondition(), error.getDescription());
        }
        link.close();
        final boolean linkEstablished = link.attachments().get(KEY_LINK_ESTABLISHED, Boolean.class);
        if (linkEstablished && closeHook != null) {
            closeHook.handle(address);
        }
    }
}
