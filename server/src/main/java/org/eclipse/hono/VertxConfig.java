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
 */

package org.eclipse.hono;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.vertx.core.Vertx;

/**
 * Definition of a singleton {@code Vert.x} instance to be used with
 * Spring dependency injection.
 */
@Configuration
public class VertxConfig {

    private static final Vertx vertx = Vertx.vertx();

    /**
     * Gets the singleton instance.
     * 
     * @return the instance.
     */
    @Bean
    public static Vertx getInstance() {
        return vertx;
    }
}
