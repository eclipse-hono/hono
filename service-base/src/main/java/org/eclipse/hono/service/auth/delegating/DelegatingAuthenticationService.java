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

package org.eclipse.hono.service.auth.delegating;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.client.amqp.connection.ConnectionFactory;
import org.eclipse.hono.service.HealthCheckProvider;
import org.eclipse.hono.service.auth.AbstractHonoAuthenticationService;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.dns.DnsClient;
import io.vertx.core.impl.VertxInternal;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;


/**
 * An authentication service that delegates authentication requests to a remote identity server.
 * <p>
 * This is the default authentication service for all Hono services.
 */
public class DelegatingAuthenticationService
    extends AbstractHonoAuthenticationService<AuthenticationServerClientConfigProperties>
    implements HealthCheckProvider {

    private AuthenticationServerClient client;
    private ConnectionFactory factory;
    private DnsClient dnsClient;

    /**
     * Gets the supported SASL mechanisms from the service configuration. If no configuration is set, the
     * mechanisms EXTERNAL and PLAIN (in that order) are returned.
     *
     * @return The supported SASL mechanisms.
     */
    @Override
    public final String[] getSupportedSaslMechanisms() {
        return Optional.ofNullable(getConfig())
                .map(config -> config.getSupportedSaslMechanisms().toArray(new String[0]))
                .orElse(DEFAULT_SASL_MECHANISMS);
    }

    /**
     * Sets the DNS client to use for checking availability of an <em>Authentication</em> service.
     * <p>
     * If not set, the vert.x instance's address resolver is used instead.
     *
     * @param dnsClient The client.
     * @throws NullPointerException if client is {@code null}.
     */
    public void setDnsClient(final DnsClient dnsClient) {
        this.dnsClient = Objects.requireNonNull(dnsClient);
    }

    /**
     * Sets the factory to use for connecting to the authentication server.
     *
     * @param connectionFactory The factory.
     * @throws NullPointerException if the factory is {@code null}.
     */
    public void setConnectionFactory(final ConnectionFactory connectionFactory) {
        this.factory = Objects.requireNonNull(connectionFactory);
    }

    /**
     * This method does not register any specific liveness checks.
     */
    @Override
    public void registerLivenessChecks(final HealthCheckHandler livenessHandler) {
        // do not register any specific checks
    }

    /**
     * Registers a check which succeeds if the configured <em>Authentication</em> service host name
     * can be resolved via DNS.
     * <p>
     * If no DNS resolver is available, then no check will be registered.
     *
     * @param readinessHandler The health check handler to register the checks with.
     */
    @Override
    public void registerReadinessChecks(final HealthCheckHandler readinessHandler) {

        if (dnsClient != null) {
            log.info("registering readiness check using DNS Client");
            readinessHandler.register("authentication-service-availability", status -> {
                log.trace("checking availability of Authentication service");
                dnsClient.lookup(getConfig().getHost(), lookupAttempt -> {
                    if (lookupAttempt.succeeded()) {
                        status.tryComplete(Status.OK());
                    } else {
                        log.debug("readiness check failed to resolve Authentication service address [{}]: {}",
                                getConfig().getHost(), lookupAttempt.cause().getMessage());
                        status.tryComplete(Status.KO());
                    }
                });
            });
        } else if (vertx instanceof VertxInternal vertxInternal) {
            log.info("registering readiness check using vert.x Address Resolver");
            readinessHandler.register("authentication-service-availability", status -> {
                log.trace("checking availability of Authentication service");
                vertxInternal.resolveAddress(getConfig().getHost(), lookupAttempt -> {
                    if (lookupAttempt.succeeded()) {
                        status.tryComplete(Status.OK());
                    } else {
                        log.debug("readiness check failed to resolve Authentication service address [{}]: {}",
                                getConfig().getHost(), lookupAttempt.cause().getMessage());
                        status.tryComplete(Status.KO());
                    }
                });
            });
        } else {
            log.warn("cannot register readiness check, no DNS resolver available");
        }
    }

    @Override
    protected final void doStart(final Promise<Void> startFuture) {

        if (factory == null) {
            startFuture.fail(new IllegalStateException("no connection factory for Authentication service set"));
        } else {
            client = new AuthenticationServerClient(vertx, factory);
            if (log.isInfoEnabled()) {
                final String saslMechanisms = getConfig().getSupportedSaslMechanisms().stream()
                        .collect(Collectors.joining(", "));
                log.info("starting {} with support for SASL mechanisms: {}", this, saslMechanisms);
            }
            startFuture.complete();
        }
    }

    @Override
    public Future<HonoUser> verifyExternal(final String authzid, final String subjectDn) {
        return client.verifyExternal(authzid, subjectDn);
    }

    @Override
    public Future<HonoUser> verifyPlain(final String authzid, final String authcid, final String password) {
        return client.verifyPlain(authzid, authcid, password);
    }

    @Override
    public String toString() {
        return new StringBuilder(getClass().getSimpleName())
                .append(" [")
                .append("Authentication service: ").append(getConfig().getHost()).append(":").append(getConfig().getPort())
                .append("]")
                .toString();
    }
}
