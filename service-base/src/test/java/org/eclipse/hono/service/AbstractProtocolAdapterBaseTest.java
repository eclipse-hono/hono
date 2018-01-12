/**
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonHelper;


/**
 * Tests verifying behavior of {@link AbstractProtocolAdapterBase}.
 *
 */
public class AbstractProtocolAdapterBaseTest {

    private ProtocolAdapterProperties properties;
    private AbstractProtocolAdapterBase<ProtocolAdapterProperties> adapter;
    private RegistrationClient registrationClient;

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Before
    public void setup() {

        registrationClient = mock(RegistrationClient.class);

        HonoClient honoClient = mock(HonoClient.class);
        doAnswer(invocation -> {
            Handler handler = invocation.getArgumentAt(1, Handler.class);
            handler.handle(Future.succeededFuture(registrationClient));
            return null;
        }).when(honoClient).getOrCreateRegistrationClient(anyString(), any(Handler.class));

        properties = new ProtocolAdapterProperties();
        adapter = newProtocolAdapter(properties);
        adapter.setRegistrationServiceClient(honoClient);
    }

    /**
     * Verifies that the registration assertion is set on a downstream message.
     */
    @Test
    public void testAddPropertiesAddsRegistrationAssertion() {

        final Message message = ProtonHelper.message();
        adapter.addProperties(message, newRegistrationAssertionResult("token"));
        assertThat(MessageHelper.getRegistrationAssertion(message), is("token"));
    }

    /**
     * Verifies that the registered default content type is set on a downstream message.
     */
    @Test
    public void testAddPropertiesAddsDefaultContentType() {

        final Message message = ProtonHelper.message();
        adapter.addProperties(message, newRegistrationAssertionResult("token", "application/hono"));
        assertThat(MessageHelper.getRegistrationAssertion(message), is("token"));
        assertThat(message.getContentType(), is("application/hono"));
    }

    /**
     * Verifies that the registered default content type is not set on a downstream message
     * that already contains a content type.
     */
    @Test
    public void testAddPropertiesDoesNotAddDefaultContentType() {

        final Message message = ProtonHelper.message();
        message.setContentType("application/existing");
        adapter.addProperties(message, newRegistrationAssertionResult("token", "application/hono"));
        assertThat(MessageHelper.getRegistrationAssertion(message), is("token"));
        assertThat(message.getContentType(), is("application/existing"));
    }

    /**
     * Verifies that the fall back content type is set on a downstream message
     * if no default has been configured for the device.
     */
    @Test
    public void testAddPropertiesAddsFallbackContentType() {

        final Message message = ProtonHelper.message();
        adapter.addProperties(message, newRegistrationAssertionResult("token"));
        assertThat(MessageHelper.getRegistrationAssertion(message), is("token"));
        assertThat(message.getContentType(), is(AbstractProtocolAdapterBase.CONTENT_TYPE_OCTET_STREAM));
    }

    private AbstractProtocolAdapterBase<ProtocolAdapterProperties> newProtocolAdapter(final ProtocolAdapterProperties props) {


        AbstractProtocolAdapterBase<ProtocolAdapterProperties> result = new AbstractProtocolAdapterBase<ProtocolAdapterProperties>() {

            @Override
            public int getPortDefaultValue() {
                return 0;
            }
            
            @Override
            public int getInsecurePortDefaultValue() {
                return 0;
            }
            
            @Override
            protected int getActualPort() {
                return 0;
            }
            
            @Override
            protected int getActualInsecurePort() {
                return 0;
            }
        };
        result.setConfig(props);
        return result;
    }

    private static JsonObject newRegistrationAssertionResult(final String token) {
        return newRegistrationAssertionResult(token, null);
    }

    private static JsonObject newRegistrationAssertionResult(final String token, final String defaultContentType) {

        JsonObject result = new JsonObject()
                .put(RegistrationConstants.FIELD_ASSERTION, token);
        if (defaultContentType != null) {
            result.put(RegistrationConstants.FIELD_DEFAULTS, new JsonObject()
                    .put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, defaultContentType));
        }
        return result;
    }
}
