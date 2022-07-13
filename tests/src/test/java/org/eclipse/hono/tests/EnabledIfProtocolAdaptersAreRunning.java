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
package org.eclipse.hono.tests;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Configures a test to run only if particular protocol adapters are up and running.
 */
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(EnabledIfProtocolAdaptersAreRunningCondition.class)
public @interface EnabledIfProtocolAdaptersAreRunning {

    /**
     * Checks if the AMQP 1.0 protocol adapter is required to run.
     *
     * @return {@code true} if the test container must only be executed if the AMQP adapter is running.
     */
    boolean amqpAdapter() default false;

    /**
     * Checks if the CoAP protocol adapter is required to run.
     *
     * @return {@code true} if the test container must only be executed if the CoAP adapter is running.
     */
    boolean coapAdapter() default false;

    /**
     * Checks if the HTTP protocol adapter is required to run.
     *
     * @return {@code true} if the test container must only be executed if the HTTP adapter is running.
     */
    boolean httpAdapter() default false;

    /**
     * Checks if the Lora protocol adapter is required to run.
     *
     * @return {@code true} if the test container must only be executed if the Lora adapter is running.
     */
    boolean loraAdapter() default false;

    /**
     * Checks if the MQTT protocol adapter is required to run.
     *
     * @return {@code true} if the test container must only be executed if the MQTT adapter is running.
     */
    boolean mqttAdapter() default false;
}
