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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.hono.util.MessageHelper;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.pubsub.v1.stub.PublisherStubSettings;
import com.google.pubsub.v1.PubsubMessage;

/**
 * Utility methods for working with Pub/Sub.
 */
public final class PubSubMessageHelper {

    /**
     * The name of the Pub/Sub message property containing the identifier of the Google Cloud Project to connect to.
     */
    public static final String PUBSUB_PROPERTY_PROJECT_ID = "projectId";
    /**
     * The name of the Pub/Sub message property indicating whether a response to the message is expected/required.
     */
    public static final String PUBSUB_PROPERTY_RESPONSE_REQUIRED = "response-required";

    /**
     * Prefix to use in the Pub/Sub message properties for marking properties of command messages that should be
     * included in response messages indicating failure to deliver the command.
     */
    public static final String DELIVERY_FAILURE_NOTIFICATION_METADATA_PREFIX = "delivery-failure-notification-metadata";

    private PubSubMessageHelper() {
    }

    /**
     * Gets the provider for credentials to use for authenticating to the Pub/Sub service.
     *
     * @return An optional containing a CredentialsProvider to use for authenticating to the Pub/Sub service or an empty
     *         optional if the given GoogleCredentials is {@code null}.
     */
    public static Optional<CredentialsProvider> getCredentialsProvider() {
        return Optional.ofNullable(getCredentials())
                .map(FixedCredentialsProvider::create);
    }

    private static GoogleCredentials getCredentials() {
        try {
            return GoogleCredentials.getApplicationDefault()
                    .createScoped(PublisherStubSettings.getDefaultServiceScopes());
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Gets the topic name with the given prefix.
     *
     * @param topic The endpoint of the topic (e.g. event).
     * @param prefix The prefix of the Pub/Sub topic, it's either the tenant ID or the adapter instance ID.
     * @return The topic containing the prefix identifier and the endpoint.
     */
    public static String getTopicName(final String topic, final String prefix) {
        return getTopicName(topic, prefix, Collections.emptyList());
    }

    /**
     * Gets the topic name with the given prefix and subtopics.
     *
     * @param topic The endpoint of the topic (e.g. event).
     * @param prefix The prefix of the Pub/Sub topic, it's either the tenant ID or the adapter instance ID.
     * @param subtopics A list with the subtopics.
     * @return The topic containing the prefix identifier, the endpoint and all the subtopics.
     */
    public static String getTopicName(final String topic, final String prefix, final List<String> subtopics) {
        final StringBuilder topicBuilder = new StringBuilder();
        topicBuilder.append(prefix).append(".").append(topic);
        for (String subtopic : subtopics) {
            topicBuilder.append(".").append(subtopic);
        }
        return topicBuilder.toString();
    }

    /**
     * Gets the subtopics from the orig_address attribute of the message.
     *
     * @param origAddress The orig_address attribute.
     * @return An immutable list containing all the subtopics in hierarchical order or an empty immutable list if the topic has no subtopics.
     */
    public static List<String> getSubtopics(final String origAddress) {
        final String trimmedOrigAddress = origAddress.startsWith("/") ? origAddress.substring(1) : origAddress;
        final List<String> origAddressSplit = new ArrayList<>(Arrays.stream(trimmedOrigAddress.split("/")).toList());
        // Subtopics are located starting at the 4th position (e.g. event/tenantId/deviceId/subtopic1/subtopic2/...).
        if (origAddressSplit.size() < 4) {
            return Collections.emptyList();
        }
        origAddressSplit.subList(0, 3).clear();
        // Remove the last entry if it is a metadata property bag.
        if (origAddressSplit.get(origAddressSplit.size() - 1).startsWith("?")) {
            origAddressSplit.remove(origAddressSplit.size() - 1);
        }
        return Collections.unmodifiableList(origAddressSplit);
    }

    /**
     * Gets the subFolder from the list of subtopics.
     *
     * @param subtopics The list of subtopics.
     * @return A string containing the subFolder.
     */
    public static String getSubFolder(final List<String> subtopics) {
        return String.join("/", subtopics);
    }

    /**
     * Gets the topic endpoint as fallback from the given topic including subtopics.
     *
     * @param topic The topic containing the tenant and subtopic(s), e.g. tenant.telemetry.subtopic.
     * @param tenantId The tenant identifier related to the topic.
     * @return The original topic endpoint, e.g. telemetry, or {@code null} if no subtopic is defined.
     */
    public static String getTopicEndpointFromTopic(final String topic, final String tenantId) {
        final String topicWithoutTenant = topic.replace(tenantId, "");
        final String[] fallbackTopics = topicWithoutTenant.split("\\.");
        if (fallbackTopics.length <= 2) {
            return null;
        }
        return fallbackTopics[1];
    }

    /**
     * Gets the payload data contained in a Pub/Sub message's data body.
     *
     * @param message The Pub/Sub message data to parse.
     * @return The bytes representing the payload or an empty byte array if the message neither has data.
     *
     * @throws NullPointerException if the message is {@code null}.
     */
    public static byte[] getPayload(final PubsubMessage message) {
        Objects.requireNonNull(message);
        return message.getData().toByteArray();
    }

    /**
     * Gets the value of the {@link MessageHelper#APP_PROPERTY_DEVICE_ID} attribute.
     *
     * @param attributesMap The attributes map to get the value from.
     * @return The attributes value.
     */
    public static Optional<String> getDeviceId(final Map<String, String> attributesMap) {
        return getAttributesValue(attributesMap, MessageHelper.APP_PROPERTY_DEVICE_ID);
    }

    /**
     * Gets the value of the {@link MessageHelper#APP_PROPERTY_TENANT_ID} attribute.
     *
     * @param attributesMap The attributes map to get the value from.
     * @return The attributes value.
     */
    public static Optional<String> getTenantId(final Map<String, String> attributesMap) {
        return getAttributesValue(attributesMap, MessageHelper.APP_PROPERTY_TENANT_ID);
    }

    /**
     * Gets the value of the {@value MessageHelper#SYS_PROPERTY_CORRELATION_ID} attribute.
     *
     * @param attributesMap The attributes map to get the value from.
     * @return The attributes value.
     */
    public static Optional<String> getCorrelationId(final Map<String, String> attributesMap) {
        return getAttributesValue(attributesMap, MessageHelper.SYS_PROPERTY_CORRELATION_ID);
    }

    /**
     * Gets the value of the {@value #PUBSUB_PROPERTY_RESPONSE_REQUIRED} attribute.
     *
     * @param attributesMap The attributes map to get the value from.
     * @return The attributes value.
     */
    public static boolean isResponseRequired(final Map<String, String> attributesMap) {
        return Boolean
                .parseBoolean(getAttributesValue(attributesMap, PUBSUB_PROPERTY_RESPONSE_REQUIRED).orElse("false"));
    }

    /**
     * Gets the value of the {@value MessageHelper#SYS_PROPERTY_CONTENT_TYPE} attribute.
     *
     * @param attributesMap The attributes map to get the value from.
     * @return The attributes value.
     */
    public static Optional<String> getContentType(final Map<String, String> attributesMap) {
        return getAttributesValue(attributesMap, MessageHelper.SYS_PROPERTY_CONTENT_TYPE);
    }

    /**
     * Gets the value of the {@value MessageHelper#SYS_PROPERTY_SUBJECT} attribute.
     *
     * @param attributesMap The attributes map to get the value from.
     * @return The attributes value.
     */
    public static Optional<String> getSubject(final Map<String, String> attributesMap) {
        return getAttributesValue(attributesMap, MessageHelper.SYS_PROPERTY_SUBJECT);
    }

    /**
     * Gets the properties of the attributes which starts with the prefix
     * {@value #DELIVERY_FAILURE_NOTIFICATION_METADATA_PREFIX}.
     *
     * @param attributesMap The attributes map to get the value from.
     * @return The properties.
     */
    public static Map<String, String> getDeliveryFailureNotificationMetadata(final Map<String, String> attributesMap) {
        Objects.requireNonNull(attributesMap);
        return attributesMap
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().startsWith(DELIVERY_FAILURE_NOTIFICATION_METADATA_PREFIX))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Gets the value of the {@value MessageHelper#APP_PROPERTY_CMD_VIA} attribute.
     *
     * @param attributesMap The attributes map to get the value from.
     * @return The attributes value.
     */
    public static Optional<String> getVia(final Map<String, String> attributesMap) {
        return getAttributesValue(attributesMap, MessageHelper.APP_PROPERTY_CMD_VIA);
    }

    private static Optional<String> getAttributesValue(final Map<String, String> attributesMap, final String key) {
        Objects.requireNonNull(attributesMap);
        Objects.requireNonNull(key);
        return Optional.ofNullable(attributesMap.get(key));
    }

}
