/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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
import java.util.Optional;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.EventBusService;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Adapter to bind {@link RegistrationService} to the vertx event bus.
 * <p>
 * This base class provides support for receiving <em>assert Registration</em> request messages via vert.x' event bus
 * and routing them to specific methods accepting the query parameters contained in the request message.
 * @deprecated This class will be removed in future versions as AMQP endpoint does not use event bus anymore.
 *             Please use {@link org.eclipse.hono.service.registration.AbstractRegistrationAmqpEndpoint} based implementation in the future.
 */
@Deprecated
public abstract class EventBusRegistrationAdapter extends EventBusService implements Verticle {

    /**
     * The default number of seconds that information returned by this service's
     * operations may be cached for.
     */
    public static final int DEFAULT_MAX_AGE_SECONDS = 300;

    private static final String SPAN_NAME_ASSERT_DEVICE_REGISTRATION = "assert Device Registration";

    /**
     * The service to forward requests to.
     *
     * @return The service to bind to, must never return {@code null}.
     */
    protected abstract RegistrationService getService();

    @Override
    protected String getEventBusAddress() {
        return RegistrationConstants.EVENT_BUS_ADDRESS_REGISTRATION_IN;
    }

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
    public Future<EventBusMessage> processRequest(final EventBusMessage requestMessage) {

        Objects.requireNonNull(requestMessage);

        switch (requestMessage.getOperation()) {
        case RegistrationConstants.ACTION_ASSERT:
            return processAssertRequest(requestMessage);
        default:
            return processCustomRegistrationMessage(requestMessage);
        }
    }

    private Future<EventBusMessage> processAssertRequest(final EventBusMessage request) {

        final String tenantId = request.getTenant();
        final String deviceId = request.getDeviceId();
        final String gatewayId = request.getGatewayId();
        final SpanContext spanContext = request.getSpanContext();

        final Span span = newChildSpan(SPAN_NAME_ASSERT_DEVICE_REGISTRATION, spanContext, tenantId, deviceId, gatewayId);
        final Future<EventBusMessage> resultFuture;
        if (tenantId == null || deviceId == null) {
            TracingHelper.logError(span, "missing tenant and/or device");
            resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            resultFuture = Optional.ofNullable(gatewayId)
                    .map(ok -> {
                        log.debug("asserting registration of device [{}] with tenant [{}] for gateway [{}]",
                                deviceId, tenantId, gatewayId);
                        return getService().assertRegistration(tenantId, deviceId, gatewayId, span);
                    }).orElseGet(() -> {
                        log.debug("asserting registration of device [{}] with tenant [{}]", deviceId, tenantId);
                        return getService().assertRegistration(tenantId, deviceId, span);
                    }).map(res -> request.getResponse(res.getStatus())
                            .setDeviceId(deviceId)
                            .setJsonPayload(res.getPayload())
                            .setCacheDirective(res.getCacheDirective()));
        }
        return finishSpanOnFutureCompletion(span, resultFuture);
    }

    /**
     * Processes a request for a non-standard operation.
     * <p>
     * Subclasses should override this method in order to support additional, custom
     * operations that are not defined by Hono's Device Registration API.
     * <p>
     * This default implementation simply returns a future that is failed with a
     * {@link ClientErrorException} with an error code <em>400 Bad Request</em>.
     *
     * @param request The request to process.
     * @return A future indicating the outcome of the service invocation.
     */
    protected Future<EventBusMessage> processCustomRegistrationMessage(final EventBusMessage request) {
        log.debug("invalid operation in request message [{}]", request.getOperation());
        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
    }

    /**
     * Checks if a gateway may act on behalf of the given device. This is determined by checking whether
     * the 'via' property of the device registration data has one or more entries.
     * <p>
     * Subclasses may override this method to provide a different means to determine gateway support.
     *
     * @param tenantId The tenant id.
     * @param deviceId The device id.
     * @param registrationInfo The device's registration information.
     * @return {@code true} if a gateway may act on behalf of the given device.
     */
    protected boolean isGatewaySupportedForDevice(final String tenantId, final String deviceId,
            final JsonObject registrationInfo) {
        final Object viaObj = registrationInfo.getValue(RegistrationConstants.FIELD_VIA);
        return (viaObj instanceof String && !((String) viaObj).isEmpty())
                || (viaObj instanceof JsonArray && !((JsonArray) viaObj).isEmpty());
    }

    /**
     * Creates a new <em>OpenTracing</em> span for tracing the execution of a registration service operation.
     * <p>
     * The returned span will already contain tags for the given tenant, device and gateway ids (if either is not {@code null}).
     *
     * @param operationName The operation name that the span should be created for.
     * @param spanContext Existing span context.
     * @param tenantId The tenant id.
     * @param deviceId The device id.
     * @param gatewayId The gateway id.
     * @return The new {@code Span}.
     * @throws NullPointerException if operationName is {@code null}.
     */
    protected final Span newChildSpan(final String operationName, final SpanContext spanContext, final String tenantId,
            final String deviceId, final String gatewayId) {
        Objects.requireNonNull(operationName);
        // we set the component tag to the class name because we have no access to
        // the name of the enclosing component we are running in
        final Tracer.SpanBuilder spanBuilder = TracingHelper.buildChildSpan(tracer, spanContext, operationName)
                .ignoreActiveSpan()
                .withTag(Tags.COMPONENT.getKey(), getClass().getSimpleName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER);
        if (tenantId != null) {
            spanBuilder.withTag(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
        }
        if (deviceId != null) {
            spanBuilder.withTag(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        }
        if (gatewayId != null) {
            spanBuilder.withTag(MessageHelper.APP_PROPERTY_GATEWAY_ID, gatewayId);
        }
        return spanBuilder.start();
    }

    /**
     * Handles an unimplemented operation by invoking the given handler with a successful Future
     * containing an empty result with a <em>501 Not Implemented</em> status code.
     *
     * @param resultHandler The handler.
     */
    protected void handleUnimplementedOperation(final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        resultHandler.handle(Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_NOT_IMPLEMENTED)));
    }

    /**
     * Checks if a gateway is authorized to act <em>on behalf of</em> a device.
     * <p>
     * This default implementation checks if the gateway's identifier matches the
     * value of the {@link RegistrationConstants#FIELD_VIA} property in the device's registration information.
     * The property may either contain a single String value or a JSON array of Strings.
     * <p>
     * Subclasses may override this method in order to implement a more
     * sophisticated check.
     *
     * @param gatewayId The identifier of the gateway.
     * @param gatewayData The data registered for the gateway.
     * @param deviceId The identifier of the device.
     * @param deviceData The data registered for the device.
     * @return {@code true} if the gateway is authorized.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected boolean isGatewayAuthorized(final String gatewayId, final JsonObject gatewayData,
            final String deviceId, final JsonObject deviceData) {

        Objects.requireNonNull(gatewayId);
        Objects.requireNonNull(gatewayData);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(deviceData);

        final Object obj = deviceData.getValue(RegistrationConstants.FIELD_VIA);
        if (obj instanceof String) {
            return gatewayId.equals(obj);
        } else if (obj instanceof JsonArray) {
            return ((JsonArray) obj).stream().filter(o -> o instanceof String).anyMatch(id -> gatewayId.equals(id));
        } else {
            return false;
        }
    }

    /**
     * Gets the cache directive to include in responses to the assert Registration
     * operation.
     * <p>
     * Subclasses should override this method in order to return a specific
     * directive other than the default.
     * <p>
     * This default implementation returns a directive to cache values for
     * {@link #DEFAULT_MAX_AGE_SECONDS} seconds.
     *
     * @param deviceId The identifier of the device that is the subject of the assertion.
     * @param tenantId The tenant that the device belongs to.
     * @return The cache directive.
     */
    protected CacheDirective getRegistrationAssertionCacheDirective(final String deviceId, final String tenantId) {
        return CacheDirective.maxAgeDirective(DEFAULT_MAX_AGE_SECONDS);
    }

    /**
     * Wraps a given device ID and registration data into a JSON structure suitable
     * to be returned to clients as the result of a registration operation.
     *
     * @param deviceId The device ID.
     * @param data The registration data.
     * @return The JSON structure.
     */
    protected static final JsonObject getResultPayload(final String deviceId, final JsonObject data) {

        return new JsonObject()
                .put(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId)
                .put(RegistrationConstants.FIELD_DATA, data);
    }

}
