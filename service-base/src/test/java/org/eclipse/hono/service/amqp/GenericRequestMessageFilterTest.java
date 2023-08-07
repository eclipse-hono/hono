/*******************************************************************************
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.amqp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.qpid.proton.message.Message;
import org.junit.jupiter.api.Test;

import io.vertx.proton.ProtonHelper;

/**
 * Test verifying behavior of {@link GenericRequestMessageFilter}.
 */
public class GenericRequestMessageFilterTest {

    /**
     * Verifies that the filter detects a missing subject.
     */
    @Test
    public void testFilterDetectsMissingSubject() {

        final Message msg = ProtonHelper.message();
        msg.setMessageId("msg");
        msg.setReplyTo("reply");

        assertFalse(GenericRequestMessageFilter.isValidRequestMessage(msg));
    }

    /**
     * Verifies that the filter detects a missing reply-to.
     */
    @Test
    public void testFilterDetectsMissingReplyTo() {

        final Message msg = ProtonHelper.message();
        msg.setMessageId("msg");
        msg.setSubject("action");

        assertFalse(GenericRequestMessageFilter.isValidRequestMessage(msg));
    }

    /**
     * Verifies that the filter detects a missing reply-to.
     */
    @Test
    public void testFilterDetectsMissingCorrelationId() {

        final Message msg = ProtonHelper.message();
        msg.setSubject("action");
        msg.setReplyTo("reply");

        assertFalse(GenericRequestMessageFilter.isValidRequestMessage(msg));
    }

    /**
     * Verifies that the filter succeeds for a valid message.
     */
    @Test
    public void testFilterSucceedsForValidGetAction() {
        final Message msg = ProtonHelper.message();
        msg.setMessageId("msg");
        msg.setReplyTo("reply");
        msg.setSubject("action");
        assertTrue(GenericRequestMessageFilter.isValidRequestMessage(msg));
    }
}
