/*******************************************************************************
 * Copyright (c) 2016, 2022  Contributors to the Eclipse Foundation
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

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.cert.TrustAnchor;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.security.auth.x500.X500Principal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Encapsulates the tenant information that was found by the get operation of the
 * <a href="https://www.eclipse.org/hono/docs/api/tenant/">Tenant API</a>.
 */
@JsonInclude(value = Include.NON_NULL)
public final class TenantObject extends JsonBackedValueObject {

    private static final Logger LOG = LoggerFactory.getLogger(TenantObject.class);

    @JsonIgnore
    private List<Adapter> adapters;
    @JsonIgnore
    private ResourceLimits resourceLimits;
    @JsonIgnore
    private TenantTracingConfig tracingConfig;
    @JsonIgnore
    private Set<TrustAnchor> trustAnchors;

    /**
     * Creates a new tenant.
     *
     * @param tenantId The tenant's identifier.
     * @param enabled {@code true} if devices of this tenant are allowed to connect to Hono.
     * @throws NullPointerException if identifier is {@code null}.
     */
    public TenantObject(
            @JsonProperty(value = RequestResponseApiConstants.FIELD_PAYLOAD_TENANT_ID, required = true)
            final String tenantId,
            @JsonProperty(value = RequestResponseApiConstants.FIELD_ENABLED, required = true)
            final boolean enabled) {
        Objects.requireNonNull(tenantId);
        setProperty(RequestResponseApiConstants.FIELD_PAYLOAD_TENANT_ID, tenantId);
        setProperty(RequestResponseApiConstants.FIELD_ENABLED, enabled);
    }

    /**
     * Adds a property to this tenant.
     *
     * @param name The property name.
     * @param value The property value.
     * @return This object for command chaining.
     * @throws NullPointerException if name is {@code null}.
     */
    @JsonAnySetter
    public TenantObject setProperty(final String name, final Object value) {
        json.put(Objects.requireNonNull(name), value);
        return this;
    }

    /**
     * Gets this tenant's identifier.
     *
     * @return The identifier or {@code null} if not set.
     */
    @JsonIgnore
    public String getTenantId() {
        return getProperty(RequestResponseApiConstants.FIELD_PAYLOAD_TENANT_ID, String.class);
    }

    /**
     * Checks if this tenant is enabled.
     *
     * @return {@code true} if this tenant is enabled.
     */
    @JsonIgnore
    public boolean isEnabled() {
        return getProperty(RequestResponseApiConstants.FIELD_ENABLED, Boolean.class, true);
    }

    /**
     * Sets the trusted certificate authority to use for authenticating
     * devices of this tenant.
     * <p>
     * This method removes all existing trust anchors.
     *
     * @param publicKey The CA's public key.
     * @param subjectDn The CA's subject DN.
     * @return This tenant for command chaining.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see #addTrustAnchor(PublicKey, X500Principal, Boolean)
     */
    @JsonIgnore
    public TenantObject setTrustAnchor(final PublicKey publicKey, final X500Principal subjectDn) {

        Objects.requireNonNull(publicKey);
        Objects.requireNonNull(subjectDn);

        setProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, new JsonArray());
        return addTrustAnchor(publicKey, subjectDn, false);
    }

    /**
     * Adds a trusted certificate authority to use for authenticating devices of this tenant.
     *
     * @param publicKey The CA's public key.
     * @param subjectDn The CA's subject DN.
     * @param autoProvisioningEnabled A flag indicating whether this CA may be used for automatic provisioning.
     * @return This tenant for command chaining.
     * @throws NullPointerException if the public key or subjectDN parameters is {@code null}.
     */
    @JsonIgnore
    public TenantObject addTrustAnchor(final PublicKey publicKey, final X500Principal subjectDn,
            final Boolean autoProvisioningEnabled) {

        Objects.requireNonNull(publicKey);
        Objects.requireNonNull(subjectDn);

        return addTrustAnchor(publicKey.getEncoded(), publicKey.getAlgorithm(), subjectDn, null,
                autoProvisioningEnabled);

    }

    /**
     * Adds a trusted certificate authority to use for authenticating devices of this tenant.
     *
     * @param publicKey The CA's public key in encoded form.
     * @param publicKeyAlgorithm The algorithm of the public key.
     * @param subjectDn The CA's subject DN.
     * @param authIdTemplate The template to generate the authentication identifier.
     * @param autoProvisioningEnabled A flag indicating whether this CA may be used for automatic provisioning.
     * @return This tenant for command chaining.
     * @throws NullPointerException if the public key, algorithm or subjectDN parameters is {@code null}.
     */
    @JsonIgnore
    public TenantObject addTrustAnchor(final byte[] publicKey,
                                       final String publicKeyAlgorithm,
                                       final X500Principal subjectDn,
                                       final String authIdTemplate,
                                       final Boolean autoProvisioningEnabled) {

        Objects.requireNonNull(publicKey);
        Objects.requireNonNull(publicKeyAlgorithm);
        Objects.requireNonNull(subjectDn);

        final JsonObject trustedCa = new JsonObject();
        trustedCa.put(RequestResponseApiConstants.FIELD_PAYLOAD_SUBJECT_DN, subjectDn.getName(X500Principal.RFC2253));
        trustedCa.put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, publicKey);
        trustedCa.put(TenantConstants.FIELD_PAYLOAD_KEY_ALGORITHM, publicKeyAlgorithm);
        trustedCa.put(TenantConstants.FIELD_AUTO_PROVISIONING_ENABLED, autoProvisioningEnabled);
        Optional.ofNullable(authIdTemplate)
                .ifPresent(t -> trustedCa.put(RequestResponseApiConstants.FIELD_PAYLOAD_AUTH_ID_TEMPLATE, t));
        final JsonArray cas = getProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, JsonArray.class, new JsonArray());
        trustAnchors = null;
        return setProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, cas.add(trustedCa));
    }

    /**
     * Gets the trust anchors for this tenant.
     * <p>
     * This method tries to create the trust anchors based on the information
     * from the JSON objects contained in the <em>trusted-ca</em> property.
     * <p>
     * The JSON objects are expected to contain
     * <ul>
     * <li>the Base64 encoded DER encoding of the trusted certificate's public key in
     * the <em>public-key</em> property and</li>
     * <li>the subject DN of the CA in the <em>subject-dn</em> property.</li>
     * </ul>
     * <p>
     * Once a non empty set of trust anchors has been created, it will be cached and
     * returned on subsequent invocations of this method.
     *
     * @return The set of trust anchors, may be empty.
     */
    @JsonIgnore
    public Set<TrustAnchor> getTrustAnchors() {

        if (trustAnchors == null) {
            trustAnchors = getProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, JsonArray.class, new JsonArray())
                .stream()
                .filter(obj -> obj instanceof JsonObject)
                .map(JsonObject.class::cast)
                .map(this::getTrustAnchorForPublicKey)
                .filter(anchor -> anchor != null)
                .collect(Collectors.toSet());
        }
        return trustAnchors;
    }

    @JsonIgnore
    private TrustAnchor getTrustAnchorForPublicKey(final JsonObject keyProps) {

        if (keyProps == null) {
            return null;
        } else {
            final X500Principal principal;
            // Prefer DER encoded form if available to avoid inconsistencies in formatting with original certificate
            final byte[] subjectDnBytes = getProperty(keyProps, RequestResponseApiConstants.FIELD_PAYLOAD_SUBJECT_DN_BYTES, byte[].class);
            if (subjectDnBytes != null) {
                principal = new X500Principal(subjectDnBytes);
            } else {
                final String subjectDn = getProperty(keyProps, RequestResponseApiConstants.FIELD_PAYLOAD_SUBJECT_DN, String.class);
                if (subjectDn == null) {
                    LOG.debug("trust anchor definition does not contain required property {}",
                            RequestResponseApiConstants.FIELD_PAYLOAD_SUBJECT_DN);
                    return null;
                }
                principal = new X500Principal(subjectDn);
            }
            final byte[] encodedKey = getProperty(keyProps, TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, byte[].class);
            if (encodedKey == null) {
                LOG.debug("trust anchor definition does not contain required property {}",
                        TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY);
                return null;
            }
            try {
                final String type = getProperty(
                        keyProps,
                        TenantConstants.FIELD_PAYLOAD_KEY_ALGORITHM,
                        String.class,
                        CredentialsConstants.RSA_ALG);
                final X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encodedKey);
                final KeyFactory factory = KeyFactory.getInstance(type);
                final PublicKey publicKey = factory.generatePublic(keySpec);
                return new RevocableTrustAnchor(principal, publicKey, null, keyProps);
            } catch (final GeneralSecurityException e) {
                LOG.debug("failed to instantiate trust anchor's public key", e);
                return null;
            }
        }
    }

    /**
     * Checks whether auto-provisioning is enabled for a CA.
     *
     * @param subjectDn The subject DN of the CA to check.
     * @return {@code true} if auto-provisioning is enabled.
     * @throws NullPointerException if the parameter subjectDN is {@code null}.
     */
    @JsonIgnore
    public boolean isAutoProvisioningEnabled(final String subjectDn) {
        Objects.requireNonNull(subjectDn);
        return getProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, JsonArray.class, new JsonArray())
                .stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .filter(ca -> subjectDn.equals(getProperty(ca, RequestResponseApiConstants.FIELD_PAYLOAD_SUBJECT_DN, String.class)))
                .map(caInUse -> getProperty(caInUse, TenantConstants.FIELD_AUTO_PROVISIONING_ENABLED, Boolean.class, false))
                .findFirst().orElse(false);
    }

    /**
     * Gets the template to generate authentication identifier for the given subject DN.
     * <p>
     * If no template is configured, {@link Optional#empty()} is returned.
     *
     * @param subjectDn The subject DN of the CA to check.
     * @return The template to generate authentication identifier.
     * @throws NullPointerException if the parameter subjectDN is {@code null}.
     */
    @JsonIgnore
    public Optional<String> getAuthIdTemplate(final String subjectDn) {
        Objects.requireNonNull(subjectDn, "Subject DN must not be null");

        return getProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, JsonArray.class, new JsonArray())
                .stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .filter(ca -> subjectDn.equals(getProperty(ca, RequestResponseApiConstants.FIELD_PAYLOAD_SUBJECT_DN, String.class)))
                .map(caInUse -> getProperty(caInUse, RequestResponseApiConstants.FIELD_PAYLOAD_AUTH_ID_TEMPLATE, String.class))
                .filter(Objects::nonNull)
                .findFirst();
    }

    /**
     * Gets the list of configured adapters for the tenant.
     *
     * @return The list of configured adapters or {@code null} if not set.
     */
    @JsonProperty(TenantConstants.FIELD_ADAPTERS)
    public List<Adapter> getAdapters() {
        return adapters;
    }

    /**
     * Sets the given list of adapters for the tenant.
     *
     * @param adapters The list of adapters or {@code null} if not set.
     * @return This tenant for command chaining.
     * @throws IllegalArgumentException if more than one of the adapters have the same <em>type</em>.
     */
    @JsonProperty(TenantConstants.FIELD_ADAPTERS)
    public TenantObject setAdapters(final List<Adapter> adapters) {
        this.adapters = validateAdapterTypes(adapters);
        return this;
    }

    @JsonIgnore
    private List<Adapter> validateAdapterTypes(final List<Adapter> adapters) {
        if (adapters != null) {
            final Set<String> uniqueAdapterTypes = adapters.stream()
                    .map(Adapter::getType)
                    .collect(Collectors.toSet());
            if (adapters.size() != uniqueAdapterTypes.size()) {
                throw new IllegalArgumentException("Each adapter must have a unique type");
            }
        }
        return adapters;
    }

    /**
     * Gets the adapter configuration for the given type.
     *
     * @param type The adapter's type.
     * @return The adapter configuration or {@code null} if not set.
     * @throws NullPointerException if type is {@code null}.
     */
    @JsonIgnore
    public Adapter getAdapter(final String type) {

        Objects.requireNonNull(type);

        return Optional.ofNullable(adapters)
                .flatMap(ok -> adapters.stream()
                        .filter(adapter -> type.equals(adapter.getType()))
                        .findFirst())
                .orElse(null);
    }

    /**
     * Adds an adapter configuration.
     *
     * @param adapter The adapter configuration to add.
     * @return This tenant for command chaining.
     * @throws NullPointerException if the given adapter configuration is {@code null}.
     * @throws IllegalArgumentException  if any of the already existing adapters has the same <em>type</em>.
     */
    public TenantObject addAdapter(final Adapter adapter) {

        Objects.requireNonNull(adapter);

        if (adapters == null) {
            adapters = new ArrayList<>();
        } else if (getAdapter(adapter.getType()) != null) {
            throw new IllegalArgumentException(
                    String.format("Already an adapter of the type [%s] exists", adapter.getType()));
        }
        adapters.add(adapter);
        return this;
    }

    /**
     * Checks if this tenant is enabled and if a given protocol adapter is enabled for this tenant.
     *
     * @param typeName The type name of the adapter.
     * @return {@code true} if this tenant and the given adapter are enabled.
     */
    @JsonIgnore
    public boolean isAdapterEnabled(final String typeName) {

        if (!isEnabled()) {
            return false;
        } else if (adapters == null) {
            // all adapters are enabled
            return true;
        } else {
            final Adapter adapter = getAdapter(typeName);
            if (adapter == null) {
                // if not explicitly configured, the adapter is disabled by default
                return false;
            }
            return adapter.isEnabled();
        }
    }

    /**
     * Gets the maximum number of seconds that a protocol adapter should wait for a command targeted at a device.
     * <p>
     * The returned value is determined as follows:
     * <ol>
     * <li>if this tenant configuration extensions contains an integer typed {@link TenantConstants#FIELD_MAX_TTD} 
     * property specific to the given adapter type, then return its value provided it is &gt;= 0</li>
     * <li>otherwise, return {@link TenantConstants#DEFAULT_MAX_TTD}</li>
     * </ol>
     *
     * @param typeName The type of protocol adapter to get the TTD for.
     * @return The number of seconds.
     */
    @JsonIgnore
    public int getMaxTimeUntilDisconnect(final String typeName) {

        Objects.requireNonNull(typeName);

        return Optional.ofNullable(getAdapter(typeName))
                .map(Adapter::getExtensions)
                .map(extensions -> {
                    try {
                        return (Integer) extensions.get(TenantConstants.FIELD_MAX_TTD);
                    } catch (final ClassCastException e) {
                        return null;
                    }
                })
                .filter(maxTtd -> maxTtd >= 0)
                .orElse(TenantConstants.DEFAULT_MAX_TTD);
    }

    /**
     * Creates an enabled tenant for a tenantId.
     *
     * @param tenantId The tenant for which the object is constructed.
     * @return The TenantObject.
     * @throws NullPointerException if tenantId is {@code null}.
     */
    public static TenantObject from(final String tenantId) {

        return new TenantObject(tenantId, true);
    }

    /**
     * Creates a TenantObject for a tenantId and the enabled property.
     *
     * @param tenantId The tenant for which the object is constructed.
     * @param enabled {@code true} if the tenant shall be enabled.
     * @return The TenantObject.
     * @throws NullPointerException if tenantId is {@code null}.
     */
    public static TenantObject from(final String tenantId, final boolean enabled) {

        return new TenantObject(tenantId, enabled);
    }

    /**
     * Gets the resource limits for the tenant.
     *
     * @return The resource limits or {@code null} if not set.
     */
    @JsonGetter(TenantConstants.FIELD_RESOURCE_LIMITS)
    public ResourceLimits getResourceLimits() {
        return this.resourceLimits;
    }

    /**
     * Sets the resource limits for the tenant.
     *
     * @param resourceLimits The resource limits configuration (may be {@code null}).
     * @return This tenant for command chaining.
     * @throws IllegalArgumentException if the resource limits object cannot be 
     *                                  instantiated from the given jsonObject.
     */
    @JsonIgnore
    public TenantObject setResourceLimits(final JsonObject resourceLimits) {
        return setResourceLimits(Optional.of(resourceLimits)
                .map(json -> json.mapTo(ResourceLimits.class))
                .orElse(null));
    }

    /**
     * Sets the resource limits for the tenant.
     *
     * @param resourceLimits The resource limits configuration (may be {@code null}).
     * @return This tenant for command chaining.
     */
    @JsonSetter(TenantConstants.FIELD_RESOURCE_LIMITS)
    public TenantObject setResourceLimits(final ResourceLimits resourceLimits) {
        this.resourceLimits = resourceLimits;
        return this;
    }

    /**
     * Gets the minimum message size in bytes.
     *
     * @return The minimum message size in bytes or {@link TenantConstants#DEFAULT_MINIMUM_MESSAGE_SIZE} if not set.
     */
    @JsonIgnore
    public int getMinimumMessageSize() {
        return getProperty(TenantConstants.FIELD_MINIMUM_MESSAGE_SIZE, Integer.class,
                TenantConstants.DEFAULT_MINIMUM_MESSAGE_SIZE);
    }

    /**
     * Sets the minimum message size in bytes.
     *
     * @param payloadSize The payload size of the incoming message.
     * @return The TenantObject.
     * @throws IllegalArgumentException if the message payload size is negative.
     */
    public TenantObject setMinimumMessageSize(final int payloadSize) {

        if (payloadSize < 0) {
            throw new IllegalArgumentException("message payload size must be >= 0");
        }
        return setProperty(TenantConstants.FIELD_MINIMUM_MESSAGE_SIZE, payloadSize);
    }

    /**
     * Gets the default property values used for all devices of this tenant.
     *
     * @return The default properties or an empty JSON object if no default properties
     *         have been defined for this tenant.
     */
    @JsonIgnore
    public JsonObject getDefaults() {
        return getProperty(RequestResponseApiConstants.FIELD_PAYLOAD_DEFAULTS, JsonObject.class, new JsonObject());
    }

    /**
     * Sets the default property values to use for all devices of this tenant.
     *
     * @param defaultProperties The properties or an empty JSON object if no default properties
     *         have been defined for this tenant.
     * @return This tenant for command chaining.
     */
    @JsonIgnore
    public TenantObject setDefaults(final JsonObject defaultProperties) {
        setProperty(RequestResponseApiConstants.FIELD_PAYLOAD_DEFAULTS, Objects.requireNonNull(defaultProperties));
        return this;
    }

    /**
     * Gets this tenant's tracing configuration.
     *
     * @return The tracing configuration or {@code null} if not set.
     */
    @JsonProperty(value = TenantConstants.FIELD_TRACING)
    public TenantTracingConfig getTracingConfig() {
        return tracingConfig;
    }

    /**
     * Sets this tenant's tracing configuration.
     *
     * @param tracing The tracing configuration.
     * @return This tenant for command chaining.
     */
    @JsonProperty(value = TenantConstants.FIELD_TRACING)
    public TenantObject setTracingConfig(final TenantTracingConfig tracing) {
        this.tracingConfig = tracing;
        return this;
    }
}
