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
package org.eclipse.hono.service.command;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.hono.client.impl.AbstractConsumer;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.tracing.MessageAnnotationsExtractAdapter;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.log.Fields;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;

/**
 * A wrapper around an AMQP receiver link for consuming commands.
 */
public class CommandConsumer extends AbstractConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(CommandConsumer.class);
    /**
     * A handler that may be called when the consumer is considered to be recreated (e.g. after a connection loss). Must be explicitly set, the default value is {@code null}.
     */
    private Handler<ProtonConnection> recreateHandler;
    private Boolean markedForRecreation = false;

    private CommandConsumer(
            final Context context,
            final ClientConfigProperties config,
            final ProtonReceiver protonReceiver,
            final Tracer tracer) {

        super(context, config, protonReceiver, tracer);
    }

    /**
     * Call the recreate handler of the command consumer, if the receiver link is found to be closed and if no other call to this method is currently processed.
     *
     * @param con The connection for which the link is recreated.
     */
    public final void recreate(final ProtonConnection con) {
        if (!markedForRecreation) {
            markedForRecreation = true;

            Optional.ofNullable(this.recreateHandler).map(rh -> {
                context.owner().setTimer(Constants.DEFAULT_RECONNECT_INTERVAL_MILLIS, id -> {
                    try {
                        if (!this.receiver.isOpen()) {
                            LOG.debug("found receiver link closed for link {}, now recreating it.",
                                    this.receiver.getSource().getAddress());
                            this.recreateHandler.handle(con);
                        }
                    } finally {
                        // by all means reset the recreation flag
                        markedForRecreation = false;
                    }
                });
                LOG.debug("recreate done for link {}.", this.receiver.getSource().getAddress());
                return rh;
            }).orElseGet(() -> {
                LOG.trace("no recreate handler found for link {}.", this.receiver.getSource().getAddress());
                markedForRecreation = false;
                return null;
            });
        } else {
            LOG.trace("recreate receiver link already in progress - ignoring: {}", this.receiver.getSource().getAddress());
        }
    }

    /**
     * Creates a new command consumer.
     * <p>
     * The underlying receiver link will be created with the following properties:
     * <ul>
     * <li><em>auto accept</em> will be set to {@code true}</li>
     * <li><em>pre-fetch size</em> will be set to {@code 0} to enforce manual flow control.
     * However, the sender will be issued one credit on link establishment.</li>
     * </ul>
     *
     * @param context The vert.x context to run all interactions with the server on.
     * @param clientConfig The configuration properties to use.
     * @param con The AMQP connection to the server.
     * @param tenantId The tenant to consume commands from.
     * @param deviceId The device for which the commands should be consumed.
     * @param commandHandler The handler to invoke for each command received.
     * @param receiverCloseHook A handler to invoke if the peer closes the receiver link unexpectedly.
     * @param creationHandler The handler to invoke with the outcome of the creation attempt.
     * @param tracer The tracer to use for tracking the processing of received
     *               messages. If {@code null}, OpenTracing's {@code NoopTracer} will
     *               be used.
     * @throws NullPointerException if any of the parameters other than tracer are {@code null}.
     */
    public static final void create(
            final Context context,
            final ClientConfigProperties clientConfig,
            final ProtonConnection con,
            final String tenantId,
            final String deviceId,
            final Handler<CommandContext> commandHandler,
            final Handler<String> receiverCloseHook,
            final Handler<AsyncResult<CommandConsumer>> creationHandler,
            final Tracer tracer) {

        Objects.requireNonNull(context);
        Objects.requireNonNull(clientConfig);
        Objects.requireNonNull(con);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(commandHandler);
        Objects.requireNonNull(receiverCloseHook);
        Objects.requireNonNull(creationHandler);

        LOG.trace("creating new command consumer [tenant-id: {}, device-id: {}]", tenantId, deviceId);

        final String address = ResourceIdentifier.from(CommandConstants.COMMAND_ENDPOINT, tenantId, deviceId).toString();
        final ClientConfigProperties props = new ClientConfigProperties(clientConfig);
        props.setInitialCredits(0);

        final AtomicReference<ProtonReceiver> receiverRef = new AtomicReference<>();

        createReceiver(
                context,
                props,
                con,
                address,
                ProtonQoS.AT_LEAST_ONCE,
                (delivery, msg) -> {

                    final Command command = Command.from(msg, tenantId, deviceId);

                    // try to extract Span context from incoming message
                    final SpanContext spanContext = tracer.extract(Format.Builtin.TEXT_MAP, new MessageAnnotationsExtractAdapter(msg));
                    // start a Span to use for tracing the delivery of the command to the device
                    // we set the component tag to the class name because we have no access to
                    // the name of the enclosing component we are running in
                    final Span currentSpan = tracer.buildSpan("send command")
                            .addReference(References.CHILD_OF, spanContext)
                            .ignoreActiveSpan()
                            .withTag(Tags.COMPONENT.getKey(), CommandConsumer.class.getSimpleName())
                            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CONSUMER)
                            .withTag(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId)
                            .withTag(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId)
                            .start();

                    final Map<String, String> items = new HashMap<>(3);
                    items.put(Fields.EVENT, "received command message");
                    if (command.isValid()) {
                        currentSpan.setTag(Constants.HEADER_COMMAND_REQUEST_ID, command.getRequestId());
                        items.put("name", command.getName());
                        items.put("content-type", command.getContentType());
                    }
                    currentSpan.log(items);
                    try {
                        commandHandler.handle(CommandContext.from(command, delivery, receiverRef.get(), currentSpan));
                    } finally {
                        currentSpan.finish();
                    }
                },
                receiverCloseHook).map(receiver -> {
                    LOG.debug("successfully created command consumer [tenant-id: {}, device-id: {}]", tenantId, deviceId);
                    receiverRef.set(receiver);
                    receiver.flow(1); // allow sender to sender one command
                    final CommandConsumer commandConsumer = new CommandConsumer(context, props, receiver, tracer);
                    commandConsumer.setRecreateHandler(newConnection -> {
                        LOG.debug("Recreating command receiver link : [tenant-id: {}, device-id: {}]", tenantId, deviceId);

                        create(context,  clientConfig,
                                newConnection,
                                tenantId,
                                deviceId,
                                commandHandler,
                                receiverCloseHook,
                                creation -> {
                                    if (creation.succeeded()) {
                                        LOG.debug("Successfully recreated command consumer  [tenant-id: {}, device-id: {}]", tenantId, deviceId);
                                    }
                                },
                                tracer);
                    });
                    LOG.trace("recreate handler set for command consumer [tenant-id: {}, device-id: {}]", tenantId, deviceId);
                    creationHandler.handle(Future.succeededFuture(commandConsumer));
                    return receiver;
            }).recover(t -> {
                LOG.debug("failed to create command consumer [tenant-id: {}, device-id: {}]", tenantId, deviceId, t);
                creationHandler.handle(Future.failedFuture(t));
                return null;
            });
    }

    protected final void setRecreateHandler(final Handler<ProtonConnection> recreateHandler) {
        this.recreateHandler = recreateHandler;
    }
}
