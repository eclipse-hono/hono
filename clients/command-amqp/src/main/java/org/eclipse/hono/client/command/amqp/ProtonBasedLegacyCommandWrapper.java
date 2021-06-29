/*******************************************************************************
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.command.amqp;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.hono.client.command.Command;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.log.Fields;
import io.vertx.core.buffer.Buffer;

/**
 * A wrapper around a legacy {@link org.eclipse.hono.client.Command}.
 *
 */
public final class ProtonBasedLegacyCommandWrapper implements Command {

    private static final Logger LOG = LoggerFactory.getLogger(ProtonBasedLegacyCommandWrapper.class);

    private final org.eclipse.hono.client.Command command;

    /**
     * Creates a new command as a wrapper around a legacy command.
     *
     * @param command The command to wrap.
     * @throws NullPointerException if the command is {@code null}.
     */
    public ProtonBasedLegacyCommandWrapper(final org.eclipse.hono.client.Command command) {
        this.command = Objects.requireNonNull(command);
        if (Strings.isNullOrEmpty(command.getTenant()) || Strings.isNullOrEmpty(command.getOriginalDeviceId())) {
            LOG.warn("given command expected to have a valid tenant/device id: {}", command);
        }
    }

    @Override
    public MessagingType getMessagingType() {
        return MessagingType.amqp;
    }

    @Override
    public boolean isOneWay() {
        return command.isOneWay();
    }

    @Override
    public boolean isValid() {
        return command.isValid();
    }

    @Override
    public String getInvalidCommandReason() {
        return command.getInvalidCommandReason();
    }

    @Override
    public String getTenant() {
        return command.getTenant();
    }

    @Override
    public String getGatewayOrDeviceId() {
        // the legacy command class associates getDeviceId() with the gateway or, if not set, original command target device.
        return command.getDeviceId();
    }

    @Override
    public boolean isTargetedAtGateway() {
        return command.isTargetedAtGateway();
    }

    @Override
    public String getDeviceId() {
        return command.getOriginalDeviceId();
    }

    @Override
    public String getGatewayId() {
        return isTargetedAtGateway() ? getGatewayOrDeviceId() : null;
    }

    @Override
    public void setGatewayId(final String gatewayId) {
        command.setGatewayId(gatewayId);
    }

    @Override
    public String getName() {
        return command.getName();
    }

    @Override
    public String getRequestId() {
        return command.getRequestId();
    }

    @Override
    public Buffer getPayload() {
        return command.getPayload();
    }

    @Override
    public int getPayloadSize() {
        return command.getPayloadSize();
    }

    @Override
    public String getContentType() {
        return command.getContentType();
    }

    @Override
    public String getReplyToId() {
        return command.getReplyToId();
    }

    @Override
    public String getCorrelationId() {
        return command.getCorrelationId();
    }

    @Override
    public String toString() {
        return command.toString();
    }

    @Override
    public void logToSpan(final Span span) {
        Objects.requireNonNull(span);
        if (command.isValid()) {
            TracingHelper.TAG_CORRELATION_ID.set(span, command.getCorrelationId());
            final Map<String, String> items = new HashMap<>(5);
            items.put(Fields.EVENT, "received command message");
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
