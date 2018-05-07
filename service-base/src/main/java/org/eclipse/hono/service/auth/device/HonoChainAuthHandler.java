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

package org.eclipse.hono.service.auth.device;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.web.handler.ChainAuthHandler;
import io.vertx.ext.web.handler.impl.HttpStatusException;


/**
 * A Hono specific version of vert.x web's standard {@code ChainAuthHandlerImpl}
 * that does not swallow the root exception which caused an authentication failure.
 * <p>
 * This class is a copy of {@code io.vertx.ext.web.handler.impl.ChainAuthHandlerImpl}.
 */
@SuppressWarnings("checkstyle:FinalParameters")
public class HonoChainAuthHandler extends HonoAuthHandler implements ChainAuthHandler {

    private final List<AuthHandler> handlers = new ArrayList<>();

    /**
     * Creates a new handler.
     */
    public HonoChainAuthHandler() {
      super(null);
    }

    @Override
    public ChainAuthHandler append(AuthHandler other) {
      handlers.add(other);
      return this;
    }

    @Override
    public boolean remove(AuthHandler other) {
      return handlers.remove(other);
    }

    @Override
    public void clear() {
      handlers.clear();
    }

    @Override
    public AuthHandler addAuthority(String authority) {
      for (AuthHandler h : handlers) {
        h.addAuthority(authority);
      }
      return this;
    }

    @Override
    public AuthHandler addAuthorities(Set<String> authorities) {
      for (AuthHandler h : handlers) {
        h.addAuthorities(authorities);
      }
      return this;
    }

    @Override
    public void parseCredentials(RoutingContext context, Handler<AsyncResult<JsonObject>> handler) {
      // iterate all possible authN
      iterate(0, context, null, handler);
    }

    private void iterate(final int idx, final RoutingContext ctx, HttpStatusException lastException, Handler<AsyncResult<JsonObject>> handler) {
      // stop condition
      if (idx >= handlers.size()) {
        // no more providers, means that we failed to find a provider capable of performing this operation
        handler.handle(Future.failedFuture(lastException));
        return;
      }

      // parse the request in order to extract the credentials object
      final AuthHandler authHandler = handlers.get(idx);

      authHandler.parseCredentials(ctx, res -> {
        if (res.failed()) {
          if (res.cause() instanceof HttpStatusException) {
            final HttpStatusException exception = (HttpStatusException) res.cause();
            switch (exception.getStatusCode()) {
              case 302:
              case 400:
              case 401:
              case 403:
                // try again with next provider since we know what kind of error it is
                iterate(idx + 1, ctx, exception, handler);
                return;
            }
          }
          handler.handle(Future.failedFuture(res.cause()));
          return;
        }

        // setup the desired auth provider if we can
        if (authHandler instanceof HonoAuthHandler) {
          ctx.put(AUTH_PROVIDER_CONTEXT_KEY, ((HonoAuthHandler) authHandler).authProvider);
        }
        handler.handle(Future.succeededFuture(res.result()));
      });
    }
}
