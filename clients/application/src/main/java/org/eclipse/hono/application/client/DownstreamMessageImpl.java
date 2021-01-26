/*
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
 */

package org.eclipse.hono.application.client;

import java.util.Objects;

import org.eclipse.hono.util.QoS;

import io.vertx.core.buffer.Buffer;

/**
 * A downstream message of Hono's north bound APIs.
 *
 * @param <T> The type of context that the message is being received in.
 */
public class DownstreamMessageImpl<T extends MessageContext> implements DownstreamMessage<T> {

    private final String tenantId;
    private final String deviceId;
    private final MessageProperties properties;
    private final String contentType;
    private final T messageContext;
    private final QoS qos;
    private final Buffer payload;

    /**
     * Creates a downstream message.
     *
     * @param tenantId The tenant that sent the message.
     * @param deviceId The device that sent the message.
     * @param properties The metadata of the message.
     * @param contentType The content type of the message payload.
     * @param messageContext The context to be added to the message.
     * @param qos The quality of service level that the device requested.
     * @param payload The payload - may be {@code null}.
     * @throws NullPointerException if any of the parameters, except payload, is {@code null}.
     */
    public DownstreamMessageImpl(final String tenantId, final String deviceId, final MessageProperties properties,
            final String contentType, final T messageContext, final QoS qos, final Buffer payload) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(properties);
        Objects.requireNonNull(contentType);
        Objects.requireNonNull(messageContext);
        Objects.requireNonNull(qos);

        this.tenantId = tenantId;
        this.deviceId = deviceId;
        this.properties = properties;
        this.contentType = contentType;
        this.messageContext = messageContext;
        this.qos = qos;
        this.payload = payload;
    }

    @Override
    public final String getTenantId() {
        return tenantId;
    }

    @Override
    public final String getDeviceId() {
        return deviceId;
    }

    @Override
    public final MessageProperties getProperties() {
        return properties;
    }

    @Override
    public final String getContentType() {
        return contentType;
    }

    @Override
    public final T getMessageContext() {
        return messageContext;
    }

    @Override
    public final QoS getQos() {
        return qos;
    }

    @Override
    public final Buffer getPayload() {
        return payload;
    }
}
