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

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.hono.service.authorization.Activity;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Tests verifying behavior of {@link InMemoryAuthorizationService}.
 */
public class InMemoryAuthorizationServiceTest {

    private static final ResourceIdentifier TELEMETRY = ResourceIdentifier.fromStringAssumingDefaultTenant("telemetry");
    private static final ResourceIdentifier CONTROL   = ResourceIdentifier.fromStringAssumingDefaultTenant("control");
    private static final ResourceIdentifier REGISTRATION = ResourceIdentifier.fromStringAssumingDefaultTenant("registration");

    private static final String SUBJECT   = "subject";
    private static final String READER    = "reader";
    private static final String WRITER    = "writer";
    private static final String EXECUTOR  = "executor";
    private static final String USER_ADMIN = "ADMIN";
    private static final String PERMISSIONS_RESOURCE_PATH = "authorization-service-test-permissions.json";

    private InMemoryAuthorizationService underTest;

    /**
     * Loads permissions from a file.
     * 
     * @throws Exception if the permissions file cannot be read.
     */
    @Before
    public void setUp() throws Exception {

        underTest = new InMemoryAuthorizationService();
        underTest.setPermissionsPath(new ClassPathResource(PERMISSIONS_RESOURCE_PATH));
        underTest.loadPermissions();
    }

    @Test
    public void testHasPermissionOnResource() {

        assertThat(underTest.hasPermission(SUBJECT, TELEMETRY, Activity.READ)).isFalse();
        assertThat(underTest.hasPermission(SUBJECT, TELEMETRY, Activity.WRITE)).isFalse();

        assertThat(underTest.hasPermission(READER, TELEMETRY, Activity.READ)).isTrue();
        assertThat(underTest.hasPermission(READER, TELEMETRY, Activity.WRITE)).isFalse();

        assertThat(underTest.hasPermission(WRITER, TELEMETRY, Activity.READ)).isTrue();
        assertThat(underTest.hasPermission(WRITER, TELEMETRY, Activity.WRITE)).isTrue();
    }

    @Test
    public void testHasPermissionOnOperation() {

        assertThat(underTest.hasPermission(EXECUTOR, REGISTRATION, "assert")).isTrue();
        assertThat(underTest.hasPermission(EXECUTOR, REGISTRATION, Activity.READ)).isFalse();
    }

    @Test
    public void testDeviceLevelPermission() {

        final ResourceIdentifier TENANT1 = ResourceIdentifier.fromString("telemetry/tenant1");
        final ResourceIdentifier DEVICE1 = ResourceIdentifier.fromString("telemetry/tenant1/device1");

        assertThat(underTest.hasPermission("device1-user", DEVICE1, Activity.WRITE)).isTrue();
        assertThat(underTest.hasPermission("tenant1-user", DEVICE1, Activity.WRITE)).isTrue();
        assertThat(underTest.hasPermission("tenant1-user", TENANT1, Activity.WRITE)).isTrue();
        assertThat(underTest.hasPermission("device1-user", TENANT1, Activity.WRITE)).isFalse();
    }

    @Test
    public void testAddPermission() {

        assertThat(underTest.hasPermission(SUBJECT, CONTROL, Activity.READ)).isFalse();
        assertThat(underTest.hasPermission(SUBJECT, CONTROL, Activity.WRITE)).isFalse();

        underTest.addPermission(SUBJECT, CONTROL, Activity.READ);

        assertThat(underTest.hasPermission(SUBJECT, CONTROL, Activity.READ)).isTrue();
        assertThat(underTest.hasPermission(SUBJECT, CONTROL, Activity.WRITE)).isFalse();
    }

    /**
     * Verifies that a user is authorized to consume telemetry data for a specific tenant if
     * the user has been granted to receive Telemetry data for the wildcard tenant ("*").
     */
    @Test
    public void testHasPermissionReturnsTrueForWildcardTenantSpec() {

        assertThat(underTest.hasPermission(USER_ADMIN, ResourceIdentifier.from("telemetry", "bumlux", "test"), Activity.READ)).isTrue();
    }

    /**
     * Verifies that a user is authorized to "register" a device in a specific tenant if
     * the user has been granted permission to execute all operations of the Device Registration API
     * for the given tenant.
     */
    @Test
    public void testHasPermissionReturnsTrueForWildcardOperationSpec() {

        assertThat(underTest.hasPermission(USER_ADMIN, ResourceIdentifier.from("registration", "bumlux", "test"), "register")).isTrue();
    }
}