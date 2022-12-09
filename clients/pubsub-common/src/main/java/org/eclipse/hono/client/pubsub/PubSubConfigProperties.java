/*******************************************************************************
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

/**
 * Common configuration properties required for access to Pub/Sub.
 */
public class PubSubConfigProperties {

    private String projectId = null;

    /**
     * Creates properties based on existing options.
     *
     * @param options The options to copy.
     */
    public PubSubConfigProperties(final PubSubPublisherOptions options) {
        setProjectId(options.projectId().orElse(null));
    }

    public String getProjectId() {
        return projectId;
    }

    private void setProjectId(final String projectId) {
        this.projectId = projectId;
    }

    public boolean isProjectIdConfigured() {
        return projectId != null;
    }
}
