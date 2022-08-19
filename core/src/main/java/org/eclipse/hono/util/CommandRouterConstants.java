/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.util;

/**
 * Constants &amp; utility methods used throughout the Command Router API.
 */

public final class CommandRouterConstants extends RequestResponseApiConstants {

    /**
     * The default name of the (remote) cache in the data grid that is used for
     * storing command router information.
     */
    public static final String DEFAULT_CACHE_NAME = "command-router";

    /**
     * The name of the Command Router API endpoint.
     */
    public static final String COMMAND_ROUTER_ENDPOINT = "cmd_router";

    /**
     * Request actions that belong to the Command Router API.
     */
    public enum CommandRouterAction {
        /**
         * The <em>set last known gateway for device</em> operation.
         */
        SET_LAST_KNOWN_GATEWAY("set-last-known-gw"),
        /**
         * The <em>register command consumer</em> operation.
         */
        REGISTER_COMMAND_CONSUMER("register-command-consumer"),
        /**
         * The <em>unregister command consumer</em> operation.
         */
        UNREGISTER_COMMAND_CONSUMER("unregister-command-consumer"),
        /**
         * The <em>enable command routing</em> operation.
         */
        ENABLE_COMMAND_ROUTING("enable-command-routing"),
        /**
         * The <em>unknown</em> operation.
         */
        UNKNOWN("unknown");

        private final String subject;

        CommandRouterAction(final String subject) {
            this.subject = subject;
        }

        /**
         * Gets the AMQP message subject corresponding to this action.
         *
         * @return The subject.
         */
        public String getSubject() {
            return subject;
        }

        /**
         * Construct a CommandRouterAction from a subject.
         *
         * @param subject The subject from which the CommandRouterAction needs to be constructed.
         * @return The CommandRouterAction as enum.
         */
        public static CommandRouterAction from(final String subject) {
            if (subject != null) {
                for (final CommandRouterAction action : values()) {
                    if (subject.equals(action.getSubject())) {
                        return action;
                    }
                }
            }
            return UNKNOWN;
        }

        /**
         * Helper method to check if a subject is a valid Command Router API action.
         *
         * @param subject The subject to validate.
         * @return boolean {@link Boolean#TRUE} if the subject denotes a valid action, {@link Boolean#FALSE} otherwise.
         */
        public static boolean isValid(final String subject) {
            return CommandRouterAction.from(subject) != CommandRouterAction.UNKNOWN;
        }
    }

    private CommandRouterConstants() {
        // prevent instantiation
    }
}
