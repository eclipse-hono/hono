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
import java.util.Optional;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.pubsub.v1.stub.PublisherStubSettings;

/**
 * Utility methods for working with Pub/Sub.
 */
public final class PubSubMessageHelper {

    private PubSubMessageHelper() {
    }

    /**
     * Gets the provider for credentials to use for authenticating to the Pub/Sub service.
     *
     * @return An optional containing a FixedCredentialsProvider to use for authenticating to the Pub/Sub service or an
     *         empty optional if the given GoogleCredentials is {@code null}.
     */
    public static Optional<FixedCredentialsProvider> getCredentialsProvider() {
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
     * @param topic The endpoint of the topic (e.g. event)
     * @param prefix The prefix of the Pub/Sub topic, it's either the tenant ID or the adapter instance ID
     * @return The topic containing the prefix identifier and the endpoint.
     */
    public static String getTopicName(final String topic, final String prefix) {
        return String.format("%s.%s", prefix, topic);
    }

}
