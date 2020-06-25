/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.adapter.lora.LoraMessage;
import org.eclipse.hono.service.http.HttpUtils;

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
     * The url path prefix which is used for this provider. E.g. "/myloraprovider".
     *
     * @return The url path prefix with leading slash. E.g. "/myloraprovider".
     */
    String pathPrefix();

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
}
