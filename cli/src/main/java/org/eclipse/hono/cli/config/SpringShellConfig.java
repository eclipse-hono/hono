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

package org.eclipse.hono.cli.config;

import org.eclipse.hono.cli.shell.InputReader;
import org.eclipse.hono.cli.shell.PromptColor;
import org.eclipse.hono.cli.shell.ShellHelper;
import org.jline.reader.History;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Parser;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.shell.jline.JLineShellAutoConfiguration;

/**
 * Configuration for the Spring shell.
 */
@Configuration
public class SpringShellConfig {
    /**
     * Exposes a terminal helper instance to be used to interact with the user through the shell.
     *
     * @param terminal Instance of terminal.
     * @return The shell helper.
     */
    @Bean
    public ShellHelper shellHelper(@Lazy final Terminal terminal) {
            return new ShellHelper(terminal);
    }
    /**
     * Exposes a terminal input instance to interact with the shell.
     *
     * @param terminal Instance of terminal.
     * @param parser Parser.
     * @param completer Completer.
     * @param history Instance of History.
     * @param shellHelper Instance of helper.
     * @return The shell inputReader.
     */
    @Bean
    public InputReader inputReader(
            @Lazy final Terminal terminal,
            @Lazy final Parser parser,
            final JLineShellAutoConfiguration.CompleterAdapter completer,
            @Lazy final History history,
            final ShellHelper shellHelper
    ) {
        final LineReaderBuilder lineReaderBuilder = LineReaderBuilder.builder()
            .terminal(terminal)
            .completer(completer)
            .history(history)
            .highlighter(
            (LineReader reader, String buffer) -> {
                return new AttributedString(
                    buffer, AttributedStyle.BOLD.foreground(PromptColor.WHITE.toJlineAttributedStyle())
                );
            }
        ).parser(parser);

        final LineReader lineReader = lineReaderBuilder.build();
        lineReader.unsetOpt(LineReader.Option.INSERT_TAB);
        return new InputReader(lineReader, shellHelper);
    }

}
