/*******************************************************************************
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.pubsub;

import jakarta.inject.Singleton;

/**
 * Common configuration properties required for access to Pub/Sub.
 */
@Singleton
public final class PubSubConfigProperties {

    private String projectId = null;

    /**
     * Creates properties based on existing options.
     *
     * @param options The options to copy.
     */
    public PubSubConfigProperties(final PubSubPublisherOptions options) {
        setProjectId(options.projectId().orElse(null));
    }

    /**
     * Gets the Google Cloud Project ID that the Pub/Sub client is configured to connect to.
     *
     * @return The projectId or {@code null} if no projectId has been set.
     */
    public String getProjectId() {
        return projectId;
    }

    /**
     * Sets the Google Cloud Project ID that the Pub/Sub client should connect to.
     *
     * @param projectId The Google Cloud Project ID.
     */
    public void setProjectId(final String projectId) {
        this.projectId = projectId;
    }

    /**
     * Checks if the projectId property has been explicitly set.
     *
     * @return {@code true} if the projectId property has been set via {@link #setProjectId(String)}.
     */
    public boolean isProjectIdConfigured() {
        return projectId != null;
    }
}
