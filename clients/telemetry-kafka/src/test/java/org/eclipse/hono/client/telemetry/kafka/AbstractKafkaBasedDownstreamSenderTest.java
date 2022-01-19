/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.util.Collections;
import java.util.Map;

import org.apache.kafka.clients.producer.MockProducer;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.producer.CachingKafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.KafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.MessagingKafkaProducerConfigProperties;
import org.eclipse.hono.kafka.test.KafkaClientUnitTestHelper;
import org.eclipse.hono.notification.NotificationEventBusSupport;
import org.eclipse.hono.notification.deviceregistry.TenantChangeNotification;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;

/**
 * Verifies behavior of {@link AbstractKafkaBasedDownstreamSender}.
 */
@ExtendWith(VertxExtension.class)
public class AbstractKafkaBasedDownstreamSenderTest {

    private static final String CONTENT_TYPE_KEY = MessageHelper.SYS_PROPERTY_CONTENT_TYPE;

    private static final String TENANT_ID = "the-tenant";
    private static final String DEVICE_ID = "the-device";
    private static final QoS qos = QoS.AT_LEAST_ONCE;
    private static final String PRODUCER_NAME = "test-producer";

    private final Tracer tracer = NoopTracerFactory.create();
    private final RegistrationAssertion device = new RegistrationAssertion(DEVICE_ID);

    private Vertx vertxMock;
    private MessagingKafkaProducerConfigProperties config;
    private AbstractKafkaBasedDownstreamSender sender;
    private TenantObject tenant;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {
        vertxMock = mock(Vertx.class);
        when(vertxMock.eventBus()).thenReturn(mock(EventBus.class));

        config = new MessagingKafkaProducerConfigProperties();
        config.setProducerConfig(Map.of("hono.kafka.producerConfig.bootstrap.servers", "localhost:9092"));
        tenant = new TenantObject(TENANT_ID, true);

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
                Buffer.buffer("hello"),
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
                Buffer.buffer("hello"),
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
                Buffer.buffer("hello"),
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
                Buffer.buffer("hello"),
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
                Buffer.buffer("hello"),
                Map.of(CONTENT_TYPE_KEY, "text/english"),
                device,
                tenant);
    }

    private void assertContentType(
            final String expectedContentType,
            final String messageContentType,
            final Buffer payload,
            final Map<String, Object> messageProperties,
            final RegistrationAssertion device,
            final TenantObject tenant) {

        final var propsWithDefaults = sender.addDefaults(
                EventConstants.EVENT_ENDPOINT,
                tenant,
                device,
                qos,
                messageContentType,
                payload,
                messageProperties);

        assertThat(propsWithDefaults.get(CONTENT_TYPE_KEY)).isEqualTo(expectedContentType);
    }

    /**
     * Verifies that the constructor throws an NPE if a parameter is {@code null}.
     */
    @Test
    public void testThatConstructorThrowsOnMissingParameter() {
        final MockProducer<String, Buffer> mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        final CachingKafkaProducerFactory<String, Buffer> factory = newProducerFactory(mockProducer);

        assertThrows(NullPointerException.class,
                () -> new AbstractKafkaBasedDownstreamSender(null, factory, PRODUCER_NAME, config, true, tracer) {
                    @Override
                    protected HonoTopic.Type getTopicType() {
                        return HonoTopic.Type.EVENT;
                    }
                });

        assertThrows(NullPointerException.class,
                () -> new AbstractKafkaBasedDownstreamSender(vertxMock, null, PRODUCER_NAME, config, true, tracer) {
                    @Override
                    protected HonoTopic.Type getTopicType() {
                        return HonoTopic.Type.EVENT;
                    }
                });

        assertThrows(NullPointerException.class,
                () -> new AbstractKafkaBasedDownstreamSender(vertxMock, factory, null, config, true, tracer) {
                    @Override
                    protected HonoTopic.Type getTopicType() {
                        return HonoTopic.Type.EVENT;
                    }
                });

        assertThrows(NullPointerException.class,
                () -> new AbstractKafkaBasedDownstreamSender(vertxMock, factory, PRODUCER_NAME, null, true, tracer) {
                    @Override
                    protected HonoTopic.Type getTopicType() {
                        return HonoTopic.Type.EVENT;
                    }
                });

        assertThrows(NullPointerException.class,
                () -> new AbstractKafkaBasedDownstreamSender(vertxMock, factory, PRODUCER_NAME, config, true, null) {
                    @Override
                    protected HonoTopic.Type getTopicType() {
                        return HonoTopic.Type.EVENT;
                    }
                });
    }

    /**
     * Verifies that the sender registers itself for notifications of the type {@link TenantChangeNotification}.
     */
    @Test
    public void testThatNotificationConsumerIsRegistered() {
        final EventBus eventBus = mock(EventBus.class);
        when(vertxMock.eventBus()).thenReturn(eventBus);

        final Span span = TracingMockSupport.mockSpan();
        final Tracer tracer = TracingMockSupport.mockTracer(span);
        final var mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        final var factory = CachingKafkaProducerFactory
                .testFactory(vertxMock, (n, c) -> KafkaClientUnitTestHelper.newKafkaProducer(mockProducer));
        @SuppressWarnings("unused")
        final var sender = new AbstractKafkaBasedDownstreamSender(vertxMock, factory, PRODUCER_NAME, config, true, tracer) {
            @Override
            protected HonoTopic.Type getTopicType() {
                return HonoTopic.Type.EVENT;
            }
        };

        verify(eventBus).consumer(eq(NotificationEventBusSupport.getEventBusAddress(TenantChangeNotification.TYPE)),
                VertxMockSupport.anyHandler());

        verifyNoMoreInteractions(eventBus);
    }

    private CachingKafkaProducerFactory<String, Buffer> newProducerFactory(
            final MockProducer<String, Buffer> mockProducer) {
        return CachingKafkaProducerFactory
                .testFactory(vertxMock, (n, c) -> KafkaClientUnitTestHelper.newKafkaProducer(mockProducer));
    }

    private AbstractKafkaBasedDownstreamSender newSender(final MockProducer<String, Buffer> mockProducer) {
        final CachingKafkaProducerFactory<String, Buffer> factory = newProducerFactory(mockProducer);
        return newSender(factory);
    }

    private AbstractKafkaBasedDownstreamSender newSender(final KafkaProducerFactory<String, Buffer> factory) {
        return new AbstractKafkaBasedDownstreamSender(vertxMock, factory, PRODUCER_NAME, config, true, tracer) {
            @Override
            protected HonoTopic.Type getTopicType() {
                return HonoTopic.Type.EVENT;
            }
        };
    }

}
