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
 * A message of Hono's northbound APIs, flowing from the messaging system to the backend application.
 */
public interface DownstreamMessage extends Message {
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
     * {@inheritDoc}
     */
    @Override
    MessageContext getMessageContext();

    /**
     * Gets the quality of service level that the device requested.
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
