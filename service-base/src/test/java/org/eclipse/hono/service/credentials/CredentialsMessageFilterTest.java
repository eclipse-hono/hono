/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.credentials;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonHelper;

/**
 * Test verifying that the filter complies with the credentials message format specification.
 */
public class CredentialsMessageFilterTest {

    private static final JsonObject BILLIE_HASHED_PASSWORD = new JsonObject()
            .put(CredentialsConstants.FIELD_TYPE, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD)
            .put(CredentialsConstants.FIELD_AUTH_ID, "billie");

    private ResourceIdentifier target;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {
        target = ResourceIdentifier.from(CredentialsConstants.CREDENTIALS_ENDPOINT, Constants.DEFAULT_TENANT, null);
    }

    /**
     * Verifies that a message that has no body does not pass the filter.
     */
    @Test
    public void testVerifyFailsForMissingBody() {

        // GIVEN a valid credentials GET message without an AMQP value
        final Message msg = givenAValidMessageWithoutBody(CredentialsConstants.CredentialsAction.get);

        // WHEN receiving the message via a link with any tenant
        final boolean filterResult = CredentialsMessageFilter.verify(target, msg);

        // THEN message validation fails
        assertFalse(filterResult);
    }

    /**
     * Verifies that a message containing a non Data section body
     * does not pass the filter.
     */
    @Test
    public void testVerifyFailsForNonDataSectionBody() {

        // GIVEN a message with an unsupported subject
        final Message msg = givenAValidMessageWithoutBody(CredentialsConstants.CredentialsAction.get);
        msg.setBody(new AmqpValue(BILLIE_HASHED_PASSWORD.encode()));
        msg.setContentType("application/json");

        // WHEN receiving the message via a link with any tenant
        final boolean filterResult = CredentialsMessageFilter.verify(target, msg);

        // THEN message validation fails
        assertFalse(filterResult);
    }

    /**
     * Verifies that a message that does not contain a message-id nor correlation-id
     * does not pass the filter.
     */
    @Test
    public void testVerifyFailsForMissingCorrelationId() {

        // GIVEN a message with an unsupported subject
        final Message msg = ProtonHelper.message();
        msg.setReplyTo("reply");
        msg.setBody(new AmqpValue(BILLIE_HASHED_PASSWORD));
        msg.setContentType("application/json");

        // WHEN receiving the message via a link with any tenant
        final boolean filterResult = CredentialsMessageFilter.verify(target, msg);

        // THEN message validation fails
        assertFalse(filterResult);
    }

    /**
     * Verifies that a message containing a subject that does not represent
     * a Credentials API operation does not pass the filter.
     */
    @Test
    public void testVerifyFailsForUnknownAction() {

        // GIVEN a message with an unsupported subject
        final Message msg = givenAValidMessageWithoutBody(CredentialsConstants.CredentialsAction.unknown);
        msg.setBody(new AmqpValue(BILLIE_HASHED_PASSWORD));
        msg.setContentType("application/json");

        // WHEN receiving the message via a link with any tenant
        final boolean filterResult = CredentialsMessageFilter.verify(target, msg);

        // THEN message validation fails
        assertFalse(filterResult);
    }

    /**
     * Verifies that a valid message passes the filter.
     */
    @Test
    public void testVerifySucceedsForValidGetAction() {

        // GIVEN a credentials message for user billie
        final Message msg = givenAValidMessageWithoutBody(CredentialsConstants.CredentialsAction.get);
        msg.setBody(new Data(new Binary(BILLIE_HASHED_PASSWORD.toBuffer().getBytes())));
        msg.setContentType("application/json");

        // WHEN receiving the message via a link with any tenant
        final boolean filterResult = CredentialsMessageFilter.verify(target, msg);

        // THEN message validation succeeds
        assertTrue(filterResult);
    }

    private static Message givenAValidMessageWithoutBody(final CredentialsConstants.CredentialsAction action) {
        final Message msg = ProtonHelper.message();
        msg.setMessageId("msg");
        msg.setReplyTo("reply");
        msg.setSubject(action.toString());
        return msg;
    }
}
