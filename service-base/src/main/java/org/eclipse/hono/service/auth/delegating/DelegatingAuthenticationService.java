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

package org.eclipse.hono.service.auth.delegating;

import java.util.Objects;

import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.client.impl.AuthenticationServerClient;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.service.HealthCheckProvider;
import org.eclipse.hono.service.auth.AbstractHonoAuthenticationService;
import org.eclipse.hono.util.AuthenticationConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.dns.DnsClient;
import io.vertx.core.impl.VertxInternal;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;


/**
 * An authentication service that delegates authentication requests to a remote identity server.
 * <p>
 * This is the default authentication service for all Hono services.
 */
@Service
@Profile("!authentication-impl")
public class DelegatingAuthenticationService extends AbstractHonoAuthenticationService<AuthenticationServerClientConfigProperties> implements HealthCheckProvider {

    private AuthenticationServerClient client;
    private ConnectionFactory factory;
    private DnsClient dnsClient;

    @Autowired
    @Override
    public void setConfig(final AuthenticationServerClientConfigProperties configuration) {
        setSpecificConfig(configuration);
    }

    /**
     * Sets the DNS client to use for checking availability of an <em>Authentication</em> service.
     * <p>
     * If not set, the vert.x instance's address resolver is used instead.
     *
     * @param dnsClient The client.
     * @throws NullPointerException if client is {@code null}.
     */
    @Autowired(required = false)
    public void setDnsClient(final DnsClient dnsClient) {
        this.dnsClient = Objects.requireNonNull(dnsClient);
    }

    /**
     * Sets the factory to use for connecting to the authentication server.
     * 
     * @param connectionFactory The factory.
     * @throws NullPointerException if the factory is {@code null}.
     */
    @Autowired
    public void setConnectionFactory(@Qualifier(AuthenticationConstants.QUALIFIER_AUTHENTICATION) final ConnectionFactory connectionFactory) {
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
     * Registers a check which succeeds if a connection with the configured <em>Authentication</em> service can be established.
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
                        log.debug("readiness check failed to resolve Authentication service address [{}]: ",
                                getConfig().getHost(), lookupAttempt.cause().getMessage());
                        status.tryComplete(Status.KO());
                    }
                });
            });
        } else if (VertxInternal.class.isInstance(vertx)) {
            log.info("registering readiness check using vert.x Address Resolver");
            readinessHandler.register("authentication-service-availability", status -> {
                log.trace("checking availability of Authentication service");
                ((VertxInternal) vertx).resolveAddress(getConfig().getHost(), lookupAttempt -> {
                    if (lookupAttempt.succeeded()) {
                        status.tryComplete(Status.OK());
                    } else {
                        log.debug("readiness check failed to resolve Authentication service address [{}]: ",
                                getConfig().getHost(), lookupAttempt.cause().getMessage());
                        status.tryComplete(Status.KO());
                    };
                });
            });
        } else {
            log.warn("cannot register readiness check, no DNS resolver available");
        }
    }

    @Override
    protected void doStart(final Future<Void> startFuture) {
        if (factory == null) {
            startFuture.fail("no connection factory for Authentication service set");
        } else {
            client = new AuthenticationServerClient(vertx, factory);
            startFuture.complete();
        }
    }

    @Override
    public void verifyExternal(final String authzid, final String subjectDn, final Handler<AsyncResult<HonoUser>> authenticationResultHandler) {
        client.verifyExternal(authzid, subjectDn, authenticationResultHandler);
    }

    @Override
    public void verifyPlain(final String authzid, final String authcid, final String password,
            final Handler<AsyncResult<HonoUser>> authenticationResultHandler) {

        client.verifyPlain(authzid, authcid, password, authenticationResultHandler);
    }

    @Override
    public String toString() {
        return new StringBuilder(DelegatingAuthenticationService.class.getSimpleName())
                .append("[Authentication service: ").append(getConfig().getHost()).append(":").append(getConfig().getPort()).append("]")
                .toString();
    }
}
