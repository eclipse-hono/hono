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
import java.util.Base64;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.PostConstruct;

import org.eclipse.leshan.server.security.NonUniqueSecurityInfoException;
import org.eclipse.leshan.server.security.SecurityInfo;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.VaultException;
import com.bettercloud.vault.response.LogicalResponse;

import io.vertx.core.json.JsonObject;

/**
 * A leshan {@code SecurityRegistry} which uses Hono's Registration API as the persistence store for identities and secrets.
 *
 */
@Component
@Profile("vault")
public class HonoVaultBasedSecurityRegistry extends AbstractHonoBasedSecurityRegistry {

    private static final String KEY_PSK = "key";
    private static final String PSK_PATH_TEMPLATE = "secret/%s/psk/%s"; // secret/${tenantId}/psk/${psk-identity}

    private Vault vault;

    @PostConstruct
    public void createVaultClient() throws Exception {
        VaultConfig vaultConfig = new VaultConfig().build();
        vault = new Vault(vaultConfig);
    }

    protected SecurityInfo fromJson(final JsonObject data) {

        if (data.containsKey(FIELD_PSK_IDENTITY)) {
            return SecurityInfo.newPreSharedKeyInfo(
                    data.getString(FIELD_ENDPOINT),
                    data.getString(FIELD_PSK_IDENTITY),
                    new byte[0]);
        } else if (data.containsKey(FIELD_RPK)) {
            return SecurityInfo.newRawPublicKeyInfo(
                    data.getString(FIELD_ENDPOINT),
                    fromEncodedKey(data.getBinary(FIELD_RPK), data.getString(FIELD_RPK_ALGORITHM)));
        } else {
            return null;
        }
    }

    private static PublicKey fromEncodedKey(final byte[] encodedKey, final String algorithm) {

        EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(encodedKey);
        try {
            return KeyFactory.getInstance(algorithm).generatePublic(publicKeySpec);
        } catch (GeneralSecurityException e) {
            return null;
        }
    }

    private static JsonObject toJson(final SecurityInfo info) {

        JsonObject keys = new JsonObject().put(FIELD_ENDPOINT, info.getEndpoint());
        if (info.getIdentity() != null) {
            keys.put(FIELD_PSK_IDENTITY, info.getIdentity());
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
        return getKeyFromVault(identity);
    }

    private SecurityInfo getKeyFromVault(final String identity) {
        try {
            String path = String.format(PSK_PATH_TEMPLATE, tenant, identity);
            LogicalResponse resp = vault.logical().read(path);
            String secret = resp.getData().get(KEY_PSK);
            if (secret != null) {
                return SecurityInfo.newPreSharedKeyInfo(identity, identity, Base64.getDecoder().decode(secret));
            } else {
                return null;
            }
        } catch (VaultException e) {
            LOG.error("could not retrieve PSK from Vault", e);
            return null;
        }
    }

    @Override
    public SecurityInfo add(final SecurityInfo info) throws NonUniqueSecurityInfoException {

        final AtomicReference<NonUniqueSecurityInfoException> exception = new AtomicReference<>(null);
        final String deviceId = UUID.randomUUID().toString();

        if (writePreSharedKeyToVault(info.getIdentity(), info.getPreSharedKey())) {

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
                                    removePreSharedKeyFromVault(info.getIdentity());
                                    exception.set(new NonUniqueSecurityInfoException("device already registered"));
                                }
                            } else {
                                // nothing we can do
                                LOG.error("could not register device with Hono", registerAttempt.cause());
                                removePreSharedKeyFromVault(info.getIdentity());
                            }
                            latch.countDown();
                        });
                    } else {
                        LOG.error("could not get RegistrationClient", c.cause());
                        removePreSharedKeyFromVault(info.getIdentity());
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
        } else {
            return null;
        }
    }

    private boolean writePreSharedKeyToVault(final String identity, final byte[] key) {
        try {
            String path = String.format(PSK_PATH_TEMPLATE, tenant, identity);
            vault.logical().write(path, Collections.singletonMap(KEY_PSK, Base64.getEncoder().encodeToString(key)));
            return true;
        } catch (VaultException e) {
            LOG.error("could not write pre-shared key [{}] to Vault", identity, e);
            return false;
        }
    }

    private void removePreSharedKeyFromVault(final String identity) {
        try {
            String path = String.format(PSK_PATH_TEMPLATE, tenant, identity);
            vault.logical().delete(path);
        } catch (VaultException e) {
            LOG.error("could not remove pre-shared key [{}] from Vault", identity, e);
        }
    }

    @Override
    public SecurityInfo remove(final String endpoint) {

        SecurityInfo removedRegistration = executeBlocking(result -> {
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

        if (removedRegistration != null) {
            removePreSharedKeyFromVault(removedRegistration.getIdentity());
        }

        return removedRegistration;
    }
}
