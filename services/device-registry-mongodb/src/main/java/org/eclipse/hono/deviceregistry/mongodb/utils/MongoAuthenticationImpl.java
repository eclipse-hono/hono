/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry.mongodb.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoAuthProviderOptions;

import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.smallrye.mutiny.Uni;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.HashingStrategy;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authentication.Credentials;
import io.vertx.ext.auth.authentication.UsernamePasswordCredentials;
import io.vertx.ext.auth.mongo.HashAlgorithm;
import io.vertx.ext.auth.mongo.HashSaltStyle;
import io.vertx.ext.auth.mongo.HashStrategy;
import io.vertx.ext.auth.mongo.MongoAuthentication;
import io.vertx.ext.auth.mongo.impl.DefaultHashStrategy;

/**
 * A Reactive MongoDB client based implementation of {@link MongoAuthentication}.
 * <p>
 * This class is a copy of the original Vert.x MongoAuthenticationImpl class that has been adapted to use the
 * Reactive MongoDB client managed by Quarkus instead of the original vertx-mongo client.
 * </p>
 */
public class MongoAuthenticationImpl implements MongoAuthentication {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(MongoAuthenticationImpl.class);

    private final HashingStrategy strategy = HashingStrategy.load();
    private final ReactiveMongoClient mongoClient;
    private final MongoAuthProviderOptions options;
    private final Vertx vertx;
    private final HashStrategy legacyStrategy;
    private final String hashField;

    /**
     * Creates a new instance.
     *
     * @param vertx The Vert.x instance to use for asynchronous operations.
     * @param mongoClient The client to use for accessing the MongoDB instance.
     * @param options The options for configuring the new instance.
     */
    public MongoAuthenticationImpl(final Vertx vertx, final ReactiveMongoClient mongoClient,
            final MongoAuthProviderOptions options) {
        this.vertx = vertx;
        this.mongoClient = mongoClient;
        this.options = options;

        if (options.saltField().isEmpty() && options.hashAlgorithm().isEmpty() && options.saltStyle().isEmpty()) {
            this.legacyStrategy = null;
            this.hashField = null;
        } else {
            // use legacy password hashing
            final var hashStrategy = new DefaultHashStrategy();
            hashStrategy.setAlgorithm(options.hashAlgorithm().orElse(HashAlgorithm.PBKDF2));
            hashStrategy.setSaltStyle(options.saltStyle().orElse(HashSaltStyle.COLUMN));
            this.legacyStrategy = hashStrategy;
            this.hashField = options.saltField().orElse(MongoAuthentication.DEFAULT_SALT_FIELD);
        }
    }

    @Override
    public void authenticate(final JsonObject credentials, final Handler<AsyncResult<User>> resultHandler) {
        authenticate(credentials).onComplete(resultHandler);
    }

    @Override
    public Future<User> authenticate(final JsonObject credentials) {
        return authenticate(
                new UsernamePasswordCredentials(
                        credentials.getString(options.usernameField()),
                        credentials.getString(options.passwordField())));
    }

    @Override
    public Future<User> authenticate(final Credentials credentials) {
        // Null username is invalid
        if (credentials == null) {
            return Future.failedFuture("Credentials must be set for authentication.");
        }

        final UsernamePasswordCredentials authInfo;

        try {
            authInfo = (UsernamePasswordCredentials) credentials;
            authInfo.checkValid(null);
        } catch (RuntimeException e) {
            return Future.failedFuture(e);
        }

        final AuthToken token = new AuthToken(authInfo.getUsername(), authInfo.getPassword());

        final var query = createQuery(authInfo.getUsername());
        final Uni<List<Document>> findUni = getCollection().find(query).collect().asList();
        return Future.fromCompletionStage(findUni.subscribeAsCompletionStage(), vertx.getOrCreateContext())
                .compose(rows -> handleSelection(rows, token));
    }

    /**
     * The default implementation uses the usernameField as search field.
     *
     * @param username the username to search for.
     * @return the query document for MongoDB.
     */
    protected Document createQuery(final String username) {
        return new Document(options.usernameField(), username);
    }

    /**
     * Examine the selection of found users and return one, if password is fitting.
     *
     * @param resultList the list of documents returned from the query.
     * @param authToken the authentication token containing the username and password.
     * @return the authenticated user.
     */
    private Future<User> handleSelection(final List<Document> resultList, final AuthToken authToken) {
        switch (resultList.size()) {
        case 0: {
            final String message = "No account found for user [" + authToken.username + "]";
            LOG.debug(message);
            return Future.failedFuture(message);
        }
        case 1: {
            final Document json = resultList.get(0);
            final User user = createUser(json);
            if (examinePassword(user, json.getString(options.passwordField()), authToken.password)) {
                LOG.debug("Successfully validated password for user [" + authToken.username + "]");
                return Future.succeededFuture(user);
            } else {
                final String message = "Invalid username/password [" + authToken.username + "]";
                LOG.debug(message);
                return Future.failedFuture(message);
            }
        }
        default: {
            // More than one row returned!
            final String message = "More than one user row found for user [" + authToken.username + "] ( "
                    + resultList.size()
                    + " ). Usernames must be unique.";
            LOG.debug(message);
            return Future.failedFuture(message);
        }
        }
    }

    private User createUser(final Document json) {
        final var jsonObject = new JsonObject(json);
        final User user = User.create(jsonObject);
        // metadata "amr"
        user.principal().put("amr", Collections.singletonList("pwd"));

        if (legacyStrategy != null) {
            jsonObject.put("__field-salt__", hashField);
            jsonObject.put("__field-password__", options.passwordField());
        }
        return user;
    }

    private boolean examinePassword(final User user, final String hash, final String password) {

        if (hash.charAt(0) != '$') {
            // this isn't a phc-string, it's legacy
            if (legacyStrategy == null) {
                throw new IllegalStateException(
                        "Mongo Authentication cannot handle legacy hashes without a HashStrategy");
            }

            final String givenPassword = this.legacyStrategy.computeHash(password, user);
            return MessageDigest.isEqual(hash.getBytes(StandardCharsets.UTF_8),
                    givenPassword.getBytes(StandardCharsets.UTF_8));
        } else {
            return strategy.verify(hash, password);
        }
    }

    @Override
    public String hash(final String id, final Map<String, String> params, final String salt, final String password) {
        return strategy.hash(id, params, salt, password);
    }

    private ReactiveMongoCollection<Document> getCollection() {
        return mongoClient.getDatabase(options.databaseName()).getCollection(options.collectionName());
    }

    /**
     * The incoming data from an authentication request.
     *
     * @author mremme
     */
    static class AuthToken {

        final String username;
        final String password;

        AuthToken(final String username, final String password) {
            this.username = username;
            this.password = password;
        }
    }
}
