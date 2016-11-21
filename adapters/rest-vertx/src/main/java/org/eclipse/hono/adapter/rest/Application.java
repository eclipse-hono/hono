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
package org.eclipse.hono.adapter.rest;

import io.vertx.core.AbstractVerticle;
import org.eclipse.hono.adapter.VertxBasedAdapterApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * The Hono REST adapter main application class.
 */
@ComponentScan(basePackages = "org.eclipse.hono")
@Configuration
@EnableAutoConfiguration
public class Application extends VertxBasedAdapterApplication {

    private static final String NAME = "REST";

    private RestAdapterFactory factory;

    /**
     * @param factory the factory to set
     */
    @Autowired
    public final void setFactory(RestAdapterFactory factory) {
        this.factory = factory;
    }

    @Override
    protected AbstractVerticle getAdapter() {
        return this.factory.getRestAdapter();
    }

    @Override
    protected String getName() {
        return NAME;
    }

    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
