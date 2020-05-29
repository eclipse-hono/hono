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

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

/**
 * The helper to interact with the user through the shell.
 */
public class ShellHelper {

    public String infoColor = "CYAN";
    public String successColor = "GREEN";
    public String warningColor = "YELLOW";
    public String errorColor = "RED";

    private Terminal terminal;
    /**
     * Constructor.
     *
     * @param terminal A terminal instance.
     */
    public ShellHelper(final Terminal terminal) {
        this.terminal = terminal;
    }

    /**
     * Construct colored message in the given color.
     *
     * @param message message to return
     * @param color   color to print
     * @return colored message
     */
    public String getColored(final String message, final PromptColor color) {
        return (new AttributedStringBuilder()).append(message, AttributedStyle.DEFAULT.foreground(color.toJlineAttributedStyle())).toAnsi();
    }

    /**
     * Public method to get the colored message based on the the specific type.
     *
     * @param message message to return
     * @return colored message
     */
    public String getInfoMessage(final String message) {
        return getColored(message, PromptColor.valueOf(infoColor));
    }

    /**
     * Public method to get the colored message based on the the specific type.
     *
     * @param message message to return
     * @return colored message
     */
    public String getSuccessMessage(final String message) {
        return getColored(message, PromptColor.valueOf(successColor));
    }

    /**
     * Public method to get the colored message based on the the specific type.
     *
     * @param message message to return
     * @return colored message
     */
    public String getWarningMessage(final String message) {
        return getColored(message, PromptColor.valueOf(warningColor));
    }

    /**
     * Public method to get the colored message based on the the specific type.
     *
     * @param message message to return
     * @return colored message
     */
    public String getErrorMessage(final String message) {
        return getColored(message, PromptColor.valueOf(errorColor));
    }

    //--- Print methods -------------------------------------------------------

    /**
     * Print message to the console in the default color.
     *
     * @param message message to print
     */
    public void print(final String message) {
        print(message, null);
    }

    /**
     * Print message to the console in the success color.
     *
     * @param message message to print
     */
    public void printSuccess(final String message) {
        print(message, PromptColor.valueOf(successColor));
    }

    /**
     * Print message to the console in the info color.
     *
     * @param message message to print
     */
    public void printInfo(final String message) {
        print(message, PromptColor.valueOf(infoColor));
    }

    /**
     * Print message to the console in the warning color.
     *
     * @param message message to print
     */
    public void printWarning(final String message) {
        print(message, PromptColor.valueOf(warningColor));
    }

    /**
     * Print message to the console in the error color.
     *
     * @param message message to print
     */
    public void printError(final String message) {
        print(message, PromptColor.valueOf(errorColor));
    }

    /**
     * Generic Print to the console method.
     *
     * @param message message to print
     * @param color   (optional) prompt color
     */
    public void print(final String message, final PromptColor color) {
        String toPrint = message;
        if (color != null) {
            toPrint = getColored(message, color);
        }
        terminal.writer().println(toPrint);
        terminal.flush();
    }

    //--- set / get methods ---------------------------------------------------

    public Terminal getTerminal() {
        return terminal;
    }

    public void setTerminal(final Terminal terminal) {
        this.terminal = terminal;
    }
}
