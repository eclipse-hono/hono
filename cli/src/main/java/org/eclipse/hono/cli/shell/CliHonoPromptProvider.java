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

import org.jline.utils.AttributedString;
import org.springframework.shell.jline.PromptProvider;
import org.springframework.stereotype.Component;

/**
 * This class implements the interface of the shell prompt.
 * Overriding the methods it allows to customize the shell for better UX.
 */
@Component
public class CliHonoPromptProvider implements PromptProvider {
    /**
     * Override the shell config to set the custom prefix.
     * @return The attribute custom string.
     */
    @Override
    public final AttributedString getPrompt() {
        return new AttributedString("HONO:> ");
    }
}
