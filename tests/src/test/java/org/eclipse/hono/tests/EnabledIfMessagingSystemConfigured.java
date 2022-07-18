/*******************************************************************************
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.tests;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.eclipse.hono.util.MessagingType;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * An annotation which configures a test to run with a specific messaging system only.
 */
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(EnabledIfMessagingSystemConfiguredCondition.class)
public @interface EnabledIfMessagingSystemConfigured {

    /**
     * The type of the messaging system for which the test shall be run exclusively.
     *
     * @return The type of the messaging system.
     */
    MessagingType type();

}
