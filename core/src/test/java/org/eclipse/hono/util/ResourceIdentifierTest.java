/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.util;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Tests verifying behavior of {@link ResourceIdentifier}.
 *
 */
public class ResourceIdentifierTest {

    /**
     * Verifies that a resource identifier created from a string
     * parses the segments correctly.
     */
    @Test
    public void testFromStringAllowsMissingDeviceId() {
        final ResourceIdentifier resourceId = ResourceIdentifier.fromString("telemetry/myTenant");
        assertNotNull(resourceId);
        assertThat(resourceId.getEndpoint(), is("telemetry"));
        assertThat(resourceId.getTenantId(), is("myTenant"));
        assertNull(resourceId.getResourceId());
        assertThat(resourceId.getBasePath(), is("telemetry/myTenant"));
        assertThat(resourceId.getPathWithoutBase(), is(""));
        assertThat(resourceId.toString(), is("telemetry/myTenant"));
    }

    /**
     * Verifies that a resource identifier can be created from a string with empty
     * path segments.
     */
    @Test
    public void testFromStringAllowsEmptyPathSegments() {
        final ResourceIdentifier resourceId = ResourceIdentifier.fromString("endpoint///req/cmd-req-id");
        assertNotNull(resourceId);
        assertThat(resourceId.getEndpoint(), is("endpoint"));
        assertNull(resourceId.getTenantId());
        assertNull(resourceId.getResourceId());
        assertThat(resourceId.getBasePath(), is("endpoint"));
        assertThat(resourceId.getPathWithoutBase(), is("/req/cmd-req-id"));
        assertThat(resourceId.getResourcePath()[4], is("cmd-req-id"));
    }

    /**
     * Verifies that a resource identifier created from a string containing
     * a single segment only contains the segment as endpoint and the default tenant.
     */
    @Test
    public void testFromStringAssumingDefaultTenantAllowsMissingDeviceId() {
        final ResourceIdentifier resourceId = ResourceIdentifier.fromStringAssumingDefaultTenant("telemetry");
        assertNotNull(resourceId);
        assertThat(resourceId.getEndpoint(), is("telemetry"));
        assertThat(resourceId.getTenantId(), is(Constants.DEFAULT_TENANT));
        assertNull(resourceId.getResourceId());
        assertThat(resourceId.getBasePath(), is("telemetry/" + Constants.DEFAULT_TENANT));
        assertThat(resourceId.getPathWithoutBase(), is(""));
        assertThat(resourceId.toString(), is("telemetry/" + Constants.DEFAULT_TENANT));
    }

    /**
     * Verifies that a resource identifier created from a string that contains
     * more than three segments correctly parses the individual path segments.
     */
    @Test
    public void testFromStringWithExtendedPath() {
        final ResourceIdentifier resourceId = ResourceIdentifier.fromString("telemetry/myTenant/deviceId/what/ever");
        assertNotNull(resourceId);
        assertThat(resourceId.getEndpoint(), is("telemetry"));
        assertThat(resourceId.getTenantId(), is("myTenant"));
        assertThat(resourceId.getResourceId(), is("deviceId"));
        assertThat(resourceId.getBasePath(), is("telemetry/myTenant"));
        assertThat(resourceId.getPathWithoutBase(), is("deviceId/what/ever"));
        assertThat(resourceId.toString(), is("telemetry/myTenant/deviceId/what/ever"));
        assertThat(resourceId.getResourcePath()[3], is("what"));
        assertThat(resourceId.getResourcePath()[4], is("ever"));
        assertThat(resourceId.getBasePath(), is("telemetry/myTenant"));
    }

    /**
     * Verifies that a resource identifier may consist of a single segment only.
     */
    @Test
    public void testFromStringSupportsSingleSegment() {
        final ResourceIdentifier resourceId = ResourceIdentifier.fromString("cbs");
        assertThat(resourceId.getEndpoint(), is("cbs"));
        assertNull(resourceId.getTenantId());
        assertNull(resourceId.getResourceId());
        assertThat(resourceId.getBasePath(), is("cbs"));
        assertThat(resourceId.getPathWithoutBase(), is(""));
    }

    /**
     * Verifies that a resource identifier may consist of a single segment only.
     */
    @Test
    public void testFromPathSupportsSingleSegment() {
        final ResourceIdentifier resourceId = ResourceIdentifier.fromPath(new String[]{ "cbs" });
        assertThat(resourceId.getEndpoint(), is("cbs"));
        assertNull(resourceId.getTenantId());
        assertNull(resourceId.getResourceId());
        assertThat(resourceId.getBasePath(), is("cbs"));
        assertThat(resourceId.getPathWithoutBase(), is(""));
    }

    /**
     * Verifies that a resource identifier may consist of a single segment only.
     */
    @Test
    public void testFromSupportsSingleSegment() {
        final ResourceIdentifier resourceId = ResourceIdentifier.from("cbs", null, null);
        assertThat(resourceId.getEndpoint(), is("cbs"));
        assertNull(resourceId.getTenantId());
        assertNull(resourceId.getResourceId());
        assertThat(resourceId.getBasePath(), is("cbs"));
        assertThat(resourceId.getPathWithoutBase(), is(""));
    }

    /**
     * Verifies that a resource identifier can be created from string segments.
     */
    @Test
    public void testFromIndividualParameters() {
        final ResourceIdentifier resourceId = ResourceIdentifier.from("telemetry", "myTenant", "myDevice");
        assertNotNull(resourceId);
        assertThat(resourceId.getEndpoint(), is("telemetry"));
        assertThat(resourceId.getTenantId(), is("myTenant"));
        assertThat(resourceId.getResourceId(), is("myDevice"));
        assertThat(resourceId.getBasePath(), is("telemetry/myTenant"));
        assertThat(resourceId.getPathWithoutBase(), is("myDevice"));
        assertThat(resourceId.toString(), is("telemetry/myTenant/myDevice"));
    }

    /**
     * Verifies that a resource identifier can be created from string containing
     * a trailing {@code null} segment.
     */
    @Test
    public void testFromAllowsMissingDeviceId() {
        final ResourceIdentifier resourceId = ResourceIdentifier.from("telemetry", "myTenant", null);
        assertNotNull(resourceId);
        assertThat(resourceId.getEndpoint(), is("telemetry"));
        assertThat(resourceId.getTenantId(), is("myTenant"));
        assertNull(resourceId.getResourceId());
        assertThat(resourceId.getBasePath(), is("telemetry/myTenant"));
        assertThat(resourceId.getPathWithoutBase(), is(""));
        assertThat(resourceId.toString(), is("telemetry/myTenant"));
    }

    /**
     * Verifies that resource identifiers with the same endpoint and tenant
     * are considered equal.
     */
    @Test
    public void testEqualsReturnsTrueForSameEndpointAndTenant() {
        ResourceIdentifier one = ResourceIdentifier.from("ep", "tenant", null);
        ResourceIdentifier two = ResourceIdentifier.from("ep", "tenant", null);
        assertTrue(one.equals(two));

        one = ResourceIdentifier.fromString("ep/tenant");
        two = ResourceIdentifier.fromString("ep/tenant");
        assertTrue(one.equals(two));
    }

    /**
     * Verifies that resource identifiers with the same endpoint and tenant
     * produce the same hash code.
     */
    @Test
    public void testHashCodeReturnsSameValueForSameEndpointAndTenant() {
        ResourceIdentifier one = ResourceIdentifier.from("ep", "tenant", null);
        ResourceIdentifier two = ResourceIdentifier.from("ep", "tenant", null);
        assertThat(one.hashCode(), is(two.hashCode()));

        one = ResourceIdentifier.fromString("ep/tenant");
        two = ResourceIdentifier.fromString("ep/tenant");
        assertThat(one.hashCode(), is(two.hashCode()));
    }

    /**
     * Verifies that a resource identifier can be created from
     * a path that contains trailing {@code null} segments.
     */
    @Test
    public void testFromPathAllowsTrailingNulls() {
        final ResourceIdentifier id = ResourceIdentifier.fromPath(new String[]{"first", "second", null, null});
        assertThat(id.length(), is(4));
        assertThat(id.elementAt(0), is("first"));
        assertThat(id.elementAt(1), is("second"));
        assertNull(id.elementAt(2));
        assertNull(id.elementAt(3));
    }

    /**
     * Verifies that a resource identifier cannot be created from
     * a path that contains non-trailing {@code null} segments.
     */
    @Test
    public void testFromPathAllowsNullSegments() {
        final ResourceIdentifier id = ResourceIdentifier.fromPath(new String[]{"first", "second", null, "last"});
        assertThat(id.length(), is(4));
        assertThat(id.elementAt(0), is("first"));
        assertThat(id.elementAt(1), is("second"));
        assertNull(id.elementAt(2));
        assertThat(id.elementAt(3), is("last"));
    }

    /**
     * Verifies that a resource identifier cannot be created from
     * a path that starts with a {@code null} segment.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testFromPathFailsForPathStartingWithNullSegment() {
        ResourceIdentifier.fromPath(new String[]{null, "second", "last"});
    }
}
