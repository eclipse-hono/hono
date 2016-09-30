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
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.PostConstruct;

import org.eclipse.leshan.server.security.NonUniqueSecurityInfoException;
import org.eclipse.leshan.server.security.SecurityInfo;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.vertx.core.json.JsonObject;

/**
 * A leshan {@code SecurityRegistry} which uses Hono's Registration API as the persistence store for identities and secrets.
 *
 */
@Component
@Profile("hono")
public class HonoBasedSecurityRegistry extends AbstractHonoBasedSecurityRegistry {

    private static final String FIELD_PSK = "psk";

    @PostConstruct
    public void init() throws Exception {
    }

    protected SecurityInfo fromJson(final JsonObject data) {

        if (data == null) {
            return null;
        } else if (data.containsKey(FIELD_PSK_IDENTITY)) {
            return SecurityInfo.newPreSharedKeyInfo(
                    data.getString(FIELD_ENDPOINT),
                    data.getString(FIELD_PSK_IDENTITY),
                    data.getBinary(FIELD_PSK));
        } else if (data.containsKey(FIELD_RPK)) {
            return SecurityInfo.newRawPublicKeyInfo(
                    data.getString(FIELD_ENDPOINT),
                    fromEncodedKey(data.getBinary(FIELD_RPK), data.getString(FIELD_RPK_ALGORITHM)));
        } else {
            return null;
        }
    }

    protected static PublicKey fromEncodedKey(final byte[] encodedKey, final String algorithm) {

        EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(encodedKey);
        try {
            return KeyFactory.getInstance(algorithm).generatePublic(publicKeySpec);
        } catch (GeneralSecurityException e) {
            return null;
        }
    }

    protected static JsonObject toJson(final SecurityInfo info) {

        JsonObject keys = new JsonObject().put(FIELD_ENDPOINT, info.getEndpoint());
        if (info.getIdentity() != null) {
            keys.put(FIELD_PSK_IDENTITY, info.getIdentity());
            keys.put(FIELD_PSK, info.getPreSharedKey());
        } else if (info.getRawPublicKey() != null) {
            keys.put(FIELD_RPK_ALGORITHM, info.getRawPublicKey().getAlgorithm());
            keys.put(FIELD_RPK, info.getRawPublicKey().getEncoded());
        }
        return keys;
    }

    /**
     * Looks up the pre-shared key (the secret) for a PSK identity.
     * 
     * @param identity The PSK identity to look up the key for.
     * @return The pre-shared key or {@code null} if no key is registered for the given identity.
     */
    @Override
    public SecurityInfo getByIdentity(final String identity) {
        return getKeyFromHono(identity);
    }

    private SecurityInfo getKeyFromHono(final String identity) {

        return executeBlocking(result -> {

            CountDownLatch latch = new CountDownLatch(1);
            hono.getOrCreateRegistrationClient(tenant, c -> {
                if (c.succeeded()) {
                    c.result().find(FIELD_PSK_IDENTITY, identity, getAttempt -> {
                        if (getAttempt.succeeded() && getAttempt.result().getStatus() == HttpURLConnection.HTTP_OK) {
                            SecurityInfo keys = fromJson(getAttempt.result().getPayload().getJsonObject(FIELD_DATA));
                            result.set(keys);
                            LOG.debug("found PSK for identity [{}]: {}", identity, keys);
                        } else {
                            LOG.debug("no device registration found for key: {}, value: {}", FIELD_PSK_IDENTITY,
                                    identity);
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

    @Override
    public SecurityInfo add(final SecurityInfo info) throws NonUniqueSecurityInfoException {

        final AtomicReference<NonUniqueSecurityInfoException> exception = new AtomicReference<>(null);
        final String deviceId = UUID.randomUUID().toString();

        SecurityInfo securityInfo = executeBlocking(result -> {
            CountDownLatch latch = new CountDownLatch(1);
            hono.getOrCreateRegistrationClient(tenant, c -> {
                if (c.succeeded()) {
                    c.result().register(deviceId, toJson(info), registerAttempt -> {
                        if (registerAttempt.succeeded()) {
                            if (registerAttempt.result().getStatus() == HttpURLConnection.HTTP_CREATED) {
                                LOG.info("successfully registered device [{}] with Hono", deviceId);
                            } else if (registerAttempt.result().getStatus() == HttpURLConnection.HTTP_CONFLICT) {
                                // TODO: try to update existing registration
                                exception.set(new NonUniqueSecurityInfoException("device already registered"));
                            }
                        } else {
                            // nothing we can do
                            LOG.error("could not register device with Hono", registerAttempt.cause());
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

        if (exception.get() != null) {
            throw exception.get();
        } else {
            return securityInfo;
        }
    }

    @Override
    public SecurityInfo remove(final String endpoint) {

        return executeBlocking(result -> {
            CountDownLatch latch = new CountDownLatch(1);
            hono.getOrCreateRegistrationClient(tenant, c -> {
                if (c.succeeded()) {
                    c.result().deregister(endpoint, removeAttempt -> {
                        if (removeAttempt.succeeded() && removeAttempt.result().getStatus() == HttpURLConnection.HTTP_OK) {
                            endpointMap.remove(endpoint);
                            result.set(null);
                        } else {
                            LOG.error("could not deregister device {}", endpoint);
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
}
