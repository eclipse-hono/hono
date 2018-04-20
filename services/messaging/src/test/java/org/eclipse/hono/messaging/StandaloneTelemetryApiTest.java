/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.messaging;

import static org.mockito.Mockito.mock;

import org.eclipse.hono.TestSupport;
import org.eclipse.hono.auth.Activity;
import org.eclipse.hono.auth.Authorities;
import org.eclipse.hono.auth.AuthoritiesImpl;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.auth.HonoUserAdapter;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.service.auth.HonoSaslAuthenticatorFactory;
import org.eclipse.hono.telemetry.impl.TelemetryEndpoint;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonClientOptions;

/**
 * Stand alone integration tests for Hono's Telemetry API.
 *
 */
@RunWith(VertxUnitRunner.class)
public class StandaloneTelemetryApiTest extends AbstractStandaloneApiTest {

    private static HonoMessaging server;

    /**
     * Sets up the fixture common to all test cases.
     * 
     * @param ctx The vert.x test context.
     */
    @BeforeClass
    public static void prepareHonoServer(final TestContext ctx) {

        vertx = Vertx.vertx();
        downstreamAdapter = new MessageDiscardingDownstreamAdapter(vertx);

        final HonoMessagingConfigProperties configProperties = new HonoMessagingConfigProperties();
        configProperties.setInsecurePort(0);

        final TelemetryEndpoint telemetryEndpoint = new TelemetryEndpoint(vertx);
        telemetryEndpoint.setMetrics(mock(MessagingMetrics.class));
        telemetryEndpoint.setTelemetryAdapter(downstreamAdapter);
        telemetryEndpoint.setRegistrationAssertionValidator(assertionHelper);
        telemetryEndpoint.setConfiguration(configProperties);

        server = new HonoMessaging();
        server.setSaslAuthenticatorFactory(new HonoSaslAuthenticatorFactory(TestSupport.createAuthenticationService(createUser())));
        server.setConfig(configProperties);
        server.addEndpoint(telemetryEndpoint);

        final Future<String> serverTracker = Future.future();
        vertx.deployVerticle(server, serverTracker.completer());

        serverTracker.compose(s -> {
            final ClientConfigProperties clientProps = new ClientConfigProperties();
            clientProps.setName("test");
            clientProps.setHost(server.getInsecurePortBindAddress());
            clientProps.setPort(server.getInsecurePort());
            clientProps.setUsername(USER);
            clientProps.setPassword(PWD);
            client = new HonoClientImpl(vertx, clientProps);
            return client.connect(new ProtonClientOptions());
        }).setHandler(ctx.asyncAssertSuccess());
    }

    /**
     * Creates a Hono user containing all authorities required for running this test class.
     * 
     * @return The user.
     */
    private static HonoUser createUser() {

        final Authorities authorities = new AuthoritiesImpl()
                .addResource(TelemetryConstants.TELEMETRY_ENDPOINT, "*", new Activity[]{ Activity.READ, Activity.WRITE });

        return new HonoUserAdapter() {
            @Override
            public String getName() {
                return "test-client";
            }

            @Override
            public Authorities getAuthorities() {
                return authorities;
            }
        };
    }

    @Override
    protected Future<MessageSender> getSender(final String tenantId) {
        return client.getOrCreateTelemetrySender(tenantId);
    }

    @Override
    protected Future<MessageSender> getSender(final String tenantId, final String deviceId) {
        return client.getOrCreateTelemetrySender(tenantId, deviceId);
    }
}
