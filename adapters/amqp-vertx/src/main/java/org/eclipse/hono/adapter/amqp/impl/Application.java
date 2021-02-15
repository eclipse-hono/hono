/*******************************************************************************
 * Copyright (c) 2018, 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.amqp.impl;

import org.eclipse.hono.service.spring.AbstractApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * The Hono AMQP main application class.
 */
@ComponentScan("org.eclipse.hono.adapter.amqp")
@ComponentScan("org.eclipse.hono.deviceconnection.infinispan.client")
@Configuration
@EnableAutoConfiguration
public class Application extends AbstractApplication {

    /**
     * Starts the AMQP Adapter application.
     *
     * @param args
     *            The command-line arguments.
     */
    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
