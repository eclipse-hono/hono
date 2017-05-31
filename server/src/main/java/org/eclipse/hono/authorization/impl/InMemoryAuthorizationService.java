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
package org.eclipse.hono.authorization.impl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.ofNullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.eclipse.hono.auth.Activity;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.service.auth.AccessControlList;
import org.eclipse.hono.service.auth.AclEntry;
import org.eclipse.hono.service.auth.BaseAuthorizationService;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Implementation of AuthorizationService that holds acl data in memory i.e. no persistent storage.
 */
@Component
@ConfigurationProperties(prefix = "hono.authorization")
public final class InMemoryAuthorizationService extends BaseAuthorizationService {

    static final Resource DEFAULT_PERMISSIONS_RESOURCE = new ClassPathResource("permissions.json");
    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryAuthorizationService.class);
    // holds mapping resource -> acl
    private static final ConcurrentMap<ResourceEntry, AccessControlList> resources = new ConcurrentHashMap<>();
    private Resource permissionsResource = DEFAULT_PERMISSIONS_RESOURCE;

    /**
     * Set the resource that the authorization rules should be loaded from.
     * <p>
     * If not set the default permissions will be loaded from <em>classpath:permissions.json</em>.
     * 
     * @param permissionsResource The resource.
     */
    public void setPermissionsPath(final Resource permissionsResource) {
        this.permissionsResource = Objects.requireNonNull(permissionsResource);
    }

    @Override
    protected void doStart(final Future<Void> startFuture) {
        try {
            loadPermissions();
            startFuture.complete();
        } catch (IOException e) {
            LOGGER.error("cannot load permissions from resource {}", permissionsResource, e);
            startFuture.fail(e);
        }
    }

    @Override
    public Future<Boolean> isAuthorized(final HonoUser user, final ResourceIdentifier resource, final Activity intent) {

        Objects.requireNonNull(user);
        Objects.requireNonNull(resource);
        Objects.requireNonNull(intent);

        Future<Boolean> result = Future.future();
        result.complete(hasPermissionInternal(user.getName(), ResourceIdentifier.from(resource.getEndpoint(), "*", null), intent) ||
                hasPermissionInternal(user.getName(), ResourceIdentifier.from(resource.getEndpoint(), resource.getTenantId(), null), intent) ||
                hasPermissionInternal(user.getName(), resource, intent));
        return result;
    }

    private boolean hasPermissionInternal(final String subject, final ResourceIdentifier resource, final Activity permission) {

        return hasPermissionInternal(subject, new ResourceEntry(resource, null), permission);
    }

    @Override
    public Future<Boolean> isAuthorized(final HonoUser user, final ResourceIdentifier resource, final String operation) {

        Objects.requireNonNull(user);
        Objects.requireNonNull(resource);
        Objects.requireNonNull(operation);

        Future<Boolean> result = Future.future();
        result.complete(hasPermissionInternal(user.getName(), ResourceIdentifier.from(resource.getEndpoint(), "*", null), operation, Activity.EXECUTE) ||
                hasPermissionInternal(user.getName(), ResourceIdentifier.from(resource.getEndpoint(), resource.getTenantId(), null), operation, Activity.EXECUTE) ||
                hasPermissionInternal(user.getName(), resource, operation, Activity.EXECUTE));
        return result;
    }

    private boolean hasPermissionInternal(final String subject, final ResourceIdentifier resource, final String operation, final Activity permission) {

        return hasPermissionInternal(subject, new ResourceEntry(resource, operation), permission) ||
                hasPermissionInternal(subject, new ResourceEntry(resource, "*"), permission);
    }

    private boolean hasPermissionInternal(final String subject, final ResourceEntry resource, final Activity permission) {
        LOGGER.debug("checking [subject: {}, resource: {}, permission: {}]", subject, resource, permission);
        return ofNullable(resources.get(resource)).map(acl -> acl.hasPermission(subject, permission)).orElse(false);
    }

    void addPermission(final String subject, final ResourceIdentifier resource, final Activity first, final Activity... rest) {
        Objects.requireNonNull(first, "permission is required");
        final EnumSet<Activity> permissions = EnumSet.of(first, rest);
        addPermission(subject, new ResourceEntry(resource), permissions);
    }

    void addPermission(final String subject, final ResourceIdentifier resource, final String operation, final Activity first, final Activity... rest) {
        Objects.requireNonNull(first, "permission is required");
        final EnumSet<Activity> permissions = EnumSet.of(first, rest);
        addPermission(subject, new ResourceEntry(resource, operation), permissions);
    }

    void addPermission(final String subject, final ResourceEntry resource, final Activity first,
            final Activity... rest) {
        Objects.requireNonNull(first, "permission is required");
        final EnumSet<Activity> permissions = EnumSet.of(first, rest);
        addPermission(subject, resource, permissions);
    }

    void addPermission(final String subject, final ResourceEntry resource, final Set<Activity> permissions) {
        Objects.requireNonNull(subject);
        Objects.requireNonNull(resource);
        Objects.requireNonNull(permissions);

        LOGGER.trace("adding permissions {} for subject {} on resource [address: {}, operation: {}]", permissions, subject, resource.getResource(), resource.getOperation());
        resources.computeIfAbsent(resource, key -> new AccessControlList())
                .setAclEntry(new AclEntry(subject, permissions));
    }

    void loadPermissions() throws IOException {
        if (permissionsResource == null) {
            throw new IllegalStateException("permissions resource is not set");
        }
        if (permissionsResource.isReadable()) {
            LOGGER.info("loading permissions from resource {}", permissionsResource.getURI().toString());
            StringBuilder json = new StringBuilder();
            load(permissionsResource, json);
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
        permissionsObject
        .stream().filter(entry -> entry.getValue() instanceof JsonObject)
        .forEach(resourceSpec -> {
            final ResourceEntry resourceEntry = getResourceEntry(resourceSpec);
            final JsonObject subjects = (JsonObject) resourceSpec.getValue();
            subjects
            .stream().filter(subject -> subject.getValue() instanceof JsonArray)
            .forEach(subject -> {
                final JsonArray permissions = (JsonArray) subject.getValue();
                addPermission(subject.getKey(), resourceEntry, toSet(permissions));
            });
        });
    }

    private ResourceEntry getResourceEntry(final Map.Entry<String, Object> resourceSpec) {

        String resource = resourceSpec.getKey();
        String op = null;
        int separatorIdx = resourceSpec.getKey().lastIndexOf(":");
        if (separatorIdx != -1) {
            resource = resourceSpec.getKey().substring(0, separatorIdx);
            op = resourceSpec.getKey().substring(separatorIdx + 1);
        }

        if (getConfig().isSingleTenant()) {
            return new ResourceEntry(ResourceIdentifier.fromStringAssumingDefaultTenant(resource), op);
        } else {
            return new ResourceEntry(ResourceIdentifier.fromString(resource), op);
        }
    }

    private Set<Activity> toSet(final JsonArray array) {
        return array.stream()
                .filter(element -> element instanceof String)
                .map(element -> (String) element)
                .map(Activity::valueOf)
                .collect(Collectors.<Activity>toSet());
    }

    private static class ResourceEntry {

        final ResourceIdentifier resource;
        final String operation;

        /**
         * Creates an entry for a resource.
         * 
         * @param resource
         */
        public ResourceEntry(final ResourceIdentifier resource) {
            this(resource, null);
        }

        /**
         * Creates an entry for a resource and operation.
         * 
         * @param resource
         * @param operation
         */
        public ResourceEntry(final ResourceIdentifier resource, final String operation) {
            this.resource = Objects.requireNonNull(resource);
            this.operation = operation;
        }

        
        /**
         * @return The resource.
         */
        public final ResourceIdentifier getResource() {
            return resource;
        }

        @Override
        public String toString() {
            return "ResourceEntry[resource: " + resource + ", operation: " + operation + "]";
        }

        /**
         * @return The operation.
         */
        public final String getOperation() {
            return operation;
        }


        /* (non-Javadoc)
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((operation == null) ? 0 : operation.hashCode());
            result = prime * result + ((resource == null) ? 0 : resource.hashCode());
            return result;
        }


        /* (non-Javadoc)
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            ResourceEntry other = (ResourceEntry) obj;
            if (operation == null) {
                if (other.operation != null) {
                    return false;
                }
            } else if (!operation.equals(other.operation)) {
                return false;
            }
            if (resource == null) {
                if (other.resource != null) {
                    return false;
                }
            } else if (!resource.equals(other.resource)) {
                return false;
            }
            return true;
        }

    }
}
