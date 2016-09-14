/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.registration.impl;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.eclipse.hono.util.RegistrationConstants.ACTION_DEREGISTER;
import static org.eclipse.hono.util.RegistrationConstants.ACTION_GET;
import static org.eclipse.hono.util.RegistrationConstants.ACTION_REGISTER;
import static org.eclipse.hono.util.RegistrationConstants.getRegistrationJson;
import static org.eclipse.hono.util.RegistrationConstants.getReply;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

/**
 * Tests {@link InMemoryRegistrationAdapter}.
 */
public class InMemoryRegistrationAdapterTest
{
   public static final String TENANT = "tenant";
   public static final String TENANT_2 = "tenant2";
   public static final String DEVICE = "device";
   private InMemoryRegistrationAdapter registrationAdapter;

   @Before
   public void setUp() throws Exception {
      final Vertx vertx = mock(Vertx.class);
      final Context ctx = mock(Context.class);
      final EventBus eventBus = mock(EventBus.class);
      when(vertx.eventBus()).thenReturn(eventBus);

      registrationAdapter = new InMemoryRegistrationAdapter();
      registrationAdapter.init(vertx, ctx);
   }

   @Test
   public void testProcessRegisterMessage() throws Exception {
      processMessageAndExpectResponse(mockMsg(ACTION_GET), getReply(HTTP_NOT_FOUND, TENANT, DEVICE));
      processMessageAndExpectResponse(mockMsg("bumlux"), getReply(HTTP_BAD_REQUEST, TENANT, DEVICE));
      processMessageAndExpectResponse(mockMsg(ACTION_DEREGISTER), getReply(HTTP_NOT_FOUND, TENANT, DEVICE));
      processMessageAndExpectResponse(mockMsg(ACTION_REGISTER),getReply(HTTP_OK, TENANT, DEVICE));
      processMessageAndExpectResponse(mockMsg(ACTION_REGISTER), getReply(HTTP_CONFLICT, TENANT, DEVICE));
      processMessageAndExpectResponse(mockMsg(ACTION_GET), getReply(HTTP_OK, TENANT, DEVICE));
      processMessageAndExpectResponse(mockMsg(ACTION_DEREGISTER), getReply(HTTP_OK, TENANT, DEVICE));
      processMessageAndExpectResponse(mockMsg(ACTION_GET), getReply(HTTP_NOT_FOUND, TENANT, DEVICE));
      processMessageAndExpectResponse(mockMsg(ACTION_REGISTER), getReply(HTTP_OK, TENANT, DEVICE));
      processMessageAndExpectResponse(mockMsg(ACTION_REGISTER, TENANT_2), getReply(HTTP_OK, TENANT_2, DEVICE));
      processMessageAndExpectResponse(mockMsg(ACTION_GET, TENANT_2), getReply(HTTP_OK, TENANT_2, DEVICE));
   }

   private Message<JsonObject> mockMsg(final String action) {
      final JsonObject registrationJson = getRegistrationJson(action, TENANT, DEVICE);
      final Message<JsonObject> message = mock(Message.class);
      when(message.body()).thenReturn(registrationJson);
      return message;
   }

   private Message<JsonObject> mockMsg(final String action, final String tenant) {
      final JsonObject registrationJson = getRegistrationJson(action, tenant, DEVICE);
      final Message<JsonObject> message = mock(Message.class);
      when(message.body()).thenReturn(registrationJson);
      return message;
   }

   private void processMessageAndExpectResponse(final Message<JsonObject> request, final JsonObject expectedResponse) {
      registrationAdapter.processRegistrationMessage(request);
      verify(request).reply(expectedResponse);
   }

}