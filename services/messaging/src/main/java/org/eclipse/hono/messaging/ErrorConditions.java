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

package org.eclipse.hono.messaging;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;

/**
 * A set of error conditions shared by Hono server components.
 */
public final class ErrorConditions {

    /**
     * Indicates that a sender did not respond in time to a drain request issued by Hono.
     */
    public static final ErrorCondition ERROR_MISSING_DRAIN_RESPONSE = new ErrorCondition(
            Symbol.valueOf("hono:missing-drain-response"), "expected response to drain request");

    /**
     * Indicates that there is no consumer available for data sent to Hono.
     */
    public static final ErrorCondition ERROR_NO_DOWNSTREAM_CONSUMER = new ErrorCondition(
            Symbol.valueOf("hono:no-downstream-consumer"), "no downstream consumer available for data");

    /**
     * Indicates that a client wants to use an inappropriate delivery mode.
     */
    public static final ErrorCondition ERROR_UNSUPPORTED_DELIVERY_MODE = new ErrorCondition(
            Symbol.valueOf("hono:unsupported-delivery-mode"), "endpoint does not support requested delivery mode");

    private ErrorConditions() {
    }

}
