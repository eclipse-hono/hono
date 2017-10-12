/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.adapter.rest;

import org.eclipse.hono.service.AbstractApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * The Hono REST adapter main application class.
 */
@ComponentScan(basePackages = { "org.eclipse.hono.adapter.rest", "org.eclipse.hono.service.metric", "org.eclipse.hono.adapter.http" })
@Configuration
@EnableAutoConfiguration
public class Application extends AbstractApplication {

    /**
     * Starts the REST Adapter application.
     * 
     * @param args Command line args passed to the application.
     */
    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
