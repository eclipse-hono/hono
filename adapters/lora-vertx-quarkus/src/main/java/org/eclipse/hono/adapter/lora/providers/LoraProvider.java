/*******************************************************************************
 * Copyright (c) 2019, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.lora.providers;

import java.util.Map;
import java.util.Set;

import org.eclipse.hono.adapter.lora.LoraCommand;
import org.eclipse.hono.adapter.lora.LoraMessage;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.util.CommandEndpoint;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;

/**
 * A LoraWAN provider which can send and receive messages from and to LoRa devices.
 */
public interface LoraProvider {

    /**
     * The name of this LoRaWAN provider. Will be used e.g. as an AMQP 1.0 message application property indicating
     * the source provider of a LoRa Message.
     *
     * @return The name of this LoRaWAN provider.
     */
    String getProviderName();

    /**
     * The url path prefix(es) which is/are used for this provider. E.g. "/myloraprovider".
     *
     * @return The url path prefix(es) with leading slash. E.g. "/myloraprovider".
     */
    Set<String> pathPrefixes();

    /**
     * Gets the content type that this provider accepts.
     *
     * @return The accepted MIME type. This default implementation returns <em>application/json</em>.
     */
    default String acceptedContentType() {
        return HttpUtils.CONTENT_TYPE_JSON;
    }

    /**
     * Gets the HTTP method that this provider requires for uplink data.
     *
     * @return The HTTP method. This default implementation returns <em>POST</em>.
     */
    default HttpMethod acceptedHttpMethod() {
        return HttpMethod.POST;
    }

    /**
     * Gets the object representation of a message payload sent by
     * the provider.
     *
     * @param request The HTTP request containing the message.
     * @return The request object.
     * @throws NullPointerException if body is {@code null}.
     * @throws LoraProviderMalformedPayloadException if the request body cannot be decoded.
     */
    LoraMessage getMessage(RoutingContext request);

    /**
     * Gets the json object to send to the lorawan network server.
     *
     * @param commandEndpoint The command endpoint configuration.
     * @param deviceId The deviceId to which the lorawan network should forward the payload.
     * @param payload The payload to be sent to the lorawan device.
     * @param subject The subject which can contain some settings for the command.
     * @return The command object.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if the commandEndpoint doesn't contain a URI.
     */
    LoraCommand getCommand(CommandEndpoint commandEndpoint, String deviceId, Buffer payload, String subject);

    /**
     * Gets the default headers to be set for this provider.
     *
     * @return The default headers for this provider.
     */
    Map<String, String> getDefaultHeaders();
}
