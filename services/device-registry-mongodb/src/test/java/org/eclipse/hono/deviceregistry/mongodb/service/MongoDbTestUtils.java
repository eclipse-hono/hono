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

import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;

/**
 * Utility class to initialize and tear down embedded mongodb.
 */
public final class MongoDbTestUtils {
    //The documentation suggests to make the MongodStarter instance static to
    // cache the extracted executables and library files.
    // For more info https://github.com/flapdoodle-oss/de.flapdoodle.embed.mongo
    private static final MongodStarter DEFAULT_MONGOD_STARTER = MongodStarter.getDefaultInstance();

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

        mongodExecutable = DEFAULT_MONGOD_STARTER.prepare(new MongodConfigBuilder()
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
