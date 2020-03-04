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

package org.eclipse.hono.cli.shell;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.shell.jline.PromptProvider;
import org.springframework.stereotype.Component;

/**
 * Shell class.
 */
@Component
public class ClihonoPromptProvider implements PromptProvider {
    /**
     * Override the shell config to set the custom prefix.
     * @return The attribute custom string.
     */
    @Override
    public AttributedString getPrompt() {
        return new AttributedString("HONO:>", AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE));
    }
}
