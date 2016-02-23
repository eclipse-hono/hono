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
    private final String        uri;

    public AmqpConnectionConfig(final String uri) {
        requireNonNull(uri, "AMQP uri must not be null");
        if (!uri.toLowerCase().startsWith(AMQP_PREFIX)) {
            throw new IllegalArgumentException("AMQP uri must begin with \"amqp://\"");
        } else {
            this.uri = uri;
        }
    }

    public AmqpConnectionConfig(final String host, final String port) {
        this(host, port, null, null, null);
    }

    public AmqpConnectionConfig(final String host, final String port, final String username, final String password,
            final String vhost) {
        this.uri = createConnectionUri(requireNonNull(host, "amqp host must be specified."),
                requireNonNull(port, "amqp port must be specified."),
                username,
                password,
                vhost);
    }

    private String createConnectionUri(final String host, final String port, final String username,
            final String password, final String vhost) {
        final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(AmqpConnectionConfig.AMQP_PREFIX);
        if (username != null && password != null) {
            stringBuilder.append(username).append(":").append(password).append("@");
        }
        stringBuilder.append(host).append(":").append(port);
        if (vhost != null) {
            stringBuilder.append("/").append(vhost);
        }
        return stringBuilder.toString();
    }

    @Override
    public String getConnectionUri() {
        return uri;
    }
}
