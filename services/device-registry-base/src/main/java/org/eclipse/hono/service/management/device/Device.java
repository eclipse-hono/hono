/*******************************************************************************
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.management.device;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.hono.util.CommandEndpoint;
import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Device Information.
 */
@RegisterForReflection(ignoreNested = false)
@JsonInclude(value = Include.NON_NULL)
@JsonIgnoreProperties(value = { RegistryManagementConstants.FIELD_STATUS }, allowGetters = true)
public class Device {

    @JsonProperty(RegistryManagementConstants.FIELD_ENABLED)
    private Boolean enabled;

    @JsonProperty(RegistryManagementConstants.FIELD_EXT)
    @JsonInclude(value = Include.NON_EMPTY)
    private Map<String, Object> extensions = new HashMap<>();

    @JsonProperty(RegistryManagementConstants.FIELD_PAYLOAD_DEFAULTS)
    @JsonInclude(value = Include.NON_EMPTY)
    private Map<String, Object> defaults = new HashMap<>();

    @JsonProperty(RegistryManagementConstants.FIELD_VIA)
    @JsonInclude(value = Include.NON_EMPTY)
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> via = new ArrayList<>();

    @JsonProperty(RegistryManagementConstants.FIELD_VIA_GROUPS)
    @JsonInclude(value = Include.NON_EMPTY)
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> viaGroups = new ArrayList<>();

    @JsonProperty(RegistryManagementConstants.FIELD_MEMBER_OF)
    @JsonInclude(value = Include.NON_EMPTY)
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> memberOf = new ArrayList<>();

    @JsonProperty(RegistryManagementConstants.FIELD_STATUS)
    @JsonInclude(value = Include.NON_EMPTY)
    private DeviceStatus status;

    @JsonProperty(RegistryManagementConstants.FIELD_DOWNSTREAM_MESSAGE_MAPPER)
    private String downstreamMessageMapper;

    @JsonProperty(RegistryManagementConstants.FIELD_UPSTREAM_MESSAGE_MAPPER)
    private String upstreamMessageMapper;

    @JsonProperty(RegistryManagementConstants.FIELD_AUTHORITIES)
    @JsonInclude(value = Include.NON_EMPTY)
    private Set<String> authorities = new HashSet<>();

    @JsonProperty(RegistryManagementConstants.COMMAND_ENDPOINT)
    private CommandEndpoint commandEndpoint;

    /**
     * Creates a new Device instance.
     */
    public Device() {
    }

    /**
     * Creates a new instance cloned from an existing instance.
     *
     * @param other The device to copy from.
     * @throws NullPointerException if other device is {@code null}.
     */
    public Device(final Device other) {
        Objects.requireNonNull(other);
        this.enabled = other.enabled;
        if (other.extensions != null) {
            this.extensions = new HashMap<>(other.extensions);
        }
        if (other.defaults != null) {
            this.defaults = new HashMap<>(other.defaults);
        }
        if (other.via != null) {
            this.via = new ArrayList<>(other.via);
        }
        if (other.viaGroups != null) {
            this.viaGroups = new ArrayList<>(other.viaGroups);
        }
        if (other.memberOf != null) {
            this.memberOf = new ArrayList<>(other.memberOf);
        }
        if (other.authorities != null) {
            this.authorities = new HashSet<>(other.authorities);
        }
        if (other.commandEndpoint != null) {
            this.commandEndpoint = other.commandEndpoint;
        }

        this.downstreamMessageMapper = other.downstreamMessageMapper;
        this.upstreamMessageMapper = other.upstreamMessageMapper;
        this.status = other.status;
    }

    /**
     * Enables or disables this device.
     * <p>
     * Disabled devices cannot connect to any of the protocol adapters.
     * The default value of this property is {@code true}.
     *
     * @param enabled {@code true} if this device should be enabled.
     * @return A reference to this for fluent use.
     */
    public Device setEnabled(final Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Checks if this device is enabled.
     * <p>
     * Disabled devices cannot connect to any of the protocol adapters.
     * The default value of this property is {@code true}.
     *
     * @return {@code true} if this device is enabled.
     */
    @JsonIgnore
    public boolean isEnabled() {
        return Optional.ofNullable(enabled).orElse(true);
    }

    /**
     * Sets the extension properties for this device.
     *
     * @param extensions The extension properties to set.
     * @return           a reference to this for fluent use.
     */
    public Device setExtensions(final Map<String, Object> extensions) {
        this.extensions = extensions;
        return this;
    }

    /**
     * Adds a new extension entry to the device.
     *
     * @param key The key of the entry.
     * @param value The value of the entry.
     * @return This instance, to allowed chained invocations.
     */
    public Device putExtension(final String key, final Object value) {
        if (this.extensions == null) {
            this.extensions = new HashMap<>();
        }
        this.extensions.put(key, value);
        return this;
    }

    /**
     * Gets the extension properties for this device.
     *
     * @return The extension properties.
     */
    public Map<String, Object> getExtensions() {
        return this.extensions;
    }

    /**
     * Sets the defaults for this device.
     *
     * @param defaults  The defaults to set for this device.
     * @return          a reference to this for fluent use.
     */
    public Device setDefaults(final Map<String, Object> defaults) {
        this.defaults = defaults;
        return this;
    }

    /**
     * Gets the default properties for this device.
     *
     * @return The default properties.
     */
    public Map<String, Object> getDefaults() {
        return defaults;
    }

    /**
     * Gets the identifiers of the gateway devices that this device may connect via.
     *
     * @return The identifiers.
     */
    public List<String> getVia() {
        return via;
    }

    /**
     * Sets the identifiers of the gateway devices that this device may connect via.
     *
     * @param via The via property to set.
     * @return    a reference to this for fluent use.
     * @throws IllegalArgumentException if trying to set the 'via' property while the 'memberOf' property is already set.
     */
    public Device setVia(final List<String> via) {
        if (memberOf != null && !memberOf.isEmpty()) {
            throw new IllegalArgumentException("Trying to set the 'via' property while the 'memberOf' property is already set though both properties must not be set at the same time.");
        }
        this.via = via;
        return this;
    }

    /**
     * Gets the identifiers of the gateway groups that this device may connect via.
     *
     * @return The group identifiers
     */
    public List<String> getViaGroups() {
        return viaGroups;
    }

    /**
     * Sets the identifiers of the gateway groups that this device may connect via.
     *
     * @param viaGroups The viaGroups property to set.
     * @return a reference to this for fluent use.
     * @throws IllegalArgumentException if trying to set the 'viaGroups' property while the 'memberOf' property is already set.
     */
    public Device setViaGroups(final List<String> viaGroups) {
        if (memberOf != null && !memberOf.isEmpty()) {
            throw new IllegalArgumentException("Trying to set the 'viaGroups' property while the 'memberOf' property is already set though both properties must not be set at the same time.");
        }
        this.viaGroups = viaGroups;
        return this;
    }

    /**
     * Gets the identifiers of the gateway groups in which the device is a member.
     *
     * @return The identifiers.
     */
    public List<String> getMemberOf() {
        return memberOf;
    }

    /**
     * Sets the identifiers of the gateway groups in which the device is a member.
     *
     * @param memberOf The memberOf property to set.
     * @return    a reference to this for fluent use.
     * @throws IllegalArgumentException if trying to set the 'memberOf' property while the 'via' or 'viaGroups' property is already set.
     */
    public Device setMemberOf(final List<String> memberOf) {
        if (via != null && !via.isEmpty()) {
            throw new IllegalArgumentException("Trying to set the 'memberOf' property while the 'via' property is already set though both properties must not be set at the same time.");
        }
        if (viaGroups != null && !viaGroups.isEmpty()) {
            throw new IllegalArgumentException("Trying to set the 'memberOf' property while the 'viaGroups' property is already set though both properties must not be set at the same time.");
        }
        this.memberOf = memberOf;
        return this;
    }

    /**
     * Sets the (logical) name of a service that can be used to transform downstream messages
     * uploaded by this device before they are forwarded to downstream consumers.
     *
     * @param downstreamMessageMapper The service name or {@code null} if no service should be invoked.
     * @return A reference to this for fluent use.
     */
    public Device setDownstreamMessageMapper(final String downstreamMessageMapper) {
        this.downstreamMessageMapper = downstreamMessageMapper;
        return this;
    }

    /**
     * Sets the (logical) name of a service that can be used to transform downstream messages
     * uploaded by this device before they are forwarded to downstream consumers.
     *
     * @return The service name or {@code null} if no service is configured.
     */
    public String getDownstreamMessageMapper() {
        return downstreamMessageMapper;
    }

    /**
     * Sets the (logical) name of a service that can be used to transform upstream commands
     * to be sent to this device.
     *
     * @param upstreamMessageMapper The service name or {@code null} if no service should be invoked.
     * @return A reference to this for fluent use.
     */
    public Device setUpstreamMessageMapper(final String upstreamMessageMapper) {
        this.upstreamMessageMapper = upstreamMessageMapper;
        return this;
    }

    /**
     * Sets the (logical) name of a service that can be used to transform upstream commands
     * to be sent to this device.
     *
     * @return The service name or {@code null} if no service is configured.
     */
    public String getUpstreamMessageMapper() {
        return upstreamMessageMapper;
    }

    /**
     * Sets the registry internal status information of this device.
     *
     * @param status The status information to be set or {@code null} if there is none.
     * @return A reference to this for fluent use.
     */
    public final Device setStatus(final DeviceStatus status) {
        this.status = status;
        return this;
    }

    /**
     * Gets the registry internal status information of this device.
     *
     * @return The registry internal status information or {@code null} if there is none.
     */
    public final DeviceStatus getStatus() {
        return status;
    }

    /**
     * Gets a copy of this device without its status.
     *
     * @return The copied device.
     */
    public final Device withoutStatus() {
        final var copy = new Device(this);
        copy.status = null;
        return copy;
    }

    /**
     * Sets the authorities granted to this device.
     *
     * @param authorities The device's authorities.
     *
     * @return A reference to this for fluent use.
     */
    public final Device setAuthorities(final Set<String> authorities) {
        this.authorities = Optional.ofNullable(authorities)
                .map(HashSet::new)
                .orElseGet(HashSet::new);
        return this;
    }

    /**
     * Gets the authorities granted to this device.
     *
     * @return The device's authorities.
     */
    public final Set<String> getAuthorities() {
        return Collections.unmodifiableSet(authorities);
    }

    /**
     * Gets the definition of the endpoint that commands for this device should be sent to.
     *
     * @return The endpoint definition or {@code null} if not set.
     */
    public final CommandEndpoint getCommandEndpoint() {
        return commandEndpoint;
    }

    /**
     * Sets the definition of the endpoint that commands for this device should be sent to.
     *
     * @param endpoint The endpoint definition.
     * @return A reference to this for fluent use.
     * @throws NullPointerException if endpoint is {@code null}.
     */
    public final Device setCommandEndpoint(final CommandEndpoint endpoint) {
        this.commandEndpoint = Objects.requireNonNull(endpoint);
        return this;
    }
}
