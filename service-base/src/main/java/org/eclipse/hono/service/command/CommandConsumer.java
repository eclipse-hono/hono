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
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.hono.client.MessageConsumer;
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

    private CommandConsumer(
            final Context context,
            final ClientConfigProperties config,
            final ProtonReceiver protonReceiver,
            final Tracer tracer) {

        super(context, config, protonReceiver, tracer);
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
            final Handler<AsyncResult<MessageConsumer>> creationHandler,
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
                receiverCloseHook).setHandler(s -> {

                    if (s.succeeded()) {
                        final ProtonReceiver receiver = s.result();
                        LOG.debug("successfully created command consumer [tenant-id: {}, device-id: {}]", tenantId, deviceId);
                        receiverRef.set(receiver);
                        receiver.flow(1); // allow sender to sender one command
                        creationHandler.handle(Future.succeededFuture(new CommandConsumer(context, props, receiver, tracer)));
                    } else {
                        LOG.debug("failed to create command consumer [tenant-id: {}, device-id: {}]", tenantId, deviceId, s.cause());
                        creationHandler.handle(Future.failedFuture(s.cause()));
                    }
                });
    }
}
