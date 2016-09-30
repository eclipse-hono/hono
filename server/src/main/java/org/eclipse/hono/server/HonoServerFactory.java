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

package org.eclipse.hono.server;

import java.util.Objects;

import org.eclipse.hono.util.VerticleFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A factory for creating {@code HonoServer} instances configured via Spring Boot.
 * <p>
 * The following Spring Boot environment properties are evaluated:
 * <ul>
 * <li>{@code hono.server.bindaddress} - The IP address to bind the server to. If not specified
 * the server binds to the loopback device (127.0.0.1).</li>
 * <li>{@code hono.server.port} - The port to listen on. If not specified the server listens on the
 * AMQP 1.0 standard port 5672.</li>
 * <li>{@code hono.networkdebuglogging} - If {@code true} Hono logs TCP traffic. Defaults to {@code false}.</li>
 * <li>{@code hono.singletenant} - If {@code true} the server supports a single tenant only. Defaults to {@false}.</li>
 * </ul>
 */
@Component
public class HonoServerFactory implements VerticleFactory<HonoServer> {

    private String                bindAddress;
    private int                   port;
    private boolean               singleTenant;
    private boolean               networkDebugLoggingEnabled;

    /**
     * Sets the port Hono will listen on for AMQP 1.0 connections.
     * <p>
     * If not set Hono binds to the standard AMQP 1.0 port (5672). If set to {@code 0} Hono will bind to an
     * arbitrary free port chosen by the operating system during startup.
     * </p>
     *
     * @param port the port to bind to.
     */
    @Value(value = "${hono.server.port:5672}")
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Sets the IP address Hono will bind to.
     * <p>
     * If not set Hono binds to the <em>loopback device</em> (usually 127.0.0.1 on an IPv4 stack).
     * </p>
     *  
     * @param bindAddress the IP address.
     */
    @Value(value = "${hono.server.bindaddress:127.0.0.1}")
    public void setBindAddress(final String bindAddress) {
        this.bindAddress = Objects.requireNonNull(bindAddress);
    }

    /**
     * Sets whether this instance should support a single tenant only.
     * <p>
     * Default is {@code false}.
     * </p>
     * 
     * @param singleTenant {@code true} if this Hono server should support a single tenant only.
     */
    @Value(value = "${hono.singletenant:false}")
    public void setSingleTenant(final boolean singleTenant) {
        this.singleTenant = singleTenant;
    }

    /**
     * Sets whether this instance should log TCP traffic.
     * <p>
     * Default is {@code false}.
     * </p>
     * 
     * @param enabled {@code true} if TCP traffic should be logged.
     */
    @Value(value = "${hono.networkdebuglogging:false}")
    public void setNetworkDebugLoggingEnabled(final boolean enabled) {
        this.networkDebugLoggingEnabled = enabled;
    }

    @Override
    public HonoServer newInstance() {
        return new HonoServer(bindAddress, port, singleTenant);
    }

    @Override
    public HonoServer newInstance(final int instanceNo, final int totalNoOfInstances) {
        HonoServer server = new HonoServer(bindAddress, port, singleTenant, instanceNo);
        server.setNetworkDebugLoggingEnabled(networkDebugLoggingEnabled);
        return server;
    }
}
