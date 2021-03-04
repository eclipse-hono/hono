/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Encapsulates the command endpoint information for a gateway as defined by the
 * <a href="https://www.eclipse.org/hono/docs/api/device-registration/">Device Registration API</a>.
 */
public class CommandEndpoint {
    private String uri;
    private Map<String, String> headers = new HashMap<>();
    private Map<String, Object> payloadProperties = new HashMap<>();

    /**
     * Gets the uri format to be used when sending a command to this endpoint.
     *
     * @return The uri format or {@code null} if not set.
     */
    @JsonProperty(value = RegistrationConstants.FIELD_COMMAND_ENDPOINT_URI)
    public String getUri() {
        return uri;
    }

    /**
     * Sets the uri format to be used when sending a command to this endpoint.
     *
     * @param uri The uri format to set or {@code null}.
     * @return A reference to this object for method chaining.
     */
    public CommandEndpoint setUri(final String uri) {
        this.uri = uri;
        return this;
    }

    /**
     * Gets headers to be used when sending a command to this endpoint.
     *
     * @return An unmodifiable view on the headers.
     */
    @JsonProperty(value = RegistrationConstants.FIELD_COMMAND_ENDPOINT_HEADERS)
    public Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    /**
     * Sets headers to be used when sending a command to this endpoint.
     *
     * @param headers The headers to set or {@code null} to clear all headers.
     * @return A reference to this object for method chaining.
     */
    public CommandEndpoint setHeaders(final Map<String, String> headers) {
        this.headers.clear();
        Optional.ofNullable(headers).ifPresent(this.headers::putAll);
        return this;
    }

    /**
     * Gets payload properties to be used when sending a command to this endpoint.
     *
     * @return An unmodifiable view on the payload properties.
     */
    @JsonProperty(value = RegistrationConstants.FIELD_COMMAND_ENDPOINT_PAYLOAD_PROPERTIES)
    public Map<String, Object> getPayloadProperties() {
        return Collections.unmodifiableMap(payloadProperties);
    }

    /**
     * Sets payload properties to be used when sending a command to this endpoint.
     *
     * @param payloadProperties The payload properties to set or {@code null} to clear all payload properties.
     * @return A reference to this object for method chaining.
     */
    public CommandEndpoint setPayloadProperties(final Map<String, String> payloadProperties) {
        this.payloadProperties.clear();
        Optional.ofNullable(payloadProperties).ifPresent(this.payloadProperties::putAll);
        return this;
    }

    /**
     * Checks whether the configured uri is valid.
     *
     * @return true when the uri is not null and the uri can be converted without exceptions.
     */
    public boolean isUriValid() {
        if (uri == null) {
            return false;
        }
        try {
            new URI(getFormattedUri("deviceId"));
            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Returns the configured uri formatted with the deviceId.
     *
     * @param deviceId The deviceId to replace the placeholder with in the configured uri.
     * @return the fully formatted uri.
     */
    public String getFormattedUri(final String deviceId) {
        return uri.replaceAll("\\{\\{deviceId\\}\\}", deviceId);
    }
}
