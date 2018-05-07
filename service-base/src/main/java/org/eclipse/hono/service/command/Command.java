/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.service.command;

import java.net.HttpURLConnection;
import java.util.Map;

import io.vertx.core.buffer.Buffer;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.MessageHelper;

import io.vertx.core.Handler;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;

/**
 * Represents a Command and Control API command on the receiver side (adapter).
 * <p>
 * Received command includes the message data and all application properties.
 */
public class Command {

    private Object correlationId;
    private String replyAddress;
    private String contentType;
    private Buffer messageData;
    private Map<String, Object> applicationProperties;
    private CommandResponder responder;

    Command(final CommandResponder responder, final Message message) {
        this.responder = responder;
        this.correlationId = message.getMessageId();
        this.replyAddress = message.getReplyTo();
        this.messageData = MessageHelper.getPayload(message);
        this.contentType = message.getContentType();
        ApplicationProperties applicationProperties = message.getApplicationProperties();
        if (applicationProperties != null) {
            this.applicationProperties = applicationProperties.getValue();
        }
    }

    void sendResponse(final Buffer data, final Map<String, Object> properties, final Handler<ProtonDelivery> update) {
        Message message = ProtonHelper.message();
        message.setCorrelationId(correlationId);
        if(data!=null) {
            message.setBody(new Data(new Binary(data.getBytes())));
        }
        if (properties != null) {
            message.setApplicationProperties(new ApplicationProperties(properties));
        }
        MessageHelper.addProperty(message, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        if(update!=null) {
            responder.getSender().send(message, update);
        }
        else {
            responder.getSender().send(message);
        }
    }

    CommandResponder getResponder() {
        return responder;
    }

    /**
     * Gets the reply address of the sender link.
     *
     * @return The reply address.
     */
    public String getReplyAddress() {
        return replyAddress;
    }

    /**
     * Gets the data of the commands request.
     *
     * @return The raw request data.
     */
    public Buffer getRequestData() {
        return messageData;
    }

    /**
     * Gets the AMQP application properties of the request.
     *
     * @return The application properties or {@code null}.
     */
    public Map<String, Object> getRequestProperties() {
        return applicationProperties;
    }

    /**
     * Gets the AMQP content type property of the request.
     *
     * @return The content type of the data or {@code null}.
     */
    public String getContentType() {
        return contentType;
    }
}
