/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry.jdbc;

import io.vertx.core.Future;

/**
 * Provides support to automatically create the expected database schema if it does not exist.
 */
public interface SchemaCreator {

    /**
     * Creates the database schema.
     *
     * @return A future indicating the outcome of the creation attempt.
     */
    Future<Void> createDbSchema();
}
