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

package org.eclipse.hono.client.impl;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.message.Message;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;


/**
 * Tests verifying behavior of {@link AbstractHonoClient}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class AbstractHonoClientTest {

    /**
     * Time out each test after five seconds.
     */
    @Rule
    public final Timeout timeout = Timeout.seconds(5);

    /**
     * Verifies that the given application properties are propagated to
     * the message.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testApplicationPropertiesAreSetAtTheMessage(final TestContext ctx) {

        final Message msg = mock(Message.class);
        final Map<String, Object> applicationProps = new HashMap<>();
        applicationProps.put("string-key", "value");
        applicationProps.put("int-key", 15);
        applicationProps.put("long-key", 1000L);

        final ArgumentCaptor<ApplicationProperties> applicationPropsCaptor = ArgumentCaptor.forClass(ApplicationProperties.class);

        AbstractHonoClient.setApplicationProperties(msg, applicationProps);

        verify(msg).setApplicationProperties(applicationPropsCaptor.capture());
        assertThat(applicationPropsCaptor.getValue().getValue(), is(applicationProps));
    }
}
