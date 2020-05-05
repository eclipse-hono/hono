/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.mqtt;

import javax.net.ssl.SSLSession;

import org.eclipse.hono.client.TenantClientFactory;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.BaseExecutionContextTenantAndAuthIdProvider;
import org.eclipse.hono.util.TenantObjectWithAuthId;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.mqtt.MqttAuth;

/**
 * Provides a method to determine the tenant and auth-id of a HTTP request from the given HttpContext.
 */
public class MqttContextTenantAndAuthIdProvider extends BaseExecutionContextTenantAndAuthIdProvider<MqttContext> {

    /**
     * Creates a new HttpContextTenantAndAuthIdProvider.
     *
     * @param config The configuration.
     * @param tenantClientFactory The factory to use for creating a Tenant service client.
     * @throws NullPointerException if either of the parameters is {@code null}.
     */
    public MqttContextTenantAndAuthIdProvider(final ProtocolAdapterProperties config,
            final TenantClientFactory tenantClientFactory) {
        super(config, tenantClientFactory);
    }

    @Override
    public Future<TenantObjectWithAuthId> get(final MqttContext context, final SpanContext spanContext) {
        if (config.isAuthenticationRequired()) {
            return getTenantViaCert(context, spanContext)
                    .recover(thr -> getTenantFromAuthHeader(context, spanContext));
        }
        if (context.topic() != null && context.topic().getTenantId() != null
                && context.topic().getResourceId() != null) {
            // unauthenticated request
            final String tenantId = context.topic().getTenantId();
            final String deviceId = context.topic().getResourceId();
            return tenantClientFactory.getOrCreateTenantClient()
                    .compose(tenantClient -> tenantClient.get(tenantId, spanContext))
                    .map(tenantObject -> new TenantObjectWithAuthId(tenantObject, deviceId));
        }
        return Future.failedFuture("tenant could not be determined");
    }

    private Future<TenantObjectWithAuthId> getTenantViaCert(final MqttContext context, final SpanContext spanContext) {
        if (!context.deviceEndpoint().isSsl()) {
            return Future.failedFuture("no cert found (not SSL/TLS encrypted)");
        }
        final SSLSession sslSession = context.deviceEndpoint().sslSession();
        return getFromClientCertificate(sslSession, spanContext);
    }

    private Future<TenantObjectWithAuthId> getTenantFromAuthHeader(final MqttContext context,
            final SpanContext spanContext) {
        final MqttAuth auth = context.deviceEndpoint().auth();
        if (auth == null) {
            return Future.failedFuture("no credentials provided");
        }
        return getFromUserName(auth.getUsername(), spanContext);
    }
}
