/**
 * Copyright (c) 2016, 2017 Red Hat and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Red Hat - initial creation
 *    Bosch Software Innovations GmbH - extend AbtractApplication
 */

package org.eclipse.hono.adapter.mqtt.impl;

import org.eclipse.hono.service.AbstractApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * The Hono MQTT adapter main application class.
 */
@ComponentScan(basePackages = { "org.eclipse.hono.adapter.mqtt", "org.eclipse.hono.service.metric", "org.eclipse.hono.service.credentials" })
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
