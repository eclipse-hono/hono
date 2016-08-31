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
package org.eclipse.hono.server;

import java.util.Objects;

import io.vertx.core.Vertx;
import io.vertx.proton.sasl.ProtonSaslAuthenticator;
import io.vertx.proton.sasl.ProtonSaslAuthenticatorFactory;

/**
 * A factory for objects performing SASL authentication on an AMQP connection.
 */
public final class PlainSaslAuthenticatorFactory implements ProtonSaslAuthenticatorFactory {

    private Vertx vertx;

    /**
     * Creates a new factory for a Vertx environment.
     * 
     * @param vertx the Vertx environment to run the factory in.
     */
    public PlainSaslAuthenticatorFactory(final Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
    }

    @Override
    public ProtonSaslAuthenticator create() {
        return new PlainSaslAuthenticator(vertx);
    }
}
