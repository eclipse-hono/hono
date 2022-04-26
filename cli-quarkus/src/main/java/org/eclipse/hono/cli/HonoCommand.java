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


package org.eclipse.hono.cli;

import org.eclipse.hono.cli.adapter.amqp.AmqpAdapter;
import org.eclipse.hono.cli.app.NorthBoundApis;
import org.eclipse.hono.cli.util.PropertiesVersionProvider;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine;

/**
 * The top level Hono command line client command.
 *
 */
@TopCommand
@CommandLine.Command(
        name = "hono",
        mixinStandardHelpOptions = true,
        versionProvider = PropertiesVersionProvider.class,
        sortOptions = false,
        subcommands = { NorthBoundApis.class, AmqpAdapter.class })
public class HonoCommand {

}
