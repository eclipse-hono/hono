/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service;

/**
 * Configuration of common properties that are valid for an application (and not only a specific server).
 *
 */
public class ApplicationConfigProperties {

    private int maxInstances = 0;
    private boolean amqpMessagingDisabled = false;
    private boolean kafkaMessagingDisabled = false;
    private boolean pubSubMessagingDisabled = false;

    /**
     * Creates new properties using default values.
     */
    public ApplicationConfigProperties() {
        super();
    }

    /**
     * Creates a new instance from existing options.
     *
     * @param options The options. All of the options are copied to the newly created instance.
     */
    public ApplicationConfigProperties(final ApplicationOptions options) {
        super();
        setMaxInstances(options.maxInstances());
        this.amqpMessagingDisabled = options.amqpMessagingDisabled();
        this.kafkaMessagingDisabled = options.kafkaMessagingDisabled();
        this.pubSubMessagingDisabled = options.pubSubMessagingDisabled();
    }

    /**
     * Gets the number of verticle instances to deploy.
     * <p>
     * The number is calculated as follows:
     * <ol>
     * <li>if 0 &lt; <em>maxInstances</em> &lt; #processors, then return <em>maxInstances</em></li>
     * <li>else return {@code Runtime.getRuntime().availableProcessors()}</li>
     * </ol>
     *
     * @return the number of verticles to deploy.
     */
    public final int getMaxInstances() {
        if (maxInstances > 0 && maxInstances < Runtime.getRuntime().availableProcessors()) {
            return maxInstances;
        } else {
            return Runtime.getRuntime().availableProcessors();
        }
    }

    /**
     * Sets the number of verticle instances to deploy.
     * <p>
     * The default value of this property is 0.
     *
     * @param maxVerticleInstances The number of verticles to deploy.
     * @throws IllegalArgumentException if the number is &lt; 0.
     */
    public final void setMaxInstances(final int maxVerticleInstances) {
        if (maxVerticleInstances < 0) {
            throw new IllegalArgumentException("maxInstances must be >= 0");
        }
        this.maxInstances = maxVerticleInstances;
    }

    /**
     * Checks if AMQP 1.0 based messaging has been disabled explicitly.
     *
     * @return {@code true} if disabled explicitly.
     */
    public final boolean isAmqpMessagingDisabled() {
        return amqpMessagingDisabled;
    }

    /**
     * Disables general support for AMQP 1.0 based messaging.
     *
     * @param disabled {@code true} to disable explicitly.
     */
    public final void setAmqpMessagingDisabled(final boolean disabled) {
        this.amqpMessagingDisabled = disabled;
    }

    /**
     * Checks if Kafka based messaging has been disabled explicitly.
     *
     * @return {@code true} if disabled explicitly.
     */
    public final boolean isKafkaMessagingDisabled() {
        return kafkaMessagingDisabled;
    }

    /**
     * Disables general support for Kafka based messaging.
     *
     * @param disabled {@code true} to disable explicitly.
     */
    public final void setKafkaMessagingDisabled(final boolean disabled) {
        this.kafkaMessagingDisabled = disabled;
    }

    /**
     * Checks if PubSub based messaging has been disabled explicitly.
     *
     * @return {@code true} if disabled explicitly.
     */
    public final boolean isPubSubMessagingDisabled() {
        return pubSubMessagingDisabled;
    }

    /**
     * Disables general support for PubSub based messaging.
     *
     * @param disabled {@code true} to disable explicitly.
     */
    public final void setPubSubMessagingDisabled(final boolean disabled) {
        this.pubSubMessagingDisabled = disabled;
    }
}
