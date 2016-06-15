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

package org.eclipse.hono.telemetry.impl;

import static org.eclipse.hono.telemetry.TelemetryConstants.PATH_SEPARATOR;

import java.util.Objects;

import org.eclipse.hono.telemetry.SenderFactory;
import org.eclipse.hono.telemetry.TelemetryAdapter;
import org.eclipse.hono.util.ComponentFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * A factory for creating {@code ForwardingTelemetryAdapter} instances.
 *
 */
@Component
@Profile({"forwarding-telemetry", "activemq"})
public class ForwardingTelemetryAdapterFactory implements ComponentFactory<TelemetryAdapter> {

    private static final int MAX_PORT_NO = 65535;

    private String          downstreamContainerHost;
    private int             downstreamContainerPort;
    private String          pathSeparator             = PATH_SEPARATOR;
    private SenderFactory   senderFactory;

    /**
     * Creates a new instance using a <em>Proton</em> sender factory.
     * 
     * @param senderFactory the {@code ProtonSender} factory to pass in to the
     *                      telemetry adapters instantiated by this factory.
     */
    @Autowired
    public ForwardingTelemetryAdapterFactory(final SenderFactory senderFactory) {
        this.senderFactory = Objects.requireNonNull(senderFactory);
    }

    /**
     * @param host the hostname or IP address of the downstream AMQP 1.0 container
     *             to forward telemetry data to.
     */
    @Value(value = "${hono.telemetry.downstream.host}")
    public void setDownstreamContainerHost(final String host) {
        this.downstreamContainerHost = host;
    }

    /**
     * @param port the port of the downstream AMQP 1.0 container to forward telemetry data to.
     */
    @Value(value = "${hono.telemetry.downstream.port}")
    public void setDownstreamContainerPort(final int port) {
        if (port < 1 || port > MAX_PORT_NO) {
            throw new IllegalArgumentException(String.format("Port must be in the range [%d, %d]", 1, MAX_PORT_NO));
        }
        this.downstreamContainerPort = port;
    }

    @Value(value = "${hono.telemetry.pathSeparator:/}")
    public void setPathSeparator(String pathSeparator) {
        this.pathSeparator = pathSeparator;
    }

    @Override
    public TelemetryAdapter newInstance() {
        return newInstance(0, 1);
    }

    @Override
    public TelemetryAdapter newInstance(final int instanceNo, final int totalNoOfInstances) {
        ForwardingTelemetryAdapter result = new ForwardingTelemetryAdapter(senderFactory, instanceNo, totalNoOfInstances);
        result.setDownstreamContainerHost(downstreamContainerHost);
        result.setDownstreamContainerPort(downstreamContainerPort);
        result.setPathSeparator(pathSeparator);
        return result;
    }
}
