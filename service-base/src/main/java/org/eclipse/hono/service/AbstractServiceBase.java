package org.eclipse.hono.service;

/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import org.eclipse.hono.config.HonoConfigProperties;
import org.eclipse.hono.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;

public abstract class AbstractServiceBase extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractServiceBase.class);

    protected HonoConfigProperties config = new HonoConfigProperties();

    protected int port         = Constants.PORT_UNCONFIGURED;
    protected int insecurePort = Constants.PORT_UNCONFIGURED;

    /**
     * Define the default value for the TLS port number in the implementing server class (e.g. 5671 for AMQPS 1.0).
     * @return The default value for the TLS port.
     */
    protected abstract int getPortDefaultValue();
    /**
     * Define the default value for the unencrypted port number in the implementing server class (e.g. 5672 for AMQP 1.0).
     * @return The default value for the unencrypted port.
     */
    protected abstract int getInsecurePortDefaultValue();

    /**
     * Return the port number of the TLS port. If the implementing service determined the port during startup
     * (ephemeral port configuration), return this port number.
     * @return The port number of the TLS port.
     */
    public abstract int getPort();

    /**
     * Return the port number of the unencrypted port. If the implementing service determined the port during startup
     * (ephemeral port configuration), return this port number.
     * @return The port number of the unencrypted port.
     */
    public abstract int getInsecurePort();

    /**
     * Set the configuration for the Hono service.
     * @param props The configuration properties.
     * @throws NullPointerException if props is {@code null}.
     */
    @Autowired(required = false)
    public final void setConfig(final HonoConfigProperties props) {
        this.config = Objects.requireNonNull(props);
    }

    /**
     * Either one secure, one insecure or one secure AND insecure ports are supported for servers in Hono.
     *
     * @param portConfigurationTracker The future to continue or fail (in case of configuration errors).
     * @throws NullPointerException if portConfigurationTracker is null.
     */
    protected final void determinePortConfigurations(Future<Void> portConfigurationTracker) {

        Objects.requireNonNull(portConfigurationTracker);

        // secure port first
        determineSecurePortConfiguration();
        determineInsecurePortConfiguration();

        if (isPortNumberUnconfigured(port) && isPortNumberUnconfigured(insecurePort)) {
            final String message = "cannot start up Server - no listening ports found.";
            portConfigurationTracker.fail(message);
        } else if (isPortNumberExplicitlySet(port) && isPortNumberExplicitlySet(insecurePort) && port == insecurePort) {
            final String message = String.format("cannot start up Server - secure and insecure ports configured to same port number %s.",
                    port);
            portConfigurationTracker.fail(message);
        } else {
            portConfigurationTracker.complete();
        }
    }

    private void determineSecurePortConfiguration() {

        if (config.getKeyCertOptions() == null) {
            if (config.getPort() >= 0) {
                LOG.warn("Secure port number configured, but the certificate setup is not correct. No secure port will be opened - please check your configuration!");
            }
            return;
        }

        port = config.getPort(getPortDefaultValue());

        if (port == getPortDefaultValue()) {
            LOG.info("Server uses secure standard port {}", getPortDefaultValue());
        } else if (config.getPort() == 0) {
            LOG.info("Server found secure port number configured for ephemeral port selection (port chosen automatically).");
        }
    }

    private void determineInsecurePortConfiguration() {

        if (config.isInsecurePortUnconfigured() && !config.isInsecurePortEnabled())
            return;

        insecurePort = config.getInsecurePort(getInsecurePortDefaultValue());

        if (insecurePort == 0) {
            LOG.info("Server found insecure port number configured for ephemeral port selection (port chosen automatically).");
        } else if (insecurePort == getInsecurePortDefaultValue()) {
            LOG.info("Server uses standard insecure port {}", getInsecurePortDefaultValue());
        } else if (insecurePort == getPortDefaultValue()) {
            LOG.warn("Server found insecure port number configured to standard port for secure connections {}", config.getInsecurePort());
            LOG.warn("Possibly misconfigured?");
        }
    }

    /**
     * Gets if Hono service opens a secure port.
     *
     * @return The flag value for opening a secure port.
     */
    public final boolean isOpenSecurePort() {
        return (port >= 0);
    }

    /**
     * Gets if Hono service opens an insecure port.
     *
     * @return The flag value for opening an insecure port.
     */
    public final boolean isOpenInsecurePort() {
        return (insecurePort >= 0);
    }

    /**
     * Gets the IP address Hono is bound to.
     *
     * @return The IP address.
     */
    public final String getBindAddress() {
        return config.getBindAddress();
    }

    /**
     * Gets the IP address Hono's insecure port is bound to.
     *
     * @return The IP address.
     */
    public final String getInsecurePortBindAddress() {
        return config.getInsecurePortBindAddress();
    }

    /**
     * Checks if the given port number represents an unconfigured port.
     * @param portToCheck The port number to check.
     * @return True if the port number if unconfigured.
     */
    public static final boolean isPortNumberUnconfigured(int portToCheck) {
        return (portToCheck == Constants.PORT_UNCONFIGURED);
    }

    /**
     * Checks if the given port number represents an explicitly configured port.
     * @param portToCheck The port number to check.
     * @return True if the port number was configured explicitly.
     */
    public static final boolean isPortNumberExplicitlySet(int portToCheck) {
        return (portToCheck != Constants.PORT_UNCONFIGURED && portToCheck != 0);
    }

}
