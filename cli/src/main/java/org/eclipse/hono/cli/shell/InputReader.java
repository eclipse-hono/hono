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

import org.jline.reader.LineReader;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;

/**
 * The input reader is used to interact with the user through the shell.
 */
public class InputReader {

    public static final Character DEFAULT_MASK = '*';

    private Character mask;

    private LineReader lineReader;

    private ShellHelper shellHelper;

    /**
     * Constructor.
     *
     * @param lineReader Instance or reader
     * @param shellHelper Instance of helper
     */
    public InputReader(final LineReader lineReader, final ShellHelper shellHelper) {
        this(lineReader, shellHelper, null);
    }

    /**
     * Constructor.
     *
     * @param lineReader Instance or reader
     * @param shellHelper Instance of helper
     * @param mask Character used for pearsing the input
     */
    public InputReader(final LineReader lineReader, final ShellHelper shellHelper, final Character mask) {
        this.lineReader = lineReader;
        this.shellHelper = shellHelper;
        this.mask = mask != null ? mask : DEFAULT_MASK;
    }

    /**
     * Prompts user for input.
     *
     * @param prompt Question
     * @return A string with the answer to print
     */
    public String prompt(final String prompt) {
        return prompt(prompt, null, true);
    }

    /**
     * Prompts user for input.
     *
     * @param prompt Question
     * @param defaultValue Default
     * @return A string with the answer to print
     */
    public String prompt(final String prompt, final String defaultValue) {
        return prompt(prompt, defaultValue, true);
    }

    /**
     * Prompts user for input.
     *
     * @param prompt Question
     * @param defaultValue Default
     * @param echo Flag for parsing the input
     * @return A string with the answer to print
     */
    public String prompt(final String prompt, final String defaultValue, final boolean echo) {
        String answer = "";

        if (echo) {
            answer = lineReader.readLine(prompt + ": ");
        } else {
            answer = lineReader.readLine(prompt + ": ", mask);
        }
        if (StringUtils.isEmpty(answer)) {
            return defaultValue;
        }
        return answer;
    }

    /**
     * Loops until one of the `options` is provided. Pressing return is equivalent to
     * returning `defaultValue`.
     * <br/>
     * Passing null for defaultValue signifies that there is no default value.<br/>
     * Passing "" or null among optionsAsList means that empty answer is allowed, in these cases this method returns
     * empty String "" as the result of its execution.
     *
     * @param prompt Question
     * @param defaultValue  Default
     * @param optionsAsList List of options
     * @return A string with the answer to print
     */
    public String promptWithOptions(final String prompt, final String defaultValue, final List<String> optionsAsList) {
        String answer;
        final List<String> allowedAnswers = new ArrayList<>(optionsAsList);
        if (StringUtils.hasText(defaultValue)) {
            allowedAnswers.add("");
        }
        do {
            answer = lineReader.readLine(String.format("%s %s: ", prompt, formatOptions(defaultValue, optionsAsList)));
        } while (!allowedAnswers.contains(answer) && !"".equals(answer));

        if (StringUtils.isEmpty(answer) && allowedAnswers.contains("")) {
            return defaultValue;
        }
        return answer;
    }

    private List<String> formatOptions(final String defaultValue, final List<String> optionsAsList) {
        final List<String> result = new ArrayList();
        for (String option : optionsAsList) {
            String val = option;
            if ("".equals(option) || option == null) {
                val = "''";
            }
            if (defaultValue != null ) {
               if (defaultValue.equals(option) || (defaultValue.equals("") && option == null)) {
                   val = shellHelper.getInfoMessage(val);
               }
            }
            result.add(val);
        }
        return result;
    }

    /**
     * Loops until one value from the list of options is selected, printing each option on its own line.
     *
     * @param headingMessage Header
     * @param promptMessage  Label
     * @param options Map of options
     * @param ignoreCase Flag for case sensitive
     * @param defaultValue Default
     * @return A string with the answer to print
     */
    public String selectFromList(final String headingMessage, final String promptMessage, final Map<String, String> options, final boolean ignoreCase, final String defaultValue) {
        String answer;
        final Set<String> allowedAnswers = new HashSet<>(options.keySet());
        if (defaultValue != null && !defaultValue.equals("")) {
            allowedAnswers.add("");
        }
        shellHelper.print(String.format("%s: ", headingMessage));
        do {
            for (Map.Entry<String, String> option: options.entrySet()) {
                String defaultMarker = null;
                if (defaultValue != null) {
                    if (option.getKey().equals(defaultValue)) {
                        defaultMarker = "*";
                    }
                }
                if (defaultMarker != null) {
                    shellHelper.printInfo(String.format("%s [%s] %s ", defaultMarker, option.getKey(), option.getValue()));
                } else {
                    shellHelper.print(String.format("  [%s] %s", option.getKey(), option.getValue()));
                }
            }
            answer = lineReader.readLine(String.format("%s: ", promptMessage));
        } while (!containsString(allowedAnswers, answer, ignoreCase) && !answer.equals(""));

        if (StringUtils.isEmpty(answer) && allowedAnswers.contains("")) {
            return defaultValue;
        }
        return answer;
    }

    private boolean containsString(final Set<String> l, final String s, final boolean ignoreCase){
        if (!ignoreCase) {
            return l.contains(s);
        }
        final Iterator<String> it = l.iterator();
        while (it.hasNext()) {
            if (it.next().equalsIgnoreCase(s)) {
                return true;
            }
        }
        return false;
    }

}
