/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

import static org.junit.jupiter.api.Assertions.assertThrows;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;

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
        assertThat(resourceId).isNotNull();
        assertThat(resourceId.getEndpoint()).isEqualTo("telemetry");
        assertThat(resourceId.getTenantId()).isEqualTo("myTenant");
        assertThat(resourceId.getResourceId()).isNull();
        assertThat(resourceId.getBasePath()).isEqualTo("telemetry/myTenant");
        assertThat(resourceId.getPathWithoutBase()).isEqualTo("");
        assertThat(resourceId.toString()).isEqualTo("telemetry/myTenant");
    }

    /**
     * Verifies that a resource identifier can be created from a string with empty
     * path segments.
     */
    @Test
    public void testFromStringAllowsEmptyPathSegments() {
        final ResourceIdentifier resourceId = ResourceIdentifier.fromString("endpoint///req/cmd-req-id");
        assertThat(resourceId).isNotNull();
        assertThat(resourceId.getEndpoint()).isEqualTo("endpoint");
        assertThat(resourceId.getTenantId()).isNull();
        assertThat(resourceId.getResourceId()).isNull();
        assertThat(resourceId.getBasePath()).isEqualTo("endpoint");
        assertThat(resourceId.getPathWithoutBase()).isEqualTo("/req/cmd-req-id");
        assertThat(resourceId.getResourcePath()[4]).isEqualTo("cmd-req-id");
    }

    /**
     * Verifies that a resource identifier created from a string that contains
     * more than three segments correctly parses the individual path segments.
     */
    @Test
    public void testFromStringWithExtendedPath() {
        final ResourceIdentifier resourceId = ResourceIdentifier.fromString("telemetry/myTenant/deviceId/what/ever");
        assertThat(resourceId).isNotNull();
        assertThat(resourceId.getEndpoint()).isEqualTo("telemetry");
        assertThat(resourceId.getTenantId()).isEqualTo("myTenant");
        assertThat(resourceId.getResourceId()).isEqualTo("deviceId");
        assertThat(resourceId.getBasePath()).isEqualTo("telemetry/myTenant");
        assertThat(resourceId.getPathWithoutBase()).isEqualTo("deviceId/what/ever");
        assertThat(resourceId.toString()).isEqualTo("telemetry/myTenant/deviceId/what/ever");
        assertThat(resourceId.getResourcePath()[3]).isEqualTo("what");
        assertThat(resourceId.getResourcePath()[4]).isEqualTo("ever");
        assertThat(resourceId.getBasePath()).isEqualTo("telemetry/myTenant");
    }

    /**
     * Verifies that a resource identifier may consist of a single segment only.
     */
    @Test
    public void testFromStringSupportsSingleSegment() {
        final ResourceIdentifier resourceId = ResourceIdentifier.fromString("cbs");
        assertThat(resourceId.getEndpoint()).isEqualTo("cbs");
        assertThat(resourceId.getTenantId()).isNull();
        assertThat(resourceId.getResourceId()).isNull();
        assertThat(resourceId.getBasePath()).isEqualTo("cbs");
        assertThat(resourceId.getPathWithoutBase()).isEqualTo("");
    }

    /**
     * Verifies that a resource identifier may consist of a single segment only.
     */
    @Test
    public void testFromPathSupportsSingleSegment() {
        final ResourceIdentifier resourceId = ResourceIdentifier.fromPath(new String[]{ "cbs" });
        assertThat(resourceId.getEndpoint()).isEqualTo("cbs");
        assertThat(resourceId.getTenantId()).isNull();
        assertThat(resourceId.getResourceId()).isNull();
        assertThat(resourceId.getBasePath()).isEqualTo("cbs");
        assertThat(resourceId.getPathWithoutBase()).isEqualTo("");
    }

    /**
     * Verifies that a resource identifier may consist of a single segment only.
     */
    @Test
    public void testFromSupportsSingleSegment() {
        final ResourceIdentifier resourceId = ResourceIdentifier.from("cbs", null, null);
        assertThat(resourceId.getEndpoint()).isEqualTo("cbs");
        assertThat(resourceId.getTenantId()).isNull();
        assertThat(resourceId.getResourceId()).isNull();
        assertThat(resourceId.getBasePath()).isEqualTo("cbs");
        assertThat(resourceId.getPathWithoutBase()).isEqualTo("");
    }

    /**
     * Verifies that a resource identifier can be created from string segments.
     */
    @Test
    public void testFromIndividualParameters() {
        final ResourceIdentifier resourceId = ResourceIdentifier.from("telemetry", "myTenant", "myDevice");
        assertThat(resourceId).isNotNull();
        assertThat(resourceId.getEndpoint()).isEqualTo("telemetry");
        assertThat(resourceId.getTenantId()).isEqualTo("myTenant");
        assertThat(resourceId.getResourceId()).isEqualTo("myDevice");
        assertThat(resourceId.getBasePath()).isEqualTo("telemetry/myTenant");
        assertThat(resourceId.getPathWithoutBase()).isEqualTo("myDevice");
        assertThat(resourceId.toString()).isEqualTo("telemetry/myTenant/myDevice");
    }

    /**
     * Verifies that a resource identifier can be created from string segments
     * containing {@code null} as resourceId part.
     */
    @Test
    public void testFromParametersAllowsNullResourceId() {
        final ResourceIdentifier resourceId = ResourceIdentifier.from("telemetry", "myTenant", null);
        assertThat(resourceId).isNotNull();
        assertThat(resourceId.getEndpoint()).isEqualTo("telemetry");
        assertThat(resourceId.getTenantId()).isEqualTo("myTenant");
        assertThat(resourceId.getResourceId()).isNull();
        assertThat(resourceId.getBasePath()).isEqualTo("telemetry/myTenant");
        assertThat(resourceId.getPathWithoutBase()).isEqualTo("");
        assertThat(resourceId.toString()).isEqualTo("telemetry/myTenant");
    }

    /**
     * Verifies that a resource identifier can be created from string segments
     * containing {@code null} as tenantId part.
     */
    @Test
    public void testFromParametersAllowsNullTenantId() {
        final ResourceIdentifier resourceId = ResourceIdentifier.from("telemetry", null, "myDevice");
        assertThat(resourceId).isNotNull();
        assertThat(resourceId.getEndpoint()).isEqualTo("telemetry");
        assertThat(resourceId.getTenantId()).isNull();
        assertThat(resourceId.getResourceId()).isEqualTo("myDevice");
        assertThat(resourceId.getBasePath()).isEqualTo("telemetry");
        assertThat(resourceId.getPathWithoutBase()).isEqualTo("myDevice");
        assertThat(resourceId.toString()).isEqualTo("telemetry//myDevice");
    }

    /**
     * Verifies that resource identifiers with the same endpoint and tenant
     * are considered equal.
     */
    @Test
    public void testEqualsReturnsTrueForSameEndpointAndTenant() {
        ResourceIdentifier one = ResourceIdentifier.from("ep", "tenant", null);
        ResourceIdentifier two = ResourceIdentifier.from("ep", "tenant", null);
        assertThat(one.equals(two)).isTrue();

        one = ResourceIdentifier.fromString("ep/tenant");
        two = ResourceIdentifier.fromString("ep/tenant");
        assertThat(one.equals(two)).isTrue();
    }

    /**
     * Verifies that resource identifiers with the same endpoint and tenant
     * produce the same hash code.
     */
    @Test
    public void testHashCodeReturnsSameValueForSameEndpointAndTenant() {
        ResourceIdentifier one = ResourceIdentifier.from("ep", "tenant", null);
        ResourceIdentifier two = ResourceIdentifier.from("ep", "tenant", null);
        assertThat(one.hashCode()).isEqualTo(two.hashCode());

        one = ResourceIdentifier.fromString("ep/tenant");
        two = ResourceIdentifier.fromString("ep/tenant");
        assertThat(one.hashCode()).isEqualTo(two.hashCode());
    }

    /**
     * Verifies that a resource identifier can be created from
     * a path that contains trailing {@code null} segments.
     */
    @Test
    public void testFromPathAllowsTrailingNulls() {
        final ResourceIdentifier id = ResourceIdentifier.fromPath(new String[]{"first", "second", null, null});
        assertThat(id.length()).isEqualTo(4);
        assertThat(id.elementAt(0)).isEqualTo("first");
        assertThat(id.elementAt(1)).isEqualTo("second");
        assertThat(id.elementAt(2)).isNull();
        assertThat(id.elementAt(3)).isNull();
    }

    /**
     * Verifies that a resource identifier can be created from
     * a path that contains non-trailing {@code null} segments.
     */
    @Test
    public void testFromPathAllowsNullSegments() {
        final ResourceIdentifier id = ResourceIdentifier.fromPath(new String[]{"first", "second", null, "last"});
        assertThat(id.length()).isEqualTo(4);
        assertThat(id.elementAt(0)).isEqualTo("first");
        assertThat(id.elementAt(1)).isEqualTo("second");
        assertThat(id.elementAt(2)).isNull();
        assertThat(id.elementAt(3)).isEqualTo("last");
    }

    /**
     * Verifies that a resource identifier can be created from another
     * resource identifier.
     */
    @Test
    public void testFromOtherResourceIdentifier() {
        ResourceIdentifier otherResourceId = ResourceIdentifier.fromString("telemetry/otherTenant/otherDeviceId/what/ever");
        ResourceIdentifier resourceId = ResourceIdentifier.from(otherResourceId, "myTenant", "myDevice");
        assertThat(resourceId).isNotNull();
        assertThat(resourceId.getEndpoint()).isEqualTo("telemetry");
        assertThat(resourceId.getTenantId()).isEqualTo("myTenant");
        assertThat(resourceId.getResourceId()).isEqualTo("myDevice");
        assertThat(resourceId.getBasePath()).isEqualTo("telemetry/myTenant");
        assertThat(resourceId.getPathWithoutBase()).isEqualTo("myDevice/what/ever");
        assertThat(resourceId.toString()).isEqualTo("telemetry/myTenant/myDevice/what/ever");
        assertThat(resourceId.getResourcePath()[3]).isEqualTo("what");
        assertThat(resourceId.getResourcePath()[4]).isEqualTo("ever");

        otherResourceId = ResourceIdentifier.fromString("telemetry/otherTenant");
        resourceId = ResourceIdentifier.from(otherResourceId, "myTenant", "myDevice");
        assertThat(resourceId).isNotNull();
        assertThat(resourceId.getEndpoint()).isEqualTo("telemetry");
        assertThat(resourceId.getTenantId()).isEqualTo("myTenant");
        assertThat(resourceId.getResourceId()).isEqualTo("myDevice");
        assertThat(resourceId.getBasePath()).isEqualTo("telemetry/myTenant");
        assertThat(resourceId.getPathWithoutBase()).isEqualTo("myDevice");
        assertThat(resourceId.toString()).isEqualTo("telemetry/myTenant/myDevice");

        otherResourceId = ResourceIdentifier.fromString("telemetry");
        resourceId = ResourceIdentifier.from(otherResourceId, "myTenant", "myDevice");
        assertThat(resourceId).isNotNull();
        assertThat(resourceId.getEndpoint()).isEqualTo("telemetry");
        assertThat(resourceId.getTenantId()).isEqualTo("myTenant");
        assertThat(resourceId.getResourceId()).isEqualTo("myDevice");
        assertThat(resourceId.getBasePath()).isEqualTo("telemetry/myTenant");
        assertThat(resourceId.getPathWithoutBase()).isEqualTo("myDevice");
        assertThat(resourceId.toString()).isEqualTo("telemetry/myTenant/myDevice");
    }

    /**
     * Verifies that a resource identifier cannot be created from
     * a path array that is empty.
     */
    @Test
    public void testFromPathFailsForEmptyPathArray() {
        assertThrows(IllegalArgumentException.class, () -> ResourceIdentifier.fromPath(new String[] {}));
    }

    /**
     * Verifies that a resource identifier cannot be created from
     * a path that starts with a {@code null} segment.
     */
    @Test
    public void testFromPathFailsForPathStartingWithNullSegment() {
        assertThrows(IllegalArgumentException.class,
                () -> ResourceIdentifier.fromPath(new String[] { null, "second", "last" }));
    }

    /**
     * Verifies that a resource identifier cannot be created from
     * a path that starts with an empty segment.
     */
    @Test
    public void testFromPathFailsForPathStartingWithEmptySegment() {
        assertThrows(IllegalArgumentException.class,
                () -> ResourceIdentifier.fromPath(new String[] { "", "second", "last" }));
    }

    /**
     * Verifies that a resource identifier cannot be created from
     * string segments where the first segment is empty.
     */
    @Test
    public void testFromIndividualParametersFailsForEmptyEndpoint() {
        assertThrows(IllegalArgumentException.class, () -> ResourceIdentifier.from("", "myTenant", "myDevice"));
    }

    /**
     * Verifies that a resource identifier cannot be created from
     * an empty string.
     */
    @Test
    public void testFromStringFailsForEmptyString() {
        assertThat(ResourceIdentifier.isValid("")).isFalse();
        assertThrows(IllegalArgumentException.class, () -> ResourceIdentifier.fromString(""));
    }

    /**
     * Verifies that a resource identifier cannot be created from
     * a string with an empty first segment.
     */
    @Test
    public void testFromStringFailsForStringWithEmptyFirstSegment() {
        assertThat(ResourceIdentifier.isValid("/")).isFalse();
        assertThrows(IllegalArgumentException.class, () -> ResourceIdentifier.fromString("/"));

        assertThat(ResourceIdentifier.isValid("/test")).isFalse();
        assertThrows(IllegalArgumentException.class, () -> ResourceIdentifier.fromString("/test"));
    }
}
