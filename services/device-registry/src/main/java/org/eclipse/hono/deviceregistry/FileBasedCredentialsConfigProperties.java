/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

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
