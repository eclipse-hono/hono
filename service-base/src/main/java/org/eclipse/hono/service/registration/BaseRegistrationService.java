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

import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;

import io.opentracing.Span;
import io.vertx.core.AsyncResult;
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
 * status. Subclasses may override the {@link #assertRegistration(String, String, String, Span, Handler)}
 * method in order to implement a more sophisticated assertion method.
 * <p>
 * The default implementation of <em>assertRegistration</em> relies on {@link #getDevice(String, String, Handler)}
 * to retrieve a device's registration information from persistent storage. Thus, subclasses need
 * to override (and implement) this method in order to get a working implementation of the default
 * assertion mechanism.
 * 
 * @param <T> The type of configuration properties this service requires.
 * @deprecated - Use {@link RegistrationService} and {@link org.eclipse.hono.service.management.device.DeviceManagementService} instead.
 */
@Deprecated
public abstract class BaseRegistrationService<T> extends EventBusRegistrationAdapter implements RegistrationService {

    /**
     * The default number of seconds that information returned by this service's
     * operations may be cached for.
     */
    public static final int DEFAULT_MAX_AGE_SECONDS = 300;

    private T config;

    private final AbstractRegistrationService service = new AbstractRegistrationService() {

        @Override
        protected void getDevice(final String tenantId, final String deviceId, final Span span,
                final Handler<AsyncResult<RegistrationResult>> resultHandler) {
            BaseRegistrationService.this.getDevice(tenantId, deviceId, resultHandler);
        }
    };

    @Override
    protected RegistrationService getService() {
        return this;
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
    public void getDevice(final String tenantId, final String deviceId,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    @Override
    public final void assertRegistration(final String tenantId, final String deviceId,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        service.assertRegistration(tenantId, deviceId, resultHandler);
    }

    @Override
    public void assertRegistration(final String tenantId, final String deviceId, final Span span,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        service.assertRegistration(tenantId, deviceId, span, resultHandler);
    }

    @Override
    public final void assertRegistration(final String tenantId, final String deviceId, final String gatewayId,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        service.assertRegistration(tenantId, deviceId, gatewayId, resultHandler);
    }

    @Override
    public void assertRegistration(final String tenantId, final String deviceId, final String gatewayId,
            final Span span,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        service.assertRegistration(tenantId, deviceId, gatewayId, span, resultHandler);
    }

    /**
     * Creates the payload of the assert Registration response message.
     * <p>
     * The returned JSON object may contain the {@link RegistrationConstants#FIELD_VIA} property of the device's
     * registration information and may also contain <em>default</em> values registered for the device under key
     * {@link RegistrationConstants#FIELD_PAYLOAD_DEFAULTS}.
     * 
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The device to create the assertion token for.
     * @param registrationInfo The device's registration information.
     * @return The payload.
     */
    protected final JsonObject getAssertionPayload(final String tenantId, final String deviceId,
            final JsonObject registrationInfo) {

        return service.getAssertionPayload(tenantId, deviceId, registrationInfo);
    }

    /**
     * Sets the specific object instance to use for configuring this <em>Verticle</em>.
     * 
     * @param props The properties.
     */
    protected final void setSpecificConfig(final T props) {
        this.config = props;
    }

    /**
     * Sets the properties to use for configuring this <em>Verticle</em>.
     * <p>
     * Subclasses <em>must</em> invoke {@link #setSpecificConfig(Object)} with the configuration object.
     * <p>
     * This method mainly exists so that subclasses can annotate its concrete implementation with Spring annotations
     * like {@code Autowired} and/or {@code Qualifier} to get injected a particular bean instance.
     * 
     * @param configuration The configuration properties.
     * @throws NullPointerException if configuration is {@code null}.
     */
    public abstract void setConfig(T configuration);

    /**
     * Gets the properties that this <em>Verticle</em> has been configured with.
     * 
     * @return The properties or {@code null} if not set.
     */
    public final T getConfig() {
        return this.config;
    }
}
