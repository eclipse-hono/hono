/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.cli.util;

import picocli.CommandLine.ITypeConverter;


/**
 * Converts a reference to an environment variable to an integer.
 *
 */
public class IntegerVariableConverter extends AbstractVariableConverter implements ITypeConverter<Integer> {

    @Override
    public Integer convert(final String value) throws Exception {

        final String valueToConvert = getResolvedValue(value, System.getenv());
        return Integer.parseInt(valueToConvert);
    }
}
