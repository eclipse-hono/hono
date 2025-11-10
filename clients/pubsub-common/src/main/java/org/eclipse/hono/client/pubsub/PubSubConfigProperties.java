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

import io.netty.util.internal.StringUtil;
import jakarta.inject.Singleton;

/**
 * Common configuration properties required for using Pub/Sub.
 */
@Singleton
public final class PubSubConfigProperties {

    private String projectId = null;
    private String emulatorHost = null;

    /**
     * Creates properties based on existing pubSubPublisherOptions.
     *
     * @param pubSubQuarkusOptions   The pubSubQuarkusOptions containing the Google Cloud Project ID.
     */
    public PubSubConfigProperties(final PubSubQuarkusOptions pubSubQuarkusOptions) {
        setProjectId(pubSubQuarkusOptions.projectId().orElse(null));
        setEmulatorHost(pubSubQuarkusOptions.pubsub().emulatorHost().orElse(null));
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
        return projectId != null && StringUtil.length(projectId) > 0;
    }

    /**
     * Gets the emulator host that the Pub/Sub client is configured to connect to.
     *
     * @return The emulatorHost or {@code null} if no emulatorHost has been set.
     */
    public String getEmulatorHost() {
        return emulatorHost;
    }

    /**
     * Sets the emulator host that the Pub/Sub client should connect to.
     *
     * @param emulatorHost The emulator host if configured.
     */
    public void setEmulatorHost(final String emulatorHost) {
        this.emulatorHost = emulatorHost;
    }

    /**
     * Checks if the emulatorHost property has been explicitly set.
     *
     * @return {@code true} if the emulatorHost property has been set via {@link #setEmulatorHost(String)}.
     */
    public boolean isEmulatorHostConfigured() {
        return emulatorHost != null && StringUtil.length(emulatorHost) > 0;
    }
}
