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
package org.eclipse.hono.service.registration;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/**
 * A Base class for implementing {@link CompleteRegistrationService}.
 * <p>
 * In particular, this base class provides support for receiving service invocation request messages
 * via vert.x' event bus and route them to specific methods corresponding to the operation indicated
 * in the message.
 *
 * @param <T> The type of configuration properties this service requires.
 */
public abstract class CompleteBaseRegistrationService<T> extends BaseRegistrationService<T> implements CompleteRegistrationService {

    /**
     * Processes a device registration API request received via the vert.x event bus.
     * <p>
     * This method validates the request parameters against the Device Registration API
     * specification before invoking the corresponding {@code RegistrationService} methods.
     *
     * @param requestMessage The request message.
     * @return A future indicating the outcome of the service invocation.
     * @throws NullPointerException If the request message is {@code null}.
     */
    @Override
    public final Future<EventBusMessage> processRequest(final EventBusMessage requestMessage) {

        Objects.requireNonNull(requestMessage);

        switch (requestMessage.getOperation()) {
        case RegistrationConstants.ACTION_REGISTER:
            return processRegisterRequest(requestMessage);
        case RegistrationConstants.ACTION_ASSERT:
            return super.processRequest(requestMessage);
        case RegistrationConstants.ACTION_GET:
            return processGetRequest(requestMessage);
        case RegistrationConstants.ACTION_UPDATE:
            return processUpdateRequest(requestMessage);
        case RegistrationConstants.ACTION_DEREGISTER:
            return processDeregisterRequest(requestMessage);
        default:
            return processCustomRegistrationMessage(requestMessage);
        }
    }

    private Future<EventBusMessage> processRegisterRequest(final EventBusMessage request) {

        final String tenantId = request.getTenant();
        final String deviceId = request.getDeviceId();
        final JsonObject payload = getRequestPayload(request.getJsonPayload());

        if (tenantId == null || deviceId == null) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            log.debug("registering device [{}] for tenant [{}]", deviceId, tenantId);
            final Future<RegistrationResult> result = Future.future();
            addDevice(tenantId, deviceId, payload, result.completer());
            return result.map(res -> {
                return request.getResponse(res.getStatus())
                        .setDeviceId(deviceId)
                        .setCacheDirective(res.getCacheDirective());
            });
        }
    }

    private Future<EventBusMessage> processGetRequest(final EventBusMessage request) {

        final String tenantId = request.getTenant();
        final String deviceId = request.getDeviceId();

        if (tenantId == null || deviceId == null) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            log.debug("retrieving device [{}] of tenant [{}]", deviceId, tenantId);
            final Future<RegistrationResult> result = Future.future();
            getDevice(tenantId, deviceId, result.completer());
            return result.map(res -> {
                return request.getResponse(res.getStatus())
                        .setDeviceId(deviceId)
                        .setJsonPayload(res.getPayload())
                        .setCacheDirective(res.getCacheDirective());
            });
        }
    }

    private Future<EventBusMessage> processUpdateRequest(final EventBusMessage request) {

        final String tenantId = request.getTenant();
        final String deviceId = request.getDeviceId();
        final JsonObject payload = getRequestPayload(request.getJsonPayload());

        if (tenantId == null || deviceId == null) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            log.debug("updating registration information for device [{}] of tenant [{}]", deviceId, tenantId);
            final Future<RegistrationResult> result = Future.future();
            updateDevice(tenantId, deviceId, payload, result.completer());
            return result.map(res -> {
                return request.getResponse(res.getStatus())
                        .setDeviceId(deviceId)
                        .setCacheDirective(res.getCacheDirective());
            });
        }
    }

    private Future<EventBusMessage> processDeregisterRequest(final EventBusMessage request) {

        final String tenantId = request.getTenant();
        final String deviceId = request.getDeviceId();

        if (tenantId == null || deviceId == null) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            log.debug("deregistering device [{}] of tenant [{}]", deviceId, tenantId);
            final Future<RegistrationResult> result = Future.future();
            removeDevice(tenantId, deviceId, result.completer());
            return result.map(res -> {
                return request.getResponse(res.getStatus())
                        .setDeviceId(deviceId)
                        .setCacheDirective(res.getCacheDirective());
            });
        }
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void addDevice(final String tenantId, final String deviceId, final JsonObject otherKeys,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void updateDevice(final String tenantId, final String deviceId, final JsonObject otherKeys,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void removeDevice(final String tenantId, final String deviceId,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }
}
