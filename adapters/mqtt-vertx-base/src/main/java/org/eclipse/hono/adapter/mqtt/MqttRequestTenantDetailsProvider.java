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

import java.net.HttpURLConnection;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.TenantClientFactory;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.auth.device.CredentialsWithTenantDetailsProvider;
import org.eclipse.hono.service.auth.device.TenantDetailsDeviceCredentials;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TenantObjectContainer;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.mqtt.MqttAuth;
import io.vertx.mqtt.MqttEndpoint;

/**
 * Provides the tenant and device credentials determined from an MQTT request.
 */
public class MqttRequestTenantDetailsProvider {

    private final CredentialsWithTenantDetailsProvider credentialsProvider;
    private final ProtocolAdapterProperties config;
    private final TenantClientFactory tenantClientFactory;

    /**
     * Creates a new MqttRequestTenantDetailsProvider instance.
     *
     * @param config The configuration.
     * @param tenantClientFactory The factory to use for creating a Tenant service client.
     * @throws NullPointerException if either of the parameters is {@code null}.
     */
    public MqttRequestTenantDetailsProvider(final ProtocolAdapterProperties config,
            final TenantClientFactory tenantClientFactory) {
        this.config = config;
        this.tenantClientFactory = tenantClientFactory;
        credentialsProvider = new CredentialsWithTenantDetailsProvider(config, tenantClientFactory);
    }

    /**
     * Gets tenant details and credentials out of either the client certificate or the username/password
     * credentials provided in the MQTT CONNECT packet.
     * <p>
     * This method will return a failed future in case of an unauthenticated MQTT connection.
     *
     * @param endpoint The MQTT endpoint to extract the data out of.
     * @param spanContext The OpenTracing context to use for tracking the operation (may be {@code null}).
     * @return A future containing the tenant and authentication identifier. The future will be failed if there was an
     *         error getting the credentials via the credentialsProvider. In particular, the future will be failed with
     *         a {@link ClientErrorException} if there was no corresponding data given in the connection attempt to
     *         determine the tenant.
     * @throws NullPointerException if endpoint is {@code null}.
     */
    public Future<TenantDetailsDeviceCredentials> getFromMqttEndpoint(final MqttEndpoint endpoint, final SpanContext spanContext) {

        return credentialsProvider.getFromClientCertificate(endpoint.sslSession(), null, spanContext)
                .recover(thr -> getFromMqttAuth(endpoint.auth(), spanContext));
    }

    private Future<TenantDetailsDeviceCredentials> getFromMqttAuth(final MqttAuth auth, final SpanContext spanContext) {
        if (auth == null) {
            return Future.failedFuture(new ClientErrorException(
                    HttpURLConnection.HTTP_UNAUTHORIZED,
                    "device did not provide credentials in CONNECT packet"));
        }
        return credentialsProvider.getFromUserName(auth.getUsername(), () -> new ClientErrorException(
                HttpURLConnection.HTTP_UNAUTHORIZED,
                "device provided malformed credentials in CONNECT packet"), spanContext);
    }

    /**
     * Gets tenant details out of context information pertaining to a received PUBLISH message.
     * <p>
     * The data is either taken from the client certificate or username/password credentials provided in the original
     * MQTT CONNECT packet, or in case of an unauthenticated connection it is taken from the topic of the published
     * message.
     *
     * @param endpoint The MQTT endpoint to extract the data out of in case of an authenticated request.
     * @param topic The topic of the published message.
     * @param spanContext The OpenTracing context to use for tracking the operation (may be {@code null}).
     * @return A future containing the tenant and authentication identifier. The future will be failed if there was an
     *         error getting the credentials via the credentialsProvider. In particular, the future will be failed with
     *         a {@link ClientErrorException} if there was no corresponding data given in the connection attempt to
     *         determine the tenant.
     * @throws NullPointerException if endpoint is {@code null}.
     */
    public Future<TenantObjectContainer> getFromMqttEndpointOrPublishTopic(final MqttEndpoint endpoint,
            final ResourceIdentifier topic, final SpanContext spanContext) {

        if (config.isAuthenticationRequired()) {
            return getFromMqttEndpoint(endpoint, spanContext).map(cred -> cred);
        }
        // unauthenticated connection
        if (topic != null && topic.getTenantId() != null) {
            return tenantClientFactory.getOrCreateTenantClient()
                    .compose(tenantClient -> tenantClient.get(topic.getTenantId(), spanContext))
                    .map(tenantObject -> () -> tenantObject);
        } else {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "malformed topic name"));
        }
    }
}
