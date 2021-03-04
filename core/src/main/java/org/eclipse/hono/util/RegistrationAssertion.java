/**
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
 */


package org.eclipse.hono.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Encapsulates the registration assertion information for a device as defined by the
 * <a href="https://www.eclipse.org/hono/docs/api/device-registration/">Device Registration API</a>.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(content = Include.NON_EMPTY)
public final class RegistrationAssertion {

    private final String deviceId;

    private List<String> authorizedGateways = new ArrayList<>();
    private Map<String, Object> defaults = new HashMap<>();
    private String mapper;
    private CommandEndpoint commandEndpoint;

    /**
     * Creates a new registration assertion for a device.
     *
     * @param deviceId The identifier of the device.
     */
    public RegistrationAssertion(
            @JsonProperty(value = RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, required = true)
            final String deviceId) {

        Objects.requireNonNull(deviceId);
        this.deviceId = deviceId;
    }

    /**
     * Gets the identifier of the asserted device.
     *
     * @return The identifier.
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * Gets the gateway devices that are authorized to act on behalf of
     * the device.
     *
     * @return The gateway identifiers.
     */
    @JsonProperty(value = RegistrationConstants.FIELD_VIA)
    public List<String> getAuthorizedGateways() {
        return authorizedGateways;
    }

    /**
     * Sets the gateway devices that are authorized to act on behalf of
     * the device.
     *
     * @param gatewayIds The gateway identifiers or {@code null} to clear the list.
     * @return A reference to this object for method chaining.
     */
    public RegistrationAssertion setAuthorizedGateways(final List<String> gatewayIds) {
        this.authorizedGateways.clear();
        Optional.ofNullable(gatewayIds).ifPresent(this.authorizedGateways::addAll);
        return this;
    }

    /**
     * Gets the name of the mapper definition to use for the device.
     *
     * @return The mapper or {@code null} if not set.
     */
    @JsonProperty(value = RegistrationConstants.FIELD_MAPPER)
    public String getMapper() {
        return mapper;
    }

    /**
     * Sets the name of the mapper definition to use for the device.
     *
     * @param mapper The mapper to set or {@code null} if no mapper should be used.
     * @return A reference to this object for method chaining.
     */
    public RegistrationAssertion setMapper(final String mapper) {
        this.mapper = mapper;
        return this;
    }

    /**
     * Gets default properties to be used for augmenting messages from the device with missing information.
     *
     * @return An unmodifiable view on the properties.
     */
    @JsonProperty(value = RegistrationConstants.FIELD_PAYLOAD_DEFAULTS)
    public Map<String, Object> getDefaults() {
        return Collections.unmodifiableMap(defaults);
    }

    /**
     * Sets default properties to be used for augmenting messages from the device with missing information.
     *
     * @param defaults The properties to set or {@code null} to clear all properties.
     * @return A reference to this object for method chaining.
     */
    public RegistrationAssertion setDefaults(final Map<String, Object> defaults) {
        this.defaults.clear();
        Optional.ofNullable(defaults).ifPresent(this.defaults::putAll);
        return this;
    }

    /**
     * Gets the endpoint to use when sending commands.
     *
     * @return The command endpoint or {@code null} if not set.
     */
    @JsonProperty(value = RegistrationConstants.FIELD_COMMAND_ENDPOINT)
    public CommandEndpoint getCommandEndpoint() {
        return commandEndpoint;
    }

    /**
     * Sets the command endpoint to be used when sending commands.
     *
     * @param commandEndpoint The command endpoint to set or {@code null} if no command endpoint is available.
     * @return A reference to this object for method chaining.
     */
    public RegistrationAssertion setCommandEndpoint(final CommandEndpoint commandEndpoint) {
        this.commandEndpoint = commandEndpoint;
        return this;
    }
}
