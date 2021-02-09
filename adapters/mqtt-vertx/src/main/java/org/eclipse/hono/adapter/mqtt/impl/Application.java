/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.mqtt.impl;

import org.eclipse.hono.service.spring.AbstractApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * The Hono MQTT adapter main application class.
 */
@ComponentScan("org.eclipse.hono.adapter.mqtt")
@ComponentScan("org.eclipse.hono.service.metric")
@ComponentScan("org.eclipse.hono.deviceconnection.infinispan.client")
@Configuration
@EnableAutoConfiguration
public class Application extends AbstractApplication {

    /**
     * Starts the MQTT Adapter application.
     *
     * @param args Command line args passed to the application.
     */
    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
