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

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.TestSupport;
import org.eclipse.hono.auth.Activity;
import org.eclipse.hono.auth.Authorities;
import org.eclipse.hono.auth.AuthoritiesImpl;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.auth.HonoUserAdapter;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.event.impl.EventEndpoint;
import org.eclipse.hono.service.auth.HonoSaslAuthenticatorFactory;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonHelper;

/**
 * Stand alone integration tests for Hono's Event API.
 *
 */
@RunWith(VertxUnitRunner.class)
public class StandaloneEventApiTest extends AbstractStandaloneApiTest {

    private static HonoMessaging server;

    /**
     * Sets up Hono Messaging service.
     * 
     * @param ctx The test context.
     */
    @BeforeClass
    public static void prepareHonoServer(final TestContext ctx) {

        vertx = Vertx.vertx();
        downstreamAdapter = new MessageDiscardingDownstreamAdapter(vertx);

        final HonoMessagingConfigProperties configProperties = new HonoMessagingConfigProperties();
        configProperties.setInsecurePort(0);

        final EventEndpoint eventEndpoint = new EventEndpoint(vertx);
        eventEndpoint.setMetrics(mock(MessagingMetrics.class));
        eventEndpoint.setEventAdapter(downstreamAdapter);
        eventEndpoint.setRegistrationAssertionValidator(assertionHelper);
        eventEndpoint.setConfiguration(configProperties);

        server = new HonoMessaging();
        server.setSaslAuthenticatorFactory(new HonoSaslAuthenticatorFactory(TestSupport.createAuthenticationService(createUser())));
        server.setConfig(configProperties);
        server.addEndpoint(eventEndpoint);

        Future<String> serverTracker = Future.future();
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
                .addResource(EventConstants.EVENT_ENDPOINT, "*", new Activity[]{ Activity.READ, Activity.WRITE });

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
        return client.getOrCreateEventSender(tenantId);
    }

    @Override
    protected Future<MessageSender> getSender(final String tenantId, final String deviceId) {
        return client.getOrCreateEventSender(tenantId, deviceId);
    }

    /**
     * Verifies that messages that contain a registration assertions which does not match
     * the device identifier are not forwarded downstream.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testMessageWithNonMatchingRegistrationAssertionGetRejected(final TestContext ctx) {

        final String assertion = getAssertion(Constants.DEFAULT_TENANT, "not-" + DEVICE_1);
        final Future<MessageSender> senderTracker = getSender(Constants.DEFAULT_TENANT);

        senderTracker.compose(sender -> {
            return sender.send(DEVICE_1, "payload", "text/plain", assertion);
        }).setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertTrue(ClientErrorException.class.isInstance(t));
            ctx.assertTrue(senderTracker.result().isOpen());
        }));
    }

    /**
     * Verifies that malformed messages are not forwarded downstream.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testMalformedMessageGetsRejected(final TestContext ctx) {

        final Message msg = ProtonHelper.message("malformed");
        msg.setMessageId("malformed-message");
        final Future<MessageSender> senderTracker = getSender(Constants.DEFAULT_TENANT);

        senderTracker.compose(sender -> {
            return sender.send(msg);
        }).setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertTrue(ClientErrorException.class.isInstance(t));
            ctx.assertTrue(senderTracker.result().isOpen());
        }));

    }

    /**
     * Verifies that messages published to a device specific endpoint are not forwarded
     * downstream if the device's identifier does not match the endpoint address.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testMessageOriginatingFromOtherDeviceGetsRejected(final TestContext ctx) {

        final String registrationAssertion = getAssertion(Constants.DEFAULT_TENANT, DEVICE_1);
        final Future<MessageSender> senderTracker = getSender(Constants.DEFAULT_TENANT, "not-" + DEVICE_1);

        senderTracker.compose(sender -> {
            return sender.send(DEVICE_1, "from other device", "text/plain", registrationAssertion);
        }).setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertTrue(ClientErrorException.class.isInstance(t));
            ctx.assertTrue(senderTracker.result().isOpen());
        }));
    }
}
