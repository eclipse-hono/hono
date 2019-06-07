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

import java.io.ByteArrayInputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.security.auth.x500.X500Principal;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Encapsulates the tenant information that was found by the get operation of the
 * <a href="https://www.eclipse.org/hono/docs/latest/api/tenant-api/">Tenant API</a>.
 */
@JsonInclude(value = Include.NON_NULL)
public final class TenantObject extends JsonBackedValueObject {

    @JsonIgnore
    private Map<String, JsonObject> adapterConfigurations;
    @JsonIgnore
    private TrustAnchor trustAnchor;

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
     * Gets the subject DN of this tenant's configured trusted
     * certificate authority.
     * 
     * @return The DN or {@code null} if no CA has been set.
     */
    @JsonIgnore
    public X500Principal getTrustedCaSubjectDn() {

        final JsonObject trustedCa = getProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, JsonObject.class);
        if (trustedCa == null) {
            return null;
        } else {
            return Optional
                    .ofNullable(getProperty(trustedCa, TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, String.class))
                    .map(dn -> new X500Principal(dn)).orElse(null);
        }
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
        return setProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, trustedCa);
    }

    /**
     * Sets the trusted certificate authority to use for authenticating
     * devices of this tenant.
     * 
     * @param certificate The CA certificate.
     * @return This tenant for command chaining.
     * @throws NullPointerException if certificate is {@code null}.
     * @throws IllegalArgumentException if the certificate cannot be (binary) encoded.
     */
    @JsonIgnore
    public TenantObject setTrustAnchor(final X509Certificate certificate) {

        Objects.requireNonNull(certificate);

        try {
            final JsonObject trustedCa = new JsonObject()
                    .put(TenantConstants.FIELD_PAYLOAD_CERT, certificate.getEncoded());
            return setProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, trustedCa);
        } catch (final CertificateEncodingException e) {
            throw new IllegalArgumentException("cannot encode certificate");
        }
    }

    /**
     * Gets the trusted certificate authority configured for this tenant.
     * <p>
     * This method tries to extract the certificate from the data contained in
     * the JSON object of the <em>trusted-ca</em> property.
     * The value of the JSON object's <em>cert</em> property is expected to contain
     * the Base64 encoded <em>binary</em> DER-encoding of the certificate, i.e. the same
     * value as the <em>printable</em> form but without the leading
     * {@code -----BEGIN CERTIFICATE-----} and trailing {@code -----END CERTIFICATE-----}.
     * 
     * @return The certificate or {@code null} if no certificate authority
     *         has been set.
     * @throws CertificateException if the value of <em>cert</em> property cannot be parsed into
     *             an X.509 certificate.
     */
    @JsonIgnore
    public X509Certificate getTrustedCertificateAuthority() throws CertificateException {

        final JsonObject trustedCa = getProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, JsonObject.class);
        if (trustedCa == null) {
            return null;
        } else {
            final Object obj = trustedCa.getValue(TenantConstants.FIELD_PAYLOAD_CERT);
            if (obj instanceof String) {
                try {
                    final byte[] derEncodedCert = Base64.getDecoder().decode(((String) obj));
                    final CertificateFactory factory = CertificateFactory.getInstance("X.509");
                    return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(derEncodedCert));
                } catch (final IllegalArgumentException e) {
                    // Base64 decoding failed
                    throw new CertificateParsingException("cert property does not contain valid Base64 scheme", e);
                }
            } else {
                return null;
            }
        }
    }

    /**
     * Gets the trust anchor for this tenant.
     * <p>
     * This method tries to create the trust anchor based on the information
     * from the JSON object contained in the <em>trusted-ca</em> property.
     * <ol>
     * <li>If the object contains a <em>cert</em> property then its content is
     * expected to contain the Base64 encoded (binary) DER encoding of the
     * trusted certificate. The returned trust anchor will contain this certificate.</li>
     * <li>Otherwise, if the object contains a <em>public-key</em> and a <em>subject-dn</em>
     * property, then the public key property is expected to contain the Base64 encoded
     * DER encoding of the trusted certificate's public key. The returned trust anchor
     * will contain this public key.</li>
     * <li>Otherwise, this method returns {@code null}.</li>
     * </ol>
     * <p>
     * Once a (non {@code null}) trust anchor has been created, it will be cached and
     * returned on subsequent invocations of this method.
     * 
     * @return The trust anchor or {@code null} if no trusted certificate authority
     *         has been set.
     * @throws GeneralSecurityException if the value of the <em>trusted-ca</em> property
     *             cannot be parsed into a trust anchor, e.g. because of unsupported
     *             key type, malformed certificate or public key encoding etc. 
     */
    @JsonIgnore
    public TrustAnchor getTrustAnchor() throws GeneralSecurityException {

        if (trustAnchor != null) {
            return trustAnchor;
        } else {
            final X509Certificate cert = getTrustedCertificateAuthority();
            if (cert != null) {
                trustAnchor = new TrustAnchor(cert, null);
                return trustAnchor;
            } else {
                return getTrustAnchorForPublicKey(
                        getProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, JsonObject.class));
            }
        }
    }

    @JsonIgnore
    private TrustAnchor getTrustAnchorForPublicKey(final JsonObject keyProps) throws GeneralSecurityException {

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
                    trustAnchor = new TrustAnchor(subjectDn, publicKey, null);
                    return trustAnchor;
                } catch (final IllegalArgumentException e) {
                    // Base64 decoding failed
                    throw new InvalidKeySpecException("cannot decode Base64 encoded public key", e);
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
            final List<Map<String, Object>> result = new LinkedList<>();
            adapterConfigurations.values().forEach(config -> result.add(config.getMap()));
            return result;
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
            adapterConfigurations.values().forEach(config -> result.add(config));
            return result;
        }
    }

    /**
     * Gets the configuration properties for a protocol adapter.
     * 
     * @param type The adapter's type.
     * @return The configuration properties or {@code null} if no specific
     *         properties have been set.
     */
    public JsonObject getAdapterConfiguration(final String type) {
        if (adapterConfigurations == null) {
            return null;
        } else {
            return adapterConfigurations.get(type);
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
     * @throws NullPointerException if the list is {@code null}.
     * @return This tenant for command chaining.
     */
    @JsonProperty(TenantConstants.FIELD_ADAPTERS)
    public TenantObject setAdapterConfigurations(final List<Map<String, Object>> configurations) {
        if (configurations == null) {
            this.adapterConfigurations = null;
        } else {
            configurations.stream().forEach(map -> {
                final JsonObject config = new JsonObject(map);
                addAdapterConfiguration(config);
            });
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
            this.adapterConfigurations = new HashMap<>();
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
     */
    public TenantObject addAdapterConfiguration(final JsonObject config) {

        final Object type = config.getValue(TenantConstants.FIELD_ADAPTERS_TYPE);
        if (String.class.isInstance(type)) {
            if (adapterConfigurations == null) {
                adapterConfigurations= new HashMap<>();
            }
            adapterConfigurations.put((String) type, config);
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
     * <li>if this tenant configuration contains an integer typed {@link TenantConstants#FIELD_MAX_TTD} property
     * specific to the given adapter type, then return its value if it is &gt;= 0</li>
     * <li>otherwise, if this tenant configuration contains a general integer typed
     * {@link TenantConstants#FIELD_MAX_TTD} property, then return its value if it is &gt;= 0</li>
     * <li>otherwise, return {@link TenantConstants#DEFAULT_MAX_TTD}</li>
     * </ol>
     * 
     * @param typeName The type of protocol adapter to get the TTD for.
     * @return The number of seconds.
     */
    @JsonIgnore
    public int getMaxTimeUntilDisconnect(final String typeName) {

        Objects.requireNonNull(typeName);

        final int maxTtd = Optional
                .ofNullable(getAdapterConfiguration(typeName))
                .map(conf -> {
                    return Optional.ofNullable(getProperty(conf, TenantConstants.FIELD_MAX_TTD, Integer.class))
                            .orElse(TenantConstants.DEFAULT_MAX_TTD);
                })
                .orElse(Optional.ofNullable(getProperty(TenantConstants.FIELD_MAX_TTD, Integer.class))
                        .orElse(TenantConstants.DEFAULT_MAX_TTD));

        if (maxTtd < 0) {
            return TenantConstants.DEFAULT_MAX_TTD;
        } else {
            return maxTtd;
        }
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
    @JsonIgnore
    public JsonObject getResourceLimits() {
        return getProperty(TenantConstants.FIELD_RESOURCE_LIMITS, JsonObject.class);
    }

    /**
     * Sets the resource limits for the tenant.
     *
     * @param resourceLimits The resource limits configuration to add.
     * @return This tenant for command chaining.
     * @throws NullPointerException if resource limits to be set is {@code null}.
     */
    @JsonIgnore
    public TenantObject setResourceLimits(final JsonObject resourceLimits) {
        Objects.requireNonNull(resourceLimits);
        return setProperty(TenantConstants.FIELD_RESOURCE_LIMITS, resourceLimits);
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
}
