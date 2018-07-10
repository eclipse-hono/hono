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
    public TrustOptions clone() {
        return new ValidityBasedTrustOptions();
    }

    @Override
    public TrustManagerFactory getTrustManagerFactory(final Vertx vertx) {

        return factory;
    }
}
