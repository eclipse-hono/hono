/*******************************************************************************
 * Copyright (c) 2019, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.sigfox.impl;

import org.eclipse.hono.deviceconnection.infinispan.client.HotrodCacheConfig;
import org.eclipse.hono.service.spring.AbstractApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * The Hono Sigfox adapter main application class.
 */
@Import({ Config.class, HotrodCacheConfig.class })
@SpringBootApplication
public class Application extends AbstractApplication {

    /**
     * Starts the Sigfox Adapter application.
     *
     * @param args Command line args passed to the application.
     */
    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
