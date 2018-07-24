/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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


/**
 * Configuration properties for Hono's credentials API as own server.
 *
 */
public final class FileBasedCredentialsConfigProperties extends AbstractFileBasedRegistryConfigProperties {

    /**
     * The default name of the file that the registry persists credentials to.
     */
    private static final String DEFAULT_CREDENTIALS_FILENAME = "/var/lib/hono/device-registry/credentials.json";


    /**
     * {@inheritDoc}
     */
    @Override
    protected String getDefaultFileName() {
        return DEFAULT_CREDENTIALS_FILENAME;
    }
}
