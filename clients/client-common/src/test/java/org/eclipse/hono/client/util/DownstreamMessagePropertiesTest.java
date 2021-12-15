/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.client.util;

import static com.google.common.truth.Truth.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.ResourceLimits;
import org.eclipse.hono.util.TelemetryConstants;
import org.eclipse.hono.util.TenantConstants;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;


/**
 * Tests verifying behavior of {@link DownstreamMessageProperties}.
 *
 */
public class DownstreamMessagePropertiesTest {

    /**
     * Verifies that the TTL is set according to the TTL value provided by a device and the maximum and
     * default TTL values defined for the device and its tenant.
     *
     * @param endpointName The name of the endpoint that the message is sent to.
     * @param qosLevel The quality of service that the device wants to use for the message.
     * @param configuredTenantDefaultTtlSeconds The default TTL configured for the endpoint at the tenant level.
     * @param configuredDeviceDefaultTtlSeconds The default TTL configured for the endpoint at the device level.
     * @param configuredMaxTtlSeconds The maximum TTL configured for the endpoint.
     * @param deviceProvidedTtlSeconds The TTL specified by the device.
     * @param expectedTtlMillis The TLL value (milliseconds) expected to be set on the downstream message.
     */
    @ParameterizedTest
    @CsvSource(nullValues = { "NULL" }, value = {
        "e,NULL,NULL,NULL,NULL,NULL,NULL",
        "e,1,60,100,NULL,NULL,60000",
        "event,1,60,NULL,100,NULL,60000",
        "event,1,60,NULL,NULL,100,60000",
        "event,1,NULL,100,50,NULL,50000",
        "event,0,NULL,NULL,50,20,20000",
        "event,0,NULL,NULL,NULL,33,33000",
        "t,NULL,NULL,NULL,NULL,NULL,NULL",
        "t,0,10,100,NULL,NULL,10000",
        "telemetry,1,20,NULL,100,NULL,20000",
        "telemetry,0,10,NULL,NULL,100,10000",
        "telemetry,1,NULL,30,15,NULL,15000",
        "telemetry,0,NULL,NULL,30,10,10000",
        "telemetry,1,NULL,NULL,NULL,15,15000",
        "command_response,NULL,NULL,NULL,NULL,NULL,NULL",
        "command_response,0,30,60,NULL,NULL,30000",
        "command_response,1,30,NULL,60,NULL,30000",
        "command_response,0,30,NULL,NULL,60,30000",
        "command_response,1,NULL,60,40,NULL,40000",
        "command_response,1,NULL,NULL,40,20,20000",
        "command_response,0,NULL,NULL,NULL,25,25000"
        })
    public void testTtlIsSetAccordingToLimitsAndDefaults(
            final String endpointName,
            final Integer qosLevel,
            final Long configuredMaxTtlSeconds,
            final Long configuredTenantDefaultTtlSeconds,
            final Long configuredDeviceDefaultTtlSeconds,
            final Long deviceProvidedTtlSeconds,
            final Long expectedTtlMillis) {

        final var resourceLimits = new ResourceLimits();
        final Optional<Long> configuredMaxTtl = Optional.ofNullable(configuredMaxTtlSeconds);
        final Optional<Long> configuredTenantDefaultTtl = Optional.ofNullable(configuredTenantDefaultTtlSeconds);
        final Optional<Long> configuredDeviceDefaultTtl = Optional.ofNullable(configuredDeviceDefaultTtlSeconds);

        final Map<String, Object> tenantLevelDefaults = new HashMap<>();
        final Map<String, Object> deviceLevelDefaults = new HashMap<>();
        final Map<String, Object> messageProperties = new HashMap<>();
        Optional.ofNullable(qosLevel).ifPresent(l -> messageProperties.put(MessageHelper.APP_PROPERTY_QOS, l));

        if (TelemetryConstants.isTelemetryEndpoint(endpointName)) {
            switch (Optional.ofNullable(qosLevel).map(QoS::from).orElse(QoS.AT_MOST_ONCE)) {
            case AT_MOST_ONCE:
                configuredMaxTtl.ifPresent(resourceLimits::setMaxTtlTelemetryQoS0);
                configuredTenantDefaultTtl.ifPresent(t -> tenantLevelDefaults.put(TenantConstants.FIELD_TTL_TELEMETRY_QOS0, t));
                configuredDeviceDefaultTtl.ifPresent(t -> deviceLevelDefaults.put(TenantConstants.FIELD_TTL_TELEMETRY_QOS0, t));
                break;
            case AT_LEAST_ONCE:
                configuredMaxTtl.ifPresent(resourceLimits::setMaxTtlTelemetryQoS1);
                configuredTenantDefaultTtl.ifPresent(t -> tenantLevelDefaults.put(TenantConstants.FIELD_TTL_TELEMETRY_QOS1, t));
                configuredDeviceDefaultTtl.ifPresent(t -> deviceLevelDefaults.put(TenantConstants.FIELD_TTL_TELEMETRY_QOS1, t));
            }
        } else if (EventConstants.isEventEndpoint(endpointName)) {
            configuredMaxTtl.ifPresent(resourceLimits::setMaxTtl);
            configuredTenantDefaultTtl.ifPresent(t -> tenantLevelDefaults.put(MessageHelper.SYS_HEADER_PROPERTY_TTL, t));
            configuredDeviceDefaultTtl.ifPresent(t -> deviceLevelDefaults.put(MessageHelper.SYS_HEADER_PROPERTY_TTL, t));
        } else if (CommandConstants.isNorthboundCommandResponseEndpoint(endpointName)) {
            configuredMaxTtl.ifPresent(resourceLimits::setMaxTtlCommandResponse);
            configuredTenantDefaultTtl.ifPresent(t -> tenantLevelDefaults.put(TenantConstants.FIELD_TTL_COMMAND_RESPONSE, t));
            configuredDeviceDefaultTtl.ifPresent(t -> deviceLevelDefaults.put(TenantConstants.FIELD_TTL_COMMAND_RESPONSE, t));
        }

        Optional.ofNullable(deviceProvidedTtlSeconds)
            .ifPresent(v -> messageProperties.put(MessageHelper.SYS_HEADER_PROPERTY_TTL, v));

        final var props = new DownstreamMessageProperties(
                endpointName,
                tenantLevelDefaults,
                deviceLevelDefaults,
                messageProperties,
                resourceLimits);

        if (expectedTtlMillis == null) {
            assertThat(props.asMap()).doesNotContainKey(MessageHelper.SYS_HEADER_PROPERTY_TTL);
        } else {
            assertThat(props.asMap()).containsEntry(MessageHelper.SYS_HEADER_PROPERTY_TTL, expectedTtlMillis);
        }
    }

    /**
     * Verifies that properties at the message level take precedence over default properties at the device level
     * which take precedence over default properties at the tenant level.
     *
     * @param tenantDefaultPropertyValue The value of the property defined at the tenant level.
     * @param deviceDefaultPropertyValue The value of the property defined at the device level.
     * @param messagePropertyValue The value of the property defined at the message level.
     * @param expectedPropertyValue The property value expected to be set on the downstream message.
     */

    @ParameterizedTest
    @CsvSource( nullValues = "NULL", value = {
                            "NULL,NULL,NULL,NULL",
                            "NULL,NULL,message,message",
                            "NULL,device,NULL,device",
                            "NULL,device,message,message",
                            "tenant,NULL,NULL,tenant",
                            "tenant,NULL,message,message",
                            "tenant,device,NULL,device",
                            "tenant,device,message,message"
    })
    public void testPropertyValueHierarchy(
            final String tenantDefaultPropertyValue,
            final String deviceDefaultPropertyValue,
            final String messagePropertyValue,
            final String expectedPropertyValue) {

        final String propertyName = "property";
        final var tenantDefaults = Optional.ofNullable(tenantDefaultPropertyValue)
                .map(v -> Map.<String, Object>of(propertyName, v))
                .orElse(null);
        final var deviceDefaults = Optional.ofNullable(deviceDefaultPropertyValue)
                .map(v -> Map.<String, Object>of(propertyName, v))
                .orElse(null);
        final var messageProperties = Optional.ofNullable(messagePropertyValue)
                .map(v -> Map.<String, Object>of(propertyName, v))
                .orElse(null);

        final var props = new DownstreamMessageProperties(
                EventConstants.EVENT_ENDPOINT,
                tenantDefaults,
                deviceDefaults,
                messageProperties,
                null);

        if (expectedPropertyValue == null) {
            assertThat(props.asMap()).doesNotContainKey(propertyName);
        } else {
            assertThat(props.asMap()).containsEntry(propertyName, expectedPropertyValue);
        }
    }
}
