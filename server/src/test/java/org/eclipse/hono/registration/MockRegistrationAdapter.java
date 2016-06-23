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
package org.eclipse.hono.registration;

import java.net.HttpURLConnection;

import org.eclipse.hono.registration.impl.BaseRegistrationAdapter;

import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

/**
 * Simple mock implementation of RegistrationAdapter that always returns success.
 */
public class MockRegistrationAdapter extends BaseRegistrationAdapter {
    @Override
    public void processRegistrationMessage(final Message<JsonObject> regMsg) {
        reply(regMsg, HttpURLConnection.HTTP_OK);
    }
}