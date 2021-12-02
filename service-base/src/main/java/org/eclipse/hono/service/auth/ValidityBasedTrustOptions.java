/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.auth;

import java.util.function.Function;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import io.vertx.core.Vertx;
import io.vertx.core.net.TrustOptions;


/**
 * Options for trusting certificates which are valid at the current moment
 * according to their <em>not before</em> and <em>not after</em> properties.
 */
public class ValidityBasedTrustOptions implements TrustOptions {

    private final ValidityOnlyTrustManagerFactory factory;

    /**
     * Creates new options.
     */
    public ValidityBasedTrustOptions() {
        this.factory = new ValidityOnlyTrustManagerFactory();
    }

    @Override
    public TrustOptions copy() {
        return new ValidityBasedTrustOptions();
    }

    @Override
    public TrustManagerFactory getTrustManagerFactory(final Vertx vertx) {

        return factory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Function<String, TrustManager[]> trustManagerMapper(final Vertx vertx) throws Exception {
        return sniName -> null;
    }
}
