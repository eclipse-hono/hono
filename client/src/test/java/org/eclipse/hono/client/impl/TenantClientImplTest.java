/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.client.impl;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TenantConstants;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Handler;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;


/**
 * Tests verifying behavior of {@link TenantClientImpl}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class TenantClientImplTest extends AbstractClientUnitTestSupport {

    /**
     * Time out test cases after 5 seconds.
     */
    @Rule
    public Timeout globalTimeout = Timeout.seconds(5000);

    private TenantClientImpl client;

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {

        createMocks();

        final RequestResponseClientConfigProperties config = new RequestResponseClientConfigProperties();
        client = new TenantClientImpl(context, config, sender, receiver);
    }

    /**
     * Verifies that the client includes the required information in the request
     * message sent to the Tenant service.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testGetTenant(final TestContext ctx) {

        // GIVEN an adapter

        // WHEN getting tenant information
        client.get("tenant");

        // THEN the message being sent contains the tenant ID from the created client as application property
        // and the passed tenant to the get operation is ignored
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), any(Handler.class));
        final Message sentMessage = messageCaptor.getValue();
        assertThat(MessageHelper.getTenantId(sentMessage), is("tenant"));
        assertThat(sentMessage.getSubject(), is(TenantConstants.StandardAction.ACTION_GET.toString()));
    }

    /**
     * Verifies that the client uses the correct message id prefix defined for the tenant client.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testMessageIdPrefixStartsWithApiSpecificPrefix(final TestContext ctx) {

        // GIVEN an adapter

        // WHEN getting tenant information
        client.get("tenant");

        // THEN the message being sent uses the correct message id prefix
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), any(Handler.class));
        final Message sentMessage = messageCaptor.getValue();
        assertThat(sentMessage.getMessageId().toString(), startsWith(TenantConstants.MESSAGE_ID_PREFIX));
    }

}
