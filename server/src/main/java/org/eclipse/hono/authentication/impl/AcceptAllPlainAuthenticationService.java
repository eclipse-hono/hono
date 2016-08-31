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
package org.eclipse.hono.authentication.impl;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;

/**
 * A PLAIN SASL authenticator that accepts all credentials.
 */
public final class AcceptAllPlainAuthenticationService extends BaseAuthenticationService {

    private static final String PLAIN = "PLAIN";
    private static final Logger LOG = LoggerFactory.getLogger(AcceptAllPlainAuthenticationService.class);

    /**
     * Creates a new authentication service instance with instance ID {@code 0}
     * supporting multiple tenants.
     */
    public AcceptAllPlainAuthenticationService() {
        this(0, 1);
    }

    /**
     * Creates a new authentication service.
     * 
     * @param instanceId the ID to use for this instance.
     * @param totalNoOfInstances the total number of instances that will be used.
     */
    public AcceptAllPlainAuthenticationService(final int instanceId, final int totalNoOfInstances) {
        super(instanceId, totalNoOfInstances, false);
    }

    @Override
    public void validateResponse(String mechanism, byte[] response, Handler<AsyncResult<String>> resultHandler) {
        if (!PLAIN.equalsIgnoreCase(mechanism)) {
            LOG.error("can only perform validation of SASL PLAIN");
            resultHandler.handle(Future.failedFuture("cannot perform " + mechanism + ", supports SASL PLAIN only"));
        } else {
            evaluatePlainResponse(response, resultHandler);
        }
    }

    private void evaluatePlainResponse(final byte[] response, final Handler<AsyncResult<String>> resultHandler) {

        try {
            String[] fields = readFields(response);
            String authzid = fields[0];
            String authcid = fields[1];
            String pwd = fields[2];
            LOG.debug("client provided [authzid: {}, authcid: {}, pwd: {}] in PLAIN response", authzid, authcid, pwd);

            // TODO: verify the given credentials

            resultHandler.handle(Future.succeededFuture(authzid.length() > 0 ? authzid : authcid));
        } catch (IllegalArgumentException e) {
            // response did not contain expected values
            resultHandler.handle(Future.failedFuture(e));
        }
    }

    private String[] readFields(final byte[] buffer) {
        List<String> fields = new ArrayList<>();
        int pos = 0;
        Buffer b = Buffer.buffer();
        while (pos < buffer.length) {
            byte val = buffer[pos];
            if (val == 0x00) {
                fields.add(b.toString(StandardCharsets.UTF_8));
                b = Buffer.buffer();
            } else {
                b.appendByte(val);
            }
            pos++;
        }
        fields.add(b.toString(StandardCharsets.UTF_8));

        if (fields.size() != 3) {
            throw new IllegalArgumentException("client provided malformed PLAIN response");
        } else if (fields.get(1) == null) {
            throw new IllegalArgumentException("response must contain an authentication ID");
        } else if(fields.get(2) == null) {
            throw new IllegalArgumentException("response must contain a password");
        } else {
            return fields.toArray(new String[3]);
        }
    }
}
