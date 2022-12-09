/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration of common properties that are valid for an application (and not only a specific server).
 *
 */
@ConfigMapping(prefix = "hono.app", namingStrategy = ConfigMapping.NamingStrategy.VERBATIM)
public interface ApplicationOptions {

    /**
     * Gets the number of verticle instances to deploy.
     * <p>
     * The default value of this property is 0.
     *
     * @return the number of verticles to deploy.
     */
    @WithDefault("0")
    int maxInstances();

    /**
     * Checks if AMQP 1.0 based messaging has been disabled explicitly.
     *
     * @return {@code true} if disabled explicitly.
     */
    @WithDefault("false")
    boolean amqpMessagingDisabled();

    /**
     * Checks if Kafka based messaging has been disabled explicitly.
     *
     * @return {@code true} if disabled explicitly.
     */
    @WithDefault("false")
    boolean kafkaMessagingDisabled();

    /**
     * Checks if PubSub based messaging has been disabled explicitly.
     *
     * @return {@code true} if disabled explicitly.
     */
    @WithDefault("false")
    boolean pubSubMessagingDisabled();
}
