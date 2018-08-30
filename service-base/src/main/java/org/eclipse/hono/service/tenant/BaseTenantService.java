/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

import java.net.HttpURLConnection;
import java.util.Objects;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.service.EventBusService;
import org.eclipse.hono.util.EventBusMessage;

import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantResult;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/**
 * a Base class for implementing {@link TenantService}.
 * <p>
 * In particular, this base class provides support for receiving service invocation request messages
 * via vert.x' event bus and routing them to specific methods accepting the
 * query parameters contained in the request message.
 *
 * @param <T> The type of configuration properties this service requires.
 */
public abstract class BaseTenantService<T> extends EventBusService<T> implements TenantService {

    @Override
    protected final String getEventBusAddress() {
        return TenantConstants.EVENT_BUS_ADDRESS_TENANT_IN;
    }

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
    public Future<EventBusMessage> processRequest(final EventBusMessage request) {

        Objects.requireNonNull(request);

        switch (TenantConstants.TenantAction.from(request.getOperation())) {
        case get:
            return processGetRequest(request);
        default:
            return processCustomTenantMessage(request);
        }
    }

    Future<EventBusMessage> processGetRequest(final EventBusMessage request) {

        final String tenantId = request.getTenant();
        final JsonObject payload = request.getJsonPayload();

        if (tenantId == null && payload == null) {

            log.debug("request does not contain any query parameters");
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));

        } else if (tenantId != null) {

            // deprecated API
            log.debug("retrieving tenant [{}] using deprecated variant of get tenant request", tenantId);
            return processGetByIdRequest(request, tenantId);

        } else {

            final String tenantIdFromPayload = getTypesafeValueForField(String.class, payload,
                    TenantConstants.FIELD_PAYLOAD_TENANT_ID);
            final String subjectDn = getTypesafeValueForField(String.class, payload,
                    TenantConstants.FIELD_PAYLOAD_SUBJECT_DN);

            if (tenantIdFromPayload == null && subjectDn == null) {
                log.debug("payload does not contain any query parameters");
                return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
            } else if (tenantIdFromPayload != null) {
                log.debug("retrieving tenant [id: {}]", tenantIdFromPayload);
                return processGetByIdRequest(request, tenantIdFromPayload);
            } else {
                return processGetByCaRequest(request, subjectDn);
            }
        }
    }

    private Future<EventBusMessage> processGetByIdRequest(final EventBusMessage request, final String tenantId) {

        final Future<TenantResult<JsonObject>> getResult = Future.future();
        get(tenantId, getResult.completer());
        return getResult.map(tr -> {
            return request.getResponse(tr.getStatus())
                    .setJsonPayload(tr.getPayload())
                    .setTenant(tenantId)
                    .setCacheDirective(tr.getCacheDirective());
        });
    }

    private Future<EventBusMessage> processGetByCaRequest(final EventBusMessage request, final String subjectDn) {

        try {
            final X500Principal dn = new X500Principal(subjectDn);
            log.debug("retrieving tenant [subject DN: {}]", subjectDn);
            final Future<TenantResult<JsonObject>> getResult = Future.future();
            get(dn, getResult.completer());
            return getResult.map(tr -> {
                final EventBusMessage response = request.getResponse(tr.getStatus())
                        .setJsonPayload(tr.getPayload())
                        .setCacheDirective(tr.getCacheDirective());
                if (tr.isOk() && tr.getPayload() != null) {
                    response.setTenant(getTypesafeValueForField(String.class, tr.getPayload(),
                            TenantConstants.FIELD_PAYLOAD_TENANT_ID));
                }
                return response;
            });
        } catch (final IllegalArgumentException e) {
            // the given subject DN is invalid
            log.debug("cannot parse subject DN [{}] provided by client", subjectDn);
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        }
    }

    /**
     * Processes a request for a non-standard operation.
     * <p>
     * Subclasses should override this method in order to support additional, custom
     * operations that are not defined by Hono's Tenant API.
     * <p>
     * This default implementation simply returns a future that is failed with a
     * {@link ClientErrorException} with an error code <em>400 Bad Request</em>.
     *
     * @param request The request to process.
     * @return A future indicating the outcome of the service invocation.
     */
    protected Future<EventBusMessage> processCustomTenantMessage(final EventBusMessage request) {
        log.debug("invalid operation in request message [{}]", request.getOperation());
        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void get(final String tenantId, final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void get(final X500Principal subjectDn,
            final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    /**
     * Handles an unimplemented operation by failing the given handler
     * with a {@link ClientErrorException} having a <em>501 Not Implemented</em> status code.
     *
     * @param resultHandler The handler.
     */
    protected void handleUnimplementedOperation(final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {
        resultHandler.handle(Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_NOT_IMPLEMENTED)));
    }
}
