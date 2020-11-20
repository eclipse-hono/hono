/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.client.command.amqp;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.hono.adapter.client.command.Command;
import org.eclipse.hono.tracing.TracingHelper;

import io.opentracing.Span;
import io.opentracing.log.Fields;
import io.vertx.core.buffer.Buffer;

/**
 * A wrapper around a legacy {@link org.eclipse.hono.client.Command}.
 *
 */
public final class ProtonBasedCommand implements Command {

    private final org.eclipse.hono.client.Command command;

    /**
     * Creates a new wrapper around a command.
     *
     * @param command The command to wrap.
     * @throws NullPointerException if the command is {@code null}.
     */
    public ProtonBasedCommand(final org.eclipse.hono.client.Command command) {
        this.command = Objects.requireNonNull(command);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isOneWay() {
        return command.isOneWay();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isValid() {
        return command.isValid();
    }

    /**
     * {@inheritDoc}}
     */
    @Override
    public String getInvalidCommandReason() {
        return command.getInvalidCommandReason();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return command.getName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getTenant() {
        return command.getTenant();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDeviceId() {
        return command.getDeviceId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isTargetedAtGateway() {
        return command.isTargetedAtGateway();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getOriginalDeviceId() {
        return command.getOriginalDeviceId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getRequestId() {
        return command.getRequestId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Buffer getPayload() {
        return command.getPayload();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getPayloadSize() {
        return command.getPayloadSize();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getContentType() {
        return command.getContentType();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getReplyToId() {
        return command.getReplyToId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCorrelationId() {
        return command.getCorrelationId();
    }

    @Override
    public String toString() {
        return command.toString();
    }

    /**
     * Logs information about the command.
     *
     * @param span The span to log to.
     * @throws NullPointerException if span is {@code null}.
     */
    public void logToSpan(final Span span) {
        Objects.requireNonNull(span);
        if (command.isValid()) {
            final Map<String, String> items = new HashMap<>(4);
            items.put(Fields.EVENT, "received command message");
            TracingHelper.TAG_CORRELATION_ID.set(span, command.getCorrelationId());
            items.put("to", command.getCommandMessage().getAddress());
            items.put("reply-to", command.getCommandMessage().getReplyTo());
            items.put("name", command.getName());
            items.put("content-type", command.getContentType());
            span.log(items);
        } else {
            TracingHelper.logError(span, "received invalid command message [" + command + "]");
        }
    }
}
