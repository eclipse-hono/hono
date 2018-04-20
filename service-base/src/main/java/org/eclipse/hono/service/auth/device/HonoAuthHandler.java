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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.hono.client.ServiceInvocationException;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.web.handler.impl.HttpStatusException;


/**
 * A Hono specific version of vert.x web's standard {@code AuthHandlerImpl}
 * that does not swallow the root exception which caused an authentication failure.
 * <p>
 * This class is a copy of {@code io.vertx.ext.web.handler.impl.AuthHandlerImpl}
 * with small modifications in the <em>handle</em> and <em>processException</em>
 * methods to allow handling of {@code ServiceInvocationException}s.
 */
public abstract class HonoAuthHandler implements AuthHandler {

    protected static final String AUTH_PROVIDER_CONTEXT_KEY = "io.vertx.ext.web.handler.AuthHandler.provider";

    protected static final HttpStatusException FORBIDDEN = new HttpStatusException(403);
    protected static final HttpStatusException UNAUTHORIZED = new HttpStatusException(401);
    protected static final HttpStatusException BAD_REQUEST = new HttpStatusException(400);

    protected final String realm;
    protected final AuthProvider authProvider;
    protected final Set<String> authorities = new HashSet<>();

    /**
     * Creates a handler for an authentication provider.
     * 
     * @param authProvider The provider to use for verifying credentials.
     */
    public HonoAuthHandler(final AuthProvider authProvider) {
      this(authProvider, "");
    }

    /**
     * Creates a handler for an authentication provider and a security realm.
     * 
     * @param authProvider The provider to use for verifying credentials.
     * @param realm The security realm name.
     */
    public HonoAuthHandler(final AuthProvider authProvider, final String realm) {
      this.authProvider = authProvider;
      this.realm = realm;
    }

    @Override
    public AuthHandler addAuthority(final String authority) {
      authorities.add(authority);
      return this;
    }

    @Override
    public AuthHandler addAuthorities(final Set<String> authorities) {
      this.authorities.addAll(authorities);
      return this;
    }

    @Override
    public void authorize(final User user, final Handler<AsyncResult<Void>> handler) {
      int requiredcount = authorities.size();
      if (requiredcount > 0) {
        if (user == null) {
          handler.handle(Future.failedFuture(FORBIDDEN));
          return;
        }

        AtomicInteger count = new AtomicInteger();
        AtomicBoolean sentFailure = new AtomicBoolean();

        Handler<AsyncResult<Boolean>> authHandler = res -> {
          if (res.succeeded()) {
            if (res.result()) {
              if (count.incrementAndGet() == requiredcount) {
                // Has all required authorities
                handler.handle(Future.succeededFuture());
              }
            } else {
              if (sentFailure.compareAndSet(false, true)) {
                handler.handle(Future.failedFuture(FORBIDDEN));
              }
            }
          } else {
            handler.handle(Future.failedFuture(res.cause()));
          }
        };
        for (String authority : authorities) {
          if (!sentFailure.get()) {
            user.isAuthorized(authority, authHandler);
          }
        }
      } else {
        // No auth required
        handler.handle(Future.succeededFuture());
      }
    }

    protected String authenticateHeader(final RoutingContext context) {
      return null;
    }

    @Override
    public void handle(final RoutingContext ctx) {

      if (handlePreflight(ctx)) {
        return;
      }

      User user = ctx.user();
      if (user != null) {
        // proceed to AuthZ
        authorizeUser(ctx, user);
        return;
      }
      // parse the request in order to extract the credentials object
      parseCredentials(ctx, res -> {
        if (res.failed()) {
          processException(ctx, res.cause());
          return;
        }
        // check if the user has been set
        User updatedUser = ctx.user();

        if (updatedUser != null) {
          Session session = ctx.session();
          if (session != null) {
            // the user has upgraded from unauthenticated to authenticated
            // session should be upgraded as recommended by owasp
            session.regenerateId();
          }
          // proceed to AuthZ
          authorizeUser(ctx, updatedUser);
          return;
        }

        // proceed to authN
        getAuthProvider(ctx).authenticate(res.result(), authN -> {
          if (authN.succeeded()) {
            User authenticated = authN.result();
            ctx.setUser(authenticated);
            Session session = ctx.session();
            if (session != null) {
              // the user has upgraded from unauthenticated to authenticated
              // session should be upgraded as recommended by owasp
              session.regenerateId();
            }
            // proceed to AuthZ
            authorizeUser(ctx, authenticated);
          } else {
            String header = authenticateHeader(ctx);
            if (header != null) {
              ctx.response()
                .putHeader("WWW-Authenticate", header);
            }
            // to allow further processing if needed
            processException(ctx, authN.cause());
          }
        });
      });
    }

    /**
     * This method is protected so custom auth handlers can override the default
     * error handling.
     * 
     * @param ctx The routing context.
     * @param exception The cause of failure to process the request.
     */
    protected void processException(final RoutingContext ctx, final Throwable exception) {

      if (exception != null) {

        if (exception instanceof HttpStatusException || exception instanceof ServiceInvocationException) {
          int statusCode;
          String payload;
          if (exception instanceof HttpStatusException) {
              statusCode = ((HttpStatusException) exception).getStatusCode();
              payload = ((HttpStatusException) exception).getPayload();
          } else {
              statusCode = ((ServiceInvocationException) exception).getErrorCode();
              payload = null;
          }

          switch (statusCode) {
            case 302:
              ctx.response()
                .putHeader(HttpHeaders.LOCATION, payload)
                .setStatusCode(302)
                .end("Redirecting to " + payload + ".");
              return;
            case 401:
              String header = authenticateHeader(ctx);
              if (header != null) {
                ctx.response()
                  .putHeader("WWW-Authenticate", header);
              }
              ctx.fail(401);
              return;
            default:
              ctx.fail(statusCode);
              return;
          }
        }
      }

      // fallback 500
      ctx.fail(exception);
    }

    private void authorizeUser(final RoutingContext ctx, final User user) {
      authorize(user, authZ -> {
        if (authZ.failed()) {
          processException(ctx, authZ.cause());
          return;
        }
        // success, allowed to continue
        ctx.next();
      });
    }

    private boolean handlePreflight(final RoutingContext ctx) {
      final HttpServerRequest request = ctx.request();
      // See: https://www.w3.org/TR/cors/#cross-origin-request-with-preflight-0
      // Preflight requests should not be subject to security due to the reason UAs will remove the Authorization header
      if (request.method() == HttpMethod.OPTIONS) {
        // check if there is a access control request header
        final String accessControlRequestHeader = ctx.request().getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
        if (accessControlRequestHeader != null) {
          // lookup for the Authorization header
          for (String ctrlReq : accessControlRequestHeader.split(",")) {
            if (ctrlReq.equalsIgnoreCase("Authorization")) {
              // this request has auth in access control, so we can allow preflighs without authentication
              ctx.next();
              return true;
            }
          }
        }
      }

      return false;
    }

    private AuthProvider getAuthProvider(final RoutingContext ctx) {
      try {
        AuthProvider provider = ctx.get(AUTH_PROVIDER_CONTEXT_KEY);
        if (provider != null) {
          // we're overruling the configured one for this request
          return provider;
        }
      } catch (RuntimeException e) {
        // bad type, ignore and return default
      }

      return authProvider;
    }
}
