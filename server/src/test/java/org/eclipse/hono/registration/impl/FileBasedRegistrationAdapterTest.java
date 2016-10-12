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

import static java.net.HttpURLConnection.*;
import static org.eclipse.hono.util.RegistrationConstants.*;
import static org.mockito.Mockito.*;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

/**
 * Tests {@link FileBasedRegistrationAdapter}.
 */
public class FileBasedRegistrationAdapterTest
{
   private static final String TENANT = "tenant";
   private static final String DEVICE = "device";
   private static FileBasedRegistrationAdapter registrationAdapter;

   @BeforeClass
   public static void setUp() throws Exception {
      final Vertx vertx = mock(Vertx.class);
      final Context ctx = mock(Context.class);
      final EventBus eventBus = mock(EventBus.class);
      when(vertx.eventBus()).thenReturn(eventBus);

      registrationAdapter = new FileBasedRegistrationAdapter();
      registrationAdapter.init(vertx, ctx);
   }

   @After
   public void clear() {
       registrationAdapter.clear();
   }

   @Test
   public void testGetUnknownDeviceReturnsNotFound() {
       processMessageAndExpectResponse(mockMsg(ACTION_GET), getReply(HTTP_NOT_FOUND, TENANT, DEVICE));
   }

   @Test
   public void testDeregisterUnknownDeviceReturnsNotFound() {
       processMessageAndExpectResponse(mockMsg(ACTION_DEREGISTER), getReply(HTTP_NOT_FOUND, TENANT, DEVICE));
   }

   @Test
   public void testProcessRegisterMessageDetectsUnsupportedAction() {
       processMessageAndExpectResponse(mockMsg("bumlux"), getReply(HTTP_BAD_REQUEST, TENANT, DEVICE));
   }

   @Test
   public void testDuplicateRegistrationFails() {
       processMessageAndExpectResponse(mockMsg(ACTION_REGISTER),getReply(HTTP_CREATED, TENANT, DEVICE));
       processMessageAndExpectResponse(mockMsg(ACTION_REGISTER), getReply(HTTP_CONFLICT, TENANT, DEVICE));
   }

   @Test
   public void testGetSucceedsForRegisteredDevice() {
       processMessageAndExpectResponse(mockMsg(ACTION_REGISTER),getReply(HTTP_CREATED, TENANT, DEVICE));
       processMessageAndExpectResponse(mockMsg(ACTION_GET), getReply(HTTP_OK, TENANT, DEVICE, expectedMessage(DEVICE)));
   }

   @Test
   public void testGetFailsForDeregisteredDevice() {
       processMessageAndExpectResponse(mockMsg(ACTION_REGISTER),getReply(HTTP_CREATED, TENANT, DEVICE));
       processMessageAndExpectResponse(mockMsg(ACTION_DEREGISTER), getReply(HTTP_OK, TENANT, DEVICE, expectedMessage(DEVICE)));
       processMessageAndExpectResponse(mockMsg(ACTION_GET), getReply(HTTP_NOT_FOUND, TENANT, DEVICE));
   }

   private static JsonObject expectedMessage(final String id) {
       return new JsonObject().put("id", id).put("data", new JsonObject());
   }

   private static Message<JsonObject> mockMsg(final String action) {
      final JsonObject registrationJson = getRegistrationJson(action, TENANT, DEVICE);
      final Message<JsonObject> message = mock(Message.class);
      when(message.body()).thenReturn(registrationJson);
      return message;
   }

   private static Message<JsonObject> mockMsg(final String action, final String tenant) {
      final JsonObject registrationJson = getRegistrationJson(action, tenant, DEVICE);
      final Message<JsonObject> message = mock(Message.class);
      when(message.body()).thenReturn(registrationJson);
      return message;
   }

   private static void processMessageAndExpectResponse(final Message<JsonObject> request, final JsonObject expectedResponse) {
      registrationAdapter.processRegistrationMessage(request);
      verify(request).reply(expectedResponse);
   }

}