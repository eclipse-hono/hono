/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.test.pubsub;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.pubsub.PubSubBasedAdminClientManager;
import org.eclipse.hono.client.pubsub.PubSubConfigProperties;
import org.eclipse.hono.client.pubsub.PubSubQuarkusOptions;

import com.google.api.gax.core.NoCredentialsProvider;

import io.vertx.core.Vertx;

/**
 * A class to set up topics and subscription for local pubsub emulator.
 */
public class PubSubEmulatorSetup {

    private PubSubEmulatorSetup() {

    }

    /**
     * main used to set up the topics / subscription in the emulator.
     * @param args -
     * @throws IOException -
     */
    public static void main(final String[] args) throws IOException {
        final String projectId = "local-test-project";
        final String hostPort = "localhost:8085"; // Emulator address
        final NoCredentialsProvider credentialsProvider = NoCredentialsProvider.create();
        final var pubSubQuarkusOptions = new PubSubQuarkusOptions() {
            @Override
            public Optional<String> projectId() {
                return Optional.of(projectId);
            }

            @Override
            public PubSubQuarkusOptions.PubSubConfig pubsub() {
                return () -> Optional.of(hostPort);
            }
        };
        final var vertx = Vertx.vertx();
        // Use try-with-resources to automatically close the clients
        final PubSubBasedAdminClientManager clientManager = new PubSubBasedAdminClientManager(new PubSubConfigProperties(pubSubQuarkusOptions), credentialsProvider, vertx);
        try {
            clientManager.getOrCreateTopic("notification", "registry-tenant");
            clientManager.getOrCreateTopic("notification", "registry-device");
            clientManager.getOrCreateSubscription("notification", "registry-tenant");
            clientManager.getOrCreateSubscription("notification", "registry-device");
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            System.out.println((e.getMessage()));
        }
        try {
            clientManager.closeAdminClients();
            TimeUnit.SECONDS.sleep(1);
            vertx.close();
            TimeUnit.SECONDS.sleep(1);
        } catch (Exception e) {
            System.out.println((e.getMessage()));
        }
    }
}
