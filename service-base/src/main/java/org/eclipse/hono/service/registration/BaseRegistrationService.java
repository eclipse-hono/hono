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
package org.eclipse.hono.service.registration;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.service.EventBusService;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopSpan;
import io.opentracing.tag.Tags;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * A base class for implementing {@link RegistrationService}.
 * <p>
 * This base class provides support for receiving <em>assert Registration</em> request messages
 * via vert.x' event bus and routing them to specific methods accepting the
 * query parameters contained in the request message.
 * <p>
 * <em>NB</em> This class provides a basic implementation for asserting a device's registration
 * status. Subclasses may override the {@link #assertRegistration(String, String, String, Span, Handler)}
 * method in order to implement a more sophisticated assertion method.
 * <p>
 * The default implementation of <em>assertRegistration</em> relies on {@link #getDevice(String, String, Handler)}
 * to retrieve a device's registration information from persistent storage. Thus, subclasses need
 * to override (and implement) this method in order to get a working implementation of the default
 * assertion mechanism.
 * 
 * @param <T> The type of configuration properties this service requires.
 */
public abstract class BaseRegistrationService<T> extends EventBusService<T> implements RegistrationService {

    /**
     * The default number of seconds that information returned by this service's
     * operations may be cached for.
     */
    public static final int DEFAULT_MAX_AGE_SECONDS = 300;

    private static final String SPAN_NAME_ASSERT_DEVICE_REGISTRATION = "assert Device Registration";
    private static final String SPAN_NAME_GET_LAST_USED_GATEWAY = "get last-used gateway";
    private static final String SPAN_NAME_SET_LAST_USED_GATEWAY = "set last-used gateway";

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
        case RegistrationConstants.ACTION_GET_LAST_USED_GATEWAY:
            return processGetLastUsedGatewayRequest(requestMessage);
        case RegistrationConstants.ACTION_SET_LAST_USED_GATEWAY:
            return processSetLastUsedGatewayRequest(requestMessage);
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
            final Future<RegistrationResult> result = Future.future();
            if (gatewayId == null) {
                log.debug("asserting registration of device [{}] with tenant [{}]", deviceId, tenantId);
                assertRegistration(tenantId, deviceId, span, result);
            } else {
                log.debug("asserting registration of device [{}] with tenant [{}] for gateway [{}]",
                        deviceId, tenantId, gatewayId);
                assertRegistration(tenantId, deviceId, gatewayId, span, result);
            }
            resultFuture = result.map(res -> {
                return request.getResponse(res.getStatus())
                        .setDeviceId(deviceId)
                        .setJsonPayload(res.getPayload())
                        .setCacheDirective(res.getCacheDirective());
            });
        }
        return finishSpanOnFutureCompletion(span, resultFuture);
    }

    private Future<EventBusMessage> processGetLastUsedGatewayRequest(final EventBusMessage request) {
        final String tenantId = request.getTenant();
        final String deviceId = request.getDeviceId();
        final SpanContext spanContext = request.getSpanContext();

        final Span span = newChildSpan(SPAN_NAME_GET_LAST_USED_GATEWAY, spanContext, tenantId, deviceId, null);
        final Future<EventBusMessage> resultFuture;
        if (tenantId == null || deviceId == null) {
            TracingHelper.logError(span, "missing tenant and/or device");
            resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            final Future<RegistrationResult> result = Future.future();
            log.debug("getting last-used gateway for tenant [{}], device [{}]", tenantId, deviceId);
            getLastUsedGateway(tenantId, deviceId, span, result);

            resultFuture = result.map(res -> {
                return request.getResponse(res.getStatus())
                        .setDeviceId(deviceId)
                        .setJsonPayload(res.getPayload())
                        .setCacheDirective(res.getCacheDirective());
            });
        }
        return finishSpanOnFutureCompletion(span, resultFuture);
    }

    private Future<EventBusMessage> processSetLastUsedGatewayRequest(final EventBusMessage request) {
        final String tenantId = request.getTenant();
        final String deviceId = request.getDeviceId();
        final String gatewayId = request.getGatewayId();
        final SpanContext spanContext = request.getSpanContext();

        final Span span = newChildSpan(SPAN_NAME_SET_LAST_USED_GATEWAY, spanContext, tenantId, deviceId, gatewayId);
        final Future<EventBusMessage> resultFuture;
        if (tenantId == null || deviceId == null || gatewayId == null) {
            TracingHelper.logError(span, "missing tenant, device and/or gateway");
            resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            final Future<RegistrationResult> result = Future.future();
            log.debug("setting last-used gateway for tenant [{}], device [{}] to {}", tenantId, deviceId, gatewayId);
            setLastUsedGateway(tenantId, deviceId, gatewayId, span, result);

            resultFuture = result.map(res -> {
                return request.getResponse(res.getStatus())
                        .setDeviceId(deviceId)
                        .setJsonPayload(res.getPayload())
                        .setCacheDirective(res.getCacheDirective());
            });
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
     * Gets device registration data by device ID.
     * <p>
     * This method is invoked by {@link #assertRegistration(String, String, String, Span, Handler)} to retrieve
     * device registration information from the persistent store.
     * <p>
     * This default implementation simply invokes the given handler with a successful Future containing an empty result
     * with status code 501 (Not Implemented).
     * Subclasses need to override this method and provide a reasonable implementation in order for this
     * class' default implementation of <em>assertRegistration</em> to work properly.
     * 
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The ID of the device to remove.
     * @param resultHandler The handler to invoke with the registration information.
     */
    @Override
    public void getDevice(final String tenantId, final String deviceId,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    @Override
    public final void assertRegistration(
            final String tenantId,
            final String deviceId,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        assertRegistration(tenantId, deviceId, NoopSpan.INSTANCE, resultHandler);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Subclasses may override this method in order to implement a more sophisticated approach for asserting registration status, e.g.
     * using cached information etc.
     * This method requires a functional {@link #getDevice(String, String, Handler) getDevice} method to work.
     */
    @Override
    public void assertRegistration(
            final String tenantId,
            final String deviceId,
            final Span span,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(span);
        Objects.requireNonNull(resultHandler);

        final Future<RegistrationResult> getResultTracker = Future.future();
        getDevice(tenantId, deviceId, getResultTracker);

        getResultTracker.compose(result -> {
            if (isDeviceEnabled(result)) {
                final JsonObject deviceData = result.getPayload().getJsonObject(RegistrationConstants.FIELD_DATA);
                return updateDeviceLastViaIfNeeded(tenantId, deviceId, deviceId, deviceData, span).map(res -> {
                    return createSuccessfulRegistrationResult(tenantId, deviceId, deviceData);
                }).recover(t -> {
                    return Future.succeededFuture(RegistrationResult.from(ServiceInvocationException.extractStatusCode(t)));
                });
            } else {
                TracingHelper.logError(span, "device not enabled");
                return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_NOT_FOUND));
            }
        }).setHandler(resultHandler);
    }

    @Override
    public final void assertRegistration(
            final String tenantId,
            final String deviceId,
            final String gatewayId,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        assertRegistration(tenantId, deviceId, gatewayId, NoopSpan.INSTANCE, resultHandler);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Subclasses may override this method in order to implement a more sophisticated approach for asserting registration status, e.g.
     * using cached information etc.
     * This method requires a functional {@link #getDevice(String, String, Handler) getDevice} method to work.
     */
    @Override
    public void assertRegistration(
            final String tenantId,
            final String deviceId,
            final String gatewayId,
            final Span span,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(gatewayId);
        Objects.requireNonNull(span);
        Objects.requireNonNull(resultHandler);

        final Future<RegistrationResult> deviceInfoTracker = Future.future();
        final Future<RegistrationResult> gatewayInfoTracker = Future.future();

        getDevice(tenantId, deviceId, deviceInfoTracker);
        getDevice(tenantId, gatewayId, gatewayInfoTracker);

        CompositeFuture.all(deviceInfoTracker, gatewayInfoTracker).compose(ok -> {

            final RegistrationResult deviceResult = deviceInfoTracker.result();
            final RegistrationResult gatewayResult = gatewayInfoTracker.result();

            if (!isDeviceEnabled(deviceResult)) {
                TracingHelper.logError(span, "device not enabled");
                return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_NOT_FOUND));
            } else if (!isDeviceEnabled(gatewayResult)) {
                TracingHelper.logError(span, "gateway not enabled");
                return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_FORBIDDEN));
            } else {

                final JsonObject deviceData = deviceResult.getPayload().getJsonObject(RegistrationConstants.FIELD_DATA, new JsonObject());
                final JsonObject gatewayData = gatewayResult.getPayload().getJsonObject(RegistrationConstants.FIELD_DATA, new JsonObject());

                if (isGatewayAuthorized(gatewayId, gatewayData, deviceId, deviceData)) {
                    return updateDeviceLastViaIfNeeded(tenantId, deviceId, gatewayId, deviceData, span).map(res -> {
                        return createSuccessfulRegistrationResult(tenantId, deviceId, deviceData);
                    }).recover(t -> {
                        return Future.succeededFuture(RegistrationResult.from(ServiceInvocationException.extractStatusCode(t)));
                    });
                } else {
                    TracingHelper.logError(span, "gateway not authorized");
                    return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_FORBIDDEN));
                }
            }
        }).setHandler(resultHandler);
    }

    private RegistrationResult createSuccessfulRegistrationResult(
            final String tenantId,
            final String deviceId,
            final JsonObject deviceData) {

        final CacheDirective cacheDirective = isGatewaySupportedForDevice(tenantId, deviceId, deviceData) ? CacheDirective.noCacheDirective()
                : getRegistrationAssertionCacheDirective(deviceId, tenantId);
        return RegistrationResult.from(
                HttpURLConnection.HTTP_OK,
                getAssertionPayload(tenantId, deviceId, deviceData),
                cacheDirective);
    }

    private Future<Void> updateDeviceLastViaIfNeeded(final String tenantId, final String deviceId,
            final String gatewayId, final JsonObject deviceData, final Span span) {
        if (!isGatewaySupportedForDevice(tenantId, deviceId, deviceData)) {
            return Future.succeededFuture();
        }
        return updateDeviceLastVia(tenantId, deviceId, gatewayId, deviceData)
                .recover(t -> {
                    log.error("update of the 'last-via' property failed", t);
                    TracingHelper.logError(span, "update of the 'last-via' property failed: " + t.toString());
                    return Future.failedFuture(t);
                });
    }

    @Override
    public void setLastUsedGateway(final String tenantId, final String deviceId, final String gatewayId,
                                   final Span span, final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    @Override
    public void getLastUsedGateway(final String tenantId, final String deviceId, final Span span,
                                   final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
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
     * Updates device registration data and adds a {@code last-via} property containing the given gateway identifier
     * as well as the current date.
     * <p>
     * This method is called by this class' default implementation of <em>assertRegistration</em> for a device that
     * has one or more gateways defined in its {@code via} property.
     * <p>
     * If such a device connects directly instead of through a gateway, the device identifier is to be used as value
     * for the <em>gatewayId</em> parameter.
     * <p>
     * Subclasses need to override this method and provide a reasonable implementation in order to support scenarios
     * where devices may connect via multiple gateways, along with gateways subscribing to command messages only
     * using their gateway id. In such scenarios, the identifier stored in the {@code last-via} property is
     * used to route command messages to the protocol adapter that the gateway is connected to.
     *
     * @param tenantId The tenant id.
     * @param deviceId The device id.
     * @param gatewayId The gateway id (or the device id if the request comes directly from the device).
     * @param deviceData The device's current registration information.
     * @return A future indicating whether the operation succeeded or not.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected abstract Future<Void> updateDeviceLastVia(String tenantId, String deviceId, String gatewayId, JsonObject deviceData);

    /**
     * Creates a new <em>OpenTracing</em> span for tracing the execution of a registration service operation.
     * <p>
     * The returned span will already contain tags for the given tenant, device and gateway ids (if either is not {code null}).
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
        final Tracer.SpanBuilder spanBuilder = tracer.buildSpan(operationName)
                .addReference(References.CHILD_OF, spanContext)
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

    private boolean isDeviceEnabled(final RegistrationResult registrationResult) {
        return registrationResult.isOk() &&
                isDeviceEnabled(registrationResult.getPayload().getJsonObject(RegistrationConstants.FIELD_DATA));
    }

    private boolean isDeviceEnabled(final JsonObject registrationData) {
        return registrationData.getBoolean(RegistrationConstants.FIELD_ENABLED, Boolean.TRUE);
    }

    /**
     * Creates the payload of the assert Registration response message.
     * <p>
     * The returned JSON object contains the <em>gw-supported</em> property, having a {@code true} value if a gateway
     * may act on behalf of the device, and may also contain <em>default</em> values registered for the device under key
     * {@link RegistrationConstants#FIELD_PAYLOAD_DEFAULTS}.
     * 
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The device to create the assertion token for.
     * @param registrationInfo The device's registration information.
     * @return The payload.
     */
    protected final JsonObject getAssertionPayload(final String tenantId, final String deviceId, final JsonObject registrationInfo) {

        final JsonObject result = new JsonObject()
                .put(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId);
        result.put(RegistrationConstants.FIELD_PAYLOAD_GATEWAY_SUPPORTED, isGatewaySupportedForDevice(tenantId, deviceId, registrationInfo));
        final JsonObject defaults = registrationInfo.getJsonObject(RegistrationConstants.FIELD_PAYLOAD_DEFAULTS);
        if (defaults != null) {
            result.put(RegistrationConstants.FIELD_PAYLOAD_DEFAULTS, defaults);
        }
        return result;
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
