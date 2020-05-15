/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.sdk.gateway.mqtt2amqp;

import java.util.Objects;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.util.MessageHelper;

import io.vertx.core.buffer.Buffer;

/**
 * A dictionary of data for the processing of a command message received from Hono's AMQP adapter.
 */
public class MqttCommandContext {

    private final Message message;
    private final Device authenticatedDevice;

    private MqttCommandContext(final Message message, final Device authenticatedDevice) {
        this.message = message;
        this.authenticatedDevice = authenticatedDevice;
    }

    /**
     * Creates a new context for a command message.
     *
     * @param message The received command message.
     * @param authenticatedDevice The authenticated device identity.
     * @return The context.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static MqttCommandContext fromAmqpMessage(final Message message, final Device authenticatedDevice) {
        Objects.requireNonNull(message);
        Objects.requireNonNull(authenticatedDevice);

        return new MqttCommandContext(message, authenticatedDevice);
    }

    /**
     * Gets the identity of the device to which the command is addressed to.
     *
     * @return The authenticated device.
     */
    public Device getDevice() {
        return authenticatedDevice;
    }

    /**
     * Indicates if the message represents a request/response or an one-way command.
     * 
     * @return {@code true} if a response is expected.
     */
    public boolean isRequestResponseCommand() {
        return message.getReplyTo() != null;
    }

    /**
     * Returns the subject of the command message.
     * 
     * @return The subject.
     */
    public String getSubject() {
        return message.getSubject();
    }

    /**
     * Returns the content type of the payload if this information is available from the message.
     * 
     * @return The content type or {@code null}.
     */
    public String getContentType() {
        return message.getContentType();
    }

    /**
     * Returns the correlation id of the message.
     * 
     * @return The correlation id.
     */
    public Object getCorrelationId() {
        return message.getCorrelationId();
    }

    /**
     * Returns the message id of the message.
     *
     * @return The message id.
     */
    public Object getMessageId() {
        return message.getMessageId();
    }

    /**
     * Returns the reply-to address of the message.
     *
     * @return The reply-to address.
     */
    public String getReplyTo() {
        return message.getReplyTo();
    }

    /**
     * Returns the application properties of the message.
     *
     * @return The application properties.
     */
    public ApplicationProperties getApplicationProperties() {
        return message.getApplicationProperties();
    }

    /**
     * Returns the received payload.
     * 
     * @return The payload - not {@code null}.
     */
    public Buffer getPayload() {
        final Buffer payload = MessageHelper.getPayload(message);
        if (payload != null) {
            return payload;
        } else {
            return Buffer.buffer();
        }
    }
}
