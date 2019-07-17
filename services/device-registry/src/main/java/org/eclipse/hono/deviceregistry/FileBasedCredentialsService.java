/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.hono.auth.BCryptHelper;
import org.eclipse.hono.auth.HonoPasswordEncoder;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.credentials.PasswordCredential;
import org.eclipse.hono.service.management.credentials.PasswordSecret;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import io.opentracing.Span;
import io.opentracing.noop.NoopSpan;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;


/**
 * A credentials service that keeps all data in memory but is backed by a file.
 * <p>
 * On startup this adapter tries to load credentials from a file (if configured).
 * On shutdown all credentials kept in memory are written to the file (if configured).
 */
@Repository
@Qualifier("serviceImpl")
@ConditionalOnProperty(name = "hono.app.type", havingValue = "file", matchIfMissing = true)
public final class FileBasedCredentialsService extends AbstractVerticle
        implements CredentialsManagementService, CredentialsService {

    /**
     * The name of the JSON array within a tenant that contains the credentials.
     */
    public static final String ARRAY_CREDENTIALS = "credentials";
    /**
     * The name of the JSON property containing the tenant's ID.
     */
    public static final String FIELD_TENANT = "tenant";

    private static final Logger log = LoggerFactory.getLogger(FileBasedCredentialsService.class);

    // <tenantId, <authId, credentialsData[]>>
    private final Map<String, Map<String, JsonArray>> credentials = new HashMap<>();
    // <tenantId, <deviceId, version>>
    private final Map<String, Map<String, String>> versions = new HashMap<>();
    private boolean running = false;
    private boolean dirty = false;
    private FileBasedCredentialsConfigProperties config;

    private HonoPasswordEncoder passwordEncoder;

    @Autowired
    public void setConfig(final FileBasedCredentialsConfigProperties configuration) {
        this.config = configuration;
    }

    @Autowired
    public void setPasswordEncoder(final HonoPasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public FileBasedCredentialsConfigProperties getConfig() {
        return config;
    }

    private Future<Void> checkFileExists(final boolean createIfMissing) {

        final Future<Void> result = Future.future();
        if (getConfig().getFilename() == null) {
            result.fail("no filename set");
        } else if (vertx.fileSystem().existsBlocking(getConfig().getFilename())) {
            result.complete();
        } else if (createIfMissing) {
            vertx.fileSystem().createFile(getConfig().getFilename(), result);
        } else {
            log.debug("no such file [{}]", getConfig().getFilename());
            result.complete();
        }
        return result;
    }

    @Override
    public void init(final Vertx vertx, final Context context) {
        super.init(vertx, context);
        Json.mapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void start(final Future<Void> startFuture) {
        if (running) {
            startFuture.complete();
        } else {
            Json.mapper.registerModule(new JavaTimeModule());
            if (!getConfig().isModificationEnabled()) {
                log.info("modification of credentials has been disabled");
            }

            if (getConfig().getFilename() == null) {
                log.debug("credentials filename is not set, no credentials will be loaded");
                running = true;
                startFuture.complete();
            } else {
                checkFileExists(getConfig().isSaveToFile())
                        .compose(ok -> loadCredentials())
                        .compose(s -> {
                            if (getConfig().isSaveToFile()) {
                                log.info("saving credentials to file every 3 seconds");
                                vertx.setPeriodic(3000, saveIdentities -> {
                                    saveToFile();
                                });
                            } else {
                                log.info("persistence is disabled, will not save credentials to file");
                            }
                            running = true;
                            return Future.succeededFuture();
                        })
                        .<Void> mapEmpty()
                        .setHandler(startFuture);
            }
        }
    }

    Future<Void> loadCredentials() {

        if (getConfig().getFilename() == null || getConfig().isStartEmpty()) {
            // no need to load anything
            log.info("Either filename is null or empty start is set, won't load any credentials");
            return Future.succeededFuture();
        } else {
            final Future<Buffer> readResult = Future.future();
            log.debug("trying to load credentials from file {}", getConfig().getFilename());
            vertx.fileSystem().readFile(getConfig().getFilename(), readResult);
            return readResult
                    .compose(this::addAll)
                    .recover(t -> {
                        log.debug("cannot load credentials from file [{}]: {}", getConfig().getFilename(),
                                t.getMessage());
                        return Future.succeededFuture();
                    });
        }
    }

    private Future<Void> addAll(final Buffer credentials) {
        final Future<Void> result = Future.future();
        try {
            int credentialsCount = 0;
            final JsonArray allObjects = credentials.toJsonArray();
            log.debug("trying to load credentials for {} tenants", allObjects.size());
            for (final Object obj : allObjects) {
                if (JsonObject.class.isInstance(obj)) {
                    credentialsCount += addCredentialsForTenant((JsonObject) obj);
                }
            }
            log.info("successfully loaded {} credentials from file [{}]", credentialsCount, getConfig().getFilename());
            result.complete();
        } catch (final DecodeException e) {
            log.warn("cannot read malformed JSON from credentials file [{}]", getConfig().getFilename());
            result.fail(e);
        }
        return result;
    }

    int addCredentialsForTenant(final JsonObject tenant) {
        int count = 0;
        final String tenantId = tenant.getString(FIELD_TENANT);
        final Map<String, JsonArray> credentialsMap = new HashMap<>();
        for (final Object credentialsObj : tenant.getJsonArray(ARRAY_CREDENTIALS)) {
            final JsonObject credentials = (JsonObject) credentialsObj;
            final JsonArray authIdCredentials;
            if (credentialsMap.containsKey(credentials.getString(CredentialsConstants.FIELD_AUTH_ID))) {
                authIdCredentials = credentialsMap.get(credentials.getString(CredentialsConstants.FIELD_AUTH_ID));
            } else {
                authIdCredentials = new JsonArray();
            }
            authIdCredentials.add(credentials);
            credentialsMap.put(credentials.getString(CredentialsConstants.FIELD_AUTH_ID), authIdCredentials);
            count++;
        }
        credentials.put(tenantId, credentialsMap);
        return count;
    }

    @Override
    public void stop(final Future<Void> stopFuture) {

        if (running) {
            saveToFile().compose(s -> {
                running = false;
                stopFuture.complete();
            }, stopFuture);
        } else {
            stopFuture.complete();
        }

    }

    Future<Void> saveToFile() {

        if (!getConfig().isSaveToFile()) {
            return Future.succeededFuture();
        } else if (dirty) {
            return checkFileExists(true).compose(s -> {
                final AtomicInteger idCount = new AtomicInteger();
                final JsonArray tenants = new JsonArray();
                for (final Entry<String, Map<String, JsonArray>> entry : credentials.entrySet()) {
                    final JsonArray credentialsArray = new JsonArray();
                    for (final JsonArray singleAuthIdCredentials : entry.getValue().values()) {
                        credentialsArray.addAll(singleAuthIdCredentials.copy());
                        idCount.incrementAndGet();
                    }
                    tenants.add(
                            new JsonObject()
                                    .put(FIELD_TENANT, entry.getKey())
                                    .put(ARRAY_CREDENTIALS, credentialsArray));
                }
                final Future<Void> writeHandler = Future.future();
                vertx.fileSystem().writeFile(
                        getConfig().getFilename(),
                        Buffer.buffer(tenants.encodePrettily(), StandardCharsets.UTF_8.name()),
                        writeHandler);
                return writeHandler.map(ok -> {
                    dirty = false;
                    log.trace("successfully wrote {} credentials to file {}", idCount.get(), getConfig().getFilename());
                    return (Void) null;
                }).otherwise(t -> {
                    log.warn("could not write credentials to file {}", getConfig().getFilename(), t);
                    return (Void) null;
                });
            });
        } else {
            log.trace("credentials registry does not need to be persisted");
            return Future.succeededFuture();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * The result object will include a <em>no-cache</em> directive.
     */
    @Override
    public void get(final String tenantId, final String type, final String authId, final Span span,
            final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        get(tenantId, type, authId, null, span, resultHandler);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The result object will include a <em>max-age</em> cache directive for
     * hashed password and X.509 credential types. Otherwise, a <em>no-cache</em>
     * directive will be included.
     */
    @Override
    public void get(
            final String tenantId,
            final String type,
            final String authId,
            final JsonObject clientContext,
            final Span span,
            final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(authId);
        Objects.requireNonNull(resultHandler);

        final JsonObject data = getSingleCredentials(tenantId, authId, type, clientContext, span);
        if (data == null) {
            resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HTTP_NOT_FOUND)));
        } else {
            resultHandler.handle(Future.succeededFuture(
                    CredentialsResult.from(HTTP_OK, data.copy(), getCacheDirective(type))));
        }
    }

    private void findCredentialsForDevice(final JsonArray credentials, final String deviceId, final JsonArray result) {

        for (final Object obj : credentials) {
            if (obj instanceof JsonObject) {
                final JsonObject currentCredentials = (JsonObject) obj;
                if (deviceId.equals(getTypesafeValueForField(String.class, currentCredentials,
                        CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID))) {
                    // device ID matches, add a copy of credentials to result
                    result.add(currentCredentials.copy());
                }
            }
        }
    }

    /**
     * Gets a property value of a given type from a JSON object.
     *
     * @param clazz Type class of the type
     * @param payload The object to get the property from.
     * @param field The name of the property.
     * @param <T> The type of the field.
     * @return The property value or {@code null} if no such property exists or is not of the expected type.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected static <T> T getTypesafeValueForField(final Class<T> clazz, final JsonObject payload,
            final String field) {

        Objects.requireNonNull(clazz);
        Objects.requireNonNull(payload);
        Objects.requireNonNull(field);

        final Object result = payload.getValue(field);

        if (clazz.isInstance(result)) {
            return clazz.cast(result);
        }

        return null;
    }

    /**
     * Get the credentials associated with the authId and the given type. If type is null, all credentials associated
     * with the authId are returned (as JsonArray inside the return value).
     *
     * @param tenantId The id of the tenant the credentials belong to.
     * @param authId The authentication identifier to look up credentials for.
     * @param type The type of credentials to look up.
     * @param span The active OpenTracing span for this operation.
     * @return The credentials object of the given type or {@code null} if no matching credentials exist.
     */
    private JsonObject getSingleCredentials(final String tenantId, final String authId, final String type,
            final JsonObject clientContext, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(authId);
        Objects.requireNonNull(type);

        final Map<String, JsonArray> credentialsForTenant = credentials.get(tenantId);
        if (credentialsForTenant == null) {
            TracingHelper.logError(span, "no credentials found for tenant");
            return null;
        }

        final JsonArray authIdCredentials = credentialsForTenant.get(authId);
        if (authIdCredentials == null) {
            TracingHelper.logError(span, "no credentials found for auth-id");
            return null;
        }

        for (final Object authIdCredentialEntry : authIdCredentials) {
            final JsonObject authIdCredential = (JsonObject) authIdCredentialEntry;

            if (!type.equals(authIdCredential.getString(CredentialsConstants.FIELD_TYPE))) {
                // auth-id doesn't match ... continue search
                continue;
            }

            if (Boolean.FALSE.equals(authIdCredential.getBoolean(CredentialsConstants.FIELD_ENABLED, true))) {
                // do not report disabled
                continue;
            }

            if (clientContext != null) {
                final AtomicBoolean match = new AtomicBoolean(true);
                clientContext.forEach(field -> {
                    if (authIdCredential.containsKey(field.getKey())) {
                        if (!authIdCredential.getString(field.getKey()).equals(field.getValue())) {
                            match.set(false);
                        }
                    } else {
                        match.set(false);
                    }
                });
                if (!match.get()) {
                    continue;
                }
            }

            // copy
            final var authIdCredentialCopy = authIdCredential.copy();
            final var secrets = authIdCredentialCopy.getJsonArray(CredentialsConstants.FIELD_SECRETS);

            for (final Iterator<Object> i = secrets.iterator(); i.hasNext();) {

                final Object o = i.next();
                if (!(o instanceof JsonObject)) {
                    i.remove();
                    continue;
                }

                final JsonObject secret = (JsonObject) o;
                if (Boolean.FALSE.equals(secret.getBoolean(CredentialsConstants.FIELD_ENABLED, true))) {
                    i.remove();
                    continue;
                }
            }

            if (secrets.isEmpty()) {
                // no more secrets left
                continue;
            }

            // return the first entry that matches
            return authIdCredentialCopy;
        }

        // we ended up with no match
        if (clientContext != null) {
            TracingHelper.logError(span, "no credentials found with matching type and client context");
        } else {
            TracingHelper.logError(span, "no credentials found with matching type");
        }

        return null;
    }

    @Override
    public void set(final String tenantId, final String deviceId, final Optional<String> resourceVersion,
                    final List<CommonCredential> secrets, final Span span, final Handler<AsyncResult<OperationResult<Void>>> resultHandler) {

        resultHandler.handle(Future.succeededFuture(set(tenantId, deviceId, resourceVersion, span, secrets)));

    }

    private OperationResult<Void> set(final String tenantId, final String deviceId,
            final Optional<String> resourceVersion, final Span span, final List<CommonCredential> credentials) {

        if (!checkResourceVersion(tenantId, deviceId, resourceVersion)) {
            TracingHelper.logError(span, "Resource version mismatch");
            return OperationResult.empty(HttpURLConnection.HTTP_PRECON_FAILED);
        }

        // change version
        final var newVersion = UUID.randomUUID().toString();
        setResourceVersion(tenantId, deviceId, newVersion);

        // clean out all existing credentials for this device
        try {
            removeAllForDevice(tenantId, deviceId, span);
        } catch (final ClientErrorException e) {
            TracingHelper.logError(span, e);
            return OperationResult.empty(e.getErrorCode());
        }

        // authId->credentials[]
        final Map<String, JsonArray> credentialsForTenant = createOrGetCredentialsForTenant(tenantId);

        // now add the new ones
        for (final CommonCredential credential : credentials) {

            try {
                checkCredential(credential);
            } catch (final IllegalStateException e) {
                TracingHelper.logError(span, e);
                log.debug("Failed to validate credentials", e);
                return OperationResult.empty(HttpURLConnection.HTTP_BAD_REQUEST);
            }

            final String authId = credential.getAuthId();
            final JsonObject credentialObject = JsonObject.mapFrom(credential);
            final String type = credentialObject.getString(CredentialsConstants.FIELD_TYPE);
            final var json = createOrGetAuthIdCredentials(authId, credentialsForTenant);

            // find credentials - matching by type
            var credentialsJson = json.stream()
                    .filter(JsonObject.class::isInstance).map(JsonObject.class::cast)
                    .filter(j -> type.equals(j.getString(CredentialsConstants.FIELD_TYPE)))
                    .findAny().orElse(null);

            if (credentialsJson == null) {
                // did not found an entry, add new one
                credentialsJson = new JsonObject();
                credentialsJson.put(CredentialsConstants.FIELD_AUTH_ID, authId);
                credentialsJson.put(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId);
                credentialsJson.put(CredentialsConstants.FIELD_TYPE, type);
                credentialsJson.put(CredentialsConstants.FIELD_ENABLED, credential.getEnabled());
                credentialsJson.put(RegistryManagementConstants.FIELD_EXT, credential.getExtensions());
                json.add(credentialsJson);
            }

            if (!deviceId.equals(credentialsJson.getString(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID))) {
                // found an entry for another device, with the same auth-id
                TracingHelper.logError(span, "Auth-id already used for another device");
                return OperationResult.empty(HttpURLConnection.HTTP_CONFLICT);
            }

            // start updating
            dirty = true;

            var secretsJson = credentialsJson.getJsonArray(CredentialsConstants.FIELD_SECRETS);
            if (secretsJson == null) {
                // secrets field was missing, assign
                secretsJson = new JsonArray();
                credentialsJson.put(CredentialsConstants.FIELD_SECRETS, secretsJson);
            }
            secretsJson.addAll(credentialObject.getJsonArray(CredentialsConstants.FIELD_SECRETS));
            credentialsForTenant.put(authId, json);
        }

        return OperationResult.ok(HttpURLConnection.HTTP_NO_CONTENT, null, Optional.empty(), Optional.of(newVersion));
    }

    /**
     * Validate a secret.
     *
     * @param credential The secret to validate.
     * @throws IllegalStateException if the secret is not valid.
     */
    protected void checkCredential(final CommonCredential credential) {
        credential.checkValidity();
        if (credential instanceof PasswordCredential) {
            for (final PasswordSecret passwordSecret : ((PasswordCredential) credential).getSecrets()) {
                passwordSecret.encode(passwordEncoder);
                passwordSecret.checkValidity();
                switch (passwordSecret.getHashFunction()) {
                    case RegistryManagementConstants.HASH_FUNCTION_BCRYPT:
                        final String pwdHash = passwordSecret.getPasswordHash();
                        verifyBcryptPasswordHash(pwdHash);
                        break;
                    default:
                        // pass
                }
                // pass
            }
        }
    }

    /**
     * Verifies that a hash value is a valid BCrypt password hash.
     * <p>
     * The hash must be a version 2a hash and must not use more than the configured
     * maximum number of iterations as returned by {@link #getMaxBcryptIterations()}.
     *
     * @param pwdHash The hash to verify.
     * @throws IllegalStateException if the secret does not match the criteria.
     */
    protected void verifyBcryptPasswordHash(final String pwdHash) {

        Objects.requireNonNull(pwdHash);
        if (BCryptHelper.getIterations(pwdHash) > getMaxBcryptIterations()) {
            throw new IllegalStateException("password hash uses too many iterations, max is " + getMaxBcryptIterations());
        }
    }

    private void checkHashedPassword(final PasswordSecret secret) {
        if (secret.getHashFunction() == null) {
            throw new IllegalStateException("missing/invalid hash function");
        }

        if (secret.getPasswordHash() == null) {
            throw new IllegalStateException("missing/invalid password hash");
        }
    }

    /**
     * Remove all credentials that point to a device.
     * 
     * @param tenantId The tenant to process.
     * @param deviceId The device id to look for.
     */
    private void removeAllForDevice(final String tenantId, final String deviceId, final Span span) {

        final boolean canModify = getConfig().isModificationEnabled();

        final Map<String, JsonArray> credentialsForTenant = createOrGetCredentialsForTenant(tenantId);

        for (final JsonArray versionedCredentials : credentialsForTenant.values()) {

            for (final Iterator<Object> i = versionedCredentials.iterator(); i.hasNext();) {

                final Object o = i.next();

                if (!(o instanceof JsonObject)) {
                    continue;
                }

                final JsonObject credentials = (JsonObject) o;
                final String currentDeviceId = credentials.getString(Constants.JSON_FIELD_DEVICE_ID);
                if (!deviceId.equals(currentDeviceId)) {
                    continue;
                }

                // check if we may overwrite

                if (!canModify) {
                    TracingHelper.logError(span, "Modification is disabled for the Credentials service.");
                    throw new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN);
                }

                // remove device from credentials set
                i.remove();
                this.dirty = true;
            }
        }
    }

    @Override
    public void get(final String tenantId, final String deviceId, final Span span,
            final Handler<AsyncResult<OperationResult<List<CommonCredential>>>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(resultHandler);

        final Map<String, JsonArray> credentialsForTenant = credentials.get(tenantId);
        if (credentialsForTenant == null) {
            TracingHelper.logError(span, "No credentials found for tenant");
            resultHandler.handle(Future.succeededFuture(OperationResult.ok(HTTP_NOT_FOUND, null, Optional.empty(),
                    Optional.of(getOrCreateResourceVersion(tenantId, deviceId)))));
            return;
        }

        final JsonArray matchingCredentials = new JsonArray();
        // iterate over all credentials per auth-id in order to find credentials matching the given device
        for (final JsonArray credentialsForAuthId : credentialsForTenant.values()) {
            findCredentialsForDevice(credentialsForAuthId, deviceId, matchingCredentials);
        }
        if (matchingCredentials.isEmpty()) {
            TracingHelper.logError(span, "No credentials found for device");
            resultHandler.handle(Future.succeededFuture(OperationResult.ok(HTTP_NOT_FOUND, null, Optional.empty(),
                    Optional.of(getOrCreateResourceVersion(tenantId, deviceId)))));
            return;
        }

        // convert credentials

        final List<CommonCredential> credentials = new ArrayList<>();
        for (final Object credential : matchingCredentials) {
            final JsonObject credentialsObject = (JsonObject) credential;
            credentialsObject.remove(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID);
            final CommonCredential cred = credentialsObject.mapTo(CommonCredential.class);
            credentials.add(cred);
        }

        resultHandler.handle(Future.succeededFuture(
                OperationResult.ok(HTTP_OK,
                        credentials,
                        // TODO check cache directive
                        Optional.empty(),
                        Optional.of(getOrCreateResourceVersion(tenantId, deviceId)))));

    }

    /**
     * Remove all the credentials for the given device ID.
     * @param tenantId the Id of the tenant which the device belongs to.
     * @param deviceId the id of the device that is deleted.
     * @param span The active OpenTracing span for this operation.
     * @param resultHandler the operation result.
     */
    public void remove(final String tenantId, final String deviceId,
            final Span span, final Handler<AsyncResult<Result<Void>>> resultHandler) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(resultHandler);

        log.debug("removing credentials for device [tenant-id: {}, device-id: {}]", tenantId, deviceId);

        resultHandler.handle(Future.succeededFuture(remove(tenantId, deviceId, span)));
    }

    private Result<Void> remove(final String tenantId, final String deviceId, final Span span) {

        setResourceVersion(tenantId, deviceId, null);

        try {
            removeAllForDevice(tenantId, deviceId, span);
        } catch (final ClientErrorException e) {
            TracingHelper.logError(span, e);
            return Result.from(e.getErrorCode());
        }

        return Result.from(HttpURLConnection.HTTP_NO_CONTENT);
    }

    private boolean checkResourceVersion(final String tenantId, final String deviceId, final Optional<String> resourceVersion) {

        if (resourceVersion.isEmpty()) {
            return true;
        }

        final String version = versions.getOrDefault(tenantId, Collections.emptyMap()).get(deviceId);
        if (version == null) {
            // we have no version, and never told anyone, so the requested version of wrong.
            return false;
        }

        return version.equals(resourceVersion.get());
    }

    private String setResourceVersion(final String tenantId, final String deviceId, final String version) {

        if (version != null) {

            versions
                    .computeIfAbsent(tenantId, key -> new HashMap<>())
                    .put(deviceId, version);

        } else {

            versions.getOrDefault(tenantId, Collections.emptyMap()).remove(deviceId);

        }

        return version;

    }

    private String getOrCreateResourceVersion(final String tenantId, final String deviceId) {
        return versions.computeIfAbsent(tenantId, key -> new HashMap<>())
                .computeIfAbsent(deviceId, key -> UUID.randomUUID().toString());
    }

    @Override
    public void get(final String tenantId, final String type, final String authId,
            final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        get(tenantId, type, authId, NoopSpan.INSTANCE, resultHandler);
    }

    @Override
    public void get(final String tenantId, final String type, final String authId, final JsonObject clientContext,
            final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        get(tenantId, type, authId, clientContext, NoopSpan.INSTANCE, resultHandler);
    }

    /**
     * Create or get credentials map for a single tenant.
     * 
     * @param tenantId The tenant to get
     * @return The map, never returns {@code null}.
     */
    private Map<String, JsonArray> createOrGetCredentialsForTenant(final String tenantId) {
        return credentials.computeIfAbsent(tenantId, id -> new HashMap<>());
    }

    private JsonArray createOrGetAuthIdCredentials(final String authId,
            final Map<String, JsonArray> credentialsForTenant) {
        return credentialsForTenant.computeIfAbsent(authId, id -> new JsonArray());
    }

    private CacheDirective getCacheDirective(final String type) {

        if (getConfig().getCacheMaxAge() > 0) {
            switch(type) {
            case CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD:
            case CredentialsConstants.SECRETS_TYPE_X509_CERT:
                return CacheDirective.maxAgeDirective(getConfig().getCacheMaxAge());
            default:
                return CacheDirective.noCacheDirective();
            }
        } else {
            return CacheDirective.noCacheDirective();
        }
    }

    /**
     * Removes all credentials from the registry.
     */
    public void clear() {
        dirty = true;
        credentials.clear();
    }

    @Override
    public String toString() {
        return String.format("%s[filename=%s]", FileBasedCredentialsService.class.getSimpleName(), getConfig().getFilename());
    }

    protected int getMaxBcryptIterations() {
        return getConfig().getMaxBcryptIterations();
    }
}
