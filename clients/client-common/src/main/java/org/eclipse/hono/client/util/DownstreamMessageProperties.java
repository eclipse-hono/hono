/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.ResourceLimits;
import org.eclipse.hono.util.TelemetryConstants;
import org.eclipse.hono.util.TenantConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A set of properties that should be set on a downstream message.
 * <p>
 * The set consists of
 * <ol>
 * <li>properties explicitly set by the sender of the message,</li>
 * <li>default properties defined at the level of the tenant that the device belongs to and</li>
 * <li>default properties defined at the level of the device that the message originates from.</li>
 * </ol>
 * Properties defined at the device level override properties of the same name defined at
 * the tenant level.
 * <p>
 * This class also makes sure that resource limits that are defined for the device's tenant are being
 * enforced on message properties that the limits are applicable to.
 */
public final class DownstreamMessageProperties {

    private static final Logger LOG = LoggerFactory.getLogger(DownstreamMessageProperties.class);

    private final Map<String, Object> props = new HashMap<>();
    private final ResourceLimits resourceLimits;
    private final String endpointName;

    /**
     * Creates properties for tenant and device level defaults and existing message properties.
     * <p>
     * Also applies any resource limits defined for the device or its tenant.
     * The endpoint name and the (optional) value of the {@value MessageHelper#APP_PROPERTY_QOS}
     * message property are used to determine which limits (and defaults) need to be applied.
     *
     * @param endpointName The name of the endpoint that the message is sent to.
     * @param tenantLevelDefaults The default properties defined at the tenant level or {@code null}
     *                            if no defaults are defined.
     * @param deviceLevelDefaults The default properties defined at the device level or {@code null}
     *                            if no defaults are defined.
     * @param messageProperties Properties provided in the message itself or {@code null} if the message does not
     *                          contain any properties.
     * @param resourceLimits A set of resource limits defined for the tenant or {@code null} if no limits are defined.
     * @throws NullPointerException if endpoint name is {@code null}.
     */
    public DownstreamMessageProperties(
            final String endpointName,
            final Map<String, Object> tenantLevelDefaults,
            final Map<String, Object> deviceLevelDefaults,
            final Map<String, Object> messageProperties,
            final ResourceLimits resourceLimits) {

        this.endpointName = Objects.requireNonNull(endpointName);
        this.resourceLimits = resourceLimits;

        Optional.ofNullable(tenantLevelDefaults).ifPresent(props::putAll);
        Optional.ofNullable(deviceLevelDefaults).ifPresent(props::putAll);
        Optional.ofNullable(messageProperties).ifPresent(props::putAll);
        applyLimits();
    }

    private void applyLimits() {
        if (TelemetryConstants.isTelemetryEndpoint(endpointName)) {
            applyTelemetryLimits();
        } else if (EventConstants.isEventEndpoint(endpointName)) {
            applyEventLimits();
        } else if (CommandConstants.isNorthboundCommandResponseEndpoint(endpointName)) {
            applyCommandResponseLimits();
        }
    }

    private void applyEventLimits() {

        final long deviceSpecificTtl = Optional.ofNullable(props.remove(MessageHelper.SYS_HEADER_PROPERTY_TTL))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::longValue)
                .filter(v -> v > TenantConstants.UNLIMITED_TTL)
                .orElse(TenantConstants.UNLIMITED_TTL);

        final long configuredMaxTtl = Optional.ofNullable(resourceLimits)
                .map(ResourceLimits::getMaxTtl)
                .filter(v -> v > TenantConstants.UNLIMITED_TTL)
                .orElse(TenantConstants.UNLIMITED_TTL);

        setTtl(deviceSpecificTtl, configuredMaxTtl);
    }

    private void applyTelemetryLimits() {

        final QoS qos = Optional.ofNullable(props.get(MessageHelper.APP_PROPERTY_QOS))
                .filter(Integer.class::isInstance)
                .map(Integer.class::cast)
                .map(QoS::from)
                .orElse(QoS.AT_MOST_ONCE);

        // make sure that we always remove the default properties from the map so that
        // they are not being added to the downstream message later on
        final long configuredDefaultTtlQoS0 = getNormalizedTtlPropertyValue(TenantConstants.FIELD_TTL_TELEMETRY_QOS0);
        final long configuredDefaultTtlQoS1 = getNormalizedTtlPropertyValue(TenantConstants.FIELD_TTL_TELEMETRY_QOS1);

        final long configuredMaxTtl;
        final long configuredDefaultTtl;

        switch (qos) {
        case AT_MOST_ONCE:
            configuredDefaultTtl = configuredDefaultTtlQoS0;
            configuredMaxTtl = Optional.ofNullable(resourceLimits)
                    .map(ResourceLimits::getMaxTtlTelemetryQoS0)
                    .filter(v -> v > TenantConstants.UNLIMITED_TTL)
                    .orElse(TenantConstants.UNLIMITED_TTL);
            break;
        case AT_LEAST_ONCE:
        default:
            configuredDefaultTtl = configuredDefaultTtlQoS1;
            configuredMaxTtl = Optional.ofNullable(resourceLimits)
                    .map(ResourceLimits::getMaxTtlTelemetryQoS1)
                    .filter(v -> v > TenantConstants.UNLIMITED_TTL)
                    .orElse(TenantConstants.UNLIMITED_TTL);
        }

        final long deviceProvidedTtl = getNormalizedTtlPropertyValue(MessageHelper.SYS_HEADER_PROPERTY_TTL);
        if (deviceProvidedTtl > TenantConstants.UNLIMITED_TTL) {
            setTtl(deviceProvidedTtl, configuredMaxTtl);
        } else {
            setTtl(configuredDefaultTtl, configuredMaxTtl);
        }
    }

    private void applyCommandResponseLimits() {

        // make sure that we always remove the default property from the map so that
        // it is not being added to the downstream message later on
        final long configuredDefaultTtl = getNormalizedTtlPropertyValue(TenantConstants.FIELD_TTL_COMMAND_RESPONSE);

        final long configuredMaxTtl = Optional.ofNullable(resourceLimits)
                .map(ResourceLimits::getMaxTtlCommandResponse)
                .filter(v -> v > TenantConstants.UNLIMITED_TTL)
                .orElse(TenantConstants.UNLIMITED_TTL);

        final long deviceProvidedTtl = getNormalizedTtlPropertyValue(MessageHelper.SYS_HEADER_PROPERTY_TTL);
        if (deviceProvidedTtl > TenantConstants.UNLIMITED_TTL) {
            setTtl(deviceProvidedTtl, configuredMaxTtl);
        } else {
            setTtl(configuredDefaultTtl, configuredMaxTtl);
        }
    }

    private long getNormalizedTtlPropertyValue(final String propertyName) {
        return Optional.ofNullable(props.remove(propertyName))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::longValue)
                .filter(v -> v > TenantConstants.UNLIMITED_TTL)
                .orElse(TenantConstants.UNLIMITED_TTL);
    }

    private void setTtl(final long deviceSpecificTtl, final long configuredMaxTtl) {

        long effectiveTtl = TenantConstants.UNLIMITED_TTL;

        if (configuredMaxTtl > effectiveTtl) {
            effectiveTtl = configuredMaxTtl;
        }

        if (effectiveTtl == TenantConstants.UNLIMITED_TTL || 
                ( deviceSpecificTtl > TenantConstants.UNLIMITED_TTL && deviceSpecificTtl < effectiveTtl)) {
            effectiveTtl = deviceSpecificTtl;
        }

        if (effectiveTtl > TenantConstants.UNLIMITED_TTL) {
            final long ttlMillis = effectiveTtl * 1000L;
            LOG.trace("setting message's TTL [{}ms]", ttlMillis);
            props.put(MessageHelper.SYS_HEADER_PROPERTY_TTL, ttlMillis);
        }
    }

    /**
     * Gets the properties.
     *
     * @return The properties.
     */
    public Map<String, Object> asMap() {
        return props;
    }
}
