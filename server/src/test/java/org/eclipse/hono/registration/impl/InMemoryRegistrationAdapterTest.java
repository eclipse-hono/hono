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
import static org.eclipse.hono.registration.RegistrationConstants.ACTION_DEREGISTER;
import static org.eclipse.hono.registration.RegistrationConstants.ACTION_GET;
import static org.eclipse.hono.registration.RegistrationConstants.ACTION_REGISTER;
import static org.eclipse.hono.registration.RegistrationConstants.getRegistrationJson;
import static org.eclipse.hono.registration.RegistrationConstants.getReply;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;

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
   private final AtomicInteger cnt = new AtomicInteger(0);
   private InMemoryRegistrationAdapter registrationAdapter;

   @Before
   public void setUp() throws Exception
   {
      final Vertx vertx = mock(Vertx.class);
      final Context ctx = mock(Context.class);
      final EventBus eventBus = mock(EventBus.class);
      when(vertx.eventBus()).thenReturn(eventBus);

      registrationAdapter = new InMemoryRegistrationAdapter();
      registrationAdapter.init(vertx, ctx);
   }

   @Test
   public void testProcessRegisterMessage() throws Exception
   {
      processMessageAndExpectResponse(mockMsg(ACTION_GET), getReply(HTTP_NOT_FOUND, "0", TENANT, DEVICE, "reply-0"));
      processMessageAndExpectResponse(mockMsg("bumlux"), getReply(HTTP_BAD_REQUEST, "1", TENANT, DEVICE, "reply-1"));
      processMessageAndExpectResponse(mockMsg(ACTION_DEREGISTER), getReply(HTTP_NOT_FOUND, "2", TENANT, DEVICE, "reply-2"));
      processMessageAndExpectResponse(mockMsg(ACTION_REGISTER),getReply(HTTP_OK, "3", TENANT, DEVICE, "reply-3"));
      processMessageAndExpectResponse(mockMsg(ACTION_REGISTER), getReply(HTTP_CONFLICT, "4", TENANT, DEVICE, "reply-4"));
      processMessageAndExpectResponse(mockMsg(ACTION_GET), getReply(HTTP_OK, "5", TENANT, DEVICE, "reply-5"));
      processMessageAndExpectResponse(mockMsg(ACTION_DEREGISTER), getReply(HTTP_OK, "6", TENANT, DEVICE, "reply-6"));
      processMessageAndExpectResponse(mockMsg(ACTION_GET), getReply(HTTP_NOT_FOUND, "7", TENANT, DEVICE, "reply-7"));
      processMessageAndExpectResponse(mockMsg(ACTION_REGISTER), getReply(HTTP_OK, "8", TENANT, DEVICE, "reply-8"));
      processMessageAndExpectResponse(mockMsg(ACTION_REGISTER, TENANT_2), getReply(HTTP_OK, "9", TENANT_2, DEVICE, "reply-9"));
      processMessageAndExpectResponse(mockMsg(ACTION_GET, TENANT_2), getReply(HTTP_OK, "10", TENANT_2, DEVICE, "reply-10"));
   }

   private Message<JsonObject> mockMsg(final String action) {
      final String messageId = "" + cnt.getAndIncrement();
      final JsonObject registrationJson = getRegistrationJson(action, messageId, TENANT, DEVICE);
      final Message<JsonObject> message = mock(Message.class);
      when(message.body()).thenReturn(registrationJson);
      return message;
   }

   private Message<JsonObject> mockMsg(final String action, final String tenant) {

      final String messageId = "" + cnt.getAndIncrement();
      final JsonObject registrationJson = getRegistrationJson(action, messageId, tenant, DEVICE);
      final Message<JsonObject> message = mock(Message.class);
      when(message.body()).thenReturn(registrationJson);
      return message;
   }

   private void processMessageAndExpectResponse(final Message<JsonObject> request, final JsonObject expectedResponse)
   {
      registrationAdapter.processRegistrationMessage(request);
      verify(request).reply(expectedResponse);
   }

}