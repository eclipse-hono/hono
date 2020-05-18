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

import org.eclipse.hono.adapter.mqtt.MqttContext;
import org.eclipse.hono.util.ResourceIdentifier;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.mqtt.messages.MqttPublishMessage;

/**
 * This component requests mapping from another server if configured properly. The
 * headers are overwritten with the result of the mapper (which includes the resourceId).
 * E.g.: when the deviceId is in the payload of the message, the deviceId can be deducted in the custom mapper and
 * the payload can be changed accordingly to the payload originally received by the gateway.
 */
public interface MessageMapping {

    /**
     * Fetches the mapper if configured and calls the external mapping service.
     *
     * @param ctx The mqtt context
     * @param targetAddress The resourceIdentifier with the current targetAddress
     * @param message Received message
     * @param registrationInfo information retrieved from the device registry
     * @return Mapped message
     */
    Future<MappedMessage> mapMessage(MqttContext ctx, ResourceIdentifier targetAddress,
                                     MqttPublishMessage message, JsonObject registrationInfo);
}
