/**
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
 */


package org.eclipse.hono.tests.registry;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.HttpURLConnection;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.client.CredentialsClient;
import org.eclipse.hono.service.credentials.Credentials;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.credentials.PasswordCredential;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

/**
 * Common test cases for the Credentials API.
 *
 */
abstract class CredentialsApiTests extends DeviceRegistryTestBase {

    /**
     * Gets a client for interacting with the Credentials service.
     *
     * @param tenant The tenant to scope the client to.
     * @return The client.
     */
    protected abstract Future<CredentialsClient> getClient(String tenant);

    /**
     * Verify that a request to retrieve credentials for a non-existing authentication ID fails with a 404.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testGetCredentialsFailsForNonExistingAuthId(final VertxTestContext ctx) {

        getClient(Constants.DEFAULT_TENANT)
                .compose(client -> client.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, "nonExisting"))
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> assertErrorCode(t, HttpURLConnection.HTTP_NOT_FOUND));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the service returns credentials for a given type and authentication ID including the default value
     * for the enabled property..
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testGetCredentialsByTypeAndAuthId(final VertxTestContext ctx) {

        final String deviceId = getHelper().getRandomDeviceId(Constants.DEFAULT_TENANT);
        final String authId = UUID.randomUUID().toString();
        final String otherAuthId = UUID.randomUUID().toString();
        final List<CommonCredential> credentials = List.of(
                getRandomHashedPasswordCredential(authId),
                getRandomHashedPasswordCredential(otherAuthId));

        getHelper().registry
                .registerDevice(Constants.DEFAULT_TENANT, deviceId)
                .compose(ok -> getHelper().registry.addCredentials(Constants.DEFAULT_TENANT, deviceId, credentials))
                .compose(ok -> getClient(Constants.DEFAULT_TENANT))
                .compose(client -> client.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, otherAuthId))
                .onComplete(ctx.succeeding(result -> {
                    ctx.verify(() -> assertStandardProperties(
                            result,
                            deviceId,
                            otherAuthId,
                            CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                            2));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that if no credentials are found and the client context in the Get request contains a serialized X.509
     * certificate, the credentials and device are created (i.e., automatic provisioning is performed).
     *
     * @param ctx The vert.x test context.
     * @throws CertificateException if the self signed certificate cannot be created.
     * @throws FileNotFoundException if the self signed certificate cannot be read.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testGetCredentialsWithAutoProvisioning(final VertxTestContext ctx)
            throws CertificateException, FileNotFoundException {

        // GIVEN a client context that contains a client certificate to allow auto-provisioning
        // while device has not been registered and no credentials are stored yet
        final X509Certificate cert = createCertificate();
        final JsonObject clientCtx = new JsonObject().put(CredentialsConstants.FIELD_CLIENT_CERT, cert.getEncoded());
        final String authId = cert.getSubjectX500Principal().getName(X500Principal.RFC2253);

        getClient(Constants.DEFAULT_TENANT)
                // WHEN getting credentials
                .compose(client -> client.get(CredentialsConstants.SECRETS_TYPE_X509_CERT, authId, clientCtx))
                .onComplete(ctx.succeeding(result -> {
                    // THEN the newly created credentials are returned...
                    ctx.verify(() -> {
                        assertThat(result).isNotNull();
                        assertThat(result.isEnabled()).isTrue();
                        assertThat(result.getDeviceId()).isNotNull();
                        assertThat(result.getAuthId()).isEqualTo(authId);
                        assertThat(result.getType()).isEqualTo(CredentialsConstants.SECRETS_TYPE_X509_CERT);
                        assertThat(result.getSecrets()).isNotNull();
                        assertThat(result.getSecrets()).hasSize(1);
                    });

                    getHelper().registry.getRegistrationInfo(Constants.DEFAULT_TENANT, result.getDeviceId())
                            .onComplete(ctx.succeeding(httpResponse -> {
                                // AND the device has been registered as well
                                ctx.verify(() -> assertThat(httpResponse.bodyAsJson(Device.class).isEnabled()));
                                ctx.completeNow();
                            }));
                }));
    }

    private X509Certificate createCertificate() throws CertificateException, FileNotFoundException {
        final SelfSignedCertificate ssc = SelfSignedCertificate.create(UUID.randomUUID().toString());
        final CertificateFactory factory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) factory.generateCertificate(new FileInputStream(ssc.certificatePath()));
    }

    /**
     * Verifies that the service fails when the credentials set is disabled.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testGetCredentialsFailsForDisabledCredentials(final VertxTestContext ctx) {

        final String deviceId = getHelper().getRandomDeviceId(Constants.DEFAULT_TENANT);
        final String authId = UUID.randomUUID().toString();

        final CommonCredential credential = getRandomHashedPasswordCredential(authId);
        credential.setEnabled(false);

        getHelper().registry
                .registerDevice(Constants.DEFAULT_TENANT, deviceId)
                .compose(ok -> {
                    return getHelper().registry
                            .addCredentials(Constants.DEFAULT_TENANT, deviceId, Collections.singleton(credential))
                            .compose(ok2 -> getClient(Constants.DEFAULT_TENANT))
                            .compose(client -> client.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, authId));
                })
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> assertErrorCode(t, HttpURLConnection.HTTP_NOT_FOUND));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the service returns credentials for a given type, authentication ID and matching client context.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testGetCredentialsByClientContext(final VertxTestContext ctx) {

        final String deviceId = getHelper().getRandomDeviceId(Constants.DEFAULT_TENANT);
        final String authId = UUID.randomUUID().toString();
        final CommonCredential credentials = getRandomHashedPasswordCredential(authId)
                .putExtension("client-id", "gateway-one");

        final JsonObject clientContext = new JsonObject()
                .put("client-id", "gateway-one");

        getHelper().registry.registerDevice(Constants.DEFAULT_TENANT, deviceId)
            .compose(httpResponse -> getHelper().registry.addCredentials(Constants.DEFAULT_TENANT, deviceId, List.of(credentials)))
            .compose(ok -> getClient(Constants.DEFAULT_TENANT))
            .compose(client -> client.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, authId, clientContext))
            .onComplete(ctx.succeeding(result -> {
                ctx.verify(() -> {
                    assertStandardProperties(
                            result,
                            deviceId,
                            authId,
                            CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                            2);
                });
                ctx.completeNow();
        }));
    }

    /**
     * Verifies that a request for credentials using a client context that does not match
     * the credentials on record fails with a 404.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @EnabledIfSystemProperty(named = "deviceregistry.credentials.supportsClientContext", matches = "true")
    @Test
    public void testGetCredentialsFailsForNonMatchingClientContext(final VertxTestContext ctx) {

        final String deviceId = getHelper().getRandomDeviceId(Constants.DEFAULT_TENANT);
        final String authId = UUID.randomUUID().toString();
        final CommonCredential credentials = getRandomHashedPasswordCredential(authId)
                .putExtension("client-id", UUID.randomUUID().toString());

        final JsonObject clientContext = new JsonObject()
                .put("client-id", "non-matching");

        getHelper().registry.registerDevice(Constants.DEFAULT_TENANT, deviceId)
            .compose(httpResponse -> getHelper().registry.addCredentials(Constants.DEFAULT_TENANT, deviceId, List.of(credentials)))
            .compose(ok -> getClient(Constants.DEFAULT_TENANT))
            .compose(client -> client.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, authId, clientContext))
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> assertErrorCode(t, HttpURLConnection.HTTP_NOT_FOUND));
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a request for credentials using a client context succeeds if the credentials on record
     * do not have any extension properties with keys matching the provided client context.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testGetCredentialsSucceedsForNonExistingClientContext(final VertxTestContext ctx) {

        final String deviceId = getHelper().getRandomDeviceId(Constants.DEFAULT_TENANT);
        final String authId = UUID.randomUUID().toString();
        final CommonCredential credentials = getRandomHashedPasswordCredential(authId)
                .putExtension("other", "property");

        final JsonObject clientContext = new JsonObject()
                .put("client-id", "gateway-one");

        getHelper().registry
            .registerDevice(Constants.DEFAULT_TENANT, deviceId)
            .compose(httpResponse -> getHelper().registry.addCredentials(Constants.DEFAULT_TENANT, deviceId, List.of(credentials)))
            .compose(httpResponse -> getClient(Constants.DEFAULT_TENANT))
            .compose(client -> client.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, authId, clientContext))
            .onComplete(ctx.succeeding(credentialsObject -> {
                ctx.verify(() -> {
                    assertThat(credentialsObject.getSecrets()).isNotEmpty();
                });
                ctx.completeNow();
            }));
    }

    private static CommonCredential getRandomHashedPasswordCredential(final String authId) {

        final var secret1 = Credentials.createPasswordSecret("ClearTextPWD",
                OptionalInt.of(IntegrationTestSupport.MAX_BCRYPT_ITERATIONS));
        secret1.setNotBefore(Instant.parse("2017-05-01T14:00:00Z"));
        secret1.setNotAfter(Instant.parse("2037-06-01T14:00:00Z"));

        final var secret2 = Credentials.createPasswordSecret("hono-password",
                OptionalInt.of(IntegrationTestSupport.MAX_BCRYPT_ITERATIONS));

        return new PasswordCredential(authId, List.of(secret1, secret2));
    }

    private static void assertStandardProperties(
            final CredentialsObject credentials,
            final String expectedDeviceId,
            final String expectedAuthId,
            final String expectedType,
            final int expectedNumberOfSecrets) {

        assertThat(credentials).isNotNull();
        // only enabled credentials must be returned
        assertThat(credentials.isEnabled()).isTrue();
        assertThat(credentials.getDeviceId()).isEqualTo(expectedDeviceId);
        assertThat(credentials.getAuthId()).isEqualTo(expectedAuthId);
        assertThat(credentials.getType()).isEqualTo(expectedType);
        assertThat(credentials.getSecrets()).isNotNull();
        assertThat(credentials.getSecrets()).hasSize(expectedNumberOfSecrets);

    }

}
