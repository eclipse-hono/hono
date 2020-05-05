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

package org.eclipse.hono.adapter.amqp.impl;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Objects;

import org.eclipse.hono.adapter.amqp.SaslResponseContext;
import org.eclipse.hono.client.TenantClientFactory;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.BaseExecutionContextTenantAndAuthIdProvider;
import org.eclipse.hono.util.AuthenticationConstants;
import org.eclipse.hono.util.TenantObjectWithAuthId;

import io.opentracing.SpanContext;
import io.vertx.core.Future;

/**
 * Provides methods to determine the tenant and auth-id from a AMQP SASL handshake or an AMQP message.
 */
public class AmqpContextTenantAndAuthIdProvider extends BaseExecutionContextTenantAndAuthIdProvider<AmqpContext> {

    /**
     * Creates a new BaseExecutionContextTenantAndAuthIdProvider for the given config and tenantClientFactory.
     *
     * @param config The configuration.
     * @param tenantClientFactory The factory to use for creating a Tenant service client.
     * @throws NullPointerException if either of the parameters is {@code null}.
     */
    public AmqpContextTenantAndAuthIdProvider(final ProtocolAdapterProperties config,
            final TenantClientFactory tenantClientFactory) {
        super(config, tenantClientFactory);
    }

    @Override
    public Future<TenantObjectWithAuthId> get(final AmqpContext context, final SpanContext spanContext) {
        if (context.getAddress() == null || context.getAddress().getTenantId() == null
                || context.getAddress().getResourceId() == null) {
            return Future.failedFuture("tenant could not be determined");
        }
        return get(context.getAddress().getTenantId(), context.getAddress().getResourceId(), spanContext);
    }

    /**
     * Get the tenant and auth-id from the given SASL handshake information.
     *
     * @param context The context with information about the SASL handshake.
     * @param spanContext The OpenTracing context to use for tracking the operation (may be {@code null}).
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will fail if tenant and auth-id information could not be retrieved from the ExecutionContext
     *         or if there was an error obtaining the tenant object. In the latter case the future will be failed with a
     *         {@link org.eclipse.hono.client.ServiceInvocationException}.
     *         <p>
     *         Otherwise the future will contain the created <em>TenantObjectWithAuthId</em>.
     * @throws NullPointerException if context is {@code null}.
     */
    public Future<TenantObjectWithAuthId> get(final SaslResponseContext context, final SpanContext spanContext) {
        Objects.requireNonNull(context);
        if (AuthenticationConstants.MECHANISM_PLAIN.equals(context.getRemoteMechanism())) {
            return getFromUserName(context.getSaslResponseFields()[1], spanContext);
        } else if (AuthenticationConstants.MECHANISM_EXTERNAL.equals(context.getRemoteMechanism())) {
            final Certificate[] peerCertificateChain = context.getPeerCertificateChain();
            if (peerCertificateChain != null && peerCertificateChain.length > 0
                    && peerCertificateChain[0] instanceof X509Certificate) {
                return getFromClientCertificate((X509Certificate) peerCertificateChain[0], spanContext);
            }
        }
        return Future.failedFuture("tenant could not be determined");
    }
}
