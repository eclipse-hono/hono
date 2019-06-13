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

package org.eclipse.hono.service.tenant;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantResult;

import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/**
 * A base class for implementing {@link CompleteTenantService}.
 * <p>
 * In particular, this base class provides support for receiving service invocation request messages
 * via vert.x' event bus and route them to specific methods corresponding to the operation indicated
 * in the message.
 *
 * @param <T> The type of configuration properties this service requires.
 * @deprecated - Use {@link TenantService} and {@link org.eclipse.hono.service.management.tenant.TenantManagementService} instead.
 */
@Deprecated
public abstract class CompleteBaseTenantService<T> extends BaseTenantService<T> implements CompleteTenantService {

    /**
     * Processes a Tenant API request message received via the vert.x event bus.
     * <p>
     * This method validates the request payload against the Tenant API specification
     * before invoking the corresponding {@code TenantService} methods.
     *
     * @param request The request message.
     * @return A future indicating the outcome of the service invocation.
     * @throws NullPointerException If the request message is {@code null}.
     */
    @Override
    public final Future<EventBusMessage> processRequest(final EventBusMessage request) {

        Objects.requireNonNull(request);

        switch (TenantConstants.TenantAction.from(request.getOperation())) {
        case add:
            return processAddRequest(request);
        case get:
            return processGetRequest(request);
        case update:
            return processUpdateRequest(request);
        case remove:
            return processRemoveRequest(request);
        default:
            return processCustomTenantMessage(request);
        }
    }


    private Future<EventBusMessage> processAddRequest(final EventBusMessage request) {

        final String tenantId = request.getTenant();

        if (tenantId == null) {
            log.debug("request does not contain mandatory property [{}]",
                    MessageHelper.APP_PROPERTY_TENANT_ID);
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        }

        final JsonObject payload = getRequestPayload(request.getJsonPayload());
        if (isValidRequestPayload(payload)) {
            log.debug("creating tenant [{}]", tenantId);
            final Future<TenantResult<JsonObject>> addResult = Future.future();
            addNotPresentFieldsWithDefaultValuesForTenant(payload);
            add(tenantId, payload, addResult);
            return addResult.map(tr -> {
                return request.getResponse(tr.getStatus())
                        .setJsonPayload(tr.getPayload())
                        .setCacheDirective(tr.getCacheDirective());
            });
        } else {
            log.debug("request contains malformed payload");
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        }
    }

    private Future<EventBusMessage> processUpdateRequest(final EventBusMessage request) {

        final String tenantId = request.getTenant();
        final JsonObject payload = getRequestPayload(request.getJsonPayload());

        if (tenantId == null) {
            log.debug("request does not contain mandatory property [{}]",
                    MessageHelper.APP_PROPERTY_TENANT_ID);
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else if (isValidRequestPayload(payload)) {
            log.debug("updating tenant [{}]", tenantId);
            final Future<TenantResult<JsonObject>> updateResult = Future.future();
            addNotPresentFieldsWithDefaultValuesForTenant(payload);
            update(tenantId, payload, updateResult);
            return updateResult.map(tr -> {
                return request.getResponse(tr.getStatus())
                        .setJsonPayload(tr.getPayload())
                        .setCacheDirective(tr.getCacheDirective());
            });
        } else {
            log.debug("request contains malformed payload");
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        }
    }

    private Future<EventBusMessage> processRemoveRequest(final EventBusMessage request) {

        final String tenantId = request.getTenant();

        if (tenantId == null) {
            log.debug("request does not contain mandatory property [{}]",
                    MessageHelper.APP_PROPERTY_TENANT_ID);
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            log.debug("deleting tenant [{}]", tenantId);
            final Future<TenantResult<JsonObject>> removeResult = Future.future();
            remove(tenantId, removeResult);
            return removeResult.map(tr -> {
                return request.getResponse(tr.getStatus())
                        .setJsonPayload(tr.getPayload())
                        .setCacheDirective(tr.getCacheDirective());
            });
        }
    }

    /**
     * Checks the request payload for validity.
     *
     * @param payload The payload to check.
     * @return boolean The result of the check : {@link Boolean#TRUE} if the payload is valid, {@link Boolean#FALSE} otherwise.
     * @throws NullPointerException If the payload is {@code null}.
     */
    private boolean isValidRequestPayload(final JsonObject payload) {

        return hasValidAdapterSpec(payload) && hasValidTrustedCaSpec(payload);
    }

    /**
     * Checks if a payload contains a valid protocol adapter specification.
     *
     * @param payload The payload to check.
     * @return boolean {@code true} if the payload is valid.
     */
    private boolean hasValidAdapterSpec(final JsonObject payload) {

        final Object adaptersObj = payload.getValue(TenantConstants.FIELD_ADAPTERS);
        if (adaptersObj instanceof JsonArray) {

            final JsonArray adapters = (JsonArray) adaptersObj;
            if (adapters.size() == 0) {
                // if given, adapters config array must not be empty
                return false;
            } else {
                final boolean containsInvalidAdapter = adapters.stream()
                        .anyMatch(obj -> !(obj instanceof JsonObject) ||
                                    !((JsonObject) obj).containsKey(TenantConstants.FIELD_ADAPTERS_TYPE));
                if (containsInvalidAdapter) {
                    return false;
                }
            }
        } else if (adaptersObj != null) {
            return false;
        }
        return true;
    }

    /**
     * Ensure that the <em>public-key</em> or <em>cert</em> property of the JSON object contains a valid Base64 DER encoded value.
     * This method also validates that the trusted CA config also contains the mandatory <em>subject-dn</em>
     * property.
     * 
     * @param trustedCa The JSON object containing either the <em>public-key</em> or the <em>cert</em> property.
     * @return true if the trusted CA object is valid (i.e it can be parsed into either an X.509 certificate or the
     *         public key can be generated) or false otherwise.
     */
    private boolean isValidTrustedCaSpec(final JsonObject trustedCa) {

        final Object encodedCert = trustedCa.getValue(TenantConstants.FIELD_PAYLOAD_CERT);
        final Object encodedKey = trustedCa.getValue(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY);
        final Object subjectDn = trustedCa.getValue(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN);

        if (!String.class.isInstance(subjectDn)) {
            return false;
        }
        if (encodedCert != null && encodedKey == null) {
            // validate the certificate
            return isEncodedCertificate(encodedCert);
        } else if (encodedKey != null && encodedCert == null) {
            // validate public-key
            final String algorithmName = Optional
                    .ofNullable((String) trustedCa.getValue(TenantConstants.FIELD_ADAPTERS_TYPE)).orElse("RSA");
            return isEncodedPublicKey(encodedKey, algorithmName);
        } else {
            // Either the trusted CA configuration:
            // contains both a cert and a public-key property
            // or they are both missing
            return false;
        }
    }

    private boolean isEncodedCertificate(final Object encodedCert) {
        if (String.class.isInstance(encodedCert)) {
            try {
                CertificateFactory.getInstance("X.509").generateCertificate(
                        new ByteArrayInputStream(Base64.getDecoder().decode((String) encodedCert)));
                return true;
            } catch (final CertificateException | IllegalArgumentException e) {
                return false;
            }
        } else {
            return false;
        }
    }

    private boolean isEncodedPublicKey(final Object encodedKey, final String algorithmName) {
        if (String.class.isInstance(encodedKey)) {
            try {
                KeyFactory.getInstance(algorithmName)
                        .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode((String) encodedKey)));
                return true;
            } catch (final GeneralSecurityException | IllegalArgumentException e) {
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * Checks if a tenant payload contains a valid trusted CA specification.
     *
     * @param payload The tenant payload to check.
     * @return boolean {@code true} if the tenant payload is valid.
     */
    private boolean hasValidTrustedCaSpec(final JsonObject payload) {

        final Object object = payload.getValue(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA);
        if (object == null) {
            return true;
        } else if (JsonObject.class.isInstance(object)) {
            return isValidTrustedCaSpec((JsonObject) object);
        } else if (JsonArray.class.isInstance(object)){
            final JsonArray trustConfigs = (JsonArray) object;
            return trustConfigs.size() > 0 && isValidTrustedCaSpec(trustConfigs);
        } else {
            return false;
        }
    }

    private boolean isValidTrustedCaSpec(final JsonArray trustConfigs) {
        final boolean containsInvalidTrustedCa = trustConfigs.stream()
                .anyMatch(trustedCa -> !JsonObject.class.isInstance(trustedCa) || !isValidTrustedCaSpec((JsonObject) trustedCa));
        return !containsInvalidTrustedCa;
    }

    /**
     * Add default values for optional fields that are not filled in the payload.
     * <p>
     * Payload should be checked for validity first, there is no error handling inside this method anymore.
     * </p>
     *
     * @param checkedPayload The checked payload to add optional fields to.
     * @throws ClassCastException If the {@link TenantConstants#FIELD_ADAPTERS_TYPE} element is not a {@link JsonArray}
     *       or the JsonArray contains elements that are not of type {@link JsonObject}.
     */
    protected final void addNotPresentFieldsWithDefaultValuesForTenant(final JsonObject checkedPayload) {
        if (!checkedPayload.containsKey(TenantConstants.FIELD_ENABLED)) {
            log.trace("adding 'enabled' key to payload");
            checkedPayload.put(TenantConstants.FIELD_ENABLED, Boolean.TRUE);
        }

        final JsonArray adapters = checkedPayload.getJsonArray(TenantConstants.FIELD_ADAPTERS);
        if (adapters != null) {
            adapters.forEach(elem -> addNotPresentFieldsWithDefaultValuesForAdapter((JsonObject) elem));
        }
    }

    private void addNotPresentFieldsWithDefaultValuesForAdapter(final JsonObject adapter) {
        if (!adapter.containsKey(TenantConstants.FIELD_ENABLED)) {
            log.trace("adding 'enabled' key to payload");
            adapter.put(TenantConstants.FIELD_ENABLED, Boolean.TRUE);
        }

        if (!adapter.containsKey(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED)) {
            log.trace("adding 'device-authentication-required' key to adapter payload");
            adapter.put(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, Boolean.TRUE);
        }
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void add(final String tenantId, final JsonObject tenantObj, final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void update(final String tenantId, final JsonObject tenantObj, final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void remove(final String tenantId, final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }
}
