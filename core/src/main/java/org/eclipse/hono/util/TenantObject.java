/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.security.auth.x500.X500Principal;

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

    @JsonIgnore
    private List<Map<String, Object>> adapterConfigurations;
    @JsonIgnore
    private ResourceLimits resourceLimits;
    @JsonIgnore
    private TenantTracingConfig tracingConfig;
    @JsonIgnore
    private Set<TrustAnchor> trustAnchors;

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
        return getProperty(TenantConstants.FIELD_PAYLOAD_TENANT_ID, String.class);
    }

    /**
     * Sets this tenant's identifier.
     * 
     * @param tenantId The identifier.
     * @return This tenant for command chaining.
     */
    @JsonIgnore
    public TenantObject setTenantId(final String tenantId) {
        return setProperty(TenantConstants.FIELD_PAYLOAD_TENANT_ID, tenantId);
    }

    /**
     * Checks if this tenant is enabled.
     * 
     * @return {@code true} if this tenant is enabled.
     */
    @JsonIgnore
    public boolean isEnabled() {
        return getProperty(TenantConstants.FIELD_ENABLED, Boolean.class, true);
    }

    /**
     * Sets whether this tenant is enabled.
     * 
     * @param flag {@code true} if this tenant is enabled.
     * @return This tenant for command chaining.
     */
    @JsonIgnore
    public TenantObject setEnabled(final boolean flag) {
        return setProperty(TenantConstants.FIELD_ENABLED, flag);
    }

    /**
     * Sets the trusted certificate authority to use for authenticating
     * devices of this tenant.
     * 
     * @param publicKey The CA's public key.
     * @param subjectDn The CA's subject DN.
     * @return This tenant for command chaining.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    @JsonIgnore
    public TenantObject setTrustAnchor(final PublicKey publicKey, final X500Principal subjectDn) {

        Objects.requireNonNull(publicKey);
        Objects.requireNonNull(subjectDn);

        final JsonObject trustedCa = new JsonObject();
        trustedCa.put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, subjectDn.getName(X500Principal.RFC2253));
        trustedCa.put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, publicKey.getEncoded());
        trustedCa.put(TenantConstants.FIELD_PAYLOAD_KEY_ALGORITHM, publicKey.getAlgorithm());
        trustAnchors = null;
        return setProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, new JsonArray().add(trustedCa));
    }

    /**
     * Gets this tenant's trust anchor.
     * 
     * @return The first element of this tenant's set of trust anchors or {@code null},
     *         if no trust anchors have been defined.
     * @deprecated Use {@link #getTrustAnchors()} instead.
     */
    @JsonIgnore
    @Deprecated
    public TrustAnchor getTrustAnchor() {

        return getTrustAnchors().stream().findFirst().orElse(null);
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
            .map(ca -> getTrustAnchorForPublicKey((JsonObject) ca))
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
            final String subjectDn = getProperty(keyProps, TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, String.class);
            final String encodedKey = getProperty(keyProps, TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, String.class);
            if (subjectDn == null || encodedKey == null) {
                return null;
            } else {
                try {
                    final String type = getProperty(keyProps, TenantConstants.FIELD_PAYLOAD_KEY_ALGORITHM, String.class,
                            "RSA");
                    final X509EncodedKeySpec keySpec = new X509EncodedKeySpec(Base64.getDecoder().decode(encodedKey));
                    final KeyFactory factory = KeyFactory.getInstance(type);
                    final PublicKey publicKey = factory.generatePublic(keySpec);
                    return new TrustAnchor(subjectDn, publicKey, null);
                } catch (final IllegalArgumentException | GeneralSecurityException e) {
                    // Base64 decoding failed
                    return null;
                }
            }
        }
    }

    /**
     * Gets the configuration information for this tenant's
     * configured adapters.
     * 
     * @return An unmodifiable list of configuration properties or
     *         {@code null} if no specific configuration has been
     *         set for any protocol adapter.
     */
    @JsonProperty(TenantConstants.FIELD_ADAPTERS)
    public List<Map<String, Object>> getAdapterConfigurationsAsMaps() {
        if (adapterConfigurations == null) {
            return null;
        } else {
            return adapterConfigurations;
        }
    }

    /**
     * Gets the configuration information for this tenant's
     * configured adapters.
     * 
     * @return The configuration properties for this tenant's
     *         configured adapters or {@code null} if no specific
     *         configuration has been set for any protocol adapter.
     */
    @JsonIgnore
    public JsonArray getAdapterConfigurations() {
        if (adapterConfigurations == null) {
            return null;
        } else {
            final JsonArray result = new JsonArray();
            adapterConfigurations.stream().forEach(config -> result.add(new JsonObject(config)));
            return result;
        }
    }

    /**
     * Gets the configuration properties for a protocol adapter.
     * 
     * @param type The adapter's type.
     * @return The configuration properties or {@code null} if no specific
     *         properties have been set.
     * @throws NullPointerException if type is {@code null}.
     */
    public JsonObject getAdapterConfiguration(final String type) {

        Objects.requireNonNull(type);
        if (adapterConfigurations == null) {
            return null;
        } else {
            return adapterConfigurations.stream()
                    .filter(entry -> type.equalsIgnoreCase((String) entry.get(TenantConstants.FIELD_ADAPTERS_TYPE)))
                    .findFirst()
                    .map(entry -> new JsonObject(entry))
                    .orElse(null);
        }
    }

    /**
     * Sets the configuration information for this tenant's
     * configured adapters.
     * 
     * @param configurations A list of configuration properties, one set of properties
     *                              for each configured adapter. The list's content will be
     *                              copied into a new list in order to prevent modification
     *                              of the list after this method has been invoked.
     * @return This tenant for command chaining.
     */
    @JsonProperty(TenantConstants.FIELD_ADAPTERS)
    public TenantObject setAdapterConfigurations(final List<Map<String, Object>> configurations) {
        if (configurations == null) {
            this.adapterConfigurations = null;
        } else {
            this.adapterConfigurations = new LinkedList<>(configurations);
        }
        return this;
    }

    /**
     * Sets the configuration information for this tenant's
     * configured adapters.
     * 
     * @param configurations The configuration properties for this tenant's
     *                       configured adapters or {@code null} in order to
     *                       remove any existing configuration.
     * @return This tenant for command chaining.
     */
    @JsonIgnore
    public TenantObject setAdapterConfigurations(final JsonArray configurations) {
        if (configurations == null) {
            this.adapterConfigurations = null;
        } else {
            this.adapterConfigurations = new LinkedList<>();
            configurations.stream().filter(obj -> JsonObject.class.isInstance(obj)).forEach(config -> {
                addAdapterConfiguration((JsonObject) config);
            });
        }
        return this;
    }

    /**
     * Adds configuration information for a protocol adapter.
     * 
     * @param config The configuration properties to add.
     * @throws NullPointerException if config is {@code null}.
     * @throws IllegalArgumentException if the given configuration does not contain
     *                a <em>type</em> name.
     * @return This tenant for command chaining.
     * @throws NullPointerException if config is {@code null}.
     */
    public TenantObject addAdapterConfiguration(final JsonObject config) {

        Objects.requireNonNull(config);

        final Object type = config.getValue(TenantConstants.FIELD_ADAPTERS_TYPE);
        if (String.class.isInstance(type)) {
            if (adapterConfigurations == null) {
                adapterConfigurations = new LinkedList<>();
            }
            adapterConfigurations.add(config.getMap());
        } else {
            throw new IllegalArgumentException("adapter configuration must contain type field");
        }
        return this;
    }

    /**
     * Checks if a given protocol adapter is enabled for this tenant.
     * 
     * @param typeName The type name of the adapter.
     * @return {@code true} if this tenant and the given adapter are enabled.
     */
    @JsonIgnore
    public boolean isAdapterEnabled(final String typeName) {

        if (!isEnabled()) {
            return false;
        } else if (adapterConfigurations == null) {
            // all adapters are enabled
            return true;
        } else {
            final JsonObject config = getAdapterConfiguration(typeName);
            if (config == null) {
                // if not explicitly configured, the adapter is disabled by default
                return false;
            } else {
                return config.getBoolean(TenantConstants.FIELD_ENABLED, Boolean.FALSE);
            }
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

        return Optional.ofNullable(getAdapterConfiguration(typeName))
                .map(conf -> getProperty(conf, TenantConstants.FIELD_EXT, JsonObject.class))
                .map(extension -> getProperty(extension, TenantConstants.FIELD_MAX_TTD, Integer.class))
                .map(maxTtd -> maxTtd < 0 ? TenantConstants.DEFAULT_MAX_TTD : maxTtd)
                .orElse(TenantConstants.DEFAULT_MAX_TTD);
    }

    /**
     * Creates a TenantObject for a tenantId and the enabled property.
     *
     * @param tenantId The tenant for which the object is constructed.
     * @param enabled {@code true} if the tenant shall be enabled.
     * @return The TenantObject.
     * @throws NullPointerException if any of tenantId or enabled is {@code null}.
     */
    public static TenantObject from(final String tenantId, final Boolean enabled) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(enabled);

        final TenantObject result = new TenantObject();
        result.setTenantId(tenantId);
        result.setEnabled(enabled);
        return result;
    }

    /**
     * Creates a TenantObject from the enabled property.
     *
     * @param enabled {@code true} if the tenant shall be enabled.
     * @return The TenantObject.
     * @throws NullPointerException if enabled is {@code null}.
     */
    public static TenantObject from(final Boolean enabled) {

        Objects.requireNonNull(enabled);

        final TenantObject result = new TenantObject();
        result.setEnabled(enabled);
        return result;
    }

    /**
     * Creates new protocol adapter configuration properties.
     * 
     * @param type The adapter type.
     * @param enabled {@code true} if the adapter should be enabled.
     * @return The configuration properties.
     */
    public static JsonObject newAdapterConfig(final String type, final boolean enabled) {

        Objects.requireNonNull(type);
        return new JsonObject()
                .put(TenantConstants.FIELD_ADAPTERS_TYPE, type)
                .put(TenantConstants.FIELD_ENABLED, enabled);
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
        return getProperty(TenantConstants.FIELD_PAYLOAD_DEFAULTS, JsonObject.class, new JsonObject());
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
        setProperty(TenantConstants.FIELD_PAYLOAD_DEFAULTS, Objects.requireNonNull(defaultProperties));
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
