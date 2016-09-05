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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Example application.
 */
@Configuration
public class AppConfiguration {

    @Value(value = "${hono.server.host}")
    private String                host;
    @Value(value = "${hono.server.port}")
    private int                   port;
    @Value(value = "${tenant.id}")
    private String                tenantId;
    @Value(value = "${device.id}")
    private String                deviceId;
    @Value(value = "${hono.user}")
    private String                user;
    @Value(value = "${hono.password}")
    private String                password;
    @Value(value = "${role}")
    private String                role;
    @Value(value = "${hono.server.pathSeparator:/}")
    private String                pathSeparator;

    public String deviceId() {
        return deviceId;
    }

    public String host() {
        return host;
    }

    public String pathSeparator() {
        return pathSeparator;
    }

    public int port() {
        return port;
    }

    public String role() {
        return role;
    }

    public String user() {
        return user;
    }

    public String password() {
        return password;
    }

    public String tenantId() {
        return tenantId;
    }
}
