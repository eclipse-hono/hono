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
package org.eclipse.hono.server;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.amqp.UpstreamReceiver;
import org.eclipse.hono.service.registration.RegistrationAssertionHelper;
import org.eclipse.hono.service.registration.RegistrationAssertionHelperImpl;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.Vertx;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;

/**
 * Verifies behavior of the {@link MessageForwardingEndpoint}.
 *
 */
public class MessageForwardingEndpointTest {

    private static final String SECRET = "hfguisdauifsuifhwebfjkhsdfuigsdafigsdaozfgaDSBCMBGQ";
    private Vertx vertx = Vertx.vertx();
    private RegistrationAssertionHelper tokenValidator;

    /**
     * Initializes shared properties.
     */
    @Before
    public void init() {
        tokenValidator = mock(RegistrationAssertionHelper.class);
    }

    /**
     * Verifies that a message containing a matching registration assertion is
     * forwarded to the downstream adapter.
     */
    @Test
    public void testForwardMessageAcceptsCorrectRegistrationAssertion() {

        final String validToken = getToken(SECRET, "tenant", "4711");
        UpstreamReceiver client = mock(UpstreamReceiver.class);
        ProtonDelivery delivery = mock(ProtonDelivery.class);
        DownstreamAdapter adapter = mock(DownstreamAdapter.class);
        when(tokenValidator.isValid(validToken, "tenant", "4711")).thenReturn(Boolean.TRUE);
        MessageForwardingEndpoint<ServiceConfigProperties> endpoint = getEndpoint();
        endpoint.setRegistrationAssertionValidator(tokenValidator);
        endpoint.setDownstreamAdapter(adapter);

        Message msg = ProtonHelper.message();
        MessageHelper.addProperty(msg, MessageHelper.APP_PROPERTY_REGISTRATION_ASSERTION, validToken);
        MessageHelper.addAnnotation(msg, MessageHelper.APP_PROPERTY_RESOURCE, "telemetry/tenant/4711");
        endpoint.forwardMessage(client, delivery, msg);

        verify(adapter).processMessage(client, delivery, msg);
        verify(client, times(0)).close(any(ErrorCondition.class));
    }

    /**
     * Verifies that a message containing a registration assertion for a tenant
     * other than the one from the message's target address is rejected.
     */
    @Test
    public void testProcessMessageRejectsRegistrationAssertionForWrongTenant() {

        final String invalidToken = getToken(SECRET, "wrong-tenant", "4711");
        UpstreamReceiver client = mock(UpstreamReceiver.class);
        ProtonDelivery delivery = mock(ProtonDelivery.class);
        when(tokenValidator.isValid(invalidToken, "tenant", "4711")).thenReturn(Boolean.FALSE);
        MessageForwardingEndpoint<ServiceConfigProperties> endpoint = getEndpoint();
        endpoint.setRegistrationAssertionValidator(tokenValidator);

        Message msg = ProtonHelper.message();
        MessageHelper.addRegistrationAssertion(msg, invalidToken);
        MessageHelper.addAnnotation(msg, MessageHelper.APP_PROPERTY_RESOURCE, "telemetry/tenant/4711");
        endpoint.forwardMessage(client, delivery, msg);

        verify(delivery).disposition(any(Rejected.class), anyBoolean());
        verify(client).close(any(ErrorCondition.class));
    }

    private MessageForwardingEndpoint<ServiceConfigProperties> getEndpoint() {

        return new MessageForwardingEndpoint<ServiceConfigProperties>(vertx) {

            @Override
            public String getName() {
                return "test";
            }

            @Override
            protected boolean passesFormalVerification(ResourceIdentifier targetAddress, Message message) {
                return true;
            }

            @Override
            protected ProtonQoS[] getEndpointQos() {
                return new ProtonQoS[]{ ProtonQoS.AT_MOST_ONCE };
            }
        };
    }

    private String getToken(final String secret, final String tenantId, final String deviceId) {

        return RegistrationAssertionHelperImpl.forSharedSecret(secret, 10).getAssertion(tenantId, deviceId);
    }
}
