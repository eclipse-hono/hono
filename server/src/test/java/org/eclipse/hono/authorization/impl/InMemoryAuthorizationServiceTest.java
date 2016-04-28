package org.eclipse.hono.authorization.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.hono.authorization.AuthorizationService;
import org.eclipse.hono.authorization.Permission;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests InMemoryAuthorizationServiceTest.
 */
public class InMemoryAuthorizationServiceTest {

    public static final ResourceIdentifier TELEMETRY = ResourceIdentifier.fromStringAssumingDefaultTenant("telemetry");
    public static final ResourceIdentifier CONTROL   = ResourceIdentifier.fromStringAssumingDefaultTenant("control");

    public static final String SUBJECT   = "subject";
    public static final String READER    = "reader";
    public static final String WRITER    = "writer";

    private AuthorizationService underTest;

    @Before
    public void setUp() throws Exception {
        underTest = new InMemoryAuthorizationService();

        underTest.addPermission(READER, TELEMETRY, Permission.READ);
        underTest.addPermission(WRITER, TELEMETRY, Permission.READ, Permission.WRITE);
    }

    @Test
    public void testHasPermission() throws Exception {

        assertThat(underTest.hasPermission(SUBJECT, TELEMETRY, Permission.READ)).isFalse();
        assertThat(underTest.hasPermission(SUBJECT, TELEMETRY, Permission.WRITE)).isFalse();

        assertThat(underTest.hasPermission(READER, TELEMETRY, Permission.READ)).isTrue();
        assertThat(underTest.hasPermission(READER, TELEMETRY, Permission.WRITE)).isFalse();

        assertThat(underTest.hasPermission(WRITER, TELEMETRY, Permission.READ)).isTrue();
        assertThat(underTest.hasPermission(WRITER, TELEMETRY, Permission.WRITE)).isTrue();

    }

    @Test
    public void testDeviceLevelPermission() throws Exception {

        final ResourceIdentifier TENANT1 = ResourceIdentifier.fromString("telemetry/tenant1");
        final ResourceIdentifier DEVICE1 = ResourceIdentifier.fromString("telemetry/tenant1/device1");

        underTest.addPermission("TENANT", TENANT1, Permission.WRITE);
        underTest.addPermission("DEVICE", DEVICE1, Permission.WRITE);

        assertThat(underTest.hasPermission("DEVICE", DEVICE1, Permission.WRITE)).isTrue();
        assertThat(underTest.hasPermission("TENANT", DEVICE1, Permission.WRITE)).isTrue();
        assertThat(underTest.hasPermission("TENANT", TENANT1, Permission.WRITE)).isTrue();
        assertThat(underTest.hasPermission("DEVICE", TENANT1, Permission.WRITE)).isFalse();
    }

    @Test
    public void testAddPermission() throws Exception {

        assertThat(underTest.hasPermission(SUBJECT, CONTROL, Permission.READ)).isFalse();
        assertThat(underTest.hasPermission(SUBJECT, CONTROL, Permission.WRITE)).isFalse();

        underTest.addPermission(SUBJECT, CONTROL, Permission.READ);

        assertThat(underTest.hasPermission(SUBJECT, CONTROL, Permission.READ)).isTrue();
        assertThat(underTest.hasPermission(SUBJECT, CONTROL, Permission.WRITE)).isFalse();
    }

    @Test
    public void testRemovePermission() throws Exception {
        assertThat(underTest.hasPermission(WRITER, TELEMETRY, Permission.READ)).isTrue();
        assertThat(underTest.hasPermission(WRITER, TELEMETRY, Permission.WRITE)).isTrue();

        underTest.removePermission(WRITER, TELEMETRY, Permission.WRITE);

        assertThat(underTest.hasPermission(WRITER, TELEMETRY, Permission.READ)).isTrue();
        assertThat(underTest.hasPermission(WRITER, TELEMETRY, Permission.WRITE)).isFalse();
    }
}