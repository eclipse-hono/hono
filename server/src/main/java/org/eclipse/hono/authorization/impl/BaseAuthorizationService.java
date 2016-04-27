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
package org.eclipse.hono.authorization.impl;

import static org.eclipse.hono.authorization.AuthorizationConstants.ALLOWED;
import static org.eclipse.hono.authorization.AuthorizationConstants.AUTH_SUBJECT_FIELD;
import static org.eclipse.hono.authorization.AuthorizationConstants.DENIED;
import static org.eclipse.hono.authorization.AuthorizationConstants.EVENT_BUS_ADDRESS_AUTHORIZATION_IN;
import static org.eclipse.hono.authorization.AuthorizationConstants.PERMISSION_FIELD;
import static org.eclipse.hono.authorization.AuthorizationConstants.RESOURCE_FIELD;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.hono.authorization.AuthorizationService;
import org.eclipse.hono.authorization.Permission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;

/**
 * Base class for implementing an {@code AuthorizationService}.
 * <p>
 * Provides support for processing authorization requests via Vert.x event bus.
 * </p>
 */
public abstract class BaseAuthorizationService extends AbstractVerticle implements AuthorizationService
{
   private static final Logger LOG = LoggerFactory.getLogger(BaseAuthorizationService.class);
   private MessageConsumer<JsonObject> authRequestConsumer;

   /*
    * (non-Javadoc)
    * 
    * @see io.vertx.core.AbstractVerticle#start()
    */
   @Override
   public final void start() throws Exception
   {
      authRequestConsumer = vertx.eventBus().consumer(EVENT_BUS_ADDRESS_AUTHORIZATION_IN);
      authRequestConsumer.handler(this::processMessage);
      LOG.info("listening on event bus [address: {}] for incoming auth messages",
              EVENT_BUS_ADDRESS_AUTHORIZATION_IN);
      doStart();
   }

   protected void doStart() throws Exception
   {
      // should be overridden by subclasses
   }

   /*
    * (non-Javadoc)
    * 
    * @see io.vertx.core.AbstractVerticle#stop()
    */
   @Override
   public final void stop() throws Exception
   {
      authRequestConsumer.unregister();
      doStop();
   }

   protected void doStop() throws Exception
   {
      // to be overridden by subclasses
   }

   private void processMessage(final Message<JsonObject> message)
   {
      final JsonObject body = message.body();
      final String authSubject = body.getString(AUTH_SUBJECT_FIELD);
      final Permission permission = Permission.valueOf(body.getString(PERMISSION_FIELD));
      final List<String> resource = body.getJsonArray(RESOURCE_FIELD)
              .stream()
              .filter(o -> o instanceof String)
              .map(o -> (String)o)
              .collect(Collectors.toList());

      if (hasPermission(authSubject, resource, permission))
      {
         message.reply(ALLOWED);
      }
      else
      {
         LOG.debug("{} not allowed to {} on resource {}", authSubject, permission, resource);
         message.reply(DENIED);
      }
   }
}
