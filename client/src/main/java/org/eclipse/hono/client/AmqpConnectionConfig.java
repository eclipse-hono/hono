/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial API and implementation and initial documentation
 */
package org.eclipse.hono.client;

import static java.util.Objects.requireNonNull;

import org.eclipse.hono.client.api.ConnectionConfig;

public class AmqpConnectionConfig implements ConnectionConfig {
    private static final String AMQP_PREFIX = "amqp://";
    private final String        host;
    private final String        port;
    private final String        username;
    private final String        password;
    private final String        vhost;

    public AmqpConnectionConfig(final String host, final String port) {
        this(requireNonNull(host, "amqp host must be specified."),
           requireNonNull(port, "amqp port must be specified."),
           null, null, null);
    }

    public AmqpConnectionConfig(final String host, final String port, final String username, final String password,
            final String vhost) {
        requireNonNull(host);
        requireNonNull(port);
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.vhost = vhost;
    }

    @Override
    public String getConnectionUri() {
        final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(AmqpConnectionConfig.AMQP_PREFIX);
        if (username != null && password != null) {
            stringBuilder.append(username);
            stringBuilder.append(":");
            stringBuilder.append(password);
            stringBuilder.append("");
        }
        stringBuilder.append(host);
        stringBuilder.append(":");
        stringBuilder.append(port);
        if (vhost != null) {
            stringBuilder.append("/");
            stringBuilder.append(vhost);
        }
        return stringBuilder.toString();
    }
}
