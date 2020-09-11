/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry.file;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.hono.auth.HonoPasswordEncoder;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.service.Lifecycle;
import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import io.opentracing.Span;
import io.opentracing.noop.NoopSpan;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;


/**
 * A credentials service that keeps all data in memory but is backed by a file.
 * <p>
 * On startup this adapter tries to load credentials from a file (if configured).
 * On shutdown all credentials kept in memory are written to the file (if configured).
 */
public final class FileBasedCredentialsService implements CredentialsManagementService, CredentialsService, Lifecycle {

    /**
     * The name of the JSON array within a tenant that contains the credentials.
     */
    public static final String ARRAY_CREDENTIALS = "credentials";
    /**
     * The name of the JSON property containing the tenant's ID.
     */
    public static final String FIELD_TENANT = "tenant";

    private static final Logger LOG = LoggerFactory.getLogger(FileBasedCredentialsService.class);

    // <tenantId, <authId, credentialsData[]>>
    private final ConcurrentMap<String, ConcurrentMap<String, JsonArray>> credentials = new ConcurrentHashMap<>();
    // <tenantId, <deviceId, version>>
    private final ConcurrentMap<String, ConcurrentMap<String, String>> versions = new ConcurrentHashMap<>();
    private final Vertx vertx;

    private AtomicBoolean running = new AtomicBoolean(false);
    private AtomicBoolean dirty = new AtomicBoolean(false);
    private FileBasedCredentialsConfigProperties config;

    private HonoPasswordEncoder passwordEncoder;

    /**
     * Creates a new service instance.
     *
     * @param vertx The vert.x instance to run on.
     * @throws NullPointerException if vertx is {@code null}.
     */
    @Autowired
    public FileBasedCredentialsService(final Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
    }

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

        final Promise<Void> result = Promise.promise();
        if (getConfig().getFilename() == null) {
            result.fail("no filename set");
        } else if (vertx.fileSystem().existsBlocking(getConfig().getFilename())) {
            result.complete();
        } else if (createIfMissing) {
            vertx.fileSystem().createFile(getConfig().getFilename(), result);
        } else {
            LOG.debug("no such file [{}]", getConfig().getFilename());
            result.complete();
        }
        return result.future();
    }

    @Override
    public Future<Void> start() {

        final Promise<Void> startPromise = Promise.promise();
        if (running.compareAndSet(false, true)) {
            if (!getConfig().isModificationEnabled()) {
                LOG.info("modification of credentials has been disabled");
            }

            if (getConfig().getFilename() == null) {
                LOG.debug("credentials filename is not set, no credentials will be loaded");
                startPromise.complete();
            } else {
                checkFileExists(getConfig().isSaveToFile())
                    .compose(ok -> loadCredentials())
                    .onSuccess(ok -> {
                        if (getConfig().isSaveToFile()) {
                            LOG.info("saving credentials to file every 3 seconds");
                            vertx.setPeriodic(3000, saveIdentities -> saveToFile());
                        } else {
                            LOG.info("persistence is disabled, will not save credentials to file");
                        }
                    })
                    .onFailure(t -> {
                        LOG.error("failed to start up service", t);
                        running.set(false);
                    })
                    .onComplete(startPromise);
            }
        } else {
            startPromise.complete();
        }
        return startPromise.future();
    }

    Future<Void> loadCredentials() {

        if (getConfig().getFilename() == null || getConfig().isStartEmpty()) {
            // no need to load anything
            LOG.info("Either filename is null or empty start is set, won't load any credentials");
            return Future.succeededFuture();
        } else {
            final Promise<Buffer> readResult = Promise.promise();
            LOG.debug("trying to load credentials from file {}", getConfig().getFilename());
            vertx.fileSystem().readFile(getConfig().getFilename(), readResult);
            return readResult.future()
                    .compose(this::addAll)
                    .recover(t -> {
                        LOG.debug("cannot load credentials from file [{}]: {}", getConfig().getFilename(),
                                t.getMessage());
                        return Future.succeededFuture();
                    });
        }
    }

    private Future<Void> addAll(final Buffer credentials) {
        final Promise<Void> result = Promise.promise();
        try {
            int credentialsCount = 0;
            final JsonArray allObjects = credentials.toJsonArray();
            LOG.debug("trying to load credentials for {} tenants", allObjects.size());
            for (final Object obj : allObjects) {
                if (JsonObject.class.isInstance(obj)) {
                    credentialsCount += addCredentialsForTenant((JsonObject) obj);
                }
            }
            LOG.info("successfully loaded {} credentials from file [{}]", credentialsCount, getConfig().getFilename());
            result.complete();
        } catch (final DecodeException e) {
            LOG.warn("cannot read malformed JSON from credentials file [{}]", getConfig().getFilename());
            result.fail(e);
        }
        return result.future();
    }

    int addCredentialsForTenant(final JsonObject tenant) {
        int count = 0;
        final String tenantId = tenant.getString(FIELD_TENANT);
        final ConcurrentMap<String, JsonArray> credentialsMap = new ConcurrentHashMap<>();
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
    public Future<Void> stop() {

        final Promise<Void> stopPromise = Promise.promise();
        if (running.compareAndSet(true, false)) {
            saveToFile().onComplete(stopPromise);
        } else {
            stopPromise.complete();
        }
        return stopPromise.future();
    }

    Future<Void> saveToFile() {

        if (!getConfig().isSaveToFile()) {
            return Future.succeededFuture();
        } else if (dirty.get()) {
            return checkFileExists(true).compose(s -> {
                final AtomicInteger idCount = new AtomicInteger();
                final JsonArray tenants = new JsonArray();
                for (final Entry<String, ConcurrentMap<String, JsonArray>> entry : credentials.entrySet()) {
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
                final Promise<Void> writeHandler = Promise.promise();
                vertx.fileSystem().writeFile(
                        getConfig().getFilename(),
                        Buffer.buffer(tenants.encodePrettily(), StandardCharsets.UTF_8.name()),
                        writeHandler);
                return writeHandler.future().map(ok -> {
                    dirty.set(false);
                    LOG.trace("successfully wrote {} credentials to file {}", idCount.get(), getConfig().getFilename());
                    return (Void) null;
                }).otherwise(t -> {
                    LOG.warn("could not write credentials to file {}", getConfig().getFilename(), t);
                    return (Void) null;
                });
            });
        } else {
            LOG.trace("credentials registry does not need to be persisted");
            return Future.succeededFuture();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * The result object will include a <em>no-cache</em> directive.
     */
    @Override
    public Future<CredentialsResult<JsonObject>> get(final String tenantId, final String type, final String authId,
            final Span span) {
        return get(tenantId, type, authId, null, span);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The result object will include a <em>max-age</em> cache directive for
     * hashed password and X.509 credential types. Otherwise, a <em>no-cache</em>
     * directive will be included.
     */
    @Override
    public Future<CredentialsResult<JsonObject>> get(
            final String tenantId,
            final String type,
            final String authId,
            final JsonObject clientContext,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(authId);

        final JsonObject data = getSingleCredentials(tenantId, authId, type, clientContext, span);
        if (data == null) {
            return Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_FOUND));
        } else {
            return Future.succeededFuture(
                    CredentialsResult.from(HttpURLConnection.HTTP_OK, data.copy(), getCacheDirective(type)));
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

        final ConcurrentMap<String, JsonArray> credentialsForTenant = credentials.get(tenantId);
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
                // credentials type doesn't match ... continue search
                continue;
            }

            if (Boolean.FALSE.equals(authIdCredential.getBoolean(CredentialsConstants.FIELD_ENABLED, true))) {
                // do not report disabled
                continue;
            }

            if (!DeviceRegistryUtils.matchesWithClientContext(authIdCredential, clientContext)) {
                continue;
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
    public Future<OperationResult<Void>> updateCredentials(final String tenantId, final String deviceId,
            final List<CommonCredential> credentials,
            final Optional<String> resourceVersion,
            final Span span) {

        return Future.succeededFuture(set(tenantId, deviceId, resourceVersion, span, credentials));

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


        // authId->credentials[]
        final ConcurrentMap<String, JsonArray> credentialsForTenant = createOrGetCredentialsForTenant(tenantId);

        if (!credentialsForTenant.isEmpty()) {
            try {
                verifyOverwriteEnabled(span);
            } catch (ClientErrorException e) {
                TracingHelper.logError(span, e);
                return OperationResult.empty(e.getErrorCode());
            }
        }

        // now add the new ones
        for (final CommonCredential credential : credentials) {

            try {
                DeviceRegistryUtils.checkCredential(credential, passwordEncoder, config.getHashAlgorithmsWhitelist(),
                        config.getMaxBcryptCostFactor());
            } catch (final IllegalStateException e) {
                TracingHelper.logError(span, e);
                LOG.debug("Failed to validate credentials", e);
                return OperationResult.empty(HttpURLConnection.HTTP_BAD_REQUEST);
            }

            final String authId = credential.getAuthId();
            final JsonObject credentialObject = JsonObject.mapFrom(credential);
            final String type = credentialObject.getString(CredentialsConstants.FIELD_TYPE);
            final JsonArray json = createOrGetAuthIdCredentials(authId, credentialsForTenant);

            // find credentials - matching by type
            JsonObject credentialsJson = json.stream()
                    .filter(JsonObject.class::isInstance).map(JsonObject.class::cast)
                    .filter(j -> type.equals(j.getString(CredentialsConstants.FIELD_TYPE)))
                    .findAny().orElse(null);

            if (credentialsJson == null) {
                // did not find an entry, add new one
                credentialsJson = new JsonObject();
                credentialsJson.put(CredentialsConstants.FIELD_AUTH_ID, authId);
                credentialsJson.put(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId);
                credentialsJson.put(CredentialsConstants.FIELD_TYPE, type);
                if (!credential.isEnabled()) {
                    // only add non-default value
                    credentialsJson.put(CredentialsConstants.FIELD_ENABLED, credential.isEnabled());
                }
                credentialsJson.put(RegistryManagementConstants.FIELD_EXT, credential.getExtensions());
                json.add(credentialsJson);
            }

            if (!deviceId.equals(credentialsJson.getString(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID))) {
                // found an entry for another device, with the same auth-id
                TracingHelper.logError(span, "Auth-id already used for another device");
                return OperationResult.empty(HttpURLConnection.HTTP_CONFLICT);
            }

            JsonArray secretsJson = credentialsJson.getJsonArray(CredentialsConstants.FIELD_SECRETS);
            if (secretsJson == null) {
                // secrets field was missing, assign
                secretsJson = new JsonArray();
            }

            final JsonArray inputSecrets = credentialObject.getJsonArray(CredentialsConstants.FIELD_SECRETS);
            final JsonArray definitiveInputSecret = new JsonArray();
            for (Object inputSecret : inputSecrets) {
                final JsonObject secret = (JsonObject) inputSecret;
                final String secretId = secret.getString(RegistryManagementConstants.FIELD_ID);

                // No secret ID specified : create a new secret
                if (Strings.isNullOrEmpty(secretId)) {
                    secret.put(RegistryManagementConstants.FIELD_ID, UUID.randomUUID().toString());
                    definitiveInputSecret.add(secret);
                // secret ID specified
                } else {
                    // Find the corresponding secret with the given ID.
                    boolean found = false;
                    for (Object st : secretsJson) {
                        final JsonObject existingSecret = (JsonObject) st;
                        if (secretId.equals(existingSecret.getString(RegistryManagementConstants.FIELD_ID))) {
                            found = true;
                            final JsonObject newSecret = new JsonObject();
                            copySecretFields(secret, newSecret);

                            // update the secret : remove the old values.
                            if (secret.containsKey(CredentialsConstants.FIELD_SECRETS_PWD_PLAIN)
                                    || secret.containsKey(CredentialsConstants.FIELD_SECRETS_KEY)
                                    || secret.containsKey(CredentialsConstants.FIELD_SECRETS_PWD_HASH)
                                    || secret.containsKey(CredentialsConstants.FIELD_SECRETS_SALT)) {

                                removePasswordDetailsFromSecret(newSecret);
                            }
                            // then copy the new details.
                            for (String field : secret.fieldNames()) {
                                newSecret.put(field, secret.getValue(field));
                            }
                            definitiveInputSecret.add(newSecret);
                        }
                    }

                    // check if the secretID given was found
                    if (!found) {
                        TracingHelper.logError(span, "secret ID given does not exist for this auth-id and type");
                        return OperationResult.empty(HttpURLConnection.HTTP_BAD_REQUEST);
                    }
                }
            }

            dirty.set(true);

            // Now we can remove all the secrets
            secretsJson.clear();

            // Write the new secrets
            secretsJson.addAll(definitiveInputSecret);

            // Commit the update
            credentialsJson.put(CredentialsConstants.FIELD_SECRETS, secretsJson);

            credentialsForTenant.put(authId, json);
        }

        return OperationResult.ok(HttpURLConnection.HTTP_NO_CONTENT, null, Optional.empty(), Optional.of(newVersion));
    }

    /**
     * Remove all credentials that point to a device.
     *
     * @param tenantId The tenant to process.
     * @param deviceId The device id to look for.
     */
    private void removeAllForDevice(final String tenantId, final String deviceId, final Span span) {

        final ConcurrentMap<String, JsonArray> credentialsForTenant = createOrGetCredentialsForTenant(tenantId);

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

                verifyOverwriteEnabled(span);

                // remove device from credentials set
                i.remove();
                dirty.set(true);
            }
        }
    }

    @Override
    public Future<OperationResult<List<CommonCredential>>> readCredentials(final String tenantId, final String deviceId,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        final ConcurrentMap<String, JsonArray> credentialsForTenant = credentials.get(tenantId);
        if (credentialsForTenant == null) {
            TracingHelper.logError(span, "No credentials found for tenant");
            return Future.succeededFuture(OperationResult.ok(HttpURLConnection.HTTP_NOT_FOUND, null, Optional.empty(),
                    Optional.of(getOrCreateResourceVersion(tenantId, deviceId))));
        }

        final JsonArray matchingCredentials = new JsonArray();
        // iterate over all credentials per auth-id in order to find credentials matching the given device
        for (final JsonArray credentialsForAuthId : credentialsForTenant.values()) {
            findCredentialsForDevice(credentialsForAuthId, deviceId, matchingCredentials);
        }
        if (matchingCredentials.isEmpty()) {
            TracingHelper.logError(span, "No credentials found for device");
            return Future.succeededFuture(OperationResult.ok(HttpURLConnection.HTTP_NOT_FOUND, null, Optional.empty(),
                    Optional.of(getOrCreateResourceVersion(tenantId, deviceId))));
        }

        // convert credentials

        final List<CommonCredential> credentials = new ArrayList<>();
        for (final Object credential : matchingCredentials) {
            final JsonObject credentialsObject = (JsonObject) credential;
            credentialsObject.remove(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID);
            removePasswordDetailsFromCredential(credentialsObject);
            final CommonCredential cred = credentialsObject.mapTo(CommonCredential.class);
            credentials.add(cred);
        }

        return Future.succeededFuture(
                OperationResult.ok(HttpURLConnection.HTTP_OK,
                        credentials,
                        // TODO check cache directive
                        Optional.empty(),
                        Optional.of(getOrCreateResourceVersion(tenantId, deviceId))));
    }

    /**
     * Remove all the credentials for the given device ID.
     * @param tenantId the Id of the tenant which the device belongs to.
     * @param deviceId the id of the device that is deleted.
     * @param span The active OpenTracing span for this operation.
     * @param resultHandler the operation result.
     * @throws NullPointerException if any of the parameters except span is {@code null}.
     */
    public void remove(final String tenantId, final String deviceId,
            final Span span, final Handler<AsyncResult<Result<Void>>> resultHandler) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(resultHandler);

        LOG.debug("removing credentials for device [tenant-id: {}, device-id: {}]", tenantId, deviceId);

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

        final String version = versions.getOrDefault(tenantId, new ConcurrentHashMap<>()).get(deviceId);
        if (version == null) {
            // we have no version, and never told anyone, so the requested version of wrong.
            return false;
        }

        return version.equals(resourceVersion.get());
    }

    private String setResourceVersion(final String tenantId, final String deviceId, final String version) {

        if (version != null) {

            versions
                    .computeIfAbsent(tenantId, key -> new ConcurrentHashMap<>())
                    .put(deviceId, version);

        } else {

            versions.getOrDefault(tenantId, new ConcurrentHashMap<>()).remove(deviceId);

        }

        return version;

    }

    private String getOrCreateResourceVersion(final String tenantId, final String deviceId) {
        return versions.computeIfAbsent(tenantId, key -> new ConcurrentHashMap<>())
                .computeIfAbsent(deviceId, key -> UUID.randomUUID().toString());
    }

    @Override
    public Future<CredentialsResult<JsonObject>> get(final String tenantId, final String type, final String authId) {
        return get(tenantId, type, authId, NoopSpan.INSTANCE);
    }

    @Override
    public Future<CredentialsResult<JsonObject>> get(final String tenantId, final String type, final String authId,
            final JsonObject clientContext) {
        return get(tenantId, type, authId, clientContext, NoopSpan.INSTANCE);
    }

    /**
     * Create or get credentials map for a single tenant.
     *
     * @param tenantId The tenant to get
     * @return The map, never returns {@code null}.
     */
    private ConcurrentMap<String, JsonArray> createOrGetCredentialsForTenant(final String tenantId) {
        return credentials.computeIfAbsent(tenantId, id -> new ConcurrentHashMap<>());
    }

    private JsonArray createOrGetAuthIdCredentials(final String authId,
            final ConcurrentMap<String, JsonArray> credentialsForTenant) {
        return credentialsForTenant.computeIfAbsent(authId, id -> new JsonArray());
    }

    private CacheDirective getCacheDirective(final String type) {

        if (getConfig().getCacheMaxAge() > 0) {
            switch (type) {
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
        credentials.clear();
        dirty.set(true);
    }

    @Override
    public String toString() {
        return String.format("%s[filename=%s]", FileBasedCredentialsService.class.getSimpleName(), getConfig().getFilename());
    }

    /**
     * Strips the hashed-password details from the jsonObject if needed.
     */
    private static void removePasswordDetailsFromCredential(final JsonObject credential) {
        if (credential.getString(CredentialsConstants.FIELD_TYPE).equals(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD)) {

            credential.getJsonArray(CredentialsConstants.FIELD_SECRETS).forEach(secret ->
                    removePasswordDetailsFromSecret((JsonObject) secret));
        }
    }

    private static void removePasswordDetailsFromSecret(final JsonObject secret) {

        secret.remove(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION);
        secret.remove(CredentialsConstants.FIELD_SECRETS_PWD_HASH);
        secret.remove(CredentialsConstants.FIELD_SECRETS_SALT);
        secret.remove(CredentialsConstants.FIELD_SECRETS_PWD_PLAIN);
        secret.remove(CredentialsConstants.FIELD_SECRETS_KEY);
    }

    private static void copySecretFields(final JsonObject in, final JsonObject out) {

        out.put(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION, in.getString(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION));
        out.put(CredentialsConstants.FIELD_SECRETS_PWD_HASH, in.getString(CredentialsConstants.FIELD_SECRETS_PWD_HASH));
        out.put(CredentialsConstants.FIELD_SECRETS_SALT, in.getString(CredentialsConstants.FIELD_SECRETS_SALT));
        out.put(CredentialsConstants.FIELD_SECRETS_PWD_PLAIN, in.getString(CredentialsConstants.FIELD_SECRETS_PWD_PLAIN));
    }

    private void verifyOverwriteEnabled(final Span span) {

        // check if we may overwrite
        if (!config.isModificationEnabled()) {
            TracingHelper.logError(span, "Modification is disabled for the Credentials service.");
            throw new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN);
        }
    }
}
