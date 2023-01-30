/**
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
 */
package org.eclipse.hono.client.pubsub;

import java.util.Optional;

import io.smallrye.config.ConfigMapping;

/**
 * Common options for configuring a Google Pub/Sub client.
 * <p>
 * We are using the prefix and naming strategy to match the property name(s) defined by
 * the <a href="https://quarkiverse.github.io/quarkiverse-docs/quarkus-google-cloud-services/main/index.html">
 * Quarkus Google Cloud Services extension</a>
 */
@ConfigMapping(prefix = "quarkus.google.cloud", namingStrategy = ConfigMapping.NamingStrategy.KEBAB_CASE)
public interface PubSubPublisherOptions {

    /**
     * Gets the Google Cloud Project identifier.
     *
     * @return The identifier.
     */
    Optional<String> projectId();
}
