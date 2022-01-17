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

package org.eclipse.hono.adapter.kura.impl;

import org.eclipse.hono.adapter.spring.AbstractProtocolAdapterApplication;
import org.eclipse.hono.deviceconnection.infinispan.client.HotrodCacheConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * The Kura adapter main application class.
 */
@Configuration
@Import({ Config.class, HotrodCacheConfig.class })
@EnableAutoConfiguration
public class Application extends AbstractProtocolAdapterApplication {

    /**
     * Starts the Kura Adapter application.
     *
     * @param args Command line arguments passed to the application.
     */
    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
