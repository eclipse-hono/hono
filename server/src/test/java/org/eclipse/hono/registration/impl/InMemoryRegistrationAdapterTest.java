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
import static org.eclipse.hono.registration.RegistrationConstants.EVENT_BUS_ADDRESS_REGISTRATION_REPLY;
import static org.eclipse.hono.registration.RegistrationConstants.getReply;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;

/**
 * Tests {@link InMemoryRegistrationAdapter}.
 */
public class InMemoryRegistrationAdapterTest
{

   public static final String MSG_ID = "1234";
   public static final String TENANT = "tenant";
   public static final String TENANT_2 = "tenant2";
   public static final String DEVICE = "device";
   private InMemoryRegistrationAdapter registrationAdapter;
   private EventBus eventBus;

   @Before
   public void setUp() throws Exception
   {
      final Vertx vertx = mock(Vertx.class);
      final Context ctx = mock(Context.class);
      eventBus = mock(EventBus.class);
      when(vertx.eventBus()).thenReturn(eventBus);

      registrationAdapter = new InMemoryRegistrationAdapter();
      registrationAdapter.init(vertx, ctx);
   }

   @Test
   public void testProcessRegisterMessage() throws Exception
   {
      registrationAdapter.processRegistrationMessage(TENANT, DEVICE, ACTION_GET, "0");
      registrationAdapter.processRegistrationMessage(TENANT, DEVICE, "bumlux", "1");
      registrationAdapter.processRegistrationMessage(TENANT, DEVICE, ACTION_DEREGISTER, "2");

      registrationAdapter.processRegistrationMessage(TENANT, DEVICE, ACTION_REGISTER, "3");
      registrationAdapter.processRegistrationMessage(TENANT, DEVICE, ACTION_REGISTER, "4");
      registrationAdapter.processRegistrationMessage(TENANT, DEVICE, ACTION_GET, "5");
      registrationAdapter.processRegistrationMessage(TENANT, DEVICE, ACTION_DEREGISTER, "6");
      registrationAdapter.processRegistrationMessage(TENANT, DEVICE, ACTION_GET, "7");

      registrationAdapter.processRegistrationMessage(TENANT, DEVICE, ACTION_REGISTER, "8");
      registrationAdapter.processRegistrationMessage(TENANT_2, DEVICE, ACTION_REGISTER, "9");
      registrationAdapter.processRegistrationMessage(TENANT_2, DEVICE, ACTION_GET, "10");

      verify(eventBus).send(EVENT_BUS_ADDRESS_REGISTRATION_REPLY, getReply(HTTP_NOT_FOUND, "0", TENANT, DEVICE));
      verify(eventBus).send(EVENT_BUS_ADDRESS_REGISTRATION_REPLY, getReply(HTTP_BAD_REQUEST, "1", TENANT, DEVICE));
      verify(eventBus).send(EVENT_BUS_ADDRESS_REGISTRATION_REPLY, getReply(HTTP_NOT_FOUND, "2", TENANT, DEVICE));
      verify(eventBus).send(EVENT_BUS_ADDRESS_REGISTRATION_REPLY, getReply(HTTP_OK, "3", TENANT, DEVICE));
      verify(eventBus).send(EVENT_BUS_ADDRESS_REGISTRATION_REPLY, getReply(HTTP_CONFLICT, "4", TENANT, DEVICE));
      verify(eventBus).send(EVENT_BUS_ADDRESS_REGISTRATION_REPLY, getReply(HTTP_OK, "5", TENANT, DEVICE));
      verify(eventBus).send(EVENT_BUS_ADDRESS_REGISTRATION_REPLY, getReply(HTTP_OK, "6", TENANT, DEVICE));
      verify(eventBus).send(EVENT_BUS_ADDRESS_REGISTRATION_REPLY, getReply(HTTP_NOT_FOUND, "7", TENANT, DEVICE));
      verify(eventBus).send(EVENT_BUS_ADDRESS_REGISTRATION_REPLY, getReply(HTTP_OK, "8", TENANT, DEVICE));
      verify(eventBus).send(EVENT_BUS_ADDRESS_REGISTRATION_REPLY, getReply(HTTP_OK, "9", TENANT_2, DEVICE));
      verify(eventBus).send(EVENT_BUS_ADDRESS_REGISTRATION_REPLY, getReply(HTTP_OK, "10", TENANT_2, DEVICE));
   }

}