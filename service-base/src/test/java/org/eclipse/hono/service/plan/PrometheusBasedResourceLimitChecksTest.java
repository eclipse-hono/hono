/**
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.plan;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies the behavior of {@link PrometheusBasedResourceLimitChecks}.
 */
public class PrometheusBasedResourceLimitChecksTest {

    private PrometheusBasedResourceLimitChecks limitChecksImpl;

    /**
     * Sets up the fixture.
     */
    @Before
    public void setup() {
        limitChecksImpl = new PrometheusBasedResourceLimitChecks(mock(Vertx.class));
    }

    /**
     * Verifies that the default value for connections limit is set to
     * {@link PrometheusBasedResourceLimitChecks#DEFAULT_MAX_CONNECTIONS}.
     */
    @Test
    public void testGetConnectionsLimitDefaultValue() {
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        assertThat(limitChecksImpl.getConnectionsLimit(tenant),
                is(PrometheusBasedResourceLimitChecks.DEFAULT_MAX_CONNECTIONS));
    }

    /**
     * Verifies if the connections limit is set based on the configuration.
     */
    @Test
    public void testGetConnectionsLimit() {
        final JsonObject limitsConfig = new JsonObject()
                .put(PrometheusBasedResourceLimitChecks.FIELD_MAX_CONNECTIONS, 2);
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        tenant.setProperty(TenantConstants.FIELD_RESOURCE_LIMITS, limitsConfig);
        assertThat(limitChecksImpl.getConnectionsLimit(tenant), is(2L));
    }

    /**
     * Verifies that the default value for the parameter {@link PrometheusBasedResourceLimitChecks#FIELD_MAX_BYTES} is
     * set to {@link PrometheusBasedResourceLimitChecks#DEFAULT_MAX_BYTES}.
     */
    @Test
    public void testGetMaxBytesLimitDefaultValue() {
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        assertThat(limitChecksImpl.getMaximumNumberOfBytes(tenant),
                is(PrometheusBasedResourceLimitChecks.DEFAULT_MAX_BYTES));
    }

    /**
     * Verifies if the value corresponding to the parameter {@link PrometheusBasedResourceLimitChecks#FIELD_MAX_BYTES}
     * is set based on the configuration.
     */
    @Test
    public void testGetMaxBytesLimit() {
        final JsonObject dataVolumeConfig = new JsonObject()
                .put(PrometheusBasedResourceLimitChecks.FIELD_MAX_BYTES, 20_000_000);
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        tenant.setProperty(TenantConstants.FIELD_RESOURCE_LIMITS,
                new JsonObject().put(PrometheusBasedResourceLimitChecks.FIELD_DATA_VOLUME, dataVolumeConfig));
        assertThat(limitChecksImpl.getMaximumNumberOfBytes(tenant), is(20_000_000L));
    }

    /**
     * Verifies that the default value for the parameter {@link PrometheusBasedResourceLimitChecks#FIELD_PERIOD_IN_DAYS}
     * is set to {@link PrometheusBasedResourceLimitChecks#DEFAULT_PERIOD_IN_DAYS}.
     */
    @Test
    public void testGetPeriodInDaysDefaultValue() {
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        assertThat(limitChecksImpl.getPeriodInDays(tenant),
                is(PrometheusBasedResourceLimitChecks.DEFAULT_PERIOD_IN_DAYS));
    }

    /**
     * Verifies if the value corresponding to the parameter
     * {@link PrometheusBasedResourceLimitChecks#FIELD_PERIOD_IN_DAYS} is set based on the configuration.
     */
    @Test
    public void testGetPeriodInDays() {
        final JsonObject dataVolumeConfig = new JsonObject()
                .put(PrometheusBasedResourceLimitChecks.FIELD_PERIOD_IN_DAYS, 90);
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        tenant.setProperty(TenantConstants.FIELD_RESOURCE_LIMITS,
                new JsonObject().put(PrometheusBasedResourceLimitChecks.FIELD_DATA_VOLUME, dataVolumeConfig));
        assertThat(limitChecksImpl.getPeriodInDays(tenant), is(90L));
    }

    /**
     * Verifies if the value corresponding to the parameter
     * {@link PrometheusBasedResourceLimitChecks#FIELD_EFFECTIVE_SINCE} is set based on the configuration.
     */
    @Test
    public void testEffectiveSince() {
        final JsonObject dataVolumeConfig = new JsonObject()
                .put(PrometheusBasedResourceLimitChecks.FIELD_EFFECTIVE_SINCE, "2019-04-25");
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        tenant.setProperty(TenantConstants.FIELD_RESOURCE_LIMITS,
                new JsonObject().put(PrometheusBasedResourceLimitChecks.FIELD_DATA_VOLUME, dataVolumeConfig));
        assertThat(limitChecksImpl.getEffectiveSince(tenant), is(LocalDate.parse("2019-04-25", ISO_LOCAL_DATE)));
    }

    /**
     * Verifies that the default value for the parameter
     * {@link PrometheusBasedResourceLimitChecks#FIELD_EFFECTIVE_SINCE} is {@code null} if not set.
     */
    @Test
    public void testEffectiveSinceWhenNotSet() {
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        assertThat(limitChecksImpl.getEffectiveSince(tenant), is(nullValue()));
    }
}
