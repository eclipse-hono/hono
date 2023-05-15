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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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

    private final String tenant = "test-tenant";
    private final String device = "test-device";
    private final String topic = "event";
    private final String subtopic1 = "subtopic1";
    private final String subtopic2 = "subtopic2";

    /**
     * Verifies that the getTopicName method returns the Pub/Sub topic.
     */
    @Test
    public void testGetTopicNameWithoutSubtopics() {
        final String result = PubSubMessageHelper.getTopicName(topic, tenant);
        assertThat(result).isEqualTo(String.format("%s.%s", tenant, topic));
    }

    /**
     * Verifies that the getTopicName method returns the Pub/Sub topic with subtopics.
     */
    @Test
    public void testGetTopicNameWithSubtopics() {
        final List<String> subtopics = List.of(subtopic1, subtopic2);
        final String result = PubSubMessageHelper.getTopicName(topic, tenant, subtopics);
        assertThat(result).isEqualTo(String.format("%s.%s.%s.%s", tenant, topic, subtopic1, subtopic2));
    }

    /**
     * Verifies that the getSubtopics method returns a list with the subtopics.
     */
    @Test
    public void testGetSubtopicsWithSubtopics() {
        final String metadata = "?metadata=true";
        final String origAddress = String.format("%s/%s/%s/%s/%s/%s", topic, tenant, device, subtopic1, subtopic2, metadata);

        final List<String> result = PubSubMessageHelper.getSubtopics(origAddress);
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(subtopic1);
        assertThat(result.get(1)).isEqualTo(subtopic2);
    }

    /**
     * Verifies that the getSubtopics method returns an empty list if the orig_address attribute has no subtopics.
     */
    @Test
    public void testGetSubtopicsWithoutSubtopics() {
        final String origAddress = String.format("%s/%s/%s", topic, tenant, device);

        final List<String> result = PubSubMessageHelper.getSubtopics(origAddress);
        assertThat(result).isEmpty();
    }

    /**
     * Verifies that the getSubFolder method returns a string including two subtopics.
     */
    @Test
    public void testGetSubFolderShouldReturnStringOfSubFolder() {
        final String expectedResult = subtopic1 + "/" + subtopic2;
        final List<String> subtopics = Arrays.asList(subtopic1, subtopic2);

        final String result = PubSubMessageHelper.getSubFolder(subtopics);
        assertThat(result).isEqualTo(expectedResult);
    }

    /**
     * Verifies that the getSubFolder method returns an empty string if the passed list is empty.
     */
    @Test
    public void testGetSubFolderWithoutSubtopics() {
        final List<String> subtopics = new ArrayList<>();

        final String result = PubSubMessageHelper.getSubFolder(subtopics);
        assertThat(result).isEqualTo("");
    }

    /**
     * Verifies that the getTopicEndpointFromTopic method returns the topic endpoint.
     */
    @Test
    public void testGetTopicEndpointFromTopicReturnsTopicEndpoint() {
        final String topic = "test.tenant.telemetry.subtopic1.subtopic2";

        final String result = PubSubMessageHelper.getTopicEndpointFromTopic(topic, "test.tenant");
        assertThat(result).isEqualTo("telemetry");
    }

    /**
     * Verifies that the getTopicEndpointFromTopic method returns null when no subtopic is defined.
     */
    @Test
    public void testGetTopicEndpointFromTopicReturnNullWhenNoSubtopicIsDefined() {
        final String topic = tenant + ".telemetry";

        final String result = PubSubMessageHelper.getTopicEndpointFromTopic(topic, tenant);
        assertThat(result).isEqualTo(null);
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
                MessageHelper.APP_PROPERTY_DEVICE_ID, device,
                MessageHelper.APP_PROPERTY_TENANT_ID, tenant);

        final Optional<String> result = PubSubMessageHelper.getDeviceId(attributesMap);
        assertThat(result.isPresent()).isTrue();
        assertThat(result.get()).isEqualTo(device);
    }

    /**
     * Verifies that the getTenantId method returns an empty Optional.
     */
    @Test
    public void testThatGetTenantIdReturnsEmptyOptional() {
        final Map<String, String> attributesMap = getAttributes(
                MessageHelper.APP_PROPERTY_DEVICE_ID, device,
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
                MessageHelper.APP_PROPERTY_TENANT_ID, tenant,
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
                MessageHelper.APP_PROPERTY_DEVICE_ID, device,
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
                MessageHelper.APP_PROPERTY_DEVICE_ID, device,
                MessageHelper.APP_PROPERTY_TENANT_ID, tenant);

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
                MessageHelper.APP_PROPERTY_DEVICE_ID, device,
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

        final Map<String, String> deliveryFailureNotificationProperties = PubSubMessageHelper
                .getDeliveryFailureNotificationMetadata(attributesMap);

        assertThat(deliveryFailureNotificationProperties.size()).isEqualTo(2);

        assertThat(deliveryFailureNotificationProperties
                .get(PubSubMessageHelper.DELIVERY_FAILURE_NOTIFICATION_METADATA_PREFIX))
                .isEqualTo("test");

        assertThat(deliveryFailureNotificationProperties
                .get(PubSubMessageHelper.DELIVERY_FAILURE_NOTIFICATION_METADATA_PREFIX + "-test"))
                .isEqualTo("isPresent");
    }

    /**
     * Verifies that the getVia method returns an Optional describing the VIA property.
     */
    @Test
    public void testThatGetViaReturnsViaProperty() {
        final Map<String, String> attributesMap = getAttributes(
                MessageHelper.APP_PROPERTY_DEVICE_ID, "test-device",
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
