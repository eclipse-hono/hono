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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.eclipse.hono.auth.HonoPasswordEncoder;
import org.eclipse.hono.deviceregistry.service.credentials.AbstractCredentialsManagementService;
import org.eclipse.hono.deviceregistry.service.device.DeviceKey;
import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.RegistryManagementConstants;
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
public final class FileBasedCredentialsService extends AbstractCredentialsManagementService implements CredentialsService, Lifecycle {

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
    private final Map<String, Map<String, JsonArray>> credentials = new ConcurrentHashMap<>();
    // <tenantId, <deviceId, version>>
    private final Map<String, Map<String, String>> versions = new ConcurrentHashMap<>();

    private AtomicBoolean running = new AtomicBoolean(false);
    private AtomicBoolean dirty = new AtomicBoolean(false);
    private FileBasedCredentialsConfigProperties config;

    /**
     * Creates a new service instance.
     *
     * @param vertx The vert.x instance to run on.
     * @param configuration The service's configuration properties.
     * @param passwordEncoder The password encoder.
     * @throws NullPointerException if any of the parameters are {@code null};
     */
    @Autowired
    public FileBasedCredentialsService(
            final Vertx vertx,
            final FileBasedCredentialsConfigProperties configuration,
            final HonoPasswordEncoder passwordEncoder) {
        super(vertx, passwordEncoder, configuration.getMaxBcryptCostFactor(), configuration.getHashAlgorithmsWhitelist());
        this.config = configuration;
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

    /**
     * Gets all credentials for a device.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @return A list containing copies of the credentials objects.
     */
    private List<JsonObject> findCredentialsForDevice(final String tenantId, final String deviceId) {

        final List<JsonObject> result = new ArrayList<>();
        getCredentialsForTenant(tenantId).entrySet().forEach(entry -> {
            entry.getValue().stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .filter(obj -> deviceId.equals(getTypesafeValueForField(
                        String.class,
                        obj,
                        CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID)))
                // device ID matches, add a copy of credentials to result
                .map(JsonObject::copy)
                .forEach(result::add);
        });
        return result;
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
     * Get the credentials associated with the authId and the given type.
     *
     * @param tenantId The id of the tenant the credentials belong to.
     * @param authId The authentication identifier to look up credentials for.
     * @param type The type of credentials to look up.
     * @param span The active OpenTracing span for this operation.
     * @return The credentials object of the given type or {@code null} if no matching credentials exist.
     */
    private JsonObject getSingleCredentials(
            final String tenantId,
            final String authId,
            final String type,
            final JsonObject clientContext,
            final Span span) {

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
            final JsonArray secrets = authIdCredentialCopy.getJsonArray(CredentialsConstants.FIELD_SECRETS, new JsonArray())
                    .stream()
                    .filter(JsonObject.class::isInstance)
                    .map(JsonObject.class::cast)
                    .filter(secret -> secret.getBoolean(CredentialsConstants.FIELD_ENABLED, true))
                    .collect(JsonArray::new, JsonArray::add, JsonArray::addAll);

            authIdCredentialCopy.put(CredentialsConstants.FIELD_SECRETS, secrets);

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

    /**
     * {@inheritDoc}
     */
    @Override
    protected Future<OperationResult<Void>> processUpdateCredentials(
            final DeviceKey key,
            final Optional<String> resourceVersion,
            final List<CommonCredential> credentials,
            final Span span) {

        return Future.succeededFuture(set(key.getTenantId(), key.getDeviceId(), resourceVersion, span, credentials));

    }

    private OperationResult<Void> set(
            final String tenantId,
            final String deviceId,
            final Optional<String> resourceVersion,
            final Span span,
            final List<CommonCredential> updatedCredentials) {

        final String currentVersion = getResourceVersion(tenantId, deviceId);
        if (!checkResourceVersion(currentVersion, resourceVersion)) {
            TracingHelper.logError(span, "resource version mismatch");
            return OperationResult.empty(HttpURLConnection.HTTP_PRECON_FAILED);
        }

        // authId->credentials[]
        final Map<String, JsonArray> credentialsForTenant = getCredentialsForTenant(tenantId);

        // authid->credentials
        final Map<String, JsonArray> newCredentials = new ConcurrentHashMap<>();

        // now follow the algorithm described by the Device Registry Management API
        // https://www.eclipse.org/hono/docs/api/management/#/credentials/setAllCredentials

        for (final CommonCredential credential : updatedCredentials) {

            final JsonObject credentialObject = JsonObject.mapFrom(credential);
            credentialObject.put(RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId);

            // find credentials - matching by type and auth-id
            final String type = credential.getType();
            final String authId = credential.getAuthId();
            final JsonArray credentialsForAuthId = getCredentialsForAuthId(credentialsForTenant, authId);
            final JsonObject existingCredentials = credentialsForAuthId.stream()
                    .filter(JsonObject.class::isInstance)
                    .map(JsonObject.class::cast)
                    .filter(j -> type.equals(j.getString(RegistryManagementConstants.FIELD_TYPE)))
                    .findAny()
                    .orElse(null);

            if (existingCredentials != null) {

                if (!deviceId.equals(existingCredentials.getString(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID))) {
                    // found an entry for another device, with the same auth-id
                    TracingHelper.logError(span, "auth-id already in use with another device of the tenant");
                    return OperationResult.empty(HttpURLConnection.HTTP_CONFLICT);
                } else if (!config.isModificationEnabled()) {
                    TracingHelper.logError(span, "modification is disabled for the Credentials service");
                    return OperationResult.empty(HttpURLConnection.HTTP_FORBIDDEN);
                }

                // we have found an existing credential for the device that should be updated

                // merge existing secrets into updated secrets, if necessary
                final JsonArray existingSecrets = existingCredentials.getJsonArray(CredentialsConstants.FIELD_SECRETS, new JsonArray());
                final JsonArray updatedSecrets = credentialObject.getJsonArray(CredentialsConstants.FIELD_SECRETS, new JsonArray());

                for (final Object obj : updatedSecrets) {

                    final JsonObject updatedSecret = (JsonObject) obj;
                    final String secretId = updatedSecret.getString(RegistryManagementConstants.FIELD_ID);

                    if (secretId != null) {
                        // Find the corresponding secret with the given ID.
                        final JsonObject existingSecretWithId = existingSecrets.stream()
                                .filter(JsonObject.class::isInstance).map(JsonObject.class::cast)
                                .filter(existingSecret -> secretId.equals(existingSecret.getString(RegistryManagementConstants.FIELD_ID)))
                                .findAny().orElse(null);

                        if (existingSecretWithId == null) {
                            TracingHelper.logError(span, "no secret with given ID found for credentials");
                            return OperationResult.empty(HttpURLConnection.HTTP_BAD_REQUEST);
                        }

                        mergeSecretProperties(type, existingSecretWithId, updatedSecret);
                    }
                }

            }

            // make sure every secret has or gets a unique ID
            if (hasUniqueSecretIds(credentialObject)) {
                newCredentials.computeIfAbsent(authId, key -> new JsonArray()).add(credentialObject);
                dirty.set(true);
            } else {
                TracingHelper.logError(span, "secret IDs must be unique within each credentials object");
                LOG.debug("secret IDs must be unique within each credentials object");
                return OperationResult.empty(HttpURLConnection.HTTP_BAD_REQUEST);
            }

        }

        // delete all existing credentials registered for the device in order to be sure to
        // only keep the credentials contained in the request body on record
        Optional.ofNullable(currentVersion).ifPresent(ver -> removeAllForDevice(tenantId, deviceId, span));
        // now add the new/updated credentials to the store
        credentialsForTenant.putAll(newCredentials);

        // and change the resource version
        final String newVersion = DeviceRegistryUtils.getUniqueIdentifier();
        setResourceVersion(tenantId, deviceId, newVersion);
        return OperationResult.ok(HttpURLConnection.HTTP_NO_CONTENT, null, Optional.empty(), Optional.of(newVersion));
    }

    private boolean hasUniqueSecretIds(final JsonObject credentialObject) {
        final JsonArray secrets = credentialObject.getJsonArray(RegistryManagementConstants.FIELD_SECRETS, new JsonArray());
        final long distinctIds = secrets.stream()
                .map(JsonObject.class::cast)
                .map(secret -> {
                    final String id = secret.getString(RegistryManagementConstants.FIELD_ID);
                    if (id == null) {
                        final String newId = DeviceRegistryUtils.getUniqueIdentifier();
                        secret.put(RegistryManagementConstants.FIELD_ID, newId);
                        return newId;
                    } else {
                        return id;
                    }
                })
                .distinct()
                .count();
        return distinctIds == secrets.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Future<OperationResult<List<CommonCredential>>> processReadCredentials(
            final DeviceKey key,
            final Span span) {

        Objects.requireNonNull(key);
        Objects.requireNonNull(span);

        final String tenantId = key.getTenantId();
        final String deviceId = key.getDeviceId();

        final String currentVersion = getResourceVersion(tenantId, deviceId);

        // if we do not have a resource version on record for the device
        // then either no credentials have ever been registered for the device or
        // the device has been deleted
        if (currentVersion == null) {
            return Future.succeededFuture(OperationResult.ok(
                    HttpURLConnection.HTTP_NOT_FOUND,
                    null,
                    Optional.of(CacheDirective.noCacheDirective()),
                    Optional.empty()));
        }

        final List<CommonCredential> result = findCredentialsForDevice(tenantId, deviceId)
                .stream()
                .map(creds -> {
                    creds.remove(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID);
                    return creds.mapTo(CommonCredential.class);
                })
                .map(CommonCredential::stripPrivateInfo)
                .collect(Collectors.toList());

        // resolve cache directive for results.
        // Limit caching to the case when cache directives are the same for all results
        final Set<CacheDirective> cacheDirectives = result.stream().map(CommonCredential::getType)
                .distinct()
                .map(this::getCacheDirective)
                .collect(Collectors.collectingAndThen(Collectors.toUnmodifiableSet(),
                        // use cache options for hashed password when results is empty so that empty result gets cached
                        set -> set.isEmpty() ? Set.of(getCacheDirective(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD)) : set));
        final Optional<CacheDirective> cacheDirective = cacheDirectives.size() == 1 ?
                cacheDirectives.stream().findFirst() : Optional.empty();

        // note that the result set might be empty
        // however, in this case an empty set of credentials has been
        // registered for the device explicitly and we return
        // a response with status 200 and an empty JSON array in the body
        return Future.succeededFuture(
                OperationResult.ok(HttpURLConnection.HTTP_OK,
                        result,
                        cacheDirective,
                        Optional.of(currentVersion)));
    }

    /**
     * Removes all credentials for a device.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param span The active OpenTracing span for this operation.
     * @param resultHandler The operation result.
     * @throws NullPointerException if any of the parameters except span is {@code null}.
     */
    public void remove(
            final String tenantId,
            final String deviceId,
            final Span span,
            final Handler<AsyncResult<Result<Void>>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(resultHandler);

        LOG.debug("removing credentials for device [tenant-id: {}, device-id: {}]", tenantId, deviceId);

        final Promise<Result<Void>> result = Promise.promise();

        if (config.isModificationEnabled()) {
            setResourceVersion(tenantId, deviceId, null);
            removeAllForDevice(tenantId, deviceId, span);
            result.complete(Result.from(HttpURLConnection.HTTP_NO_CONTENT));
        } else {
            TracingHelper.logError(span, "modification is disabled for the Credentials service");
            result.complete(OperationResult.empty(HttpURLConnection.HTTP_FORBIDDEN));
        }
        resultHandler.handle(result.future());
    }

    /**
     * Remove all credentials that point to a device.
     *
     * @param tenantId The tenant to process.
     * @param deviceId The device id to look for.
     */
    private void removeAllForDevice(final String tenantId, final String deviceId, final Span span) {

        final Map<String, JsonArray> credentialsForTenant = getCredentialsForTenant(tenantId);

        for (final JsonArray versionedCredentials : credentialsForTenant.values()) {

            for (final Iterator<Object> i = versionedCredentials.iterator(); i.hasNext();) {

                final Object o = i.next();

                if (!(o instanceof JsonObject)) {
                    continue;
                }

                final JsonObject credentials = (JsonObject) o;
                final String currentDeviceId = credentials.getString(Constants.JSON_FIELD_DEVICE_ID);
                if (deviceId.equals(currentDeviceId)) {
                    // remove credentials from credentials set
                    i.remove();
                    dirty.set(true);
                }
            }
        }
    }

    private boolean checkResourceVersion(final String tenantId, final String deviceId, final Optional<String> requestedVersion) {

        if (requestedVersion.isEmpty()) {
            return true;
        }

        return checkResourceVersion(getResourceVersion(tenantId, deviceId), requestedVersion);
    }

    private boolean checkResourceVersion(final String currentVersion, final Optional<String> requestedVersion) {

        if (requestedVersion.isEmpty()) {
            return true;
        }

        if (currentVersion == null) {
            return false;
        }

        return currentVersion.equals(requestedVersion.get());
    }

    private String getResourceVersion(final String tenantId, final String deviceId) {
        return versions.computeIfAbsent(tenantId, key -> new ConcurrentHashMap<>()).get(deviceId);
    }

    /**
     * Sets the version of a device's credentials resource.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param version The resource version or {@code null} to remove the resource version.
     * @return The resource version.
     */
    private String setResourceVersion(final String tenantId, final String deviceId, final String version) {

        if (version != null) {

            versions.computeIfAbsent(tenantId, key -> new ConcurrentHashMap<>())
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
     * Gets all credentials registered for a tenant.
     *
     * @param tenantId The tenant to get
     * @return A mapping of authentication identifiers of the tenant's devices to the credentials
     *         registered for the auth-ids.
     */
    private Map<String, JsonArray> getCredentialsForTenant(final String tenantId) {
        return credentials.computeIfAbsent(tenantId, id -> new ConcurrentHashMap<>());
    }

    private JsonArray getCredentialsForAuthId(
            final Map<String, JsonArray> credentials,
            final String authId) {
        return credentials.computeIfAbsent(authId, id -> new JsonArray());
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

    private static void mergeSecretProperties(final String type, final JsonObject sourceSecret, final JsonObject targetSecret) {

        switch (type) {
        case RegistryManagementConstants.SECRETS_TYPE_HASHED_PASSWORD:
            copyPasswordSecretProperties(sourceSecret, targetSecret);
            break;
        case RegistryManagementConstants.SECRETS_TYPE_PRESHARED_KEY:
            if (!targetSecret.containsKey(RegistryManagementConstants.FIELD_SECRETS_KEY)) {
                Optional.ofNullable(sourceSecret.getString(RegistryManagementConstants.FIELD_SECRETS_KEY))
                    .ifPresent(s -> targetSecret.put(RegistryManagementConstants.FIELD_SECRETS_KEY, s));
            }
            break;
        case RegistryManagementConstants.SECRETS_TYPE_X509_CERT:
            // nothing to merge
            // fall through
        default:
            // do nothing
        }
    }

    private static void copyPasswordSecretProperties(final JsonObject sourceSecret, final JsonObject targetSecret) {
        if (targetSecret.containsKey(RegistryManagementConstants.FIELD_ID)
                && !targetSecret.containsKey(RegistryManagementConstants.FIELD_SECRETS_HASH_FUNCTION)
                && !targetSecret.containsKey(RegistryManagementConstants.FIELD_SECRETS_PWD_HASH)
                && !targetSecret.containsKey(RegistryManagementConstants.FIELD_SECRETS_PWD_PLAIN)
                && !targetSecret.containsKey(RegistryManagementConstants.FIELD_SECRETS_SALT)) {

            // copy confidential password properties
            Optional.ofNullable(sourceSecret.getString(RegistryManagementConstants.FIELD_SECRETS_HASH_FUNCTION))
                .ifPresent(s -> targetSecret.put(RegistryManagementConstants.FIELD_SECRETS_HASH_FUNCTION, s));
            Optional.ofNullable(sourceSecret.getString(RegistryManagementConstants.FIELD_SECRETS_PWD_HASH))
                .ifPresent(s -> targetSecret.put(RegistryManagementConstants.FIELD_SECRETS_PWD_HASH, s));
            Optional.ofNullable(sourceSecret.getString(RegistryManagementConstants.FIELD_SECRETS_SALT))
                .ifPresent(s -> targetSecret.put(RegistryManagementConstants.FIELD_SECRETS_SALT, s));
        }
    }
}
