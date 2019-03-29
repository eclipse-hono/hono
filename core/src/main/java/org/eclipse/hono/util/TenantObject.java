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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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
 * <a href="https://www.eclipse.org/hono/api/tenant-api/">Tenant API</a>.
 */
@JsonInclude(value = Include.NON_NULL)
public final class TenantObject extends JsonBackedValueObject {

    @JsonIgnore
    private Map<String, JsonObject> adapterConfigurations;
    @JsonIgnore
    private List<JsonObject> trustedCaList;
    @JsonIgnore
    private List<TrustAnchor> trustAnchors;
    @JsonIgnore
    private List<X500Principal> subjectNames;
    //<subjectDn, configs>
    @JsonIgnore
    private Map<String, List<JsonObject>> trustConfigurations;

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
        return (String) getProperty(TenantConstants.FIELD_PAYLOAD_TENANT_ID);
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
        return (Boolean) getProperty(TenantConstants.FIELD_ENABLED, true);
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
     * certificate authority. This method assumes that this tenant is
     * configured with a single trusted CA.
     * 
     * @return The DN or {@code null} if no CA has been set.
     */
    @JsonIgnore
    public X500Principal getTrustedCaSubjectDn() {
        final List<X500Principal> subjectdns = Optional.ofNullable(getTrustedCaSubjectNames()).orElse(null);
        return subjectdns == null ? null : subjectdns.get(0);
    }

    /**
     * Sets the trusted anchor for this tenant's Trusted CA using the given public key and subject DN.
     * 
     * @param publicKey The trusted CA's public key.
     * @param subjectDn The trusted CA's subject DN.
     * 
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

        // return addTrustedCaConfiguration(trustedCa);
    }

    /**
     * Sets the trusted certificate authority to use for authenticating
     * devices of this tenant. The certificate must contain a {@code NonNull} and non-empty
     * subject DN value.
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
                    .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, certificate.getSubjectX500Principal().getName())
                    .put(TenantConstants.FIELD_PAYLOAD_CERT, certificate.getEncoded());
            return addTrustedCaConfiguration(trustedCa);
        } catch (final CertificateEncodingException e) {
            throw new IllegalArgumentException("cannot encode certificate");
        }
    }

    /**
     * Gets the trusted certificate authority configured for this tenant.
     * This method assumes that the tenant is configured with a single Trusted CA.
     * <p>
     * This method tries to extract the certificate from the data contained in
     * the JSON object of the <em>trusted-ca</em> property.
     * The value of the JSON object's <em>cert</em> property is expected to contain
     * the Base64 encoded <em>binary</em> DER-encoding of the certificate, i.e. the same
     * value as the <em>printable</em> form but without the leading
     * {@code -----BEGIN CERTIFICATE-----} and trailing {@code -----END CERTIFICATE-----}.
     * 
     * 
     * @return The certificate or {@code null} if no certificate authority
     *         has been set.
     * @throws CertificateException if the value of <em>cert</em> property cannot be parsed into
     *              an X.509 certificate.
     */
    @JsonIgnore
    public X509Certificate getTrustedCertificateAuthority() throws CertificateException {

        final List<X509Certificate> certs = getTrustedCertificateAuthorities();
        if (certs == null) {
            return null;
        } else {
            return certs.get(0);
        }
    }

    /**
     * Gets the trust anchor for this tenant. This method assumes that this tenant is
     * configured with a single trusted CA.
     * <p>
     * This method tries to create the trust anchor based on the information
     * from the JSON array contained in the <em>trusted-ca-store</em> property. The JSON
     * array should contain a single JSON object, which stores the config. info for this
     * tenant's trusted CA.
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
     *              cannot be parsed into a trust anchor, e.g. because of unsupported
     *              key type, malformed certificate or public key encoding etc.
     */
    @JsonIgnore
    public TrustAnchor getTrustAnchor() throws GeneralSecurityException {
        return (getTrustAnchors() == null ? null : getTrustAnchors().get(0));
    }

    @JsonIgnore
    private TrustAnchor getTrustAnchorForPublicKey(final JsonObject keyProps) throws GeneralSecurityException {

        if (keyProps == null) {
            return null;
        } else {
            final String subjectDn = getProperty(keyProps, TenantConstants.FIELD_PAYLOAD_SUBJECT_DN);
            final String encodedKey = getProperty(keyProps, TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY);

            if (subjectDn == null || encodedKey == null) {
                return null;
            } else {
                try {
                    final String type = getProperty(keyProps, TenantConstants.FIELD_PAYLOAD_KEY_ALGORITHM, "RSA");
                    final X509EncodedKeySpec keySpec = new X509EncodedKeySpec(Base64.getDecoder().decode(encodedKey));
                    final KeyFactory factory = KeyFactory.getInstance(type);
                    final PublicKey publicKey = factory.generatePublic(keySpec);
                    return new TrustAnchor(subjectDn, publicKey, null);
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
            adapterConfigurations.values().forEach(config -> result.add(((JsonObject) config).getMap()));
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
            adapterConfigurations.values().forEach(config -> result.add((JsonObject) config));
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
     * Gets the maximum number of seconds that a protocol adapter should
     * wait for a command targeted at a device.
     * <p>
     * The returned value is determined as follows:
     * <ol>
     * <li>if this tenant configuration contains an integer typed {@link TenantConstants#FIELD_MAX_TTD}
     * property specific to the given adapter type, then return its value if it is &gt;= 0</li>
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

        final int maxTtd = Optional.ofNullable(getAdapterConfiguration(typeName)).map(conf -> {
            return Optional.ofNullable(getProperty(conf, TenantConstants.FIELD_MAX_TTD)).map(obj -> {
                return (Integer) obj;
            }).orElse(TenantConstants.DEFAULT_MAX_TTD);
        }).orElse(Optional.ofNullable(getProperty(TenantConstants.FIELD_MAX_TTD)).map(obj -> {
            return (Integer) obj;
        }).orElse(TenantConstants.DEFAULT_MAX_TTD));

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
        return getProperty(TenantConstants.FIELD_RESOURCE_LIMITS);
    }

    /**
     * Sets the resource limits for the tenant.
     *
     * @param resourceLimits The resource limits configuration to add.
     * @return The TenantObject.
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
        return getProperty(TenantConstants.FIELD_PAYLOAD_DEFAULTS, new JsonObject());
    }

    /**
     * Sets the default property values to use for all devices of this tenant.
     * 
     * @param defaultProperties The properties or an empty JSON object if no default properties
     *         have been defined for this tenant.
     */
    @JsonIgnore
    public void setDefaults(final JsonObject defaultProperties) {
        setProperty(TenantConstants.FIELD_PAYLOAD_DEFAULTS, Objects.requireNonNull(defaultProperties));
    }

    /** 
     * Sets the configuration information for this tenant's configured trusted CA's. The RSA public key type is assumed
     * if the trusted CA JSON object does not contain a type property.
     * 
     * @param configurations A list of configuration properties, one set of properties for each configured trusted CA.
     * @return This tenant for command chaining.
     * 
     * @throws IllegalArgumentException if the any of the trusted CA configuration is missing the required
     *             <em>subject-dn</em> property or either the {@code public-key} or {@code cert} property does not
     *             contain a valid Base64 encoding.
     */
    @JsonProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA)
    public TenantObject setTrustStore(final List<Map<String, Object>> configurations) {
        if (configurations == null) {
            this.trustConfigurations = null;
        } else {
            configurations.stream().forEach(map -> {
                final JsonObject trustedCa = new JsonObject(map);
                addTrustedCaConfiguration(trustedCa);
            });
        }
        return this;
    }

    /**
     * Gets the configuration information for this tenant's
     * configured trusted certificate authorities.
     * 
     * @return An unmodifiable list of configuration properties or
     *         {@code null} if no trust configuration has been
     *         set for this tenant.
     */
    @JsonProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA)
    public List<Map<String, Object>> getTrustConfigurationsAsMaps() {
        if (trustConfigurations == null) {
            return null;
        } else {
            final List<Map<String, Object>> result = new LinkedList<>();
            trustConfigurations.values().stream().flatMap(Collection::stream).forEach(config -> result.add(config.getMap()));
            return result;
        }
    }

    /**
     * Sets the configuration information for this tenant's
     * configured trusted certificate authorities.
     * 
     * @param configurations The configuration properties for this tenant's
     *                       configured trusted CAs or {@code null} in order to
     *                       remove any existing configuration.
     * @return This tenant for command chaining.
     * 
     * @throws IllegalArgumentException if the required {@code subject-dn} property is missing or
     *          if the {@code public-key} or {@code cert} property does not contain a valid Base64 encoding.
     */
    @JsonIgnore
    public TenantObject setTrustConfiguration(final JsonArray configurations) {
        if (configurations == null) {
            this.trustConfigurations = null;
        } else {
            configurations.stream().filter(obj -> JsonObject.class.isInstance(obj)).forEach(config -> addTrustedCaConfiguration((JsonObject) config));
        }
        return this;
    }

    /**
     * Get the list of subject distinguished names that are configured for this
     * tenant's trusted certificate authorities. The list of subject DNs is cached on a first call
     * and return in subsequent invocations of this method.
     * 
     * @return The list of subject distinguished names or {@code null} if no CA has been set for this tenant.
     */
    @JsonIgnore
    public List<X500Principal> getTrustedCaSubjectNames() {
        if (subjectNames != null) {
            return subjectNames;
        }
        if (trustConfigurations == null) {
            return null;
        } else {
            final List<JsonObject> trustedcas = Optional.ofNullable(getTrustConfigurations()).orElse(Collections.emptyList());
            trustedcas.forEach(config -> {
                if (subjectNames == null) {
                    subjectNames = new ArrayList<>();
                }
                final String subjectDn = getProperty(config, TenantConstants.FIELD_PAYLOAD_SUBJECT_DN);
                subjectNames.add(new X500Principal(subjectDn));
            });
            return subjectNames;
        }
    }

    /**
     * Get the list of trust anchors that are configured for this tenant. The list of trust anchors is created and cached the first
     * time this method is called. Subsequent calls returns the cached list.
     * 
     * @return The list of trust anchors or {@code null} if this tenant is does not contain any trusted CA.
     * @throws GeneralSecurityException if the value of the <em>trusted-ca-store</em> property
     *              cannot be parsed into a list of trust anchor, e.g. because of unsupported
     *              key type, malformed certificate or public key encoding etc. 
     */
    @JsonIgnore
    public List<TrustAnchor> getTrustAnchors() throws GeneralSecurityException {
        if (trustAnchors != null) {
            return trustAnchors;
        } else {
            trustAnchors = new ArrayList<>();
            final List<JsonObject> configs = Optional.ofNullable(getTrustConfigurations()).orElse(Collections.emptyList());
            for (JsonObject config : configs) {
                final TrustAnchor anchor = getTrustAnchor(config);
                if (anchor != null) {
                    trustAnchors.add(anchor);
                }
            }
            return trustAnchors;
        }

    }

    /**
     * Get the list of trust anchors matching the specified subject DN. The list of trust anchors is created and cached
     * the first time this method is called. Subsequent calls returns the cached list.
     * 
     * @param subjectDn The subjectDn of the configured trusted certificate authorities for this tenant.
     * @return The list of trust anchors matching the specified tenant or {@code null} if there is no trusted CA
     *         matching the specified subject DN.
     * @throws GeneralSecurityException if an instance of a trust anchor cannot be created from a configured trusted CA 
     *                  JSON object (e.g due to TODO)
     */
    @JsonIgnore
    public List<TrustAnchor> getTrustAnchors(final String subjectDn) throws GeneralSecurityException {
        List<TrustAnchor> anchors = null;
        final List<JsonObject> configs = Optional.ofNullable(getTrustedCaConfigurations(subjectDn))
                        .orElse(Collections.emptyList());
        for (JsonObject config : configs) {
            if (anchors == null) {
                anchors = new ArrayList<>();
            }
            anchors.add(getTrustAnchor(config));
        }
       return anchors;
    }

    /**
     * Get all the trusted certificate authorities configured for this tenant.
     * 
     * @return A list of trusted CA configurations for this tenant 
     *         or {@code null} if no trusted CA has been set for this tenant.
     */
    @JsonIgnore
    public List<JsonObject> getTrustConfigurations() {
        if (trustedCaList != null) {
            return trustedCaList;
        } else {
            if (trustConfigurations == null) {
                return null;
            } else {
                trustedCaList = new ArrayList<>();
                trustedCaList = trustConfigurations.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
                return trustedCaList;
            }
        }
    }

    /**
     * Gets all the trusted CA configurations matching the specified subject DN.
     * 
     * @param subjectDn The subject DN to match for.
     * @return A list of trusted CA configurations matching the specified subjectDn or
     *          {@code null} if no configuration exist for the specified subjectDn.
     */
    @JsonIgnore
    private List<JsonObject> getTrustedCaConfigurations(final String subjectDn) {
        if (trustConfigurations == null) {
            return null;
        } else {
            return trustConfigurations.get(subjectDn);
        }
    }

    @JsonIgnore
    private TrustAnchor getTrustAnchor(final JsonObject trustedCa) throws GeneralSecurityException {
        final X509Certificate cert = getTrustedCertificateAuthority(trustedCa);
        if (cert != null) {
            return new TrustAnchor(cert, null);
        } else {
            return getTrustAnchorForPublicKey(trustedCa);
        }
    }

    /**
     * Get the X.509 certificate from the specified trusted CA configuration.
     * 
     * @param trustedCa The trusted CA configuration to construct an X.509 certificate from.
     * 
     * @return The certificate or {@code null} if no certificate authority
     *          has been set.
     * @throws CertificateException if the value of <em>cert</em> property cannot be parsed into
     *              an X.509 certificate.
     */
    @JsonIgnore
    private X509Certificate getTrustedCertificateAuthority(final JsonObject trustedCa) throws CertificateException {

        final String obj = (String) trustedCa.getValue(TenantConstants.FIELD_PAYLOAD_CERT);
        if (obj != null) {
            try {
                final byte[] derEncodedCert = Base64.getDecoder().decode(((String) obj));
                final CertificateFactory factory = CertificateFactory.getInstance("X.509");
                return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(derEncodedCert));
            } catch (final IllegalArgumentException e) {
                // ignore -> should never happen
                throw new CertificateParsingException("cert property does not contain valid Base64 encoding scheme", e);
            }
        } else {
            return null;
        }

    }

    /**
     * Gets the single trusted CA that has been configured for this tenant. This method assumes that this tenant
     * is configured with a single trusted CA.
     * 
     * @return The trusted CA that has been set for this tenant or {@code null} if no trusted CA
     *         has been set.
     */
    @JsonIgnore
    private JsonObject getSingleTrustedCa() {
        if (trustConfigurations == null) {
            return null;
        } else {
            return (JsonObject) trustConfigurations.keySet().stream().map(key -> trustConfigurations.get(key));
        }
    }

    private TenantObject addTrustedCaConfiguration(final JsonObject trustedCa) {
        final String subjectDn = getProperty(trustedCa, TenantConstants.FIELD_PAYLOAD_SUBJECT_DN);
        if (trustConfigurations == null) {
            trustConfigurations = new HashMap<>();
        }
        List<JsonObject> trustedCas = trustConfigurations.get(subjectDn);
        if (trustedCas == null) {
            trustedCas = new ArrayList<>();
        }
        trustedCas.add(trustedCa);
        trustConfigurations.put(subjectDn, trustedCas);
        return this;
    }

    /**
     * Like {@link #getTrustAnchors(String)}, this method returns a list of X.509 certificates matching the specified
     * subject DN.
     * 
     * @param subjectDn The subject DN of the certificates to retrieve.
     * 
     * @return The list of trusted CA certificates or {@code null} if no trusted CA configuration with the given
     *         subject DN has been set for this tenant.
     * 
     * @throws CertificateException if any of the configured trusted CA JSON objects cannot be parsed into a
     *             certificate.
     */
    @JsonIgnore
    public List<X509Certificate> getTrustedCertificateAuthorities(final String subjectDn) throws CertificateException {
        final List<JsonObject> configs = Optional.ofNullable(getTrustedCaConfigurations(subjectDn)).orElse(Collections.emptyList());
        List<X509Certificate> certs = null;
        for (JsonObject config : configs) {
            if (certs == null) {
                certs = new ArrayList<>();
            }
            certs.add(getTrustedCertificateAuthority(config));
        }
        return certs;
    }

    /**
     * Gets all the trusted certificate authorities configured for this tenant.
     * 
     * @return The list of X.509 certificates or {@code null} if no certificate authority
     *         has been set.
     *         
     * @throws CertificateException CertificateException if any of the trusted CA configurations
     *         cannot be parsed into an X.509 certificate.
     */
    @JsonIgnore
    public List<X509Certificate> getTrustedCertificateAuthorities() throws CertificateException {
        final List<JsonObject> configs = getTrustConfigurations();
        if (configs == null) {
            return null;
        } else {
            final List<X509Certificate> result = new ArrayList<>();
            for (final JsonObject config : configs) {
                final X509Certificate cert = getTrustedCertificateAuthority(config);
                if (cert != null) {
                    result.add(cert);
                }
            }
            return result;
        }
    }
}
