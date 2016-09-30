/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.adapter.lwm2m.security;

import java.net.HttpURLConnection;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.eclipse.hono.adapter.lwm2m.AbstractHonoClientSupport;
import org.eclipse.hono.adapter.lwm2m.ServerKeyProvider;
import org.eclipse.leshan.server.security.SecurityInfo;
import org.eclipse.leshan.server.security.SecurityRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import io.vertx.core.json.JsonObject;

/**
 * A leshan {@code SecurityRegistry} which provides support for using Hono's Registration API as
 * the persistence store for identities and secrets.
 */
public abstract class AbstractHonoBasedSecurityRegistry extends AbstractHonoClientSupport implements SecurityRegistry {

    protected static final String FIELD_DATA = "data";
    protected static final String FIELD_ENDPOINT = "ep";
    protected static final String FIELD_PSK_IDENTITY = "psk-id";
    protected static final String FIELD_RPK = "rpk";
    protected static final String FIELD_RPK_ALGORITHM = "rpk-algo";
    protected static final long TIMEOUT_SECS = 5;

    protected Map<String, String> endpointMap;
    private ServerKeyProvider keyProvider;

    /**
     * Sets the map containing LWM2M endpoint name to Hono identifier mappings.
     * 
     * @param endpointMap The endpoint map.
     * @throws NullPointerException if the map is {@code null}.
     */
    @Autowired
    @Qualifier("endpointMap")
    public void setEndpointMap(final Map<String, String> endpointMap) {
        this.endpointMap = Objects.requireNonNull(endpointMap);
    }

    /**
     * Sets the provider for the server's private and public keys.
     * 
     * @param keyProvider The provider to use.
     * @throws NullPointerException if the map is {@code null}.
     */
    @Autowired
    public void setServerKeyProvider(final ServerKeyProvider keyProvider) {
        this.keyProvider = Objects.requireNonNull(keyProvider);
    }

    abstract protected SecurityInfo fromJson(final JsonObject data);

    protected SecurityInfo executeBlocking(final Function<AtomicReference<SecurityInfo>, CountDownLatch> command) {

        AtomicReference<SecurityInfo> result = new AtomicReference<>(null);
        CountDownLatch latch = command.apply(result);

        try {
            if (!latch.await(TIMEOUT_SECS, TimeUnit.SECONDS)) {
                LOG.error("registration request to Hono timed out");
                throw new RuntimeException("request timed out");
            } else {
                return result.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final SecurityInfo getByEndpoint(final String endpoint) {

        return executeBlocking(result -> {
            CountDownLatch latch = new CountDownLatch(1);
            hono.getOrCreateRegistrationClient(tenant, c -> {
                if (c.succeeded()) {
                    c.result().find(FIELD_ENDPOINT, endpoint, getAttempt -> {
                        if (getAttempt.succeeded() && getAttempt.result().getStatus() == HttpURLConnection.HTTP_OK) {
                            JsonObject payload = getAttempt.result().getPayload();
                            String honoId = payload.getString("id");
                            LOG.debug("mapping endpoint {} to Hono ID {}", endpoint, honoId);
                            endpointMap.put(endpoint, honoId);
                            result.set(fromJson(payload.getJsonObject(FIELD_DATA)));
                        } else {
                            LOG.debug("no device registration found for endpoint: {}", endpoint);
                        }
                        latch.countDown();
                    });
                } else {
                    LOG.error("could not get RegistrationClient", c.cause());
                    latch.countDown();
                }
            });
            return latch;
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<SecurityInfo> getAll() {
        return Collections.EMPTY_LIST;
    }

    @Override
    public final PublicKey getServerPublicKey() {
        return keyProvider.getServerPublicKey();
    }

    @Override
    public final PrivateKey getServerPrivateKey() {
        return keyProvider.getServerPrivateKey();
    }

    @Override
    public final X509Certificate[] getServerX509CertChain() {
        return keyProvider.getServerX509CertChain();
    }

    @Override
    public final Certificate[] getTrustedCertificates() {
        return keyProvider.getTrustedCertificates();
    }
}
