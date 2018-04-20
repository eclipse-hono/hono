/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.service.auth.impl;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.hono.auth.Activity;
import org.eclipse.hono.auth.Authorities;
import org.eclipse.hono.auth.AuthoritiesImpl;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.service.auth.AbstractHonoAuthenticationService;
import org.eclipse.hono.service.auth.AuthTokenHelper;
import org.eclipse.hono.util.AuthenticationConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * An authentication service based on authorities read from a JSON file.
 */
@Service
@Profile("authentication-impl")
public final class FileBasedAuthenticationService extends AbstractHonoAuthenticationService<AuthenticationServerConfigProperties> {

    private static final String FIELD_USERS = "users";
    private static final String FIELD_ROLES = "roles";
    private static final String FIELD_OPERATION = "operation";
    private static final String FIELD_RESOURCE = "resource";
    private static final String FIELD_ACTIVITIES = "activities";
    private static final String FIELD_AUTHORITIES = "authorities";
    private static final String FIELD_MECHANISM = "mechanism";

    private static final Map<String, Authorities> roles = new HashMap<>();
    private static final Map<String, JsonObject> users = new HashMap<>();
    private AuthTokenHelper tokenFactory;

    @Autowired
    @Override
    public void setConfig(final AuthenticationServerConfigProperties configuration) {
        setSpecificConfig(configuration);
    }

    /**
     * Sets the factory to use for creating tokens asserting a client's identity and authorities.
     * 
     * @param tokenFactory The factory.
     * @throws NullPointerException if factory is {@code null}.
     */
    @Autowired
    @Qualifier("signing")
    public void setTokenFactory(final AuthTokenHelper tokenFactory) {
        this.tokenFactory = Objects.requireNonNull(tokenFactory);
    }

    @Override
    protected void doStart(final Future<Void> startFuture) {
        if (tokenFactory == null) {
            startFuture.fail("token factory must be set");
        } else {
            try {
                loadPermissions();
                startFuture.complete();
            } catch (IOException e) {
                log.error("cannot load permissions from resource {}", getConfig().getPermissionsPath(), e);
                startFuture.fail(e);
            }
        }
    }

    /**
     * Loads permissions from <em>permissionsPath</em>.
     * 
     * @throws IOException if the permissions cannot be read.
     * @throws IllegalStateException if no permissions resource path is set.
     */
    void loadPermissions() throws IOException {
        if (getConfig().getPermissionsPath() == null) {
            throw new IllegalStateException("permissions resource is not set");
        }
        if (getConfig().getPermissionsPath().isReadable()) {
            log.info("loading permissions from resource {}", getConfig().getPermissionsPath().getURI().toString());
            StringBuilder json = new StringBuilder();
            load(getConfig().getPermissionsPath(), json);
            parsePermissions(new JsonObject(json.toString()));
        } else {
            throw new FileNotFoundException("permissions resource does not exist");
        }
    }

    private void load(final Resource source, final StringBuilder target) throws IOException {

        char[] buffer = new char[4096];
        int bytesRead = 0;
        try (Reader reader = new InputStreamReader(source.getInputStream(), UTF_8)) {
            while ((bytesRead = reader.read(buffer)) > 0) {
                target.append(buffer, 0, bytesRead);
            }
        }
    }

    private void parsePermissions(final JsonObject permissionsObject) {

        Objects.requireNonNull(permissionsObject);
        parseRoles(permissionsObject.getJsonObject(FIELD_ROLES, new JsonObject()));
        parseUsers(permissionsObject.getJsonObject(FIELD_USERS, new JsonObject()));
    }

    private void parseRoles(final JsonObject rolesObject) {
        rolesObject.stream().filter(entry -> entry.getValue() instanceof JsonArray)
            .forEach(entry -> {
                final String roleName = entry.getKey();
                final JsonArray authSpecs = (JsonArray) entry.getValue();
                log.debug("adding role [{}] with {} authorities", roleName, authSpecs.size());
                roles.put(roleName, toAuthorities(authSpecs));
            });
    }

    private void parseUsers(final JsonObject usersObject) {
        usersObject.stream().filter(entry -> entry.getValue() instanceof JsonObject)
            .forEach(entry -> {
                final String authenticationId = entry.getKey();
                final JsonObject userSpec = (JsonObject) entry.getValue();
                log.debug("adding user [{}]", authenticationId);
                users.put(authenticationId, userSpec);
            });
    }

    private JsonObject getUser(final String authenticationId, final String mechanism) {
        JsonObject result = users.get(authenticationId);
        if (result != null && mechanism.equals(result.getString(FIELD_MECHANISM))) {
            return result;
        } else {
            return null;
        }
    }

    private Authorities getAuthorities(final JsonObject user) {
        AuthoritiesImpl result = new AuthoritiesImpl();
        user.getJsonArray(FIELD_AUTHORITIES).forEach(obj -> {
            final String authority = (String) obj;
            Authorities roleAuthorities = roles.get(authority);
            if (roleAuthorities != null) {
                result.addAll(roleAuthorities);
            }
        });
        return result;
    }

    private Authorities toAuthorities(final JsonArray authorities) {

        AuthoritiesImpl result = new AuthoritiesImpl();
        Objects.requireNonNull(authorities).stream()
          .filter(obj -> obj instanceof JsonObject)
          .forEach(obj -> {
              final JsonObject authSpec = (JsonObject) obj;
              final JsonArray activities = authSpec.getJsonArray(FIELD_ACTIVITIES, new JsonArray());
              final String resource = authSpec.getString(FIELD_RESOURCE);
              final String operation = authSpec.getString(FIELD_OPERATION);
              if (resource != null) {
                  List<Activity> activityList = new ArrayList<>();
                  activities.forEach(s -> {
                      Activity act = Activity.valueOf((String) s);
                      if (act != null) {
                          activityList.add(act);
                      }
                  });
                  result.addResource(resource, activityList.toArray(new Activity[activityList.size()]));
              } else if (operation != null) {
                  String[] parts = operation.split(":", 2);
                  if (parts.length == 2) {
                      result.addOperation(parts[0], parts[1]);
                  } else {
                      log.debug("ignoring malformed operation spec [{}], operation name missing", operation);
                  }
              } else {
                  throw new IllegalArgumentException("malformed authorities");
                  }
              });
        return result;
    }

    private boolean hasAuthority(final JsonObject user, final String role) {
        return user.getJsonArray(FIELD_AUTHORITIES, new JsonArray()).contains(role);
    }

    private boolean isAuthorizedToImpersonate(final JsonObject user) {
        return hasAuthority(user, "hono-component");
    }

    @Override
    public void verifyPlain(final String authzid, final String username, final String password,
            final Handler<AsyncResult<HonoUser>> authenticationResultHandler) {

        if (username == null || username.isEmpty()) {
            authenticationResultHandler.handle(Future.failedFuture("missing username"));
        } else if (password == null || password.isEmpty()) {
            authenticationResultHandler.handle(Future.failedFuture("missing password"));
        } else {
            JsonObject user = getUser(username, AuthenticationConstants.MECHANISM_PLAIN);
            if (user == null) {
                log.debug("no such user [{}]", username);
                authenticationResultHandler.handle(Future.failedFuture("unauthorized"));
            } else if (password.equals(user.getString("password"))) {
                verify(username, user, authzid, authenticationResultHandler);
            } else {
                log.debug("password mismatch");
                authenticationResultHandler.handle(Future.failedFuture("unauthorized"));
            }
        }
    }

    @Override
    public void verifyExternal(final String authzid, final String subjectDn, final Handler<AsyncResult<HonoUser>> authenticationResultHandler) {

        if (subjectDn == null || subjectDn.isEmpty()) {
            authenticationResultHandler.handle(Future.failedFuture("missing subject DN"));
        } else {
            String commonName = AuthenticationConstants.getCommonName(subjectDn);
            if (commonName == null) {
                authenticationResultHandler.handle(Future.failedFuture("could not determine authorization ID for subject DN"));
            } else {
                JsonObject user = getUser(commonName, AuthenticationConstants.MECHANISM_EXTERNAL);
                if (user == null) {
                    authenticationResultHandler.handle(Future.failedFuture("unauthorized"));
                } else {
                    verify(commonName, user, authzid, authenticationResultHandler);
                }
            }
        }
    }

    private void verify(final String authenticationId, final JsonObject user, final String authorizationId, final Handler<AsyncResult<HonoUser>> authenticationResultHandler) {

        JsonObject effectiveUser = user;
        String effectiveAuthorizationId = authenticationId;
        if (authorizationId != null && !authorizationId.isEmpty() && isAuthorizedToImpersonate(user)) {
            JsonObject impersonatedUser = users.get(authorizationId);
            if (impersonatedUser != null) {
                effectiveUser = impersonatedUser;
                effectiveAuthorizationId = authorizationId;
                log.debug("granting authorization id specified by client");
            } else {
                log.debug("no user found for authorization id provided by client, granting authentication id instead");
            }
        }
        final Authorities grantedAuthorities = getAuthorities(effectiveUser);
        final String grantedAuthorizationId = effectiveAuthorizationId;
        final Instant tokenExpirationTime = Instant.now().plus(tokenFactory.getTokenLifetime());
        final String token = tokenFactory.createToken(grantedAuthorizationId, grantedAuthorities);
        HonoUser honoUser = new HonoUser() {

            @Override
            public String getName() {
                return grantedAuthorizationId;
            }

            @Override
            public String getToken() {
                return token;
            }

            @Override
            public Authorities getAuthorities() {
                return grantedAuthorities;
            }

            @Override
            public boolean isExpired() {
                return !Instant.now().isBefore(tokenExpirationTime);
            }

            @Override
            public Instant getExpirationTime() {
                return tokenExpirationTime;
            }
        };
        authenticationResultHandler.handle(Future.succeededFuture(honoUser));
    }
}
