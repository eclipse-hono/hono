/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hono.client.pubsub;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static com.google.common.truth.Truth.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import org.eclipse.hono.util.MessageHelper;
import org.junit.jupiter.api.Test;

import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;

/**
 * Verifies the generic behavior of {@link PubSubMessageHelper}.
 */
public class PubSubMessageHelperTest {

    /**
     * Verifies that the getTopicName method returns the formatted topic.
     */
    @Test
    public void testThatGetTopicNameReturnsFormattedString() {
        final String topic = "event";
        final String prefix = "testTenant";

        final String result = PubSubMessageHelper.getTopicName(topic, prefix);
        assertThat(result).isEqualTo("testTenant.event");
    }

    /**
     * Verifies that the getPayload method returns the bytes representing the payload.
     */
    @Test
    public void testThatGetPayloadReturnsCorrectByteArray() {
        final byte[] b = new byte[22];
        new Random().nextBytes(b);

        final ByteString data = ByteString.copyFrom(b);
        final PubsubMessage pubsubMessage = PubsubMessage.newBuilder().setData(data).build();

        final byte[] result = PubSubMessageHelper.getPayload(pubsubMessage);
        assertThat(result).isEqualTo(b);
        assertThat(result.length).isEqualTo(22);
    }

    /**
     * Verifies that the getPayload method returns an empty byte array when the payload has no data.
     */
    @Test
    public void testThatGetPayloadReturnsEmptyByteArray() {
        final PubsubMessage pubsubMessage = PubsubMessage.newBuilder().build();

        final byte[] result = PubSubMessageHelper.getPayload(pubsubMessage);
        assertThat(result).isEqualTo(new byte[0]);
        assertThat(result.length).isEqualTo(0);
    }

    /**
     * Verifies that the getPayload method throws a NullPointerException when the message is {@code null}.
     */
    @Test
    public void testThatGetPayloadThrowsNullPointer() {
        assertThrows(NullPointerException.class, () -> PubSubMessageHelper.getPayload(null));
    }
    /**
     * Verifies that the getDeviceId method returns an Optional describing the device ID.
     */
    @Test
    public void testThatGetDeviceIdReturnsDeviceId() {
        final Map<String, String> attributesMap = getAttributes(
                PubSubMessageHelper.PUBSUB_PROPERTY_DEVICE_ID, "test-device",
                PubSubMessageHelper.PUBSUB_PROPERTY_TENANT_ID, "test-tenant");

        final Optional<String> result = PubSubMessageHelper.getDeviceId(attributesMap);
        assertThat(result.isPresent()).isTrue();
        assertThat(result.get()).isEqualTo("test-device");
    }

    /**
     * Verifies that the getTenantId method returns an empty Optional.
     */
    @Test
    public void testThatGetTenantIdReturnsEmptyOptional() {
        final Map<String, String> attributesMap = getAttributes(
                PubSubMessageHelper.PUBSUB_PROPERTY_DEVICE_ID, "test-device",
                MessageHelper.APP_PROPERTY_GATEWAY_ID, "test-gateway");

        final Optional<String> result = PubSubMessageHelper.getTenantId(attributesMap);
        assertThat(result.isPresent()).isFalse();
    }

    /**
     * Verifies that the getCorrelationId method returns an Optional describing the correlation ID.
     */
    @Test
    public void testThatGetCorrelationIdReturnsCorrelationId() {
        final Map<String, String> attributesMap = getAttributes(
                PubSubMessageHelper.PUBSUB_PROPERTY_TENANT_ID, "test-tenant",
                MessageHelper.SYS_PROPERTY_CORRELATION_ID, "correlation-id-test");

        final Optional<String> result = PubSubMessageHelper.getCorrelationId(attributesMap);
        assertThat(result.isPresent()).isTrue();
        assertThat(result.get()).isEqualTo("correlation-id-test");
    }

    /**
     * Verifies that the isResponseRequired method returns true.
     */
    @Test
    public void testThatIsResponseRequiredReturnsTrue() {
        final Map<String, String> attributesMap = getAttributes(
                PubSubMessageHelper.PUBSUB_PROPERTY_DEVICE_ID, "test-device",
                PubSubMessageHelper.PUBSUB_PROPERTY_RESPONSE_REQUIRED, "true");

        final boolean result = PubSubMessageHelper.isResponseRequired(attributesMap);
        assertThat(result).isTrue();
    }

    /**
     * Verifies that the isResponseRequired method returns false when property is not set.
     */
    @Test
    public void testThatIsResponseRequiredReturnsFalse() {
        final Map<String, String> attributesMap = getAttributes(
                PubSubMessageHelper.PUBSUB_PROPERTY_DEVICE_ID, "test-device",
                PubSubMessageHelper.PUBSUB_PROPERTY_TENANT_ID, "test-tenant");

        final boolean result = PubSubMessageHelper.isResponseRequired(attributesMap);
        assertThat(result).isFalse();
    }

    /**
     * Verifies that the getContentType method returns an empty Optional.
     */
    @Test
    public void testThatGetContentTypeReturnsEmptyContentType() {
        final Map<String, String> attributesMap = new HashMap<>();

        final Optional<String> result = PubSubMessageHelper.getContentType(attributesMap);
        assertThat(result.isPresent()).isFalse();
    }

    /**
     * Verifies that the getSubject method returns an Optional describing the subject.
     */
    @Test
    public void testThatGetSubjectReturnsSubject() {
        final Map<String, String> attributesMap = getAttributes(
                PubSubMessageHelper.PUBSUB_PROPERTY_DEVICE_ID, "test-device",
                MessageHelper.SYS_PROPERTY_SUBJECT, "test-command");

        final Optional<String> result = PubSubMessageHelper.getSubject(attributesMap);
        assertThat(result.isPresent()).isTrue();
        assertThat(result.get()).isEqualTo("test-command");
    }

    /**
     * Verifies the return value of getDeliveryFailureNotificationMetadata.
     */
    @Test
    public void testThatGetDeliveryFailureNotificationMetadataReturnsProperties() {
        final Map<String, String> attributesMap = getAttributes(
                PubSubMessageHelper.DELIVERY_FAILURE_NOTIFICATION_METADATA_PREFIX, "test",
                PubSubMessageHelper.DELIVERY_FAILURE_NOTIFICATION_METADATA_PREFIX + "-test", "isPresent");
        attributesMap.put("another-key", "another-value");

        final Map<String, String> deliveryFailureNotificationProperties = PubSubMessageHelper.getDeliveryFailureNotificationMetadata(attributesMap);

        assertThat(deliveryFailureNotificationProperties.size()).isEqualTo(2);

        assertThat(deliveryFailureNotificationProperties.get(PubSubMessageHelper.DELIVERY_FAILURE_NOTIFICATION_METADATA_PREFIX))
                .isEqualTo("test");

        assertThat(deliveryFailureNotificationProperties.get(PubSubMessageHelper.DELIVERY_FAILURE_NOTIFICATION_METADATA_PREFIX + "-test"))
                .isEqualTo("isPresent");
    }

    /**
     * Verifies that the getVia method returns an Optional describing the VIA property.
     */
    @Test
    public void testThatGetViaReturnsViaProperty() {
        final Map<String, String> attributesMap = getAttributes(
                PubSubMessageHelper.PUBSUB_PROPERTY_DEVICE_ID, "test-device",
                MessageHelper.APP_PROPERTY_CMD_VIA, "test-gateway");

        final Optional<String> result = PubSubMessageHelper.getVia(attributesMap);
        assertThat(result.isPresent()).isTrue();
        assertThat(result.get()).isEqualTo("test-gateway");
    }

    private Map<String, String> getAttributes(final String property1, final String value1, final String property2,
            final String value2) {
        final Map<String, String> attributesMap = new HashMap<>();
        attributesMap.put(property1, value1);
        attributesMap.put(property2, value2);
        return attributesMap;
    }
}
