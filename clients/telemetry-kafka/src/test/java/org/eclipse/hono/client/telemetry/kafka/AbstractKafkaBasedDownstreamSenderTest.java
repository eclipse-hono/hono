/*******************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.telemetry.kafka;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.google.common.truth.Truth.assertThat;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.apache.kafka.clients.producer.MockProducer;
import org.eclipse.hono.client.kafka.producer.CachingKafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.KafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.MessagingKafkaProducerConfigProperties;
import org.eclipse.hono.kafka.test.KafkaClientUnitTestHelper;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.ResourceLimits;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Verifies behavior of {@link AbstractKafkaBasedDownstreamSender}.
 */
@ExtendWith(VertxExtension.class)
public class AbstractKafkaBasedDownstreamSenderTest {

    private static final String CONTENT_TYPE_KEY = MessageHelper.SYS_PROPERTY_CONTENT_TYPE;

    private static final String TENANT_ID = "the-tenant";
    private static final String DEVICE_ID = "the-device";
    private static final QoS qos = QoS.AT_LEAST_ONCE;
    private static final String CONTENT_TYPE = EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION;
    private static final String PRODUCER_NAME = "test-producer";

    private final Tracer tracer = NoopTracerFactory.create();
    private final TenantObject tenant = new TenantObject(TENANT_ID, true);
    private final RegistrationAssertion device = new RegistrationAssertion(DEVICE_ID);

    private MessagingKafkaProducerConfigProperties config;
    private AbstractKafkaBasedDownstreamSender sender;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {
        config = new MessagingKafkaProducerConfigProperties();
        config.setProducerConfig(Map.of("hono.kafka.producerConfig.bootstrap.servers", "localhost:9092"));

        final var mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        sender = newSender(mockProducer);
    }

    /**
     * Verifies that if no content type is present, then the default content type <em>"application/octet-stream"</em> is
     * set.
     */
    @Test
    public void testDefaultContentTypeIsUsedWhenNotPresent() {
        assertContentType(
                MessageHelper.CONTENT_TYPE_OCTET_STREAM,
                null,
                null,
                device,
                tenant);
    }

    /**
     * Verifies that properties from the tenant defaults are added as headers.
     */
    @Test
    public void testTenantDefaultsAreApplied() {

        final String contentTypeTenant = "application/tenant";
        tenant.setDefaults(new JsonObject().put(CONTENT_TYPE_KEY, contentTypeTenant));

        assertContentType(
                contentTypeTenant,
                null,
                null,
                device,
                tenant);
    }

    /**
     * Verifies that the default properties specified at device level overwrite the tenant defaults.
     */
    @Test
    public void testDeviceDefaultsTakePrecedenceOverTenantDefaults() {

        final String expected = "application/device";

        tenant.setDefaults(new JsonObject(Collections.singletonMap(CONTENT_TYPE_KEY, "application/tenant")));
        device.setDefaults(Collections.singletonMap(CONTENT_TYPE_KEY, expected));

        assertContentType(
                expected,
                null,
                null,
                device,
                tenant);

    }

    /**
     * Verifies that the values in the message <em>properties</em> overwrite the device defaults.
     */
    @Test
    public void testPropertiesParameterTakesPrecedenceOverDeviceDefaults() {

        final String expected = "text/plain";
        device.setDefaults(Map.of(CONTENT_TYPE_KEY, "text/english"));

        assertContentType(
                expected,
                null,
                Map.of(CONTENT_TYPE_KEY, expected),
                device,
                tenant);
    }

    /**
     * Verifies that the <em>contentType</em> parameter overwrites the values in the <em>properties</em> parameter.
     */
    @Test
    public void testContentTypeParameterTakesPrecedenceOverPropertiesParameter() {

        final String contentType = "text/plain";

        assertContentType(
                contentType,
                contentType,
                Map.of(CONTENT_TYPE_KEY, "text/english"),
                device,
                tenant);
    }

    private void assertContentType(
            final String expectedContentType,
            final String messageContentType,
            final Map<String, Object> messageProperties,
            final RegistrationAssertion device,
            final TenantObject tenant) {

        final var propsWithDefaults = sender.addDefaults(
                tenant,
                device,
                qos,
                messageContentType,
                messageProperties);

        assertThat(propsWithDefaults.get(CONTENT_TYPE_KEY)).isEqualTo(expectedContentType);
    }

    private void assertTtlValue(
            final Map<String, Object> messageProperties,
            final Long expectedTtl,
            final VertxTestContext ctx) {

        Optional.ofNullable(expectedTtl)
            .ifPresent(v -> ctx.verify(() -> assertThat(messageProperties.get(MessageHelper.SYS_HEADER_PROPERTY_TTL))
                    .isEqualTo(expectedTtl)));
        ctx.completeNow();
    }

    /**
     * Verifies that if the properties contain a <em>ttl</em> property that is below the <em>max-ttl</em> of the tenant,
     * it is set as a Kafka header and the value in corresponding Kafka header is converted from seconds to
     * milliseconds.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testThatTtlFromDeviceIsPreserved(final VertxTestContext ctx) {

        // GIVEN a tenant with maximum TTL configured and a lower one in the properties
        tenant.setResourceLimits(new ResourceLimits().setMaxTtl(30));
        tenant.setDefaults(new JsonObject().put(MessageHelper.SYS_HEADER_PROPERTY_TTL, 15));
        final Map<String, Object> properties = Map.of(MessageHelper.SYS_HEADER_PROPERTY_TTL, 10);

        // WHEN creating the properties to include in the downstream message
        final var propsWithDefaults = sender.addDefaults(tenant, device, qos, CONTENT_TYPE, properties);

        assertTtlValue(propsWithDefaults, 10_000L, ctx);
    }

    /**
     * Verifies that if the properties do not contain a <em>ttl</em> property but the tenant has a default value
     * configured, the latter one is used.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testThatTtlFromDefaultsIsUsed(final VertxTestContext ctx) {

        // GIVEN a tenant with maximum TTL configured and a lower one configured in the defaults
        tenant.setResourceLimits(new ResourceLimits().setMaxTtl(30));
        tenant.setDefaults(new JsonObject().put(MessageHelper.SYS_HEADER_PROPERTY_TTL, 10));

        // WHEN creating the properties to include in the downstream message
        final var propsWithDefaults = sender.addDefaults(tenant, device, qos, CONTENT_TYPE, null);

        assertTtlValue(propsWithDefaults, 10_000L, ctx);
    }

    /**
     * Verifies that defaults defined at the device level take precedence over properties defined at the tenant level.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testThatTtlFromDeviceDefaultsTakesPrecendenceOverTenantDefaults(final VertxTestContext ctx) {

        // GIVEN a tenant with default TTL configured and a device with another default TTL
        device.setDefaults(Map.of(MessageHelper.SYS_HEADER_PROPERTY_TTL, 20));
        tenant.setDefaults(new JsonObject().put(MessageHelper.SYS_HEADER_PROPERTY_TTL, 10));

        // WHEN creating the properties to include in the downstream message
        final var propsWithDefaults = sender.addDefaults(tenant, device, qos, CONTENT_TYPE, null);

        // THEN the TTL from the device's defaults is used
        assertTtlValue(propsWithDefaults, 20_000L, ctx);
    }

    /**
     * Verifies that if the properties contain a <em>ttl</em> property, the value in corresponding Kafka header is
     * limited by the <em>max-ttl</em> specified for a tenant, if the <em>time-to-live</em> provided by the device
     * exceeds the <em>max-ttl</em>.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testThatTtlIsLimitedToMaxValue(final VertxTestContext ctx) {

        // GIVEN a tenant with max TTL configured...
        tenant.setResourceLimits(new ResourceLimits().setMaxTtl(10));
        // ...AND properties that contain a higher TTL
        tenant.setDefaults(new JsonObject().put(MessageHelper.SYS_HEADER_PROPERTY_TTL, 15));
        final Map<String, Object> properties = Map.of(MessageHelper.SYS_HEADER_PROPERTY_TTL, 30);

        // WHEN creating the properties to include in the downstream message
        final var propsWithDefaults = sender.addDefaults(tenant, device, qos, CONTENT_TYPE, properties);

        // THEN the TTL is limited to the max TTL
        assertTtlValue(propsWithDefaults, 10_000L, ctx);
    }

    /**
     * Verifies that if the properties do not contain a <em>ttl</em> property, the <em>max-ttl</em> is used as the
     * effective TTL.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testThatMaxValueIsUsedByDefault(final VertxTestContext ctx) {

        // GIVEN a tenant with max TTL configured but no TTL set in the properties
        tenant.setResourceLimits(new ResourceLimits().setMaxTtl(30));

        // WHEN creating the properties to include in the downstream message
        final var propsWithDefaults = sender.addDefaults(tenant, device, qos, CONTENT_TYPE, null);

        // THEN the max TTL is used
        assertTtlValue(propsWithDefaults, 30_000L, ctx);
    }

    /**
     * Verifies that the constructor throws a nullpointer exception if a parameter is {@code null}.
     */
    @Test
    public void testThatConstructorThrowsOnMissingParameter() {
        final MockProducer<String, Buffer> mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        final CachingKafkaProducerFactory<String, Buffer> factory = newProducerFactory(mockProducer);

        assertThrows(NullPointerException.class,
                () -> new AbstractKafkaBasedDownstreamSender(null, PRODUCER_NAME, config, true, tracer) {
                });

        assertThrows(NullPointerException.class,
                () -> new AbstractKafkaBasedDownstreamSender(factory, null, config, true, tracer) {
                });

        assertThrows(NullPointerException.class,
                () -> new AbstractKafkaBasedDownstreamSender(factory, PRODUCER_NAME, null, true, tracer) {
                });

        assertThrows(NullPointerException.class,
                () -> new AbstractKafkaBasedDownstreamSender(factory, PRODUCER_NAME, config, true, null) {
                });
    }

    private CachingKafkaProducerFactory<String, Buffer> newProducerFactory(
            final MockProducer<String, Buffer> mockProducer) {
        return CachingKafkaProducerFactory
                .testFactory((n, c) -> KafkaClientUnitTestHelper.newKafkaProducer(mockProducer));
    }

    private AbstractKafkaBasedDownstreamSender newSender(final MockProducer<String, Buffer> mockProducer) {
        final CachingKafkaProducerFactory<String, Buffer> factory = newProducerFactory(mockProducer);
        return newSender(factory);
    }

    private AbstractKafkaBasedDownstreamSender newSender(final KafkaProducerFactory<String, Buffer> factory) {
        return new AbstractKafkaBasedDownstreamSender(factory, PRODUCER_NAME, config, true, tracer) {
        };
    }

}
