/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.hono.service;

import java.util.Objects;

import org.eclipse.hono.config.AbstractConfig;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.ConfigurationSupportingVerticle;
import org.eclipse.hono.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import io.netty.handler.ssl.OpenSsl;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Future;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.TrustOptions;
import io.vertx.ext.healthchecks.HealthCheckHandler;

/**
 * A base class for implementing services binding to a secure and/or a non-secure port.
 *
 * @param <T> The type of configuration properties used by this service.
 */
public abstract class AbstractServiceBase<T extends ServiceConfigProperties> extends ConfigurationSupportingVerticle<T> implements HealthCheckProvider {

    /**
     * A logger to be shared with subclasses.
     */
    protected final Logger LOG = LoggerFactory.getLogger(getClass());

    /**
     * The OpenTracing {@code Tracer} for tracking processing of requests.
     */
    protected Tracer tracer = NoopTracerFactory.create();

    private HealthCheckServer healthCheckServer = new NoopHealthCheckServer();

    /**
     * Sets the OpenTracing {@code Tracer} to use for tracking the processing
     * of messages published by devices across Hono's components.
     * <p>
     * If not set explicitly, the {@code NoopTracer} from OpenTracing will
     * be used.
     * 
     * @param opentracingTracer The tracer.
     */
    @Autowired(required = false)
    public final void setTracer(final Tracer opentracingTracer) {
        LOG.info("using OpenTracing Tracer implementation [{}]", opentracingTracer.getClass().getName());
        this.tracer = Objects.requireNonNull(opentracingTracer);
    }

    /**
     * Sets the health check server for this application.
     *
     * @param healthCheckServer The health check server.
     * @throws NullPointerException if healthCheckServer is {@code null}.
     */
    @Autowired(required = false)
    public void setHealthCheckServer(final HealthCheckServer healthCheckServer) {
        this.healthCheckServer = Objects.requireNonNull(healthCheckServer);
    }

    /**
     * Starts up this component.
     * <ol>
     * <li>invokes {@link #startInternal()}</li>
     * </ol>
     * 
     * @param startFuture Will be completed if all of the invoked methods return a succeeded Future.
     */
    @Override
    public final void start(final Future<Void> startFuture) {
        healthCheckServer.registerHealthCheckResources(this);
        startInternal().setHandler(startFuture);
    }

    /**
     * Subclasses should override this method to perform any work required on start-up of this protocol component.
     * <p>
     * This method is invoked by {@link #start()} as part of the startup process.
     *
     * @return A future indicating the outcome of the startup. If the returned future fails, this component will not start up.
     */
    protected Future<Void> startInternal() {
        // should be overridden by subclasses
        return Future.succeededFuture();
    }

    /**
     * Stops this component.
     * <ol>
     * <li>invokes {@link #stopInternal()}</li>
     * </ol>
     * 
     * @param stopFuture Will be completed if all of the invoked methods return a succeeded Future.
     */
    @Override
    public final void stop(final Future<Void> stopFuture) {
        stopInternal().setHandler(stopFuture);
    }

    /**
     * Subclasses should override this method to perform any work required before shutting down this component.
     * <p>
     * This method is invoked by {@link #stop()} as part of the shutdown process.
     *
     * @return A future indicating the outcome.
     */
    protected Future<Void> stopInternal() {
        // to be overridden by subclasses
        return Future.succeededFuture();
    }

    /**
     * Registers checks to perform in order to determine whether this component is ready to serve requests.
     * <p>
     * An external systems management component can get the result of running these checks by means
     * of doing a HTTP GET /readiness.
     * 
     * @param handler The handler to register the checks with.
     */
    @Override
    public void registerReadinessChecks(final HealthCheckHandler handler) {
        // empty default implementation
    }

    /**
     * Registers checks to perform in order to determine whether this component is alive.
     * <p>
     * An external systems management component can get the result of running these checks by means
     * of doing a HTTP GET /liveness.
     * 
     * @param handler The handler to register the checks with.
     */
    @Override
    public void registerLivenessChecks(final HealthCheckHandler handler) {
        // empty default implementation
    }

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
     * Gets the port number from a running secure server if it is listening on the corresponding socket already.
     * <p>
     * If no server is listening, {@link Constants#PORT_UNCONFIGURED} is returned.
     *
     * @return The port number.
     */
    protected abstract int getActualPort();

    /**
     * Gets the port number from a running insecure server if it is listening on the corresponding socket already.
     * <p>
     * If no server is listening, {@link Constants#PORT_UNCONFIGURED} is returned.
     *
     * @return The port number.
     */
    protected abstract int getActualInsecurePort();

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
    public final int getPort() {
        if (getActualPort() != Constants.PORT_UNCONFIGURED) {
            return getActualPort();
        } else if (isSecurePortEnabled()) {
            return getConfig().getPort(getPortDefaultValue());
        } else {
            return Constants.PORT_UNCONFIGURED;
        }
    };

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
    public final int getInsecurePort() {
        if (getActualInsecurePort() != Constants.PORT_UNCONFIGURED) {
            return getActualInsecurePort();
        } else if (isInsecurePortEnabled()) {
            return getConfig().getInsecurePort(getInsecurePortDefaultValue());
        } else {
            return Constants.PORT_UNCONFIGURED;
        }
    }

    /**
     * Verifies that this service is properly configured to bind to at least one of the secure or insecure ports.
     *
     * @return A future indicating the outcome of the check.
     */
    protected final Future<Void> checkPortConfiguration() {

        if (vertx != null) {
            LOG.info("Vertx native support: {}", vertx.isNativeTransportEnabled());
        }

        final Future<Void> result = Future.future();

        if (getConfig().getKeyCertOptions() == null) {
            if (getConfig().getPort() >= 0) {
                LOG.warn("Secure port number configured, but the certificate setup is not correct. No secure port will be opened - please check your configuration!");
            }
            if (!getConfig().isInsecurePortEnabled()) {
                LOG.error("configuration must have at least one of key & certificate or insecure port set to start up");
                result.fail("no ports configured");
            } else {
                result.complete();
            }
        } else if (getConfig().isInsecurePortEnabled()) {
            if (getConfig().getPort(getPortDefaultValue()) == getConfig().getInsecurePort(getInsecurePortDefaultValue())) {
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

        final int port = getConfig().getPort(getPortDefaultValue());

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

        final int insecurePort = getConfig().getInsecurePort(getInsecurePortDefaultValue());

        if (insecurePort == 0) {
            LOG.info("Server found insecure port number configured for ephemeral port selection (port chosen automatically).");
        } else if (insecurePort == getInsecurePortDefaultValue()) {
            LOG.info("Server uses standard insecure port {}", insecurePort);
        } else if (insecurePort == getPortDefaultValue()) {
            LOG.warn("Server found insecure port number configured to standard port for secure connections {}", getConfig().getInsecurePort());
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
        return getConfig().getKeyCertOptions() != null;
    }

    /**
     * Checks if this service will bind to the insecure port during startup.
     * <p>
     * Subclasses may override this method in order to do more sophisticated checks.
     *
     * @return {@code true} if the insecure port has been enabled on <em>config</em>.
     */
    protected boolean isInsecurePortEnabled() {
        return getConfig().isInsecurePortEnabled();
    }

    /**
     * Gets the host name or IP address this server's secure port is bound to.
     *
     * @return The address.
     */
    public final String getBindAddress() {
        return getConfig().getBindAddress();
    }

    /**
     * Gets the host name or IP address this server's insecure port is bound to.
     *
     * @return The address.
     */
    public final String getInsecurePortBindAddress() {
        return getConfig().getInsecurePortBindAddress();
    }

    /**
     * Adds TLS trust anchor configuration to a given set of server options.
     * <p>
     * The options for configuring the server side trust anchor are
     * determined by invoking the {@link #getServerTrustOptions()} method.
     * However, the trust anchor options returned by that method will only be added to the
     * given server options if its <em>ssl</em> flag is set to {@code true} and if its
     * <em>trustOptions</em> property is {@code null}.
     * 
     * @param serverOptions The options to add configuration to.
     */
    protected final void addTlsTrustOptions(final NetServerOptions serverOptions) {

        if (serverOptions.isSsl() && serverOptions.getTrustOptions() == null) {

            final TrustOptions trustOptions = getServerTrustOptions();
            if (trustOptions != null) {
                serverOptions.setTrustOptions(trustOptions).setClientAuth(ClientAuth.REQUEST);
                LOG.info("enabling client authentication using certificates [{}]", trustOptions.getClass().getName());
            }
        }
    }

    /**
     * Gets the options for configuring the server side trust anchor.
     * <p>
     * This default implementation returns the options returned by
     * {@link AbstractConfig#getTrustOptions()}.
     * <p>
     * Subclasses may override this method in order to e.g. use a
     * non-key store based trust manager.
     * 
     * @return The trust options or {@code null} if authentication of devices
     *         based on certificates should be disabled.
     */
    protected TrustOptions getServerTrustOptions() {
        return getConfig().getTrustOptions();
    }

    /**
     * Adds TLS key &amp; certificate configuration to a given set of server options.
     * <p>
     * If <em>config</em> contains key &amp; certificate configuration it is added to
     * the given server options and the <em>ssl</em> flag is set to {@code true}.
     * <p>
     * If the server option' ssl flag is set, then the protocols from the <em>disabledTlsVersions</em>
     * configuration property are removed from the options (and thus disabled).
     * <p>
     * Finally, if a working instance of Netty's <em>tcnative</em> library is found, then
     * it is used instead of the JDK's default SSL engine.
     *
     * @param serverOptions The options to add configuration to.
     */
    protected final void addTlsKeyCertOptions(final NetServerOptions serverOptions) {

        final KeyCertOptions keyCertOptions = getConfig().getKeyCertOptions();

        if (keyCertOptions != null) {
            serverOptions.setSsl(true).setKeyCertOptions(keyCertOptions);
        }

        if (serverOptions.isSsl()) {

            final boolean isOpenSslAvailable = OpenSsl.isAvailable();
            final boolean supportsKeyManagerFactory =  OpenSsl.supportsKeyManagerFactory();
            final boolean useOpenSsl =
                    getConfig().isNativeTlsRequired() || (isOpenSslAvailable && supportsKeyManagerFactory);

            LOG.debug("OpenSSL [available: {}, supports KeyManagerFactory: {}]",
                    isOpenSslAvailable, supportsKeyManagerFactory);

            if (useOpenSsl) {
                LOG.info("using OpenSSL [version: {}] instead of JDK's default SSL engine",
                        OpenSsl.versionString());
                serverOptions.setSslEngineOptions(new OpenSSLEngineOptions());
            } else {
                LOG.info("using JDK's default SSL engine");
            }

            serverOptions.getEnabledSecureTransportProtocols()
                .forEach(protocol -> serverOptions.removeEnabledSecureTransportProtocol(protocol));
            getConfig().getSecureProtocols().forEach(protocol -> {
                LOG.info("enabling secure protocol [{}]", protocol);
                serverOptions.addEnabledSecureTransportProtocol(protocol);
            });
        }
    }
}
