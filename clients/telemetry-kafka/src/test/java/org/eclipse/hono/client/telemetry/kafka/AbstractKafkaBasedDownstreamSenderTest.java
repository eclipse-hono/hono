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
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.producer.CachingKafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.KafkaProducerConfigProperties;
import org.eclipse.hono.kafka.test.KafkaClientUnitTestHelper;
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
import io.vertx.core.json.Json;
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
    private static final String CONTENT_TYPE = "the-content-type";
    private static final String PRODUCER_NAME = "test-producer";

    protected final Tracer tracer = NoopTracerFactory.create();
    private final KafkaProducerConfigProperties config = new KafkaProducerConfigProperties();
    private final HonoTopic topic = new HonoTopic(HonoTopic.Type.EVENT, TENANT_ID);
    private final TenantObject tenant = new TenantObject(TENANT_ID, true);
    private final RegistrationAssertion device = new RegistrationAssertion(DEVICE_ID);

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {
        config.setProducerConfig(Map.of("hono.kafka.producerConfig.bootstrap.servers", "localhost:9092"));
    }

    /**
     * Verifies that {@link AbstractKafkaBasedDownstreamSender#start()} creates a producer and
     * {@link AbstractKafkaBasedDownstreamSender#stop()} closes it.
     */
    @Test
    public void testLifecycle() {
        final MockProducer<String, Buffer> mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        final CachingKafkaProducerFactory<String, Buffer> factory = newProducerFactory(mockProducer);
        final AbstractKafkaBasedDownstreamSender sender = newSender(factory);

        assertThat(factory.getProducer(PRODUCER_NAME).isEmpty()).isTrue();
        sender.start();
        assertThat(factory.getProducer(PRODUCER_NAME).isPresent()).isTrue();
        sender.stop();
        assertThat(factory.getProducer(PRODUCER_NAME).isEmpty()).isTrue();
    }

    /**
     * Verifies that the Kafka record is created as expected.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSendCreatesCorrectRecord(final VertxTestContext ctx) {

        // GIVEN a sender
        final String payload = "the-payload";
        final Map<String, Object> properties = Collections.singletonMap("foo", "bar");
        final MockProducer<String, Buffer> mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        final AbstractKafkaBasedDownstreamSender sender = newSender(mockProducer);

        // WHEN sending a message
        sender.send(topic, tenant, device, qos, CONTENT_TYPE, Buffer.buffer(payload), properties, null, null)
                .onComplete(ctx.succeeding(v -> {

                    final ProducerRecord<String, Buffer> actual = mockProducer.history().get(0);
                    ctx.verify(() -> {
                        // THEN the producer record is created from the given values...
                        assertThat(actual.key()).isEqualTo(DEVICE_ID);
                        assertThat(actual.topic()).isEqualTo(topic.toString());
                        assertThat(actual.value().toString()).isEqualTo(payload);
                        assertUniqueHeaderWithExpectedValue(actual.headers(), "foo", "bar");

                        // ...AND contains the standard headers
                        KafkaClientUnitTestHelper.assertStandardHeaders(actual, DEVICE_ID, CONTENT_TYPE, qos.ordinal());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the send method returns the underlying error wrapped in a {@link ServerErrorException}.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSendFailsWithTheExpectedError(final VertxTestContext ctx) {

        // GIVEN a sender sending a message
        final RuntimeException expectedError = new RuntimeException("boom");
        final MockProducer<String, Buffer> mockProducer = KafkaClientUnitTestHelper.newMockProducer(false);
        final AbstractKafkaBasedDownstreamSender sender = newSender(mockProducer);

        sender.send(topic, tenant, device, qos, CONTENT_TYPE, null, null, null, null)
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {
                        // THEN it fails with the expected error
                        assertThat(t).isInstanceOf(ServerErrorException.class);
                        assertThat(((ServerErrorException) t).getErrorCode()).isEqualTo(503);
                        assertThat(t.getCause()).isEqualTo(expectedError);
                    });
                    ctx.completeNow();
                }));

        // WHEN the send operation fails
        mockProducer.errorNext(expectedError);

    }

    /**
     * Verifies that the producer is closed when sending of a message fails with a fatal error.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testProducerIsClosedOnFatalError(final VertxTestContext ctx) {

        final AuthorizationException expectedError = new AuthorizationException("go away");

        // GIVEN a sender sending a message
        final MockProducer<String, Buffer> mockProducer = KafkaClientUnitTestHelper.newMockProducer(false);
        final CachingKafkaProducerFactory<String, Buffer> factory = newProducerFactory(mockProducer);
        newSender(factory).send(topic, tenant, device, qos, CONTENT_TYPE, null, null, null, null)
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {
                        // THEN the producer is removed and closed
                        assertThat(factory.getProducer(PRODUCER_NAME).isEmpty()).isTrue();
                        assertThat(mockProducer.closed()).isTrue();
                    });
                    ctx.completeNow();
                }));

        // WHEN the send operation fails
        mockProducer.errorNext(expectedError);

    }

    /**
     * Verifies that the producer is not closed when sending of a message fails with a non-fatal error.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testProducerIsNotClosedOnNonFatalError(final VertxTestContext ctx) {

        final RuntimeException expectedError = new RuntimeException("foo");

        // GIVEN a sender sending a message
        final MockProducer<String, Buffer> mockProducer = KafkaClientUnitTestHelper.newMockProducer(false);
        final CachingKafkaProducerFactory<String, Buffer> factory = newProducerFactory(mockProducer);
        newSender(factory).send(topic, tenant, device, qos, CONTENT_TYPE, null, null, null, null)
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {
                        // THEN the producer is present and still open
                        assertThat(factory.getProducer(PRODUCER_NAME).isPresent()).isTrue();
                        assertThat(mockProducer.closed()).isFalse();
                    });
                    ctx.completeNow();
                }));

        // WHEN the send operation fails
        mockProducer.errorNext(expectedError);

    }

    /**
     * Verifies that if no content type is present, then the default content type <em>"application/octet-stream"</em> is
     * set.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDefaultContentTypeIsUsedWhenNotPresent(final VertxTestContext ctx) {
        assertContentType("application/octet-stream", null, null, device, tenant, ctx);
    }

    /**
     * Verifies that properties from the tenant defaults are added as headers.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testTenantDefaultsAreApplied(final VertxTestContext ctx) {

        final String contentTypeTenant = "application/tenant";
        tenant.setDefaults(new JsonObject(Collections.singletonMap(CONTENT_TYPE_KEY, contentTypeTenant)));

        assertContentType(contentTypeTenant, null, null, device, tenant, ctx);
    }

    /**
     * Verifies that the default properties specified at device level overwrite the tenant defaults.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeviceDefaultsTakePrecedenceOverTenantDefaults(final VertxTestContext ctx) {

        final String expected = "application/device";

        tenant.setDefaults(new JsonObject(Collections.singletonMap(CONTENT_TYPE_KEY, "application/tenant")));
        device.setDefaults(Collections.singletonMap(CONTENT_TYPE_KEY, expected));

        assertContentType(expected, null, null, device, tenant, ctx);

    }

    /**
     * Verifies that the values in the <em>properties</em> parameter overwrite the device defaults.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testPropertiesParameterTakesPrecedenceOverDeviceDefaults(final VertxTestContext ctx) {

        final String expected = "application/properties";

        device.setDefaults(Collections.singletonMap(CONTENT_TYPE_KEY, "application/device"));
        final Map<String, Object> properties = new HashMap<>();
        properties.put(CONTENT_TYPE_KEY, expected);

        assertContentType(expected, null, properties, device, tenant, ctx);
    }

    /**
     * Verifies that the <em>contentType</em> parameter overwrites the values in the <em>properties</em> parameter.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testContentTypeParameterTakesPrecedenceOverPropertiesParameter(final VertxTestContext ctx) {

        final String expected = "application/parameter";

        final Map<String, Object> properties = new HashMap<>();
        properties.put(CONTENT_TYPE_KEY, "application/properties");

        assertContentType(expected, expected, properties, device, tenant, ctx);
    }

    private void assertContentType(final String expectedContentType, final String contentTypeParameter,
            final Map<String, Object> properties, final RegistrationAssertion device, final TenantObject tenant,
            final VertxTestContext ctx) {

        final MockProducer<String, Buffer> mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        newSender(mockProducer)
                .send(topic, tenant, device, qos, contentTypeParameter, null, properties, null, null)
                .onComplete(ctx.succeeding(v -> {
                    ctx.verify(() -> {
                        assertUniqueHeaderWithExpectedValue(mockProducer.history().get(0).headers(), CONTENT_TYPE_KEY,
                                expectedContentType);
                    });
                    ctx.completeNow();
                }));
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
        final MockProducer<String, Buffer> mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        final AbstractKafkaBasedDownstreamSender sender = newSender(mockProducer);

        // GIVEN a tenant with maximum TTL configured and a lower one in the properties
        tenant.setResourceLimits(new ResourceLimits().setMaxTtl(30));
        tenant.setDefaults(new JsonObject().put(MessageHelper.SYS_HEADER_PROPERTY_TTL, 15));
        final Map<String, Object> properties = Map.of("ttl", 10);

        // WHEN sending the message
        sender.send(topic, tenant, device, qos, CONTENT_TYPE, null, properties, null, null)
                .onComplete(ctx.succeeding(t -> {
                    ctx.verify(() -> {
                        // THEN the TTL from the properties is used
                        assertUniqueHeaderWithExpectedValue(mockProducer.history().get(0).headers(), "ttl",
                                Json.encode(10_000L));
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that if the properties do not contain a <em>ttl</em> property but the tenant has a default value
     * configured, the latter one is used.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testThatTtlFromDefaultsIsUsed(final VertxTestContext ctx) {
        final MockProducer<String, Buffer> mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        final AbstractKafkaBasedDownstreamSender sender = newSender(mockProducer);

        // GIVEN a tenant with maximum TTL configured and a lower one configured in the defaults
        tenant.setResourceLimits(new ResourceLimits().setMaxTtl(30));
        tenant.setDefaults(new JsonObject().put(MessageHelper.SYS_HEADER_PROPERTY_TTL, 10));

        // WHEN sending the message
        sender.send(topic, tenant, device, qos, CONTENT_TYPE, null, null, null, null)
                .onComplete(ctx.succeeding(t -> {
                    ctx.verify(() -> {
                        // THEN the TTL from the defaults is used
                        assertUniqueHeaderWithExpectedValue(mockProducer.history().get(0).headers(), "ttl",
                                Json.encode(10_000L));
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that defaults defined at the device level take precedence over properties defined at the tenant level.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testThatTtlFromDeviceDefaultsTakesPrecendenceOverTenantDefaults(final VertxTestContext ctx) {
        final MockProducer<String, Buffer> mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        final AbstractKafkaBasedDownstreamSender sender = newSender(mockProducer);

        // GIVEN a tenant with default TTL configured and a device with another default TTL
        device.setDefaults(Map.of(MessageHelper.SYS_HEADER_PROPERTY_TTL, 20));
        tenant.setDefaults(new JsonObject().put(MessageHelper.SYS_HEADER_PROPERTY_TTL, 10));

        // WHEN sending the message
        sender.send(topic, tenant, device, qos, CONTENT_TYPE, null, null, null, null)
                .onComplete(ctx.succeeding(t -> {
                    ctx.verify(() -> {
                        // THEN the TTL from the device's defaults is used
                        assertUniqueHeaderWithExpectedValue(mockProducer.history().get(0).headers(), "ttl",
                                Json.encode(20_000L));
                    });
                    ctx.completeNow();
                }));
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
        final MockProducer<String, Buffer> mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        final AbstractKafkaBasedDownstreamSender sender = newSender(mockProducer);

        // GIVEN a tenant with max TTL configured...
        tenant.setResourceLimits(new ResourceLimits().setMaxTtl(10));
        // ...AND properties that contain a higher TTL
        tenant.setDefaults(new JsonObject().put(MessageHelper.SYS_HEADER_PROPERTY_TTL, 15));
        final Map<String, Object> properties = Map.of("ttl", 30);

        // WHEN sending the message
        sender.send(topic, tenant, device, qos, CONTENT_TYPE, null, properties, null, null)
                .onComplete(ctx.succeeding(t -> {
                    ctx.verify(() -> {
                        // THEN the TTL is limited to the max TTL
                        assertUniqueHeaderWithExpectedValue(mockProducer.history().get(0).headers(), "ttl",
                                Json.encode(10_000L));
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that if the properties do not contain a <em>ttl</em> property, the <em>max-ttl</em> is used as the
     * effective TTL.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testThatMaxValueIsUsedByDefault(final VertxTestContext ctx) {
        final MockProducer<String, Buffer> mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        final AbstractKafkaBasedDownstreamSender sender = newSender(mockProducer);

        // GIVEN a tenant with max TTL configured but no TTL set in the properties
        tenant.setResourceLimits(new ResourceLimits().setMaxTtl(30));

        // WHEN sending the message
        sender.send(topic, tenant, device, qos, CONTENT_TYPE, null, null, null, null)
                .onComplete(ctx.succeeding(t -> {
                    ctx.verify(() -> {
                        // THEN the max TTL is used
                        assertUniqueHeaderWithExpectedValue(mockProducer.history().get(0).headers(), "ttl",
                                Json.encode(30_000L));
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that if the properties contain a <em>ttd</em> property but no <em>creation-time</em> then the latter is
     * added.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testThatCreationTimeIsAddedWhenNotPresentAndTtdIsSet(final VertxTestContext ctx) {
        final MockProducer<String, Buffer> mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        final AbstractKafkaBasedDownstreamSender sender = newSender(mockProducer);

        // GIVEN properties that contain a TTD
        final long ttd = 99L;
        final Map<String, Object> properties = new HashMap<>();
        properties.put("ttd", ttd);

        // WHEN sending the message
        sender.send(topic, tenant, device, qos, CONTENT_TYPE, null, properties, null, null)
                .onComplete(ctx.succeeding(t -> {
                    ctx.verify(() -> {
                        // THEN the producer record contains a creation time
                        final Headers headers = mockProducer.history().get(0).headers();
                        assertUniqueHeaderWithExpectedValue(headers, "ttd", Json.encode(ttd));
                        assertThat(headers.headers("creation-time")).isNotNull();
                    });
                    ctx.completeNow();
                }));

    }

    /**
     * Verifies that if the properties contain a <em>ttl</em> property but no <em>creation-time</em> then the latter is
     * added.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testThatCreationTimeIsAddedWhenNotPresentAndTtlIsSet(final VertxTestContext ctx) {
        final MockProducer<String, Buffer> mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        final AbstractKafkaBasedDownstreamSender sender = newSender(mockProducer);

        // GIVEN properties that contain a TTL
        final Map<String, Object> properties = new HashMap<>();
        properties.put("ttl", 99L);

        // WHEN sending the message
        sender.send(topic, tenant, device, qos, CONTENT_TYPE, null, properties, null, null)
                .onComplete(ctx.succeeding(t -> {
                    ctx.verify(() -> {
                        // THEN the producer record contains a creation time
                        final ProducerRecord<String, Buffer> record = mockProducer.history().get(0);
                        assertThat(record.headers().headers("creation-time")).hasSize(1);
                        assertThat(record.headers().headers("creation-time")).isNotNull();
                    });
                    ctx.completeNow();
                }));

    }

    /**
     * Verifies that if the properties contain a <em>creation-time</em> property then it is preserved.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testThatCreationTimeIsNotChanged(final VertxTestContext ctx) {
        final MockProducer<String, Buffer> mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        final AbstractKafkaBasedDownstreamSender sender = newSender(mockProducer);

        // GIVEN properties that contain creation-time
        final long creationTime = 12345L;
        final Map<String, Object> properties = new HashMap<>();
        properties.put("ttd", 99L);
        properties.put("ttl", 2L);
        properties.put("creation-time", creationTime);

        // WHEN sending the message
        sender.send(topic, tenant, device, qos, CONTENT_TYPE, null, properties, null, null)
                .onComplete(ctx.succeeding(t -> {
                    ctx.verify(() -> {
                        // THEN the creation time is preserved
                        assertUniqueHeaderWithExpectedValue(mockProducer.history().get(0).headers(), "creation-time",
                                Json.encode(creationTime));
                    });
                    ctx.completeNow();
                }));

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

    /**
     * Verifies that
     * {@link AbstractKafkaBasedDownstreamSender#send(HonoTopic, org.eclipse.hono.util.TenantObject, org.eclipse.hono.util.RegistrationAssertion, QoS, String, Buffer, Map, String, io.opentracing.SpanContext)}
     * throws a nullpointer exception if a mandatory parameter is {@code null}.
     */
    @Test
    public void testThatSendThrowsOnMissingMandatoryParameter() {
        final MockProducer<String, Buffer> mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        final AbstractKafkaBasedDownstreamSender sender = newSender(mockProducer);

        assertThrows(NullPointerException.class,
                () -> sender.send(null, tenant, device, qos, CONTENT_TYPE, null, null, null, null));

        assertThrows(NullPointerException.class,
                () -> sender.send(topic, null, device, qos, CONTENT_TYPE, null, null, null, null));

        assertThrows(NullPointerException.class,
                () -> sender.send(topic, tenant, null, qos, CONTENT_TYPE, null, null, null, null));

        assertThrows(NullPointerException.class,
                () -> sender.send(topic, tenant, device, null, CONTENT_TYPE, null, null, null, null));

    }

    private void assertUniqueHeaderWithExpectedValue(final Headers headers, final String key, final String expected) {
        assertThat(headers.headers(key)).hasSize(1);
        assertThat(headers).contains(new RecordHeader(key, expected.getBytes()));
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

    private AbstractKafkaBasedDownstreamSender newSender(final CachingKafkaProducerFactory<String, Buffer> factory) {
        return new AbstractKafkaBasedDownstreamSender(factory, PRODUCER_NAME, config, true, tracer) {
        };
    }

}
