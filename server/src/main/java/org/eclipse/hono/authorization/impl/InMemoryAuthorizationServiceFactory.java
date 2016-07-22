/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.hono.authorization.AuthorizationService;
import org.eclipse.hono.util.VerticleFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import io.vertx.core.json.JsonObject;

/**
 * A factory for creating {@link InMemoryAuthorizationService} instances configured
 * using Spring Boot.
 *
 */
@Component
public class InMemoryAuthorizationServiceFactory implements VerticleFactory<AuthorizationService> {

    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryAuthorizationServiceFactory.class);

    @Value(value = "${hono.singletenant:false}")
    private boolean singleTenant;

//    @Value(value = "${hono.permissions.path:/config/permissions.json}")
//    private String permissionsPath;

    @Value(value = "${hono.permissions.path:/permissions.json}")
    private Resource permissionFile;

    @Override
    public AuthorizationService newInstance() {
        return newInstance(0, 1);
    }

    @Override
    public AuthorizationService newInstance(int instanceId, int totalNoOfInstances) {
        try {
            JsonObject permissions = loadPermissions();
            return new InMemoryAuthorizationService(instanceId, totalNoOfInstances, singleTenant, permissions);
        } catch (IOException e) {
            LOGGER.warn("cannot load permissions from resource [{}], falling back to default config from classpath", permissionFile, e);
            return new InMemoryAuthorizationService(singleTenant);
        }
    }

    private JsonObject loadPermissions() throws IOException {
        Path path = Paths.get(permissionFile.getURI());
        LOGGER.info("loading permissions from: {}", path.toAbsolutePath());
        final String permissionsJson = new String(Files.readAllBytes(path), UTF_8);
        return new JsonObject(permissionsJson);
    }

    @Override
    public String toString() {
        return new StringBuilder("InMemoryAuthorizationServiceFactory{permissionsFile='")
                .append(permissionFile.getFilename()).append("', singleTenant=").append(singleTenant)
                .append("}").toString();
    }
}
