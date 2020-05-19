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

package org.eclipse.hono.adapter.mqtt.impl;

import org.eclipse.hono.util.ResourceIdentifier;

import io.vertx.mqtt.messages.MqttPublishMessage;

/**
 * The result of mapping a message using the {@code MessageMapping} service.
 */
final class MappedMessage {

    private final ResourceIdentifier resource;
    private final MqttPublishMessage message;

    /**
     * Creates a new mapping result.
     *
     * @param ctx The original context of the received message.
     * @param resource The original ResourceIdentifier in which the deviceId may be altered by the mapper.
     * @param message The received message from the gateway/device in which the payload may be altered by the mapper.
     */
    MappedMessage(final ResourceIdentifier targetAddress, final MqttPublishMessage message) {
        this.resource = targetAddress;
        this.message = message;
    }

    /**
     * Gets the resourceIdentifier.
     *
     * @return the resourceIdentifier
     */
    ResourceIdentifier getResource() {
        return resource;
    }

    /**
     * Gets the message.
     *
     * @return the actual message
     */
    MqttPublishMessage getMessage() {
        return message;
    }
}
