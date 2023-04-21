/**
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.client.command;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.ExecutionContext;
import org.eclipse.hono.util.MessageHelper;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;

/**
 * A context for processing a command that is targeted at a device.
 *
 */
public interface CommandContext extends ExecutionContext {

    /**
     * The key under which the current CommandContext is stored in an ExecutionContext container.
     */
    String KEY_COMMAND_CONTEXT = "command-context";
    String KEY_TENANT_CONFIG = "tenant-config";

    /**
     * The outcome of an accepted command context.
     */
    String OUTCOME_ACCEPTED = "accepted";

    /**
     * The outcome of a released command context.
     */
    String OUTCOME_RELEASED = "released";

    /**
     * The outcome of a modified command context.
     */
    String OUTCOME_MODIFIED = "modified";

    /**
     * The outcome of a rejected command context.
     */
    String OUTCOME_REJECTED = "rejected";

    /**
     * Checks if the context has already been completed.
     *
     * @return {@code true} if the context has already been completed.
     */
    boolean isCompleted();

    /**
     * Logs information about the command.
     *
     * @param span The span to log to.
     * @throws NullPointerException if span is {@code null}.
     */
    void logCommandToSpan(Span span);

    /**
     * Gets the command to process.
     *
     * @return The command.
     */
    Command getCommand();

    /**
     * Indicates to the sender that the command message has been delivered to its target.
     */
    void accept();

    /**
     * Indicates to the sender that the command message could not be delivered to its target or could
     * not be processed by its target due to reasons that are not the responsibility of the sender
     * of the command.
     */
    default void release() {
        release(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE));
    }

    /**
     * Indicates to the sender that the command message could not be delivered to its target or could
     * not be processed by its target due to reasons that are not the responsibility of the sender
     * of the command.
     *
     * @param error The delivery error.
     * @throws NullPointerException if error is {@code null}.
     */
    void release(Throwable error);

    /**
     * Indicates to the sender that the command message could not be delivered to its target due to
     * reasons that are not the responsibility of the sender of the command.
     *
     * @param deliveryFailed {@code true} if the attempt to send the command to the target device has failed.
     * @param undeliverableHere {@code true} if the component processing the context has no access to the command's
     *            target device.
     */
    void modify(boolean deliveryFailed, boolean undeliverableHere);

    /**
     * Indicates to the sender that the command message cannot be delivered to or processed by its target due to
     * reasons that are the responsibility of the sender of the command.
     * <p>
     * The reason for a command being rejected often is that the command is invalid, e.g. lacking a
     * subject or having a malformed address.
     *
     * @param error The error that caused the command to be rejected or {@code null} if the cause is unknown.
     */
    default void reject(final String error) {
        reject(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, error));
    }

    /**
     * Indicates to the sender that the command message cannot be delivered to or processed by its target due to
     * reasons that are the responsibility of the sender of the command.
     * <p>
     * The reason for a command being rejected often is that the command is invalid, e.g. lacking a
     * subject or having a malformed address.
     *
     * @param error The error that caused the command to be rejected or {@code null} if the cause is unknown.
     */
    void reject(Throwable error);

    /**
     * Creates and starts an <em>OpenTracing</em> span for the command handling operation.
     *
     * @param tracer The tracer to use.
     * @param command The command for which the span should be started.
     * @param parentSpanContext The context of the span to reference as parent span.
     * @param followsFromSpanContext A span context for which to add a <em>follows-from</em> reference,
     *                               e.g. the span context for the operation to create the command consumer.
     * @param componentName The component to set for the span.
     * @return The created span.
     * @throws NullPointerException if tracer or command is {@code null}.
     */
    static Span createSpan(final Tracer tracer, final Command command, final SpanContext parentSpanContext,
            final SpanContext followsFromSpanContext, final String componentName) {
        Objects.requireNonNull(tracer);
        Objects.requireNonNull(command);
        // we set the component tag to the class name because we have no access to
        // the name of the enclosing component we are running in
        final Tracer.SpanBuilder spanBuilder = TracingHelper
                .buildChildSpan(tracer, parentSpanContext, "handle command", componentName)
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CONSUMER)
                .withTag(TracingHelper.TAG_TENANT_ID, command.getTenant())
                .withTag(TracingHelper.TAG_DEVICE_ID, command.getDeviceId());
        if (command.getGatewayId() != null) {
            spanBuilder.withTag(MessageHelper.APP_PROPERTY_GATEWAY_ID, command.getGatewayId());
        }
        spanBuilder.addReference(References.FOLLOWS_FROM, followsFromSpanContext);
        final Span currentSpan = spanBuilder.start();
        command.logToSpan(currentSpan);
        return currentSpan;
    }
}
