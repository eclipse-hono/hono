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
import io.vertx.core.http.ClientAuth;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.TrustOptions;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;

/**
 * A base class for implementing services binding to a secure and/or a non-secure port.
 *
 * @param <T> The type of configuration properties used by this service.
 */
public abstract class AbstractServiceBase<T extends ServiceConfigProperties> extends AbstractVerticle {

    /**
     * A logger to be shared with subclasses.
     */
    protected final Logger LOG = LoggerFactory.getLogger(getClass());

    @SuppressWarnings("unchecked")
    private T config = (T) new ServiceConfigProperties();

    /**
     * Gets the default port number on which this service listens for encrypted communication (e.g. 5671 for AMQP 1.0).
     * 
     * @return The port number.
     */
    public abstract int getPortDefaultValue();

    /**
     * Gets the default port number on which this service listens for unencrypted communication (e.g. 5672 for AMQP 1.0).
     * 
     * @return The port number.
     */
    public abstract int getInsecurePortDefaultValue();

    /**
     * Gets the secure port number that this service has bound to.
     * <p>
     * The port number is determined as follows:
     * <ol>
     * <li>if this service is already listening on a secure port, the corresponding socket's actual port number is returned, else</li>
     * <li>if this service has been configured to listen on a secure port, the configured port number is returned, else</li>
     * <li>{@link Constants#PORT_UNCONFIGURED} is returned.</li>
     * </ol>
     * 
     * @return The port number.
     */
    public abstract int getPort();

    /**
     * Gets the insecure port number that this service has bound to.
     * <p>
     * The port number is determined as follows:
     * <ol>
     * <li>if this service is already listening on an insecure port, the corresponding socket's actual port number is returned, else</li>
     * <li>if this service has been configured to listen on an insecure port, the configured port number is returned, else</li>
     * <li>{@link Constants#PORT_UNCONFIGURED} is returned.</li>
     * </ol>
     * 
     * @return The port number.
     */
    public abstract int getInsecurePort();

    /**
     * Sets the properties to use for configuring the sockets to listen on.
     * 
     * @param props The properties.
     * @throws NullPointerException if props is {@code null}.
     */
    @Autowired(required = false)
    public final void setConfig(final T props) {
        this.config = Objects.requireNonNull(props);
    }

    /**
     * Gets the properties in use for configuring the sockets to listen on.
     * 
     * @return The properties.
     */
    public final T getConfig() {
        return this.config;
    }

    /**
     * Verifies that this service is properly configured to bind to at least one of the secure or insecure ports.
     *
     * @return A future indicating the outcome of the check.
     */
    protected final Future<Void> checkPortConfiguration() {

        Future<Void> result = Future.future();

        if (config.getKeyCertOptions() == null) {
            if (config.getPort() >= 0) {
                LOG.warn("Secure port number configured, but the certificate setup is not correct. No secure port will be opened - please check your configuration!");
            }
            if (!config.isInsecurePortEnabled()) {
                LOG.error("configuration must have at least one of key & certificate or insecure port set to start up");
                result.fail("no ports configured");
            } else {
                result.complete();
            }
        } else if (config.isInsecurePortEnabled()) {
            if (config.getPort(getPortDefaultValue()) == config.getInsecurePort(getInsecurePortDefaultValue())) {
                LOG.error("secure and insecure ports must be configured to bind to different port numbers");
                result.fail("secure and insecure ports configured to bind to same port number");
            } else {
                result.complete();
            }
        } else {
            result.complete();
        }

        return result;
    }

    /**
     * Determines the secure port to bind to.
     * <p>
     * The port is determined by invoking {@code HonoConfigProperties#getPort(int)}
     * with the value returned by {@link #getPortDefaultValue()}.
     * 
     * @return The port.
     */
    protected final int determineSecurePort() {

        int port = config.getPort(getPortDefaultValue());

        if (port == getPortDefaultValue()) {
            LOG.info("Server uses secure standard port {}", port);
        } else if (port == 0) {
            LOG.info("Server found secure port number configured for ephemeral port selection (port chosen automatically).");
        }
        return port;
    }

    /**
     * Determines the insecure port to bind to.
     * <p>
     * The port is determined by invoking {@code HonoConfigProperties#getInsecurePort(int)}
     * with the value returned by {@link #getInsecurePortDefaultValue()}.
     * 
     * @return The port.
     */
    protected final int determineInsecurePort() {

        int insecurePort = config.getInsecurePort(getInsecurePortDefaultValue());

        if (insecurePort == 0) {
            LOG.info("Server found insecure port number configured for ephemeral port selection (port chosen automatically).");
        } else if (insecurePort == getInsecurePortDefaultValue()) {
            LOG.info("Server uses standard insecure port {}", insecurePort);
        } else if (insecurePort == getPortDefaultValue()) {
            LOG.warn("Server found insecure port number configured to standard port for secure connections {}", config.getInsecurePort());
            LOG.warn("Possibly misconfigured?");
        }
        return insecurePort;
    }

    /**
     * Checks if this service has been configured to bind to the secure port during startup.
     * <p>
     * Subclasses may override this method in order to do more sophisticated checks.
     *  
     * @return {@code true} if <em>config</em> contains a valid key and certificate.
     */
    protected boolean isSecurePortEnabled() {
        return config.getKeyCertOptions() != null;
    }

    /**
     * Checks if this service will bind to the insecure port during startup.
     * <p>
     * Subclasses may override this method in order to do more sophisticated checks.
     *
     * @return {@code true} if the insecure port has been enabled on <em>config</em>.
     */
    protected boolean isInsecurePortEnabled() {
        return config.isInsecurePortEnabled();
    }

    /**
     * Gets the host name or IP address this server's secure port is bound to.
     *
     * @return The address.
     */
    public final String getBindAddress() {
        return config.getBindAddress();
    }

    /**
     * Gets the host name or IP address this server's insecure port is bound to.
     *
     * @return The address.
     */
    public final String getInsecurePortBindAddress() {
        return config.getInsecurePortBindAddress();
    }

    /**
     * Copies TLS trust store configuration to a given set of server options.
     * <p>
     * The trust store configuration is taken from <em>config</em> and will
     * be added only if the <em>ssl</em> flag is set on the given server options.
     * 
     * @param serverOptions The options to add configuration to.
     */
    protected final void addTlsTrustOptions(final NetServerOptions serverOptions) {

        if (serverOptions.isSsl() && serverOptions.getTrustOptions() == null) {

            TrustOptions trustOptions = getConfig().getTrustOptions();
            if (trustOptions != null) {
                serverOptions.setTrustOptions(trustOptions).setClientAuth(ClientAuth.REQUEST);
                LOG.info("enabling TLS for client authentication");
            }
        }
    }

    /**
     * Copies TLS key &amp; certificate configuration to a given set of server options.
     * <p>
     * If <em>config</em> contains key &amp; certificate configuration it is added to
     * the given server options and the <em>ssl</em> flag is set to {@code true}.
     * 
     * @param serverOptions The options to add configuration to.
     */
    protected final void addTlsKeyCertOptions(final NetServerOptions serverOptions) {

        KeyCertOptions keyCertOptions = getConfig().getKeyCertOptions();

        if (keyCertOptions != null) {
            serverOptions.setSsl(true).setKeyCertOptions(keyCertOptions);
        }
    }
}
