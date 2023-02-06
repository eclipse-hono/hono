/*******************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.deviceregistry.util;

import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.auth.HonoPasswordEncoder;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.credentials.PasswordCredential;
import org.eclipse.hono.service.management.credentials.PasswordSecret;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * A collection of utility methods for implementing device registries.
 *
 */
public final class DeviceRegistryUtils {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceRegistryUtils.class);

    private DeviceRegistryUtils() {
        // prevent instantiation
    }

    /**
     * Maps an error.
     *
     * @param <T> The type of result.
     * @param error The error to map.
     * @param tenantId The tenant that the error occurred for.
     * @return A future failed with the mapped error.
     */
    public static <T> Future<T> mapError(final Throwable error, final String tenantId) {
        if (error instanceof IllegalArgumentException) {
            return Future.failedFuture(new ClientErrorException(tenantId, HttpURLConnection.HTTP_BAD_REQUEST, error.getMessage()));
        }
        return Future.failedFuture(error);
    }

    /**
     * Converts tenant object of type {@link Tenant} to an object of type {@link TenantObject}.
     *
     * @param tenantId The identifier of the tenant.
     * @param source   The source tenant object.
     * @return The converted tenant object of type {@link TenantObject}
     * @throws NullPointerException if the tenantId or source is null.
     */
    public static JsonObject convertTenant(final String tenantId, final Tenant source) {
        return convertTenant(tenantId, source, false);
    }

    /**
     * Converts a {@link Tenant} instance to a {@link TenantObject}.
     *
     * @param tenantId The identifier of the tenant.
     * @param source The source tenant object.
     * @param filterAuthorities {@code true} if CAs which are not valid at this point in time should be filtered out.
     * @return The converted tenant object.
     * @throws NullPointerException if tenantId or source are {@code null}.
     */
    public static JsonObject convertTenant(final String tenantId, final Tenant source, final boolean filterAuthorities) {

        final Instant now = Instant.now();

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(source);

        final TenantObject target = TenantObject.from(tenantId, source.isEnabled());
        target.setResourceLimits(source.getResourceLimits());
        target.setTracingConfig(source.getTracing());

        Optional.ofNullable(source.getMinimumMessageSize())
            .ifPresent(size -> target.setMinimumMessageSize(size));

        Optional.ofNullable(source.getDefaults())
            .map(JsonObject::new)
            .ifPresent(defaults -> target.setDefaults(defaults));

        Optional.ofNullable(source.getAdapters())
            .filter(adapters -> !adapters.isEmpty())
            .map(adapters -> adapters.stream()
                            .map(adapter -> JsonObject.mapFrom(adapter))
                            .map(json -> json.mapTo(org.eclipse.hono.util.Adapter.class))
                            .collect(Collectors.toList()))
            .ifPresent(adapters -> target.setAdapters(adapters));

        Optional.ofNullable(source.getExtensions())
            .map(JsonObject::new)
            .ifPresent(extensions -> target.setProperty(RegistryManagementConstants.FIELD_EXT, extensions));

        Optional.ofNullable(source.getTrustedCertificateAuthorities())
            .map(list -> list.stream()
                    .filter(ca -> {
                        if (filterAuthorities) {
                            // filter out CAs which are not valid at this point in time
                            return !now.isBefore(ca.getNotBefore()) && !now.isAfter(ca.getNotAfter());
                        } else {
                            return true;
                        }
                    })
                    .map(ca -> JsonObject.mapFrom(ca))
                    .map(json -> {
                        // validity period is not included in TenantObject
                        json.remove(RegistryManagementConstants.FIELD_SECRETS_NOT_BEFORE);
                        json.remove(RegistryManagementConstants.FIELD_SECRETS_NOT_AFTER);
                        // remove the attributes that are internal to the device registry
                        // and not to be exposed to the adapters
                        json.remove(RegistryManagementConstants.FIELD_AUTO_PROVISION_AS_GATEWAY);
                        json.remove(RegistryManagementConstants.FIELD_AUTO_PROVISIONING_DEVICE_ID_TEMPLATE);
                        return json;
                    })
                    .collect(JsonArray::new, JsonArray::add, JsonArray::addAll))
            .ifPresent(authorities -> target.setProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, authorities));

        return JsonObject.mapFrom(target);
    }

    /**
     * Gets the cache directive corresponding to the given max age for the cache.
     *
     * @param cacheMaxAge the maximum period of time in seconds that the information
     *                    returned by the service's operations may be cached for.
     * @return the cache directive corresponding to the given max age for the cache.
     */
    public static CacheDirective getCacheDirective(final long cacheMaxAge) {
        if (cacheMaxAge > 0) {
            return CacheDirective.maxAgeDirective(cacheMaxAge);
        } else {
            return CacheDirective.noCacheDirective();
        }
    }

    /**
     * Gets a unique identifier generated using {@link UUID#randomUUID()}.
     *
     * @return The generated unique identifier.
     */
    public static String getUniqueIdentifier() {
        return UUID.randomUUID().toString();
    }

    /**
     * Gets the certificate of the device to be provisioned from the client context.
     *
     * @param tenantId The tenant to which the device belongs.
     * @param authId The authentication identifier.
     * @param clientContext The client context that can be used to get the X.509 certificate 
     *                      of the device to be provisioned.
     *                      <p>
     *                      If it is {@code null} then the result contains {@link Optional#empty()}.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method! An
     *             implementation should log (error) events on this span and it may set tags and use this span 
     *             as the parent for any spans created in this method.
     * @return A future indicating the outcome of the operation. If the operation succeeds, the
     *         retrieved certificate is returned. Else {@link Optional#empty()} is returned.
     * @throws NullPointerException if tenantId or authId is {@code null}.
     */
    public static Future<Optional<X509Certificate>> getCertificateFromClientContext(
            final String tenantId,
            final String authId,
            final JsonObject clientContext,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(authId);

        if (clientContext == null) {
            return Future.succeededFuture(Optional.empty());
        }

        try {
            final byte[] bytes = clientContext.getBinary(CredentialsConstants.FIELD_CLIENT_CERT);
            if (bytes == null) {
                return Future.succeededFuture(Optional.empty());
            }
            final CertificateFactory factory = CertificateFactory.getInstance("X.509");
            final X509Certificate cert = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(bytes));

            return Future.succeededFuture(Optional.of(cert));
        } catch (final IllegalArgumentException error) {
            LOG.error("failed to decode certificate from client context with authId [%s] for tenant [%s]:{}{}",
                    System.lineSeparator(),
                    clientContext.encodePrettily(),
                    error);
            TracingHelper.logError(span, "failed to decode certificate from client context", error);
            return Future.failedFuture(new ClientErrorException(
                    tenantId,
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    "failed to decode certificate from client context",
                    error));
        } catch (final CertificateException | ClassCastException error) {
            final String errorMessage = String.format(
                    "error getting certificate from client context with authId [%s] for tenant [%s]", authId, tenantId);
            LOG.error(errorMessage, error);
            TracingHelper.logError(span, errorMessage, error);
            return Future.failedFuture(new ClientErrorException(
                    tenantId,
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    errorMessage,
                    error));
        }
    }

    /**
     * Validates the given credentials for consistency.
     * <p>
     * The following checks are performed
     * <ol>
     * <li>The credential's <em>checkValidity</em> method is invoked and</li>
     * <li>if the given credentials object is of type {@link PasswordCredential}, for
     * each of the credential's hashed password secrets the secret's
     * {@link PasswordSecret#encode(HonoPasswordEncoder)}, {@link PasswordSecret#checkValidity()}
     * and {@link PasswordSecret#verifyHashAlgorithm(Set, int)} methods are invoked.</li>
     * </ol>
     *
     * @param credential The secret to validate.
     * @param passwordEncoder The password encoder.
     * @param hashAlgorithmsWhitelist The list of supported hashing algorithms for pre-hashed passwords.
     * @param maxBcryptCostFactor The maximum cost factor to use for bcrypt password hashes.
     * @throws IllegalStateException if any of the checks fail.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static void checkCredential(
            final CommonCredential credential,
            final HonoPasswordEncoder passwordEncoder,
            final Set<String> hashAlgorithmsWhitelist,
            final int maxBcryptCostFactor) {

        Objects.requireNonNull(credential);
        Objects.requireNonNull(passwordEncoder);
        Objects.requireNonNull(hashAlgorithmsWhitelist);

        credential.checkValidity();
        for (final var secret : credential.getSecrets()) {
            if (secret instanceof PasswordSecret passwordSecret ) {
                passwordSecret.encode(passwordEncoder);
                passwordSecret.verifyHashAlgorithm(hashAlgorithmsWhitelist, maxBcryptCostFactor);
            }
            secret.checkValidity();
        }
    }

    /**
     * Verifies that the elements of a list of credentials are unique regarding type and authentication
     * identifier.
     *
     * @param credentials The credentials to check.
     * @return A future indicating the outcome of the check.
     *         The future will be failed with a {@link ClientErrorException} if the elements are
     *         not unique.
     */
    public static Future<Void> assertTypeAndAuthIdUniqueness(final List<? extends CommonCredential> credentials) {

        final long uniqueAuthIdAndTypeCount = credentials.stream()
            .map(credential -> String.format("%s::%s", credential.getType(), credential.getAuthId()))
            .distinct()
            .count();

        if (credentials.size() > uniqueAuthIdAndTypeCount) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    "credentials must have unique (type, auth-id)"));
        } else {
            return Future.succeededFuture();
        }
    }

    /**
     * Checks if the properties in the given client context matches with the extension properties of the
     * given credential.
     * <p>
     * The matching properties in the client context and credential extensions are filtered out based 
     * on their property names. If their values are equal then {@code true} is returned. Otherwise {@code false}.
     * <p>
     * The properties that exist only in the client context but not in the extension properties are ignored.
     * If none of the properties in the client context are found in the credential extensions,
     * then {@code true} is returned.
     *
     * @param credential The credential object to match.
     * @param clientContext The client context, which contains properties that can be used to identify the device.
     * @return {@code true} if the properties from client context matches with those of the given credential,
     *         {@code false} otherwise.
     * @throws NullPointerException if credential is {@code null}.
     */
    public static boolean matchesWithClientContext(final JsonObject credential, final JsonObject clientContext) {

        Objects.requireNonNull(credential);

        final JsonObject extensionProperties = credential.getJsonObject(RegistryManagementConstants.FIELD_EXT,
                new JsonObject());

        if (Objects.isNull(clientContext) || clientContext.isEmpty() || extensionProperties.isEmpty()) {
            return true;
        }

        return clientContext.stream()
                .filter(entry -> Objects.nonNull(entry.getValue()))
                .allMatch(entry -> Optional
                        .ofNullable(extensionProperties.getValue(entry.getKey()))
                        .map(value -> {
                            LOG.debug("comparing client context property [name: {}, value: {}] to value on record: {}",
                                    entry.getKey(), entry.getValue(), value);
                            return value.equals(entry.getValue());
                        })
                        .orElse(true));

    }

    /**
     * Returns a regex expression based on the given filter value which can be used by the device registry 
     * implementations to support wildcard matching during search operations.
     * <p>
     * The below wildcard characters are supported.
     * <ol>
     * <li>`*` will match zero or any number of characters.</li>
     * <li>`?` will match exactly one character.</li>
     * </ol>
     * @param filterValue The value corresponding to the field to use for filtering.
     * @return The regex expression to use for filtering.
     * @throws NullPointerException if the filterValue is {@code null}
     */
    public static String getRegexExpressionForSearchOperation(final String filterValue) {
        Objects.requireNonNull(filterValue);

        return Collections.list(new StringTokenizer(filterValue, "*?", true))
                .stream()
                .map(token -> (String) token)
                .map(token -> {
                    if (token.equals("*")) {
                        return "(.*)";
                    }
                    if (token.equals("?")) {
                        return "(.)";
                    } else {
                        return Pattern.quote(token);
                    }
                })
                .collect(Collectors.joining("", "^", "$"));
    }
}
