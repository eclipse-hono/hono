/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package org.eclipse.hono.adapter.http;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.handler.impl.HttpStatusException;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Combination of the original classes
 * <ul>
 * <li>io.vertx.ext.web.handler.impl.AuthHandlerImpl</li>
 * <li>io.vertx.ext.web.handler.impl.AuthorizationAuthHandler</li>
 * <li>io.vertx.ext.web.handler.impl.BasicAuthHandlerImpl</li>
 * </ul>
 * together with the fix of https://github.com/vert-x3/vertx-web/issues/756
 * to have a workaround until this is fixed in vertx-web
 */
public class HonoAuthHandlerImpl implements AuthHandler {

    // this should match the IANA registry: https://www.iana.org/assignments/http-authschemes/http-authschemes.xhtml
    enum Type {
        BASIC("Basic"),
        DIGEST("Digest"),
        BEARER("Bearer"),
        // these have no known implementation
        HOBA("HOBA"),
        MUTUAL("Mutual"),
        NEGOTIATE("Negotiate"),
        OAUTH("OAuth"),
        SCRAM_SHA_1("SCRAM-SHA-1"),
        SCRAM_SHA_256("SCRAM-SHA-256");

        private final String label;

        Type(String label) {
            this.label = label;
        }

        public boolean is(String other) {
            return label.equalsIgnoreCase(other);
        }
    }

    protected Type type = Type.BASIC;

    static final String AUTH_PROVIDER_CONTEXT_KEY = "io.vertx.ext.web.handler.AuthHandler.provider";

    static final HttpStatusException FORBIDDEN = new HttpStatusException(403);
    static final HttpStatusException UNAUTHORIZED = new HttpStatusException(401);
    static final HttpStatusException BAD_REQUEST = new HttpStatusException(400);

    protected final String realm;
    protected final AuthProvider authProvider;
    protected final Set<String> authorities = new HashSet<>();

    public HonoAuthHandlerImpl(AuthProvider authProvider, String realm) {
        this.authProvider = authProvider;
        this.realm = realm;
    }

    @Override
    public AuthHandler addAuthority(String authority) {
        authorities.add(authority);
        return this;
    }

    @Override
    public AuthHandler addAuthorities(Set<String> authorities) {
        this.authorities.addAll(authorities);
        return this;
    }

    @Override
    public void authorize(User user, Handler<AsyncResult<Void>> handler) {
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

    protected String authenticateHeader(RoutingContext context) {
        return null;
    }

    @Override
    public void handle(RoutingContext ctx) {

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
                    processException(ctx, authN.cause());
                }
            });
        });
    }

    protected void processException(RoutingContext ctx, Throwable exception) {

        if (exception != null) {
            if (exception instanceof HttpStatusException) {
                final int statusCode = ((HttpStatusException) exception).getStatusCode();
                final String payload = ((HttpStatusException) exception).getPayload();

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

    private void authorizeUser(RoutingContext ctx, User user) {
        authorize(user, authZ -> {
            if (authZ.failed()) {
                processException(ctx, authZ.cause());
                return;
            }
            // success, allowed to continue
            ctx.next();
        });
    }

    private boolean handlePreflight(RoutingContext ctx) {
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

    private AuthProvider getAuthProvider(RoutingContext ctx) {
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

    @Override
    public void parseCredentials(RoutingContext context, Handler<AsyncResult<JsonObject>> handler) {

        parseAuthorization(context, false, parseAuthorization -> {
            if (parseAuthorization.failed()) {
                handler.handle(Future.failedFuture(parseAuthorization.cause()));
                return;
            }

            final String suser;
            final String spass;

            try {
                // decode the payload
                String decoded = new String(Base64.getDecoder().decode(parseAuthorization.result()));

                int colonIdx = decoded.indexOf(":");
                if (colonIdx != -1) {
                    suser = decoded.substring(0, colonIdx);
                    spass = decoded.substring(colonIdx + 1);
                } else {
                    suser = decoded;
                    spass = null;
                }
            } catch (RuntimeException e) {
                // IllegalArgumentException includes PatternSyntaxException
                context.fail(e);
                return;
            }

            handler.handle(Future.succeededFuture(new JsonObject().put("username", suser).put("password", spass)));
        });
    }

    protected final void parseAuthorization(RoutingContext ctx, boolean optional, Handler<AsyncResult<String>> handler) {

        final HttpServerRequest request = ctx.request();
        final String authorization = request.headers().get(HttpHeaders.AUTHORIZATION);

        if (authorization == null) {
            if (optional) {
                // this is allowed
                handler.handle(Future.succeededFuture());
            } else {
                handler.handle(Future.failedFuture(UNAUTHORIZED));
            }
            return;
        }

        try {
            int idx = authorization.indexOf(' ');

            if (idx <= 0) {
                handler.handle(Future.failedFuture(BAD_REQUEST));
                return;
            }

            if (!type.is(authorization.substring(0, idx))) {
                handler.handle(Future.failedFuture(UNAUTHORIZED));
                return;
            }

            handler.handle(Future.succeededFuture(authorization.substring(idx + 1)));
        } catch (RuntimeException e) {
            handler.handle(Future.failedFuture(e));
        }
    }

}
