/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.authentication.file;

import java.io.FileNotFoundException;
import java.net.HttpURLConnection;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.hono.auth.Activity;
import org.eclipse.hono.auth.Authorities;
import org.eclipse.hono.auth.AuthoritiesImpl;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.auth.AbstractHonoAuthenticationService;
import org.eclipse.hono.service.auth.AuthTokenFactory;
import org.eclipse.hono.util.AuthenticationConstants;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * An authentication service based on authorities read from a JSON file.
 */
public final class FileBasedAuthenticationService extends AbstractHonoAuthenticationService<FileBasedAuthenticationServiceConfigProperties> {

    private static final String FIELD_USERS = "users";
    private static final String FIELD_ROLES = "roles";
    private static final String FIELD_OPERATION = "operation";
    private static final String FIELD_RESOURCE = "resource";
    private static final String FIELD_ACTIVITIES = "activities";
    private static final String FIELD_AUTHORITIES = "authorities";
    private static final String FIELD_MECHANISM = "mechanism";
    private static final String UNAUTHORIZED = "unauthorized";
    private static final String PREFIX_FILE_RESOURCE = "file://";

    private final Map<String, Authorities> roles = new HashMap<>();
    private final Map<String, JsonObject> users = new HashMap<>();

    private AuthTokenFactory tokenFactory;

    /**
     * Gets the supported SASL mechanisms from the service configuration. If no configuration is set, the
     * mechanisms EXTERNAL and PLAIN (in that order) are returned.
     *
     * @return The supported SASL mechanisms.
     */
    @Override
    public String[] getSupportedSaslMechanisms() {
        return Optional.ofNullable(getConfig())
                .map(config -> config.getSupportedSaslMechanisms().toArray(new String[0]))
                .orElse(DEFAULT_SASL_MECHANISMS);
    }

    /**
     * Sets the factory to use for creating tokens asserting a client's identity and authorities.
     *
     * @param tokenFactory The factory.
     * @throws NullPointerException if factory is {@code null}.
     */
    public void setTokenFactory(final AuthTokenFactory tokenFactory) {
        this.tokenFactory = Objects.requireNonNull(tokenFactory);
    }

    @Override
    protected void doStart(final Promise<Void> startPromise) {

        if (tokenFactory == null) {
            startPromise.fail("token factory must be set");
        } else {

            loadPermissions()
                .onSuccess(ok -> {
                    if (log.isInfoEnabled()) {
                        final String saslMechanisms = getConfig().getSupportedSaslMechanisms().stream()
                                .collect(Collectors.joining(", "));
                        log.info("starting {} with support for SASL mechanisms: {}", getClass().getSimpleName(), saslMechanisms);
                    }
                })
                .onComplete(startPromise);
        }
    }

    private Future<Void> loadPermissions() {
        final String permissionsPath = getConfig().getPermissionsPath();
        if (permissionsPath == null) {
            return Future.failedFuture(new IllegalStateException("permissions path is not set"));
        } else {
            return load(permissionsPath)
                .compose(this::parsePermissions)
                .onFailure(e -> log.error("cannot load permissions from path [{}]", permissionsPath, e));
        }
    }

    private Future<JsonObject> load(final String permissionsPath) {

        final String path;
        if (permissionsPath.toLowerCase().startsWith(PREFIX_FILE_RESOURCE)) {
            // tolerate URI file resource syntax
            path = permissionsPath.substring(PREFIX_FILE_RESOURCE.length());
        } else {
            path = permissionsPath;
        }
        return getVertx().fileSystem().exists(path)
                .compose(fileExists -> {
                    if (fileExists) {
                        log.info("loading permissions from resource [{}]", path);
                        return getVertx().fileSystem().readFile(path);
                    } else {
                        return Future.failedFuture(new FileNotFoundException("no such file: " + path));
                    }
                })
                .map(JsonObject::new);
    }

    private Future<Void> parsePermissions(final JsonObject permissionsObject) {

        Optional.ofNullable(permissionsObject.getJsonObject(FIELD_ROLES))
            .ifPresent(this::parseRoles);
        Optional.ofNullable(permissionsObject.getJsonObject(FIELD_USERS))
            .ifPresent(this::parseUsers);
        return Future.succeededFuture();
    }

    private void parseRoles(final JsonObject rolesObject) {
        rolesObject.stream()
            .filter(entry -> entry.getValue() instanceof JsonArray)
            .forEach(entry -> {
                final String roleName = entry.getKey();
                final JsonArray authSpecs = (JsonArray) entry.getValue();
                log.debug("adding role [{}] with {} authorities", roleName, authSpecs.size());
                roles.put(roleName, toAuthorities(authSpecs));
            });
    }

    private Authorities toAuthorities(final JsonArray authorities) {

        final AuthoritiesImpl result = new AuthoritiesImpl();
        authorities.stream()
            .filter(JsonObject.class::isInstance)
            .map(JsonObject.class::cast)
            .forEach(authSpec -> {
                final JsonArray activities = authSpec.getJsonArray(FIELD_ACTIVITIES, new JsonArray());
                final String resource = authSpec.getString(FIELD_RESOURCE);
                final String operation = authSpec.getString(FIELD_OPERATION);
                if (resource != null) {
                    final List<Activity> activityList = activities.stream()
                            .filter(String.class::isInstance)
                            .map(String.class::cast)
                            .map(Activity::valueOf)
                            .collect(Collectors.toList());
                  result.addResource(resource, activityList.toArray(Activity[]::new));
                } else if (operation != null) {
                    final String[] parts = operation.split(":", 2);
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

    private void parseUsers(final JsonObject usersObject) {
        usersObject.stream()
            .filter(entry -> entry.getValue() instanceof JsonObject)
            .forEach(entry -> {
                final String authenticationId = entry.getKey();
                final JsonObject userSpec = (JsonObject) entry.getValue();
                log.debug("adding user [{}]", authenticationId);
                users.put(authenticationId, userSpec);
            });
    }

    private JsonObject getUser(final String authenticationId, final String mechanism) {
        final JsonObject result = users.get(authenticationId);
        if (result != null && mechanism.equals(result.getString(FIELD_MECHANISM))) {
            return result;
        } else {
            return null;
        }
    }

    private Authorities getAuthorities(final JsonObject user) {
        final AuthoritiesImpl result = new AuthoritiesImpl();
        user.getJsonArray(FIELD_AUTHORITIES).forEach(obj -> {
            final String authority = (String) obj;
            final Authorities roleAuthorities = roles.get(authority);
            if (roleAuthorities != null) {
                result.addAll(roleAuthorities);
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
    public Future<HonoUser> verifyPlain(final String authzid, final String username, final String password) {

        if (username == null || username.isEmpty()) {
            return Future.failedFuture(new ClientErrorException(
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    "missing username"));
        } else if (password == null || password.isEmpty()) {
            return Future.failedFuture(new ClientErrorException(
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    "missing password"));
        } else {
            final JsonObject user = getUser(username, AuthenticationConstants.MECHANISM_PLAIN);
            if (user == null) {
                log.debug("no such user [{}]", username);
                return Future.failedFuture(new ClientErrorException(
                        HttpURLConnection.HTTP_UNAUTHORIZED,
                        UNAUTHORIZED));
            } else if (password.equals(user.getString("password"))) {
                return verify(username, user, authzid);
            } else {
                log.debug("password mismatch");
                return Future.failedFuture(new ClientErrorException(
                        HttpURLConnection.HTTP_UNAUTHORIZED,
                        UNAUTHORIZED));
            }
        }
    }

    @Override
    public Future<HonoUser> verifyExternal(final String authzid, final String subjectDn) {

        if (subjectDn == null || subjectDn.isEmpty()) {
            return Future.failedFuture(new ClientErrorException(
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    "missing subject DN"));
        } else {
            final String commonName = AuthenticationConstants.getCommonName(subjectDn);
            if (commonName == null) {
                return Future.failedFuture(new ClientErrorException(
                        HttpURLConnection.HTTP_BAD_REQUEST,
                        "could not determine authorization ID for subject DN"));
            } else {
                final JsonObject user = getUser(commonName, AuthenticationConstants.MECHANISM_EXTERNAL);
                if (user == null) {
                    return Future.failedFuture(new ClientErrorException(
                            HttpURLConnection.HTTP_UNAUTHORIZED,
                            UNAUTHORIZED));
                } else {
                    return verify(commonName, user, authzid);
                }
            }
        }
    }

    private Future<HonoUser> verify(final String authenticationId, final JsonObject user, final String authorizationId) {

        JsonObject effectiveUser = user;
        String effectiveAuthorizationId = authenticationId;
        if (authorizationId != null && !authorizationId.isEmpty() && isAuthorizedToImpersonate(user)) {
            final JsonObject impersonatedUser = users.get(authorizationId);
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
        final HonoUser honoUser = new HonoUser() {

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
        return Future.succeededFuture(honoUser);
    }
}
