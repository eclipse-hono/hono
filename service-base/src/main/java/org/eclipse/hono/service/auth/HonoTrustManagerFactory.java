/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.service.auth;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Provider;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A factory for creating Hono specific {@code TrustManager}s, e.g for authenticating
 * devices based on X.509 client certificates using information from the Tenan API.
 *
 */
public class HonoTrustManagerFactory extends TrustManagerFactory {

    static final Logger LOG = LoggerFactory.getLogger(HonoTrustManagerFactory.class);
    private static final Provider PROVIDER = new Provider("Hono", 0.0, "") {

        private static final long serialVersionUID = 1L;
    };

    /**
     * Creates a new factory for configuration properties.
     * 
     * @param trustManagers The client to use for accessing the Tenant service.
     */
    public HonoTrustManagerFactory(final TrustManager... trustManagers) {

        super(new TrustManagerFactorySpi() {

            @Override
            protected void engineInit(final ManagerFactoryParameters spec) throws InvalidAlgorithmParameterException {
                // nothing to do
            }

            @Override
            protected void engineInit(final KeyStore ks) throws KeyStoreException {
                // nothing to do
            }

            @Override
            protected TrustManager[] engineGetTrustManagers() {
                return trustManagers.clone();
            }
        }, PROVIDER, "");
    }
}
