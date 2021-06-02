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

package org.eclipse.hono.service.management.tenant;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.util.Adapter;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.ResourceLimits;
import org.eclipse.hono.util.TenantTracingConfig;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Information about a Hono Tenant.
 * <p>
 * Represents the <em>Tenant</em> schema object defined in the
 * <a href="https://www.eclipse.org/hono/docs/api/management/">Device Registry Management API</a>
 */
@JsonInclude(value = Include.NON_NULL)
public class Tenant {

    @JsonProperty(RegistryManagementConstants.FIELD_ENABLED)
    private Boolean enabled;

    @JsonProperty(RegistryManagementConstants.FIELD_EXT)
    @JsonInclude(Include.NON_EMPTY)
    private Map<String, Object> extensions = new HashMap<>();

    @JsonProperty(RegistryManagementConstants.FIELD_PAYLOAD_DEFAULTS)
    @JsonInclude(Include.NON_EMPTY)
    private Map<String, Object> defaults = new HashMap<>();

    @JsonProperty(RegistryManagementConstants.FIELD_ADAPTERS)
    @JsonInclude(Include.NON_EMPTY)
    private List<Adapter> adapters = new LinkedList<>();

    @JsonProperty(RegistryManagementConstants.FIELD_MINIMUM_MESSAGE_SIZE)
    @JsonInclude(Include.NON_DEFAULT)
    private int minimumMessageSize = RegistryManagementConstants.DEFAULT_MINIMUM_MESSAGE_SIZE;

    @JsonProperty(RegistryManagementConstants.FIELD_RESOURCE_LIMITS)
    @JsonInclude(Include.NON_DEFAULT)
    private ResourceLimits resourceLimits;

    @JsonProperty(RegistryManagementConstants.FIELD_TRACING)
    @JsonInclude(Include.NON_EMPTY)
    private TenantTracingConfig tracing;

    @JsonProperty(RegistryManagementConstants.FIELD_PAYLOAD_TRUSTED_CA)
    private List<TrustedCertificateAuthority> trustedCertificateAuthorities;

    /**
     * Creates a new Tenant instance.
     */
    public Tenant() {
    }

    /**
     * Creates a new Tenant instance from an existing one.
     *
     * @param other The tenant to copy from.
     * @throws NullPointerException if other tenant is {@code null}.
     */
    public Tenant(final Tenant other) {
        Objects.requireNonNull(other);

        this.enabled = other.enabled;
        if (other.extensions != null) {
            this.extensions = new HashMap<>(other.extensions);
        }
        if (other.defaults != null) {
            this.defaults = new HashMap<>(other.defaults);
        }
        if (other.adapters != null) {
            this.adapters = new ArrayList<>(other.adapters);
        }
        this.minimumMessageSize = other.minimumMessageSize;
        this.resourceLimits = other.resourceLimits;
        this.tracing = other.tracing;
        if (Objects.nonNull(other.trustedCertificateAuthorities)) {
            this.trustedCertificateAuthorities = new ArrayList<>(other.trustedCertificateAuthorities);
        }
    }

    /**
     * Checks if this object contains all required data.
     *
     * @return {@code true} if all required data is available.
     */
    @JsonIgnore
    public final boolean isValid() {

        return Optional.ofNullable(trustedCertificateAuthorities)
                .map(list -> list.stream().allMatch(TrustedCertificateAuthority::isValid))
                .orElse(true);
    }

    /**
     * Sets whether devices of this tenant should be able to connect
     * to Hono.
     * <p>
     * The default value of this property is {@code true}.
     *
     * @param enabled {@code true} if devices should be able to connect.
     * @return This instance, to allow chained invocations.
     */
    @JsonIgnore
    public final Tenant setEnabled(final boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Checks whether devices of this tenant are able to connect
     * to Hono.
     * <p>
     * The default value of this property is {@code true}.
     *
     * @return {@code true} if devices are able to connect.
     */
    @JsonIgnore
    public final boolean isEnabled() {
        return Optional.ofNullable(enabled).orElse(true);
    }

    /**
     * Sets the extension properties for this tenant.
     * <p>
     * Existing extension properties are completely replaced by the new properties.
     *
     * @param extensions The extension properties.
     * @return This instance, to allow chained invocations.
     */
    public final Tenant setExtensions(final Map<String, Object> extensions) {
        this.extensions.clear();
        if (extensions != null) {
            this.extensions.putAll(extensions);
        }
        return this;
    }

    /**
     * Adds an extension property to this tenant.
     * <p>
     * If an extension property already exists for the specified key, the old value is replaced by the new value.
     *
     * @param key The key of the entry.
     * @param value The value of the entry.
     * @return This instance, to allow chained invocations.
     * @throws NullPointerException if any of the arguments are {@code null}.
     */
    public final Tenant putExtension(final String key, final Object value) {

        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        this.extensions.put(key, value);
        return this;
    }

    /**
     * Gets the extension properties of this tenant.
     *
     * @return An unmodifiable view on the extension properties.
     */
    public final Map<String, Object> getExtensions() {
        return Collections.unmodifiableMap(this.extensions);
    }

    /**
     * Sets the default properties to use for devices belonging to this tenant.
     * <p>
     * Existing default properties are completely replaced by the new properties.
     *
     * @param defaults The default properties.
     * @return This instance, to allow chained invocations.
     */
    public final Tenant setDefaults(final Map<String, Object> defaults) {
        this.defaults.clear();
        if (defaults != null) {
            this.defaults.putAll(defaults);
        }
        return this;
    }

    /**
     * Gets the default properties used for devices belonging to this tenant.
     *
     * @return An unmodifiable view on the default properties.
     */
    public final Map<String, Object> getDefaults() {
        return Collections.unmodifiableMap(defaults);
    }

    /**
     * Sets protocol adapter configuration specific to this tenant.
     * <p>
     * Existing configuration properties are completely replaced by the new properties.
     *
     * @param adapters The configuration properties.
     * @return This instance, to allow chained invocations.
     * @throws IllegalArgumentException if the adapters list is empty.
     */
    public final Tenant setAdapters(final List<Adapter> adapters) {

        if (adapters != null) {
            if (adapters.isEmpty()) {
                throw new IllegalArgumentException("At least one adapter must be configured");
            }

            final Set<String> uniqueAdapterTypes = adapters.stream()
                    .map(Adapter::getType)
                    .collect(Collectors.toSet());
            if (adapters.size() > uniqueAdapterTypes.size()) {
                throw new IllegalArgumentException("Each adapter must have a unique type");
            }
        }

        this.adapters.clear();
        if (adapters != null) {
            this.adapters.addAll(adapters);
        }
        return this;
    }

    /**
     * Gets protocol adapter configuration specific to this tenant.
     *
     * @return An unmodifiable view on the adapter configuration properties.
     */
    public final List<Adapter> getAdapters() {
        return Collections.unmodifiableList(adapters);
    }

    /**
     * Adds protocol adapter configuration properties specific to this Tenant.
     *
     * @param configuration The configuration properties to add.
     * @return This instance, to allow chained invocations.
     * @throws IllegalArgumentException if the configuration is for an adapter type for which there already exists
     *                                  a configuration.
     */
    public final Tenant addAdapterConfig(final Adapter configuration) {

        if (configuration == null) {
            return this;
        }

        final boolean hasAdapterOfSameType = adapters.stream()
                .anyMatch(adapter -> configuration.getType().equals(adapter.getType()));
        if (hasAdapterOfSameType) {
            throw new IllegalArgumentException(
                    String.format("Already an adapter of the type [%s] exists", configuration.getType()));
        }
        adapters.add(configuration);
        return this;
    }

    /**
     * Gets the minimum message size in bytes.
     *
     * @return The minimum message size in bytes or 
     *         {@link RegistryManagementConstants#DEFAULT_MINIMUM_MESSAGE_SIZE} if not set.
     */
    public final Integer getMinimumMessageSize() {
        return minimumMessageSize;
    }

    /**
     * Sets the minimum message size in bytes.
     *
     * @param minimumMessageSize The minimum message size.
     *
     * @return This instance, to allow chained invocations.
     * @throws IllegalArgumentException if the minimum message size is negative.
     */
    public final Tenant setMinimumMessageSize(final Integer minimumMessageSize) {

        if (minimumMessageSize == null || minimumMessageSize < 0) {
            throw new IllegalArgumentException("minimum message size must be >= 0");
        }
        this.minimumMessageSize = minimumMessageSize;
        return this;
    }

    /**
     * Gets resource limits defined for this tenant.
     *
     * @return The resource limits or {@code null} if not set.
     */
    public final ResourceLimits getResourceLimits() {
        return resourceLimits;
    }

    /**
     * Sets the resource limits for this tenant.
     *
     * @param resourceLimits The resource limits to set.
     * @return This instance, to allow chained invocations.
     */
    public final Tenant setResourceLimits(final ResourceLimits resourceLimits) {
        this.resourceLimits = resourceLimits;
        return this;
    }

    /**
     * Gets this tenant's tracing configuration.
     *
     * @return The tracing configuration or {@code null} if not set.
     */
    public final TenantTracingConfig getTracing() {
        return tracing;
    }

    /**
     * Sets this tenant's tracing configuration.
     *
     * @param tracing The tracing configuration.
     * @return This instance, to allow chained invocations.
     */
    public final Tenant setTracing(final TenantTracingConfig tracing) {
        this.tracing = tracing;
        return this;
    }

    /**
     * Gets the trusted certificate authorities of this tenant.
     *
     * @return  The authorities or {@code null} if not set.
     */
    public List<TrustedCertificateAuthority> getTrustedCertificateAuthorities() {
        return trustedCertificateAuthorities;
    }

    /**
     * Sets the trusted certificate authority to use for authenticating devices of this tenant.
     *
     * @param trustedCertificateAuthorities The trust configurations to set.
     * @return This instance, to allow chained invocations.
     */
    public Tenant setTrustedCertificateAuthorities(final List<TrustedCertificateAuthority> trustedCertificateAuthorities) {
        if (trustedCertificateAuthorities != null) {
            this.trustedCertificateAuthorities = Collections.unmodifiableList(trustedCertificateAuthorities);
        }
        return this;
    }

    /**
     * Gets the subject DNs of this tenant's trusted certificate authorities.
     *
     * @return The subject DNs.
     */
    @JsonIgnore
    public Set<X500Principal> getTrustedCertificateAuthoritySubjectDNs() {

        return Optional.ofNullable(trustedCertificateAuthorities)
                .map(list -> list.stream().map(TrustedCertificateAuthority::getSubjectDn).collect(Collectors.toSet()))
                .orElseGet(Set::of);
    }

    /**
     * Checks if this tenant trusts a certificate authority with a given subject DN.
     *
     * @param subjectDn The subject DN to check for.
     * @return {@code true} if this tenant trusts a certificate authority with the given
     *         subject DN.
     * @throws NullPointerException if subject DN is {@code null}.
     */
    public boolean hasTrustedCertificateAuthoritySubjectDN(final X500Principal subjectDn) {

        Objects.requireNonNull(subjectDn);

        return Optional.ofNullable(trustedCertificateAuthorities)
                .map(list -> list.stream().anyMatch(ca -> subjectDn.equals(ca.getSubjectDn())))
                .orElse(false);
    }

    /**
     * Asserts that the trust anchor IDs are unique for this tenant and also assigns a unique ID
     * to each trust anchor for which the ID is not provided.
     *
     * @return A reference to this for fluent use.
     * @throws IllegalStateException if the trust anchor IDs are not unique for this tenant.
     */
    public final Tenant assertTrustAnchorIdUniquenessAndCreateMissingIds() {
        assertTrustAnchorIdUniqueness();
        createMissingTrustAnchorIds();
        return this;
    }

    /**
     * Assigns a unique ID to each trust anchor that does not have one already.
     */
    private void createMissingTrustAnchorIds() {
        Optional.ofNullable(trustedCertificateAuthorities)
                .ifPresent(trustAnchors -> trustAnchors.stream()
                        .filter(trustAnchor -> Objects.isNull(trustAnchor.getId()))
                        .forEach(trustAnchor -> {
                            //Check if the generated id already exists, if so then generate a new id
                            // else set the generated id.
                            String generatedId;
                            do {
                                generatedId = DeviceRegistryUtils.getUniqueIdentifier();
                            } while (trustAnchorIdExists(generatedId));
                            trustAnchor.setId(generatedId);
                        }));
    }

    private boolean trustAnchorIdExists(final String id) {
        return Optional.ofNullable(trustedCertificateAuthorities)
                .map(trustAnchors -> trustAnchors.stream()
                        .map(TrustedCertificateAuthority::getId)
                        .filter(Objects::nonNull)
                        .anyMatch(existingId -> existingId.equals(id)))
                .orElse(false);
    }

    /**
     * Assert that the trust anchor IDs are unique for this tenant.
     *
     * @throws IllegalStateException if the trust anchor IDs are not unique for this tenant.
     */
    private void assertTrustAnchorIdUniqueness() {
        if (Objects.nonNull(trustedCertificateAuthorities)) {

            final Set<String> trustAnchorIds = new HashSet<>();
            final AtomicInteger count = new AtomicInteger(0);

            trustedCertificateAuthorities
                    .stream()
                    .map(TrustedCertificateAuthority::getId)
                    .filter(Objects::nonNull)
                    .forEach(id -> {
                        trustAnchorIds.add(id);
                        count.incrementAndGet();
                    });

            if (trustAnchorIds.size() != count.get()) {
                throw new IllegalStateException("trusted anchor IDs must be unique within each tenant object");
            }
        }
    }

    /**
     * Checks whether auto-provisioning is enabled for a CA with the given subject DN
     * and which is valid at this point of time.
     *
     * @param subjectDn The subject DN of the CA to check.
     * @return {@code true} if auto-provisioning is enabled.
     * @throws NullPointerException if subjectDN is {@code null}.
     */
    @JsonIgnore
    public boolean isAutoProvisioningEnabled(final String subjectDn) {
        Objects.requireNonNull(subjectDn);

        return getValidTrustedCA(subjectDn)
                .map(TrustedCertificateAuthority::isAutoProvisioningEnabled)
                .orElse(false);
    }

    /**
     * Checks whether any unregistered devices that authenticate with a client certificate
     * issued by this tenant's CA should be auto-provisioned as gateways.
     *
     * @param subjectDn The subject DN of the tenant's CA to check.
     * @return {@code true} if to be auto-provisioned as a gateway.
     * @throws NullPointerException if the subjectDN is {@code null}.
     */
    @JsonIgnore
    public boolean isAutoProvisioningAsGatewayEnabled(final String subjectDn) {
        Objects.requireNonNull(subjectDn);

        return getValidTrustedCA(subjectDn)
                .map(TrustedCertificateAuthority::isAutoProvisioningAsGatewayEnabled)
                .orElse(false);
    }

    /**
     * Gets the device-id template corresponding to the given subject DN, which is used for generating
     * the device identifier of the device/gateway being auto-provisioned.
     *
     * @param subjectDn The subject DN of the tenant's CA.
     * @return The device-id template corresponding to the given trusted CA or {@code null}.
     * @throws NullPointerException if the subjectDN is {@code null}.
     */
    @JsonIgnore
    public final String getAutoProvisioningDeviceIdTemplate(final String subjectDn) {
        Objects.requireNonNull(subjectDn);

        return getValidTrustedCA(subjectDn)
                .map(TrustedCertificateAuthority::getAutoProvisioningDeviceIdTemplate)
                .orElse(null);
    }

    private Optional<TrustedCertificateAuthority> getValidTrustedCA(final String subjectDn) {
        if (trustedCertificateAuthorities == null) {
            return Optional.empty();
        }
        final Instant now = Instant.now();
        return trustedCertificateAuthorities
                .stream()
                .filter(ca -> subjectDn.equals(ca.getSubjectDnAsString()))
                // filter out CAs which are not valid at this point in time
                .filter(ca -> !now.isBefore(ca.getNotBefore()) && !now.isAfter(ca.getNotAfter()))
                .findFirst();
    }
}
