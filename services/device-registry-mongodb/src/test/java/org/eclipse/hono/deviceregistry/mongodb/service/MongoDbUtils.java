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
package org.eclipse.hono.deviceregistry.mongodb.service;

import java.io.IOException;
import java.util.UUID;

import de.flapdoodle.embed.mongo.Command;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.DownloadConfigBuilder;
import de.flapdoodle.embed.mongo.config.ExtractedArtifactStoreBuilder;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.config.RuntimeConfigBuilder;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.config.IRuntimeConfig;
import de.flapdoodle.embed.process.extract.UUIDTempNaming;
import de.flapdoodle.embed.process.io.directories.FixedPath;
import de.flapdoodle.embed.process.io.directories.IDirectory;
import de.flapdoodle.embed.process.runtime.Network;

/**
 * Utility class to initialize and tear down embedded mongodb.
 */
public final class MongoDbUtils {

    private MongodExecutable mongodExecutable;
    private MongodProcess mongodProcess;

    /**
     * Initializes embedded mongodb.
     *
     * @param host the mongodb hostname.
     * @param port the mongodb port.
     * @throws IOException if the embedded mongodb cannot be initialised.
     */
    public void startEmbeddedMongoDb(final String host, final int port) throws IOException {
        final IDirectory artifactStorePath = new FixedPath(
                "target/embeddedMongodbCustomPath-" + UUID.randomUUID().toString());
        final IDirectory extractDir = new FixedPath("target/extractMongodbCustomPath-" + UUID.randomUUID().toString());
        final Command command = Command.MongoD;
        final IRuntimeConfig runtimeConfig = new RuntimeConfigBuilder()
                .defaults(command)
                .artifactStore(new ExtractedArtifactStoreBuilder()
                        .defaults(command)
                        .download(new DownloadConfigBuilder()
                                .defaultsForCommand(command)
                                .artifactStorePath(artifactStorePath).build())
                        .extractDir(extractDir)
                        .executableNaming(new UUIDTempNaming()))
                .build();
        mongodExecutable = MongodStarter.getInstance(runtimeConfig)
                .prepare(new MongodConfigBuilder()
                        .version(Version.Main.PRODUCTION)
                        .net(new Net(host, port, Network.localhostIsIPv6()))
                        .build());
        mongodProcess = mongodExecutable.start();
    }

    /**
     * Tears down the embedded mongo db.
     */
    public void stopEmbeddedMongoDb() {
        mongodProcess.stop();
        mongodExecutable.stop();
    }
}
