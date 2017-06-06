/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.authorization.impl;

import org.eclipse.hono.auth.Activity;
import org.eclipse.hono.auth.Authorities;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.core.io.ClassPathResource;

import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Tests verifying behavior of {@link InMemoryAuthorizationService}.
 */
@RunWith(VertxUnitRunner.class)
public class InMemoryAuthorizationServiceTest {

    private static final ResourceIdentifier TELEMETRY = ResourceIdentifier.fromStringAssumingDefaultTenant("telemetry");
    private static final ResourceIdentifier REGISTRATION = ResourceIdentifier.fromStringAssumingDefaultTenant("registration");

    private static final HonoUser SUBJECT   = getUser("subject");
    private static final HonoUser READER    = getUser("reader");
    private static final HonoUser WRITER    = getUser("writer");
    private static final HonoUser EXECUTOR  = getUser("executor");
    private static final HonoUser USER_ADMIN = getUser("ADMIN");
    private static final String PERMISSIONS_RESOURCE_PATH = "authorization-service-test-permissions.json";

    private static InMemoryAuthorizationService underTest;

    /**
     * Loads permissions from a file.
     * 
     * @throws Exception if the permissions file cannot be read.
     */
    @BeforeClass
    public static void setUp() throws Exception {

        underTest = new InMemoryAuthorizationService();
        underTest.setPermissionsPath(new ClassPathResource(PERMISSIONS_RESOURCE_PATH));
        underTest.loadPermissions();
    }

    @Test
    public void testHasPermissionOnResource(final TestContext ctx) {

        underTest.isAuthorized(SUBJECT, TELEMETRY, Activity.READ).setHandler(ctx.asyncAssertSuccess(b -> {
            ctx.assertFalse(b);
        }));
        underTest.isAuthorized(SUBJECT, TELEMETRY, Activity.WRITE).setHandler(ctx.asyncAssertSuccess(b -> {
            ctx.assertFalse(b);
        }));

        underTest.isAuthorized(READER, TELEMETRY, Activity.READ).setHandler(ctx.asyncAssertSuccess(b -> {
            ctx.assertTrue(b);
        }));
        underTest.isAuthorized(READER, TELEMETRY, Activity.WRITE).setHandler(ctx.asyncAssertSuccess(b -> {
            ctx.assertFalse(b);
        }));

        underTest.isAuthorized(WRITER, TELEMETRY, Activity.READ).setHandler(ctx.asyncAssertSuccess(b -> {
            ctx.assertTrue(b);
        }));
        underTest.isAuthorized(WRITER, TELEMETRY, Activity.WRITE).setHandler(ctx.asyncAssertSuccess(b -> {
            ctx.assertTrue(b);
        }));
    }

    @Test
    public void testHasPermissionOnOperation(final TestContext ctx) {

        underTest.isAuthorized(EXECUTOR, REGISTRATION, "assert").setHandler(ctx.asyncAssertSuccess(b -> {
            ctx.assertTrue(b);
        }));
        underTest.isAuthorized(EXECUTOR, REGISTRATION, Activity.READ).setHandler(ctx.asyncAssertSuccess(b -> {
            ctx.assertFalse(b);
        }));
    }

    @Test
    public void testDeviceLevelPermission(final TestContext ctx) {

        final ResourceIdentifier TENANT1 = ResourceIdentifier.fromString("telemetry/tenant1");
        final ResourceIdentifier DEVICE1 = ResourceIdentifier.fromString("telemetry/tenant1/device1");
        final HonoUser device1User = getUser("device1-user");
        final HonoUser tenant1User = getUser("tenant1-user");

        underTest.isAuthorized(device1User, DEVICE1, Activity.WRITE).setHandler(ctx.asyncAssertSuccess(b -> {
            ctx.assertTrue(b);
        }));
        underTest.isAuthorized(tenant1User, DEVICE1, Activity.WRITE).setHandler(ctx.asyncAssertSuccess(b -> {
            ctx.assertTrue(b);
        }));
        underTest.isAuthorized(tenant1User, TENANT1, Activity.WRITE).setHandler(ctx.asyncAssertSuccess(b -> {
            ctx.assertTrue(b);
        }));
        underTest.isAuthorized(device1User, TENANT1, Activity.WRITE).setHandler(ctx.asyncAssertSuccess(b -> {
            ctx.assertFalse(b);
        }));
    }

    /**
     * Verifies that a user is authorized to consume telemetry data for a specific tenant if
     * the user has been granted to receive Telemetry data for the wildcard tenant ("*").
     */
    @Test
    public void testHasPermissionReturnsTrueForWildcardTenantSpec(final TestContext ctx) {

        underTest.isAuthorized(USER_ADMIN, ResourceIdentifier.from("telemetry", "bumlux", "test"), Activity.READ).setHandler(ctx.asyncAssertSuccess(b -> {
            ctx.assertTrue(b);
        }));
    }

    /**
     * Verifies that a user is authorized to "register" a device in a specific tenant if
     * the user has been granted permission to execute all operations of the Device Registration API
     * for the given tenant.
     */
    @Test
    public void testHasPermissionReturnsTrueForWildcardOperationSpec(final TestContext ctx) {

        underTest.isAuthorized(USER_ADMIN, ResourceIdentifier.from("registration", "bumlux", "test"), "register").setHandler(ctx.asyncAssertSuccess(b -> {
            ctx.assertTrue(b);
        }));
    }

    private static HonoUser getUser(final String name) {
        return new HonoUser() {

            @Override
            public String getName() {
                return name;
            }

            @Override
            public Authorities getAuthorities() {
                return null;
            }

            @Override
            public String getToken() {
                return null;
            }
        };
    }
}