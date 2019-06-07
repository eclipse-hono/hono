/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.management.credentials;

import static org.eclipse.hono.service.management.Util.newChildSpan;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.EventBusService;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.EventBusMessage;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Adapter to bind {@link CredentialsManagementService} to the vertx event bus.
 *
 * @param <T> The type of configuration properties this service requires.
 */
public abstract class EventBusCredentialsManagementAdapter<T> extends EventBusService<T>
        implements Verticle {

    private static final String SPAN_NAME_GET_CREDENTIAL = "get Credential from management API";
    private static final String SPAN_NAME_UPDATE_CREDENTIAL = "update Credential from management API";

    /**
     * The service to forward requests to.
     * 
     * @return The service to bind to, must never return {@code null}.
     */
    protected abstract CredentialsManagementService getService();

    @Override
    protected String getEventBusAddress() {
        return RegistryManagementConstants.EVENT_BUS_ADDRESS_CREDENTIALS_MANAGEMENT_IN;
    }

    @Override
    protected Future<EventBusMessage> processRequest(final EventBusMessage requestMessage) {
        Objects.requireNonNull(requestMessage);

        switch (requestMessage.getOperation()) {
            case RegistryManagementConstants.ACTION_GET:
                return processGetRequest(requestMessage);
            case RegistryManagementConstants.ACTION_UPDATE:
                return processUpdateRequest(requestMessage);
            default:
                return processCustomCredentialsMessage(requestMessage);
        }
    }

    /**
     * Processes a request for a non-standard operation.
     * <p>
     * Subclasses should override this method in order to support additional, custom operations that are not defined by
     * Hono's Device Registration API.
     * <p>
     * This default implementation simply returns a future that is failed with a {@link ClientErrorException} with an
     * error code <em>400 Bad Request</em>.
     *
     * @param request The request to process.
     * @return A future indicating the outcome of the service invocation.
     */
    protected Future<EventBusMessage> processCustomCredentialsMessage(final EventBusMessage request) {
        log.debug("invalid operation in request message [{}]", request.getOperation());
        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
    }

    private Future<EventBusMessage> processUpdateRequest(final EventBusMessage request) {

        final String tenantId = request.getTenant();
        final String deviceId = request.getDeviceId();
        final Optional<String> resourceVersion = Optional.ofNullable(request.getResourceVersion());
        final JsonObject payload = request.getJsonPayload();
        final SpanContext spanContext = request.getSpanContext();

        if (tenantId == null) {
            return Future.failedFuture(new ClientErrorException(
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    "missing tenant ID"));
        } else if (payload == null) {
            return Future.failedFuture(new ClientErrorException(
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    "missing payload"));
        }
        try {
            final Future<List<CommonCredential>> secretsFuture = credentialsFromPayload(request);

            final Span span = newChildSpan(SPAN_NAME_UPDATE_CREDENTIAL, spanContext, tracer, tenantId, deviceId, getClass().getSimpleName());
            final Future<OperationResult<Void>> result = Future.future();

            return secretsFuture.compose(secrets -> {
                getService().set(tenantId, deviceId, resourceVersion, secrets, span, result);
                        return result.map(res -> {
                            return res.createResponse(request, id -> null).setDeviceId(deviceId);
                        });
                    }
            );
        } catch (final IllegalStateException e) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, e.getMessage(), e));
        }
    }

    /**
     * Decode a credential from a JSON object.
     * 
     * @param object The object to device from.
     * @return The decoded secret. Or {@code null} if the provided JSON object was {@code null}.
     * @throws IllegalStateException if the {@code type} field was not set.
     */
    protected CommonCredential decodeCredential(final JsonObject object) {

        if (object == null) {
            return null;
        }

        final String type = object.getString("type");
        if (type == null || type.isEmpty()) {
            throw new IllegalStateException("'type' field must be set");
        }

        final CommonCredential credential = decodeCredential(type, object);
        return credential;
    }

    /**
     * Decode a credential, based on the provided type.
     * 
     * @param type The type of the secret. Will never be {@code null}.
     * @param object The JSON object to decode. Will never be {@code null}.
     * @return The decoded secret.
     */
    protected CommonCredential decodeCredential(final String type, final JsonObject object) {
        switch (type) {
            case RegistryManagementConstants.SECRETS_TYPE_HASHED_PASSWORD:
                return object.mapTo(PasswordCredential.class);
            case RegistryManagementConstants.SECRETS_TYPE_PRESHARED_KEY:
                return object.mapTo(PskCredential.class);
            case RegistryManagementConstants.SECRETS_TYPE_X509_CERT:
                return object.mapTo(X509CertificateCredential.class);
            default:
                return object.mapTo(GenericCredential.class);
        }
    }

    /**
     * Decode a list of secrets from a JSON array.
     * <p>
     * This is a convenience method, decoding a list of secrets from a JSON array.
     * 
     * @param objects The JSON array.
     * @return The list of decoded secrets.
     * @throws NullPointerException in the case the {@code objects} parameter is {@code null}.
     */
    protected List<CommonCredential> decodeCredentials(final JsonArray objects) {
        return objects
                .stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .map(this::decodeCredential)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Extract the credentials from an event bus request.
     * 
     * @param request The request to extract information from.
     * @return A future, returning the secrets.
     * @throws NullPointerException in the case the request is {@code null}.
     */
    protected Future<List<CommonCredential>> credentialsFromPayload(final EventBusMessage request) {
        try {
            return Future.succeededFuture(Optional.ofNullable(request.getJsonPayload())
                    .map(json -> {
                        return decodeCredentials(json.getJsonArray(RegistryManagementConstants.CREDENTIALS_OBJECT));
                    })
                    .orElseGet(ArrayList::new));
        } catch (final IllegalArgumentException e) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, e));
        }
    }

    private Future<EventBusMessage> processGetRequest(final EventBusMessage request) {
        final String tenantId = request.getTenant();
        final String deviceId = request.getDeviceId();
        final SpanContext spanContext = request.getSpanContext();

        final Span span = newChildSpan(SPAN_NAME_GET_CREDENTIAL, spanContext, tracer, tenantId, deviceId, getClass().getSimpleName());
        final Future<OperationResult<List<CommonCredential>>> result = Future.future();

        getService().get(tenantId, deviceId, span, result);

        return result.map(res -> {
            return res.createResponse(request, credentials -> {
                final JsonObject ret = new JsonObject();
                final JsonArray credentialsArray = new JsonArray();
                for (final CommonCredential credential : credentials) {
                    credentialsArray.add(JsonObject.mapFrom(credential));
                }
                ret.put(RegistryManagementConstants.CREDENTIALS_OBJECT, credentialsArray);
                return ret;
            }).setDeviceId(deviceId);
        });
    }

}
