/*******************************************************************************
 * Copyright (c) 2018, 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.amqp.spring;

import org.eclipse.hono.adapter.spring.AbstractProtocolAdapterApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * The Hono AMQP main application class.
 */
@Configuration
@Import(Config.class)
@EnableAutoConfiguration
public class Application extends AbstractProtocolAdapterApplication {

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
