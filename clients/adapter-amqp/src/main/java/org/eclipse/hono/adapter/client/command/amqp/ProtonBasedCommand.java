/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.adapter.client.command.Command;
import org.eclipse.hono.tracing.TracingHelper;

import io.opentracing.Span;
import io.opentracing.log.Fields;
import io.vertx.core.buffer.Buffer;

/**
 * A command used in a vertx-proton based client.
 *
 */
public final class ProtonBasedCommand implements Command {

    private final org.eclipse.hono.client.Command command;

    /**
     * Creates a new command as a wrapper around a legacy command.
     *
     * @param command The command to wrap.
     * @throws NullPointerException if the command is {@code null}.
     */
    public ProtonBasedCommand(final org.eclipse.hono.client.Command command) {
        this.command = Objects.requireNonNull(command);
    }

    /**
     * Creates a command for an AMQP 1.0 message that should be sent to a device.
     * <p>
     * The message is expected to contain
     * <ul>
     * <li>a non-null <em>address</em>, containing a matching tenant part and a non-empty device-id part</li>
     * <li>a non-null <em>subject</em></li>
     * <li>either a null <em>reply-to</em> address (for a one-way command)
     * or a non-null <em>reply-to</em> address that matches the tenant and device IDs and consists
     * of four segments</li>
     * <li>a String valued <em>correlation-id</em> and/or <em>message-id</em></li>
     * </ul>
     * <p>
     * If any of the requirements above are not met, then the returned command's {@link #isValid()}
     * method will return {@code false}.
     * <p>
     * Note that, if set, the <em>reply-to</em> address of the given message will be adapted, making sure it contains
     * the device id.
     *
     * @param message The message containing the command.
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device that the command will be sent to. If the command has been mapped
     *                 to a gateway, this id is the gateway id and the original command target device is given in
     *                 the message address.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public ProtonBasedCommand(final Message message, final String tenantId, final String deviceId) {
        this.command = org.eclipse.hono.client.Command.from(message, tenantId, deviceId);
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
    public String getGatewayOrDeviceId() {
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
    public String getDeviceId() {
        return command.getOriginalDeviceId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getGatewayId() {
        if (!isValid()) {
            throw new IllegalStateException("command is invalid");
        }
        return isTargetedAtGateway() ? getGatewayOrDeviceId() : null;
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

    @Override
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
