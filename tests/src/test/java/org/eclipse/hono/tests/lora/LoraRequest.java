/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.tests.lora;

import io.vertx.core.http.HttpMethod;

/**
 * Properties defining a request to the Lora adapter.
 *
 */
class LoraRequest {

    HttpMethod method;
    String contentType = "application/json";
    String endpointUri;
    String requestBodyPath;

    static LoraRequest from(final String uri, final String path) {
        return from(uri, path, HttpMethod.POST);
    }

    static LoraRequest from(final String uri, final String path, final HttpMethod method) {
        final var result = new LoraRequest();
        result.endpointUri = uri;
        result.requestBodyPath = path;
        result.method = method;
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("LoraRequest [method=").append(method).append(", contentType=").append(contentType)
                .append(", endpointUri=").append(endpointUri).append(", requestBodyPath=").append(requestBodyPath)
                .append("]");
        return builder.toString();
    }
}
