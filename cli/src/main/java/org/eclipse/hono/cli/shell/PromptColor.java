/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.cli.shell;

/**
 * Class to handle the prompt color for custom messages.
 */
public enum PromptColor {
    BLACK(0),
    RED(1),
    GREEN(2),
    YELLOW(3),
    BLUE(4),
    MAGENTA(5),
    CYAN(6),
    WHITE(7),
    BRIGHT(8);

    private final int value;
    /**
     * Constructor to set the color.
     *
     * @param value The color
     */
    PromptColor(final int value) {
        this.value = value;
    }

    /**
     * Getter of the color.
     * @return The color
     */
    public int toJlineAttributedStyle() {
        return this.value;
    }
}
