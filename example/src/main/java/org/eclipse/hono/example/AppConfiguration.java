/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *
 */

package org.eclipse.hono.example;

import org.eclipse.hono.adapter.AdapterConfig;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Example application.
 */
@Configuration
public class AppConfiguration extends AdapterConfig {

    /**
     * Exposes a {@code HonoClient} as a Spring bean.
     * 
     * @return The Hono client.
     */
    @Bean
    public HonoClient honoClient() {
        return new HonoClientImpl(vertx(), honoConnectionFactory());
    }
}
