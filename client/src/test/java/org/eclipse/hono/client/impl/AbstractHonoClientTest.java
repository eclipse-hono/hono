/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import static com.google.common.truth.Truth.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.message.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;


/**
 * Tests verifying behavior of {@link AbstractHonoClient}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class AbstractHonoClientTest {

    /**
     * Verifies that the given application properties are propagated to
     * the message.
     */
    @Test
    public void testApplicationPropertiesAreSetAtTheMessage() {

        final Message msg = mock(Message.class);
        final Map<String, Object> applicationProps = new HashMap<>();
        applicationProps.put("string-key", "value");
        applicationProps.put("int-key", 15);
        applicationProps.put("long-key", 1000L);

        final ArgumentCaptor<ApplicationProperties> applicationPropsCaptor = ArgumentCaptor.forClass(ApplicationProperties.class);

        AbstractHonoClient.setApplicationProperties(msg, applicationProps);

        verify(msg).setApplicationProperties(applicationPropsCaptor.capture());
        assertThat(applicationPropsCaptor.getValue().getValue()).isEqualTo(applicationProps);
    }
}
