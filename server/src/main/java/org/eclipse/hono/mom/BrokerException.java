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
package org.eclipse.hono.mom;

/**
 * A base exception indicating a problem with underlying message oriented middleware brokers.
 */
public class BrokerException extends RuntimeException {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    /**
     * @param message
     * @param cause
     */
    public BrokerException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message
     */
    public BrokerException(String message) {
        super(message);
    }

}
