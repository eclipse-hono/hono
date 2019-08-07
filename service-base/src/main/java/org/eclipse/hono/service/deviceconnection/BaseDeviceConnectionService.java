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

package org.eclipse.hono.service.deviceconnection;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.util.DeviceConnectionResult;

import io.opentracing.Span;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * A base class for implementing {@link DeviceConnectionService}.
 * <p>
 * This base class provides support for receiving service invocation request messages
 * via vert.x' event bus and routing them to specific methods accepting the
 * query parameters contained in the request message.
 *
 * @param <T> The type of configuration class this service supports.
 * @deprecated - Use {@link DeviceConnectionService} instead.
 */
@Deprecated
public abstract class BaseDeviceConnectionService<T> extends EventBusDeviceConnectionAdapter
        implements DeviceConnectionService {

    private T config;

    @Override
    protected DeviceConnectionService getService() {
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void setLastKnownGatewayForDevice(final String tenantId, final String deviceId, final String gatewayId,
            final Span span, final Handler<AsyncResult<DeviceConnectionResult>> resultHandler) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(gatewayId);

        handleUnimplementedOperation(resultHandler);
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void getLastKnownGatewayForDevice(final String tenantId, final String deviceId, final Span span,
            final Handler<AsyncResult<DeviceConnectionResult>> resultHandler) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        handleUnimplementedOperation(resultHandler);
    }

    /**
     * Handles an unimplemented operation by failing the given handler
     * with a {@link ClientErrorException} having a <em>501 Not Implemented</em> status code.
     *
     * @param resultHandler The handler.
     */
    protected void handleUnimplementedOperation(final Handler<AsyncResult<DeviceConnectionResult>> resultHandler) {
        resultHandler.handle(Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_NOT_IMPLEMENTED)));
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
