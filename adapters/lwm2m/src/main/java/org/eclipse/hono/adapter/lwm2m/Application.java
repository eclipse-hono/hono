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
package org.eclipse.hono.adapter.lwm2m;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * The Hono LWM2M adapter main application class.
 * <p>
 * This class uses Spring Boot for configuring the adapter's properties.
 */
@ComponentScan
@Configuration
@EnableAutoConfiguration
public class Application {

    public static void main(final String[] args) {
        SpringApplication app = new SpringApplication(Application.class);
        app.setWebEnvironment(true);
        app.run(args);
    }
}
