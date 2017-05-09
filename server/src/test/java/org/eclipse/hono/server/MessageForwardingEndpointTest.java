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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.service.amqp.UpstreamReceiver;
import org.eclipse.hono.service.registration.RegistrationAssertionHelper;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
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

    private Vertx vertx = Vertx.vertx();

    /**
     * Verifies that a message containing a matching registration assertion is
     * forwarded to the downstream adapter.
     */
    @Test
    public void testForwardMessageAcceptsCorrectRegistrationAssertion() {

        UpstreamReceiver client = mock(UpstreamReceiver.class);
        ProtonDelivery delivery = mock(ProtonDelivery.class);
        DownstreamAdapter adapter = mock(DownstreamAdapter.class);
        MessageForwardingEndpoint endpoint = getEndpoint();
        endpoint.setRegistrationServiceSecret("secret-one");
        endpoint.setDownstreamAdapter(adapter);

        String invalidToken = getToken("secret-one", "tenant", "4711");
        Message msg = ProtonHelper.message();
        MessageHelper.addProperty(msg, MessageHelper.APP_PROPERTY_REGISTRATION_ASSERTION, invalidToken);
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

        UpstreamReceiver client = mock(UpstreamReceiver.class);
        ProtonDelivery delivery = mock(ProtonDelivery.class);
        MessageForwardingEndpoint endpoint = getEndpoint();
        endpoint.setRegistrationServiceSecret("secret");

        String invalidToken = getToken("secret", "wrong-tenant", "4711");
        Message msg = ProtonHelper.message();
        MessageHelper.addRegistrationAssertion(msg, invalidToken);
        MessageHelper.addAnnotation(msg, MessageHelper.APP_PROPERTY_RESOURCE, "telemetry/tenant/4711");
        endpoint.forwardMessage(client, delivery, msg);

        verify(delivery).disposition(any(Rejected.class), anyBoolean());
        verify(client).close(any(ErrorCondition.class));
    }

    private MessageForwardingEndpoint getEndpoint() {

        return new MessageForwardingEndpoint(vertx) {

            @Override
            public String getName() {
                return "test";
            }

            @Override
            protected boolean passesFormalVerification(ResourceIdentifier targetAddress, Message message) {
                return true;
            }

            @Override
            protected ProtonQoS getEndpointQos() {
                return ProtonQoS.AT_MOST_ONCE;
            }
        };
    }

    private String getToken(final String secret, final String tenantId, final String deviceId) {

        return new RegistrationAssertionHelper(secret).getAssertion(tenantId, deviceId, 10);
    }
}
