/*******************************************************************************
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.lora.impl;

import org.eclipse.hono.adapter.spring.AbstractProtocolAdapterApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * The Hono LoRa adapter main application class.
 */
@ComponentScan("org.eclipse.hono.adapter.lora.providers")
@Import(Config.class)
@SpringBootApplication
public class Application extends AbstractProtocolAdapterApplication {

    /**
     * Starts the LoRa Adapter application.
     *
     * @param args Command line args passed to the application.
     */
    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
