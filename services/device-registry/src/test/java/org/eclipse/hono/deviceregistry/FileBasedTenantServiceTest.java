/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.deviceregistry;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.service.tenant.TenantService;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantResult;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;


/**
 * Tests verifying behavior of {@link FileBasedTenantService}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class FileBasedTenantServiceTest {

    /**
     * Time out each test after five seconds.
     */
    @Rule
    public final Timeout timeout = Timeout.seconds(5);

    private static final String FILE_NAME = "/tenants.json";

    private Vertx vertx;
    private EventBus eventBus;
    private FileSystem fileSystem;
    private FileBasedTenantsConfigProperties props;
    private FileBasedTenantService svc;

    /**
     * Sets up fixture.
     */
    @Before
    public void setUp() {
        fileSystem = mock(FileSystem.class);
        Context ctx = mock(Context.class);
        eventBus = mock(EventBus.class);
        vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(eventBus);
        when(vertx.fileSystem()).thenReturn(fileSystem);

        props = new FileBasedTenantsConfigProperties();
        svc = new FileBasedTenantService();
        svc.setConfig(props);
        svc.init(vertx, ctx);
    }

    /**
     * Verifies that the tenant service creates a file for persisting tenants
     * data if it does not exist yet during startup.
     * 
     * @param ctx The vert.x context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testDoStartCreatesFile(final TestContext ctx) {

        // GIVEN a tenant service configured to persist data to a not yet existing file
        props.setSaveToFile(true);
        props.setFilename(FILE_NAME);
        when(fileSystem.existsBlocking(FILE_NAME)).thenReturn(Boolean.FALSE);
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture());
            return null;
        }).when(fileSystem).createFile(eq(props.getFilename()), any(Handler.class));
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(1);
            handler.handle(Future.failedFuture("malformed file"));
            return null;
        }).when(fileSystem).readFile(eq(props.getFilename()), any(Handler.class));

        // WHEN starting the service
        final Async startup = ctx.async();
        final Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertSuccess(started -> {
            startup.complete();
        }));
        svc.doStart(startupTracker);

        // THEN the file gets created
        startup.await();
        verify(fileSystem).createFile(eq(FILE_NAME), any(Handler.class));
    }

    /**
     * Verifies that the tenant service fails to start if it cannot create the file for
     * persisting tenants data during startup.
     *
     * @param ctx The vert.x context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testDoStartFailsIfFileCannotBeCreated(final TestContext ctx) {

        // GIVEN a tenant service configured to persist data to a not yet existing file
        props.setSaveToFile(true);
        props.setFilename(FILE_NAME);
        when(fileSystem.existsBlocking(FILE_NAME)).thenReturn(Boolean.FALSE);

        // WHEN starting the service but the file cannot be created
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(1);
            handler.handle(Future.failedFuture("no access"));
            return null;
        }).when(fileSystem).createFile(eq(props.getFilename()), any(Handler.class));
        final Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertFailure());
        svc.doStart(startupTracker);
    }

    /**
     * Verifies that the tenant service successfully starts up even if
     * the file to read tenants from contains malformed JSON.
     *
     * @param ctx The vert.x context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testDoStartIgnoresMalformedJson(final TestContext ctx) {

        // GIVEN a tenant service configured to read data from a file
        // that contains malformed JSON
        props.setFilename(FILE_NAME);
        when(fileSystem.existsBlocking(FILE_NAME)).thenReturn(Boolean.TRUE);
        doAnswer(invocation -> {
            final Buffer data = mock(Buffer.class);
            when(data.getBytes()).thenReturn("NO JSON".getBytes(StandardCharsets.UTF_8));
            final Handler handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture(data));
            return null;
        }).when(fileSystem).readFile(eq(props.getFilename()), any(Handler.class));

        // WHEN starting the service
        final Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertSuccess(started -> {
            // THEN startup succeeds
        }));
        svc.doStart(startupTracker);
    }

    /**
     * Verifies that tenants are successfully loaded from file during startup.
     *
     * @param ctx The test context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testDoStartLoadsTenants(final TestContext ctx) {

        // GIVEN a service configured with a file name
        props.setFilename(FILE_NAME);
        when(fileSystem.existsBlocking(props.getFilename())).thenReturn(Boolean.TRUE);
        doAnswer(invocation -> {
            final Buffer data = DeviceRegistryTestUtils.readFile(FILE_NAME);
            final Handler handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture(data));
            return null;
        }).when(fileSystem).readFile(eq(props.getFilename()), any(Handler.class));

        // WHEN the service is started
        final Async startup = ctx.async();
        final Future<Void> startFuture = Future.future();
        startFuture.setHandler(ctx.asyncAssertSuccess(s -> {
            startup.complete();
        }));
        svc.doStart(startFuture);

        // THEN the credentials from the file are loaded
        startup.await();
        assertTenantExists(svc, Constants.DEFAULT_TENANT, ctx);
    }

    /**
     * Verifies that the tenants file written by the registry when persisting the contents can
     * be loaded in again.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testLoadTenantsCanReadOutputOfSaveToFile(final TestContext ctx) {

        // GIVEN a service configured to persist tenants to file
        // that contains some tenants
        props.setFilename(FILE_NAME);
        props.setSaveToFile(true);
        when(fileSystem.existsBlocking(FILE_NAME)).thenReturn(Boolean.TRUE);
        final Async countDown = ctx.async();
        addTenant(Constants.DEFAULT_TENANT).compose(ok -> addTenant("OTHER_TENANT"))
            .setHandler(ctx.asyncAssertSuccess(ok -> countDown.complete()));
        countDown.await();

        // WHEN saving the content to the file and clearing the tenant registry
        final Async write = ctx.async();
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(2);
            handler.handle(Future.succeededFuture());
            write.complete();
            return null;
        }).when(fileSystem).writeFile(eq(FILE_NAME), any(Buffer.class), any(Handler.class));

        svc.saveToFile();
        write.await();
        final ArgumentCaptor<Buffer> buffer = ArgumentCaptor.forClass(Buffer.class);
        verify(fileSystem).writeFile(eq(FILE_NAME), buffer.capture(), any(Handler.class));
        svc.clear();
        assertTenantDoesNotExist(svc, Constants.DEFAULT_TENANT, ctx);

        // THEN the tenants can be loaded back in from the file
        final Async read = ctx.async();
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture(buffer.getValue()));
            read.complete();
            return null;
        }).when(fileSystem).readFile(eq(FILE_NAME), any(Handler.class));
        svc.loadTenantData();
        read.await();
        assertTenantExists(svc, Constants.DEFAULT_TENANT, ctx);
        assertTenantExists(svc, "OTHER_TENANT", ctx);
    }

    /**
     * Verifies that a tenant cannot be added if it uses an already registered
     * identifier.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddTenantFailsForDuplicateTenantId(final TestContext ctx) {

        addTenant("tenant").map(ok -> {
            svc.add(
                "tenant",
                buildTenantPayload("tenant"),
                ctx.asyncAssertSuccess(s -> {
                    ctx.assertEquals(HttpURLConnection.HTTP_CONFLICT, s.getStatus());
                }));
            return null;
        });
    }

    /**
     * Verifies that a tenant cannot be added if it uses a trusted certificate authority
     * with the same subject DN as an already existing tenant.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddTenantFailsForDuplicateCa(final TestContext ctx) {

        final JsonObject trustedCa = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, "CN=taken")
                .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, "NOTAKEY");
        final TenantObject tenant = TenantObject.from("tenant", true)
                .setProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, trustedCa);
        addTenant("tenant", JsonObject.mapFrom(tenant)).map(ok -> {
            final TenantObject newTenant = TenantObject.from("newTenant", true)
                    .setProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, trustedCa);
            svc.add(
                "newTenant",
                JsonObject.mapFrom(newTenant),
                ctx.asyncAssertSuccess(s -> {
                    ctx.assertEquals(HttpURLConnection.HTTP_CONFLICT, s.getStatus());
                }));
            return null;
        });
    }

    /**
     * Verifies that a tenant can be added with <em>modificationEnabled</em>
     * set to {@code false}.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddTenantSucceedsIfModificationIsDisabled(final TestContext ctx) {

        // GIVEN a service containing a set of tenants
        // that has been configured to not allow modification of entries
        props.setModificationEnabled(false);

        // WHEN trying to add a new tenant
        svc.add("fancy-new-tenant", new JsonObject(), ctx.asyncAssertSuccess(s -> {
            // THEN the request succeeds
            ctx.assertEquals(HttpURLConnection.HTTP_CREATED, s.getStatus());
        }));
    }

    /**
     * Verifies that the service returns 404 if a client wants to retrieve a non-existing tenant.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetTenantFailsForNonExistingTenant(final TestContext ctx) {

        svc.get("notExistingTenant" , ctx.asyncAssertSuccess(s -> {
                    assertThat(s.getStatus(), is(HttpURLConnection.HTTP_NOT_FOUND));
                }));
    }

    /**
     * Verifies that the service returns an existing tenant.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetTenantSucceedsForExistingTenants(final TestContext ctx) {

        addTenant("tenant").map(ok -> {
            svc.get("tenant", ctx.asyncAssertSuccess(s -> {
                assertThat(s.getStatus(), is(HttpURLConnection.HTTP_OK));
                assertThat(s.getPayload().getString(TenantConstants.FIELD_PAYLOAD_TENANT_ID), is("tenant"));
                assertThat(s.getPayload().getBoolean(TenantConstants.FIELD_ENABLED), is(Boolean.TRUE));
            }));
            return null;
        });
    }

    /**
     * Verifies that the service finds an existing tenant by the subject DN of
     * its configured trusted certificate authority.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetForCertificateAuthoritySucceeds(final TestContext ctx) {

        final X500Principal subjectDn = new X500Principal("O=Eclipse, OU=Hono, CN=ca");
        final JsonObject trustedCa = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, subjectDn.getName(X500Principal.RFC2253))
                .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, "NOTAPUBLICKEY");
        final JsonObject tenant = buildTenantPayload("tenant")
            .put(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, trustedCa);

        addTenant("tenant", tenant).map(ok -> {
            svc.get(subjectDn, ctx.asyncAssertSuccess(s -> {
                assertThat(s.getStatus(), is(HttpURLConnection.HTTP_OK));
                final TenantObject obj = s.getPayload().mapTo(TenantObject.class);
                assertThat(obj.getTenantId(), is("tenant"));
                final JsonObject ca = obj.getProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA);
                assertThat(ca, is(trustedCa));
            }));
            return null;
        });
    }

    /**
     * Verifies that the service does not find any tenant for an unknown subject DN.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetForCertificateAuthorityFailsForUnknownSubjectDn(final TestContext ctx) {

        final X500Principal unknownSubjectDn = new X500Principal("O=Eclipse, OU=NotHono, CN=ca");
        final X500Principal subjectDn = new X500Principal("O=Eclipse, OU=Hono, CN=ca");
        final String publicKey = "NOTAPUBLICKEY";
        final JsonObject trustedCa = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, subjectDn.getName(X500Principal.RFC2253))
                .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, publicKey);
        final JsonObject tenant = buildTenantPayload("tenant")
            .put(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, trustedCa);

        addTenant("tenant", tenant).map(ok -> {
            svc.get(unknownSubjectDn, ctx.asyncAssertSuccess(s -> {
                assertThat(s.getStatus(), is(HttpURLConnection.HTTP_NOT_FOUND));
            }));
            return null;
        });
    }

    /**
     * Verifies that the service removes tenants for a given tenantId.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testRemoveTenantsSucceeds(final TestContext ctx) {

        addTenant("tenant").map(ok -> {
            svc.remove("tenant", ctx.asyncAssertSuccess(s -> {
                assertThat(s.getStatus(), is(HttpURLConnection.HTTP_NO_CONTENT));
                assertTenantDoesNotExist(svc, "tenant", ctx);
            }));
            return null;
        });
    }

    /**
     * Verifies that the <em>modificationEnabled</em> property prevents removing a tenant.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testRemoveTenantsFailsIfModificationIsDisabled(final TestContext ctx) {

        // GIVEN a service containing a set of tenants
        // that has been configured to not allow modification of entries
        props.setModificationEnabled(false);

        // WHEN trying to update the tenant
        svc.remove("tenant", ctx.asyncAssertSuccess(s -> {
            // THEN the update fails
            ctx.assertEquals(HttpURLConnection.HTTP_FORBIDDEN, s.getStatus());
        }));
    }

    /**
     * Verifies that the service updates tenants for a given tenantId.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateTenantsSucceeds(final TestContext ctx) {

        final JsonObject updatedPayload = buildTenantPayload("tenant");
        updatedPayload.put("custom-prop", "something");

        addTenant("tenant").compose(ok -> {
            final Future<TenantResult<JsonObject>> updateResult = Future.future();
            svc.update("tenant", updatedPayload.copy(), updateResult.completer());
            return updateResult;
        }).compose(updateResult -> {
            ctx.assertEquals(HttpURLConnection.HTTP_NO_CONTENT, updateResult.getStatus());
            final Future<TenantResult<JsonObject>> getResult = Future.future();
            svc.get("tenant", getResult.completer());
            return getResult;
        }).setHandler(ctx.asyncAssertSuccess(getResult -> {
            assertThat(getResult.getStatus(), is(HttpURLConnection.HTTP_OK));
            assertThat(getResult.getPayload().getString("custom-prop"), is("something"));
        }));
    }

    /**
     * Verifies that the <em>modificationEnabled</em> property prevents updating an existing entry.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateTenantsFailsIfModificationIsDisabled(final TestContext ctx) {

        // GIVEN a service containing a set of tenants
        // that has been configured to not allow modification of entries
        props.setModificationEnabled(false);

        // WHEN trying to update the tenant
        svc.update("tenant", new JsonObject(), ctx.asyncAssertSuccess(s -> {
            // THEN the update fails
            ctx.assertEquals(HttpURLConnection.HTTP_FORBIDDEN, s.getStatus());
        }));
    }

    /**
     * Verifies that a tenant cannot be updated to use a trusted certificate authority
     * with the same subject DN as another tenant.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateTenantFailsForDuplicateCa(final TestContext ctx) {

        // GIVEN two tenants, one with a CA configured, the other with no CA
        final JsonObject trustedCa = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, "CN=taken")
                .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, "NOTAKEY");
        final TenantObject tenantOne = TenantObject.from("tenantOne", true)
                .setProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, trustedCa);
        final TenantObject tenantTwo = TenantObject.from("tenantTwo", true);
        addTenant("tenantOne", JsonObject.mapFrom(tenantOne))
        .compose(ok -> addTenant("tenantTwo", JsonObject.mapFrom(tenantTwo)))
        .map(ok -> {
            // WHEN updating the second tenant to use the same CA as the first tenant
            tenantTwo.setProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, trustedCa);
            svc.update(
                "tenantTwo",
                JsonObject.mapFrom(tenantTwo),
                ctx.asyncAssertSuccess(s -> {
                    // THEN the update fails with a 409
                    ctx.assertEquals(HttpURLConnection.HTTP_CONFLICT, s.getStatus());
                }));
            return null;
        });
    }

    private static void assertTenantExists(final TenantService svc, final String tenant, final TestContext ctx) {

        svc.get(tenant, ctx.asyncAssertSuccess(t -> {
            assertThat(t.getStatus(), is(HttpURLConnection.HTTP_OK));
        }));
    }

    private static void assertTenantDoesNotExist(final TenantService svc, final String tenant, final TestContext ctx) {

        svc.get(tenant, ctx.asyncAssertSuccess(t -> {
            assertThat(t.getStatus(), is(HttpURLConnection.HTTP_NOT_FOUND));
        }));
    }

    private Future<Void> addTenant(final String tenantId) {

        return addTenant(tenantId, buildTenantPayload(tenantId));
    }

    private Future<Void> addTenant(final String tenantId, final JsonObject payload) {

        final Future<TenantResult<JsonObject>> result = Future.future();
        svc.add(tenantId, payload, result.completer());
        return result.map(response -> {
            if (response.getStatus() == HttpURLConnection.HTTP_CREATED) {
                return null;
            } else {
                throw StatusCodeMapper.from(response);
            }
        });
    }

    /**
     * Creates a tenant object for a tenantId.
     * <p>
     * The tenant object created contains configurations for the http and the mqtt adapter.
     *
     * @param tenantId The tenant identifier.
     * @return The tenant object.
     */
    private static JsonObject buildTenantPayload(final String tenantId) {

        final JsonObject adapterDetailsHttp = new JsonObject()
                .put(TenantConstants.FIELD_ADAPTERS_TYPE, Constants.PROTOCOL_ADAPTER_TYPE_HTTP)
                .put(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, Boolean.TRUE)
                .put(TenantConstants.FIELD_ENABLED, Boolean.TRUE);
        final JsonObject adapterDetailsMqtt = new JsonObject()
                .put(TenantConstants.FIELD_ADAPTERS_TYPE, Constants.PROTOCOL_ADAPTER_TYPE_MQTT)
                .put(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, Boolean.TRUE)
                .put(TenantConstants.FIELD_ENABLED, Boolean.TRUE);
        final JsonObject tenantPayload = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_TENANT_ID, tenantId)
                .put(TenantConstants.FIELD_ENABLED, Boolean.TRUE)
                .put(TenantConstants.FIELD_ADAPTERS, new JsonArray().add(adapterDetailsHttp).add(adapterDetailsMqtt));
        return tenantPayload;
    }
}
