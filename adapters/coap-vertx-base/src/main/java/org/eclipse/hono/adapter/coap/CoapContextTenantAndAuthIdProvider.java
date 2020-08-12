/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.coap;

import org.eclipse.hono.client.TenantClientFactory;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.BaseExecutionContextTenantAndAuthIdProvider;
import org.eclipse.hono.util.TenantObjectWithAuthId;

import io.opentracing.SpanContext;
import io.vertx.core.Future;

/**
 * Provides methods to determine the tenant and auth-id of a CoAP request from the given CoapContext.
 */
public class CoapContextTenantAndAuthIdProvider extends BaseExecutionContextTenantAndAuthIdProvider<CoapContext> {

    /**
     * Creates a new BaseExecutionContextTenantAndAuthIdProvider for the given config and tenantClientFactory.
     *
     * @param config The configuration.
     * @param tenantClientFactory The factory to use for creating a Tenant service client.
     * @throws NullPointerException if either of the parameters is {@code null}.
     */
    public CoapContextTenantAndAuthIdProvider(final ProtocolAdapterProperties config,
            final TenantClientFactory tenantClientFactory) {
        super(config, tenantClientFactory);
    }

    @Override
    public Future<TenantObjectWithAuthId> get(final CoapContext context, final SpanContext spanContext) {
        // use device id as fallback for the authId
        final String authId = context.getAuthId() != null ? context.getAuthId()
                : context.getOriginDevice().getDeviceId();
        return get(context.getTenantId(), authId, spanContext);
    }

    /**
     * Get the tenant and auth-id from the given authenticated device's pre shared key identity.
     *
     * @param deviceIdentity The authenticated device's pre shared key identity.
     * @param spanContext The OpenTracing context to use for tracking the operation (may be {@code null}).
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will fail with a  {@code org.eclipse.hono.client.ServiceInvocationException}
     *         if there was an error obtaining the tenant object.
     *         <p>
     *         Otherwise the future will contain the created <em>TenantObjectWithAuthId</em>.
     */
    public Future<TenantObjectWithAuthId> get(final PreSharedKeyDeviceIdentity deviceIdentity, final SpanContext spanContext) {
        return get(deviceIdentity.getTenantId(), deviceIdentity.getAuthId(), spanContext);
    }

}
