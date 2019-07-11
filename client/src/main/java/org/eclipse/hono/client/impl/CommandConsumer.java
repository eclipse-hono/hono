/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.hono.client.Command;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.MessageHelper;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.log.Fields;
import io.opentracing.tag.Tags;
import io.vertx.proton.ProtonReceiver;

/**
 * A wrapper around an AMQP receiver link for consuming commands.
 */
public abstract class CommandConsumer extends AbstractConsumer {

    /**
     * Creates a consumer for a connection and a receiver link.
     *
     * @param connection The connection to the AMQP Messaging Network over which
     *                   commands are received.
     * @param receiver The receiver link for command messages.
     */
    protected CommandConsumer(final HonoConnection connection, final ProtonReceiver receiver) {
        super(connection, receiver);
    }

    /**
     * Creates and starts an <em>OpenTracing</em> span for a CommandConsumer operation.
     *
     * @param operationName The name of the operation.
     * @param tenantId The tenant identifier.
     * @param deviceId The device identifier.
     * @param tracer The tracer instance.
     * @param spanContext Existing span context.
     * @return The created and started span.
     */
    public static Span createSpan(final String operationName, final String tenantId,
            final String deviceId, final Tracer tracer, final SpanContext spanContext) {
        // we set the component tag to the class name because we have no access to
        // the name of the enclosing component we are running in
        return TracingHelper.buildChildSpan(tracer, spanContext, operationName)
                .ignoreActiveSpan()
                .withTag(Tags.COMPONENT.getKey(), CommandConsumer.class.getSimpleName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CONSUMER)
                .withTag(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId)
                .withTag(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId)
                .start();
    }

    /**
     * Logs information about the given command to the given span.
     *
     * @param command The command to log.
     * @param span The span to log to.
     */
    public static void logReceivedCommandToSpan(final Command command, final Span span) {
        if (command.isValid()) {
            final Map<String, String> items = new HashMap<>(4);
            items.put(Fields.EVENT, "received command message");
            TracingHelper.TAG_CORRELATION_ID.set(span, command.getCorrelationId());
            items.put("reply-to", command.getCommandMessage().getReplyTo());
            items.put("name", command.getName());
            items.put("content-type", command.getContentType());
            span.log(items);
        } else {
            TracingHelper.logError(span, "received invalid command message [" + command + "]");
        }
    }
}
