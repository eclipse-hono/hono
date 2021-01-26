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

import org.eclipse.hono.util.QoS;

import io.vertx.core.buffer.Buffer;

/**
 * A message being delivered to an application via Hono's north bound APIs.
 *
 * @param <T> The type of context that the message is being received in.
 */
public interface DownstreamMessage<T extends MessageContext> extends Message<T> {

    /**
     * Gets the tenant that sent the message.
     *
     * @return the tenant id.
     */
    String getTenantId();

    /**
     * Gets the device that sent the message.
     *
     * @return the device id.
     */
    String getDeviceId();

    /**
     * Gets the metadata of the message.
     *
     * @return the message properties.
     */
    MessageProperties getProperties();

    /**
     * Gets the content-type of the payload.
     *
     * @return the content-type.
     */
    String getContentType();

    /**
     * Gets the quality-of-service level used by the device that this message originates from.
     *
     * @return The QoS.
     */
    QoS getQos();

    /**
     * Gets the payload of the message.
     *
     * @return the payload - may be {@code null}.
     */
    Buffer getPayload();
}
