/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.ConfigurationSupportingVerticle;
import org.eclipse.hono.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.handler.ssl.OpenSsl;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Future;
import io.vertx.core.Promise;
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
    protected final Logger log = LoggerFactory.getLogger(getClass());

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
    public final void setTracer(final Tracer opentracingTracer) {
        this.tracer = Objects.requireNonNull(opentracingTracer);
    }

    /**
     * Sets the health check server for this application.
     *
     * @param healthCheckServer The health check server.
     * @throws NullPointerException if healthCheckServer is {@code null}.
     */
    public void setHealthCheckServer(final HealthCheckServer healthCheckServer) {
        this.healthCheckServer = Objects.requireNonNull(healthCheckServer);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Registers this service as a health check provider and then
     * completes the given promise based on the outcome of {@link #startInternal()}.
     */
    @Override
    public final void start(final Promise<Void> startPromise) {
        healthCheckServer.registerHealthCheckResources(this);
        startInternal().onComplete(startPromise);
    }

    /**
     * Subclasses should override this method to perform any work required on start-up of this protocol component.
     * <p>
     * This method is invoked by {@link #start(Promise)} as part of the startup process.
     *
     * @return A future indicating the outcome of the startup. If the returned future fails, this component will not start up.
     */
    protected Future<Void> startInternal() {
        // should be overridden by subclasses
        return Future.succeededFuture();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Completes the given promise based on the outcome of {@link #stopInternal()}.
     */
    @Override
    public final void stop(final Promise<Void> stopPromise) {
        stopInternal().onComplete(stopPromise);
    }

    /**
     * Subclasses should override this method to perform any work required before shutting down this component.
     * <p>
     * This method is invoked by {@link #stop(Promise)} as part of the shutdown process.
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
    }

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
            log.info("vert.x uses native transport: {}", vertx.isNativeTransportEnabled());
        }

        final Promise<Void> result = Promise.promise();

        if (getConfig().getKeyCertOptions() == null) {
            if (getConfig().getPort() >= 0) {
                log.warn("secure port number set but no key/certificate configured, secure port will not be opened");
            }
            if (!getConfig().isInsecurePortEnabled()) {
                log.error("configuration must have at least one of key & certificate or insecure port set to start up");
                result.fail("no ports configured");
            } else {
                result.complete();
            }
        } else if (getConfig().isInsecurePortEnabled()) {
            if (getConfig().getPort(getPortDefaultValue()) == getConfig().getInsecurePort(getInsecurePortDefaultValue())) {
                log.error("secure and insecure ports must be configured to bind to different port numbers");
                result.fail("secure and insecure ports configured to bind to same port number");
            } else {
                result.complete();
            }
        } else {
            result.complete();
        }

        return result.future();
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
            log.info("Server uses secure standard port {}", port);
        } else if (port == 0) {
            log.info("Server found secure port number configured for ephemeral port selection (port chosen automatically).");
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
            log.info("Server found insecure port number configured for ephemeral port selection (port chosen automatically).");
        } else if (insecurePort == getInsecurePortDefaultValue()) {
            log.info("Server uses standard insecure port {}", insecurePort);
        } else if (insecurePort == getPortDefaultValue()) {
            log.warn("Server found insecure port number configured to standard port for secure connections {}", getConfig().getInsecurePort());
            log.warn("Possibly misconfigured?");
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
                log.info("enabling client authentication using certificates [{}]", trustOptions.getClass().getName());
            }
        }
    }

    /**
     * Gets the options for configuring the server side trust anchor.
     * <p>
     * This default implementation returns the options returned by
     * {@link org.eclipse.hono.config.AbstractConfig#getTrustOptions()}.
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
     * If the server option's ssl flag is set, then the protocols from the <em>disabledTlsVersions</em>
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

            log.debug("OpenSSL [available: {}, supports KeyManagerFactory: {}]",
                    isOpenSslAvailable, supportsKeyManagerFactory);

            if (useOpenSsl) {
                log.info("using OpenSSL [version: {}] instead of JDK's default SSL engine",
                        OpenSsl.versionString());
                serverOptions.setSslEngineOptions(new OpenSSLEngineOptions());
            } else {
                log.info("using JDK's default SSL engine");
            }

            // it is important to use a sorted set here to maintain
            // the list's order
            final Set<String> protocols = new LinkedHashSet<>(getConfig().getSecureProtocols().size());
            getConfig().getSecureProtocols().forEach(p -> {
                log.info("enabling secure protocol [{}]", p);
                protocols.add(p);
            });
            serverOptions.setEnabledSecureTransportProtocols(protocols);

            getConfig().getSupportedCipherSuites()
                .forEach(suiteName -> {
                    log.info("adding supported cipher suite [{}]", suiteName);
                    serverOptions.addEnabledCipherSuite(suiteName);
                });

            serverOptions.setSni(getConfig().isSni());
            log.info("Service supports TLS ServerNameIndication: {}", getConfig().isSni());
        }
    }
}
