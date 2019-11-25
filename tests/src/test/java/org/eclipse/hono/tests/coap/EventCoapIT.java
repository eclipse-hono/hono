/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.tests.coap;

import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.util.EventConstants;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;


/**
 * Integration tests for uploading telemetry data to the CoAP adapter.
 *
 */
@ExtendWith(VertxExtension.class)
public class EventCoapIT extends CoapTestBase {

    private static final String POST_URI = "/" + EventConstants.EVENT_ENDPOINT;
    private static final String PUT_URI_TEMPLATE = POST_URI + "/%s/%s";

    @Override
    protected Future<MessageConsumer> createConsumer(final String tenantId, final Consumer<Message> messageConsumer) {

        return helper.applicationClientFactory.createEventConsumer(tenantId, messageConsumer, remoteClose -> {});
    }

    @Override
    protected String getPutResource(final String tenant, final String deviceId) {
        return String.format(PUT_URI_TEMPLATE, tenant, deviceId);
    }

    @Override
    protected String getPostResource() {
        return POST_URI;
    }

    @Override
    protected Type getMessageType() {
        return Type.CON;
    }
}
