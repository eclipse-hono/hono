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
import org.eclipse.hono.service.EventBusService;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/**
 * A base class for implementing {@link RegistrationService}.
 * <p>
 * This base class provides support for receiving <em>assert Registration</em> request messages
 * via vert.x' event bus and routing them to specific methods accepting the
 * query parameters contained in the request message.
 * <p>
 * <em>NB</em> This class provides a basic implementation for asserting a device's registration
 * status. Subclasses may override the {@link #assertRegistration(String, String, String, Handler)}
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
     * The name of the field in a device's registration information that contains
     * the identifier of the gateway that it is connected to.
     */
    public static final String PROPERTY_VIA = "via";

    private RegistrationAssertionHelper assertionFactory;

    /**
     * Sets the factory to use for creating tokens asserting a device's registration status.
     * 
     * @param assertionFactory The factory.
     * @throws NullPointerException if factory is {@code null}.
     */
    @Autowired
    @Qualifier("signing")
    public final void setRegistrationAssertionFactory(final RegistrationAssertionHelper assertionFactory) {
        this.assertionFactory = Objects.requireNonNull(assertionFactory);
    }

    /**
     * Asserts that the <em>assertionFactory</em> property is set.
     * <p>
     * The given future is succeeded if the property is not {@code null},
     * otherwise it is failed.
     * 
     * @param startFuture future to invoke once start up is complete.
     */
    @Override
    protected void doStart(final Future<Void> startFuture) {

        if (assertionFactory == null) {
            startFuture.fail(new IllegalStateException("registration assertion factory must be set"));
        } else {
            startFuture.complete();
        }
    }

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

        if (tenantId == null || deviceId == null) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else if (gatewayId == null) {
            log.debug("asserting registration of device [{}] with tenant [{}]", deviceId, tenantId);
            final Future<RegistrationResult> result = Future.future();
            assertRegistration(tenantId, deviceId, result.completer());
            return result.map(res -> {
                return request.getResponse(res.getStatus())
                        .setDeviceId(deviceId)
                        .setJsonPayload(res.getPayload())
                        .setCacheDirective(res.getCacheDirective());
            });
        } else {
            log.debug("asserting registration of device [{}] with tenant [{}] for gateway [{}]",
                    deviceId, tenantId, gatewayId);
            final Future<RegistrationResult> result = Future.future();
            assertRegistration(tenantId, deviceId, gatewayId, result.completer());
            return result.map(res -> {
                return request.getResponse(res.getStatus())
                        .setDeviceId(deviceId)
                        .setJsonPayload(res.getPayload())
                        .setCacheDirective(res.getCacheDirective());
            });
        }
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
     * This method is invoked by {@link #assertRegistration(String, String, String, Handler)} to retrieve
     * device registration information from the persistent store.
     * <p>
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses need to override this method and provide a reasonable implementation in order for this
     * class' default implementation of <em>assertRegistration</em> to work properly.
     * 
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The ID of the device to remove.
     * @param resultHandler The handler to invoke with the registration information.
     */
    public void getDevice(final String tenantId, final String deviceId,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
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
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(resultHandler);

        final Future<RegistrationResult> getResultTracker = Future.future();
        getDevice(tenantId, deviceId, getResultTracker.completer());

        getResultTracker.map(result -> {
            if (isDeviceEnabled(result)) {
                return RegistrationResult.from(
                        HttpURLConnection.HTTP_OK,
                        getAssertionPayload(tenantId, deviceId, result.getPayload().getJsonObject(RegistrationConstants.FIELD_DATA)),
                        CacheDirective.maxAgeDirective(assertionFactory.getAssertionLifetime()));
            } else {
                return RegistrationResult.from(HttpURLConnection.HTTP_NOT_FOUND);
            }
        }).setHandler(resultHandler);
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
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(gatewayId);
        Objects.requireNonNull(resultHandler);

        final Future<RegistrationResult> deviceInfoTracker = Future.future();
        final Future<RegistrationResult> gatewayInfoTracker = Future.future();

        getDevice(tenantId, deviceId, deviceInfoTracker.completer());
        getDevice(tenantId, gatewayId, gatewayInfoTracker.completer());

        CompositeFuture.all(deviceInfoTracker, gatewayInfoTracker).compose(ok -> {

            final RegistrationResult deviceResult = deviceInfoTracker.result();
            final RegistrationResult gatewayResult = gatewayInfoTracker.result();

            if (!isDeviceEnabled(deviceResult)) {
                return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_NOT_FOUND));
            } else if (!isDeviceEnabled(gatewayResult)) {
                return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_FORBIDDEN));
            } else {

                final JsonObject deviceData = deviceResult.getPayload().getJsonObject(RegistrationConstants.FIELD_DATA, new JsonObject());
                final JsonObject gatewayData = gatewayResult.getPayload().getJsonObject(RegistrationConstants.FIELD_DATA, new JsonObject());

                if (isGatewayAuthorized(gatewayId, gatewayData, deviceId, deviceData)) {
                    return Future.succeededFuture(RegistrationResult.from(
                        HttpURLConnection.HTTP_OK,
                        getAssertionPayload(tenantId, deviceId, deviceData),
                        CacheDirective.maxAgeDirective(assertionFactory.getAssertionLifetime())));
                } else {
                    return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_FORBIDDEN));
                }
            }
        }).setHandler(resultHandler);
    }

    /**
     * Handles an unimplemented operation by failing the given handler
     * with a {@link ClientErrorException} having a <em>501 Not Implemented</em> status code.
     * 
     * @param resultHandler The handler.
     */
    protected void handleUnimplementedOperation(final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        resultHandler.handle(Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_NOT_IMPLEMENTED)));
    }

    /**
     * Checks if a gateway is authorized to act <em>on behalf of</em> a device.
     * <p>
     * This default implementation checks if the value of the
     * {@link #PROPERTY_VIA} property in the device's registration information
     * matches the gateway's identifier.
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

        return gatewayId.equals(deviceData.getString(PROPERTY_VIA));
    }

    private boolean isDeviceEnabled(final RegistrationResult registrationResult) {
        return registrationResult.isOk() &&
                isDeviceEnabled(registrationResult.getPayload().getJsonObject(RegistrationConstants.FIELD_DATA));
    }

    private boolean isDeviceEnabled(final JsonObject registrationData) {
        return registrationData.getBoolean(RegistrationConstants.FIELD_ENABLED, Boolean.TRUE);
    }

    /**
     * Creates a registration assertion token for a device and wraps it in a JSON object.
     * <p>
     * The returned JSON object may also contain <em>default</em> values registered for the
     * device under key {@link RegistrationConstants#FIELD_DEFAULTS}.
     * 
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The device to create the assertion token for.
     * @param registrationInfo The device's registration information.
     * @return The payload.
     */
    protected final JsonObject getAssertionPayload(final String tenantId, final String deviceId, final JsonObject registrationInfo) {

        final JsonObject result = new JsonObject()
                .put(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId)
                .put(RegistrationConstants.FIELD_ASSERTION, assertionFactory.getAssertion(tenantId, deviceId));
        final JsonObject defaults = registrationInfo.getJsonObject(RegistrationConstants.FIELD_DEFAULTS);
        if (defaults != null) {
            result.put(RegistrationConstants.FIELD_DEFAULTS, defaults);
        }
        return result;
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
