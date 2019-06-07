/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.tenant;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.UUID;

import javax.security.auth.x500.X500Principal;

import io.opentracing.Span;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantResult;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

/**
 * Tests verifying behavior of {@link CompleteBaseTenantService}.
 *
 */
@RunWith(VertxUnitRunner.class)
@Deprecated
public class CompleteBaseTenantServiceTest {

    private static final String TEST_TENANT = "dummy";

    private static CompleteBaseTenantService<ServiceConfigProperties> tenantService;
    private static SelfSignedCertificate testCertificate;

    /**
     * Time out each test after five seconds.
     */
    public final Timeout timeout = Timeout.seconds(5);

    /**
     * Sets up the fixture.
     */
    @BeforeClass
    public static void setUp() {
        tenantService = createCompleteBaseTenantService();
        testCertificate = SelfSignedCertificate.create(UUID.randomUUID().toString());
    }

    /**
     * Deletes the test certificate.
     */
    @AfterClass
    public static void tearDown() {
        testCertificate.delete();
    }

    /**
     * Verifies that the base service accepts a request for adding
     * a tenant that contains the minimum required properties.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddSucceedsForMinimalData(final TestContext ctx) {

        final JsonObject testPayload = createValidTenantPayload();

        final EventBusMessage request = createRequest(TenantConstants.TenantAction.add, testPayload);
        tenantService.processRequest(request).setHandler(ctx.asyncAssertSuccess(response -> {
            ctx.assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            ctx.assertEquals(TEST_TENANT, response.getTenant());
        }));
    }

    /**
     * Verifies that the base service fails for a payload that defines an empty adapter array (must be null or has to
     * contain at least one element).
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddFailsForEmptyAdapterArray(final TestContext ctx) {

        final JsonObject testPayload = createValidTenantPayload();
        testPayload.put(TenantConstants.FIELD_ADAPTERS, new JsonArray());

        final EventBusMessage msg = createRequest(TenantConstants.TenantAction.add, testPayload);
        tenantService.processRequest(msg).setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that the base service fails for a payload that defines an adapter entry, but does not provide the
     * mandatory field {@link TenantConstants#FIELD_ADAPTERS_TYPE}.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddFailsForAdapterConfigWithoutType(final TestContext ctx) {

        final JsonObject testPayload = createValidTenantPayload();
        final JsonArray adapterArray = new JsonArray();
        // no type specified (which is a violation of the API)
        adapterArray.add(new JsonObject());
        testPayload.put(TenantConstants.FIELD_ADAPTERS, adapterArray);

        final EventBusMessage msg = createRequest(TenantConstants.TenantAction.add, testPayload);
        tenantService.processRequest(msg).setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that the base service fails for a payload that defines a trusted CA
     * without a Subject DN.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddFailsForTrustedCaWithoutSubjectDn(final TestContext ctx) {

        final JsonObject malformedTrustedCa = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_CERT, "certificate");
        final JsonObject testPayload = createValidTenantPayload();
        testPayload.put(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, malformedTrustedCa);

        final EventBusMessage msg = createRequest(TenantConstants.TenantAction.add, testPayload);
        tenantService.processRequest(msg).setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that the base service fails for a payload that defines a trusted CA
     * containing neither a certificate nor a public key.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddFailsForTrustedCaWithoutCertOrPk(final TestContext ctx) {

        final JsonObject malformedTrustedCa = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, "CN=test");
        final JsonObject testPayload = createValidTenantPayload();
        testPayload.put(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, malformedTrustedCa);

        final EventBusMessage msg = createRequest(TenantConstants.TenantAction.add, testPayload);
        tenantService.processRequest(msg).setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that the base service fails for a payload that defines a trusted CA
     * containing both a certificate and a public key.
     *
     * @param ctx The vert.x test context.
     * @throws CertificateException if the test certificate cannot be created.
     */
    @Test
    public void testAddFailsForTrustedCaWithCertAndPk(final TestContext ctx) throws CertificateException {

        final JsonObject malformedTrustedCa = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, "CN=test")
                .put(TenantConstants.FIELD_PAYLOAD_CERT, "certificate")
                .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, "key");
        final JsonObject testPayload = createValidTenantPayload();
        testPayload.put(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, malformedTrustedCa);

        final EventBusMessage msg = createRequest(TenantConstants.TenantAction.add, testPayload);
        tenantService.processRequest(msg).setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that the base service accepts a payload that defines a trusted CA
     * containing both a subject DN and a valid Base64 encoded public key.
     *
     * @param ctx The vert.x test context.
     * @throws CertificateException if the test certificate cannot be created.
     */
    @Test
    public void testAddSucceedsForTrustedCaWithSubjectDnAndPk(final TestContext ctx) throws CertificateException {

        final X509Certificate cert = getSelfSignedCertificate();
        final JsonObject validTrustedCa = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, "CN=test")
                .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, cert.getPublicKey().getEncoded());
        final JsonObject testPayload = createValidTenantPayload();
        testPayload.put(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, validTrustedCa);

        final EventBusMessage msg = createRequest(TenantConstants.TenantAction.add, testPayload);
        tenantService.processRequest(msg).setHandler(ctx.asyncAssertSuccess());
    }

    /**
     * Verifies that the base service accepts a payload that defines a trusted CA
     * containing both a subject DN and a valid Base64 encoded certificate.
     *
     * @param ctx The vert.x test context.
     * @throws CertificateException if the test certificate cannot be created.
     */
    @Test
    public void testAddSucceedsForTrustedCaWithSubjectDnAndCert(final TestContext ctx) throws CertificateException {

        final X509Certificate cert = getSelfSignedCertificate();
        final JsonObject validTrustedCa = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, "CN=test")
                .put(TenantConstants.FIELD_PAYLOAD_CERT, cert.getEncoded());
        final JsonObject testPayload = createValidTenantPayload();
        testPayload.put(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, validTrustedCa);

        final EventBusMessage msg = createRequest(TenantConstants.TenantAction.add, testPayload);
        tenantService.processRequest(msg).setHandler(ctx.asyncAssertSuccess());
    }

    /**
     * Verify that a request to add a tenant returns a 400 status code when the request payload defines a trusted CA
     * configuration containing an invalid Base64 encoding for its <em>public-key</em> property.
     *
     * @param ctx The Vert.x test context.
     */
    @Test
    public void testAddFailsForInvalidTrustedCaWithInvalidBase64EncondingForPK(final TestContext ctx) {
        // GIVEN a tenant payload containing a trusted CA configuration
        // with an invalid Base64 encoding of its public-key
        final JsonObject malformedPKTrustedCa = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, "CN=test")
                .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, "NOTAPUBLICKEY");

        final JsonObject testPayloadPK = createValidTenantPayload()
                .put(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, malformedPKTrustedCa);

        // WHEN there is a request to add the tenant with such malformed payload
        final EventBusMessage msgPK = createRequest(TenantConstants.TenantAction.add, testPayloadPK);
        tenantService.processRequest(msgPK).setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the base tenant service rejects the request.
            ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    /**
     * Verify that a request to add a tenant returns a 400 status code when the request payload defines a trusted CA
     * configuration containing an invalid Base64 encoding for its <em>cert</em> property.
     *
     * @param ctx The Vert.x test context.
     */
    @Test
    public void testAddFailsForInvalidTrustedCaWithInvalidBase64EncondingForCert(final TestContext ctx) {
        // GIVEN a tenant payload containing a trusted CA configuration
        // with an invalid Base64 encoding of its cert property
        final JsonObject malformedCertTrustedCa = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, "CN=test")
                .put(TenantConstants.FIELD_PAYLOAD_CERT, "NOTACERTIFICATE");
        final JsonObject testPayloadCert = createValidTenantPayload()
                .put(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, malformedCertTrustedCa);

        // WHEN there is a request to add the tenant with such malformed payload
        final EventBusMessage msgCert = createRequest(TenantConstants.TenantAction.add, testPayloadCert);
        tenantService.processRequest(msgCert).setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the base tenant service rejects the request.
            ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    private static EventBusMessage createRequest(final TenantConstants.TenantAction action, final JsonObject payload) {

        return EventBusMessage.forOperation(action.toString())
                .setTenant(TEST_TENANT)
                .setJsonPayload(payload);
    }

    private static JsonObject createValidTenantPayload() {

        final JsonObject payload = new JsonObject();
        payload.put(TenantConstants.FIELD_ENABLED, Boolean.TRUE);

        return payload;
    }

    private static X509Certificate getSelfSignedCertificate() throws CertificateException {
        try (InputStream is = new FileInputStream(testCertificate.certificatePath())) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(is);
        } catch (final IOException e) {
            throw new CertificateException("error creating test certificate");
        }
    }

    private static CompleteBaseTenantService<ServiceConfigProperties> createCompleteBaseTenantService() {

        return new CompleteBaseTenantService<>() {

            @Override
            public void add(final String tenantId, final JsonObject tenantObj, final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {
                resultHandler.handle(Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_CREATED)));
            }

            @Override
            public void get(final String tenantId, final Span span, final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {

                final TenantObject tenant = TenantObject.from(tenantId, true);
                tenant.setProperty("operation", "getById");
                resultHandler.handle(Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_OK, JsonObject.mapFrom(tenant))));
            }

            @Override
            public void get(final X500Principal subjectDn, final Span span,
                            final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {

                final TenantObject tenant = TenantObject.from(subjectDn.getName(X500Principal.RFC2253), true);
                tenant.setProperty("operation", "getByCa");
                resultHandler.handle(Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_OK, JsonObject.mapFrom(tenant))));
            }

            @Override
            public void setConfig(final ServiceConfigProperties configuration) {
                setSpecificConfig(configuration);
            }
        };
    }
}
