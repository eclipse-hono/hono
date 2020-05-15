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

package org.eclipse.hono.sdk.gateway.mqtt2amqp.downstream;

import java.util.HashMap;
import java.util.Map;

import io.vertx.core.buffer.Buffer;

/**
 * Data to be sent to Hono's AMQP adapter.
 */
public abstract class DownstreamMessage {

    private final Buffer payload;
    private Map<String, Object> applicationProperties;
    private String contentType;

    /**
     * Creates an instance.
     * 
     * @param payload The payload to be used.
     * @throws NullPointerException if payload is {@code null}.
     */
    public DownstreamMessage(final Buffer payload) {
        this.payload = payload;
    }

    /**
     * Gets the payload of the message as a byte array.
     *
     * @return The payload.
     */
    public byte[] getPayload() {
        return (payload == null) ? null : payload.getBytes();
    }

    /**
     * Gets the content type of the message payload.
     *
     * @return The type or {@code null} if the content type is unknown.
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * Sets the content type of the message payload.
     *
     * @param contentType The type or {@code null} if the content type is unknown.
     */
    public void setContentType(final String contentType) {
        this.contentType = contentType;
    }

    /**
     * Adds the given property to the AMQP application properties to be added to a message.
     *
     * @param key The key of the property.
     * @param value The value of the property.
     */
    public void addApplicationProperty(final String key, final Object value) {
        if (applicationProperties == null) {
            applicationProperties = new HashMap<>();
        }
        applicationProperties.put(key, value);
    }

    /**
     * Gets the application properties to be added to a message.
     *
     * @return The application properties.
     */
    public Map<String, Object> getApplicationProperties() {
        return applicationProperties;
    }

}
