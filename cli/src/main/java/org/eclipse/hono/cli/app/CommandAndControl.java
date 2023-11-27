/**
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.cli.app;

import java.net.HttpURLConnection;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.cli.util.CommandUtils;
import org.eclipse.hono.cli.util.IntegerVariableConverter;
import org.eclipse.hono.cli.util.PropertiesVersionProvider;
import org.eclipse.hono.cli.util.StringVariableConverter;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.fusesource.jansi.AnsiConsole;
import org.jline.builtins.ConfigurationPath;
import org.jline.console.SystemRegistry;
import org.jline.console.impl.Builtins;
import org.jline.console.impl.SystemRegistryImpl;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.MaskingCallback;
import org.jline.reader.Parser;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import io.vertx.core.buffer.Buffer;
import jakarta.inject.Singleton;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;
import picocli.shell.jline3.PicocliCommands;
import picocli.shell.jline3.PicocliCommands.PicocliCommandsFactory;

/**
 * Command for sending one-way and/or request-response command messages to devices via  Hono's north bound
 * Command &amp; Control API.
 *
 */
@Singleton
@CommandLine.Command(
        name = "command",
        description = { "Send one-way and/or request-response commands to devices." },
        mixinStandardHelpOptions = true,
        versionProvider = PropertiesVersionProvider.class,
        sortOptions = false)
public class CommandAndControl implements Callable<Integer> {

    @CommandLine.ParentCommand
    NorthBoundApis appCommand;

    private final AtomicBoolean connected = new AtomicBoolean(false);

    /**
     * Common options for sending command messages.
     *
     */
    @CommandLine.Command
    public static class CommandSendingOptions {

        @CommandLine.Option(
                names = {"-t", "--tenant"},
                description = {
                    "The tenant that the device belongs to.",
                    CommandUtils.DESCRIPTION_ENV_VARS
                    },
                defaultValue = Constants.DEFAULT_TENANT,
                order = 19,
                converter = StringVariableConverter.class)
        String tenantId;

        @CommandLine.Option(
                names = { "-d", "--device" },
                description = {
                    "The device to send the command to.",
                    CommandUtils.DESCRIPTION_ENV_VARS
                    },
                required = true,
                order = 20,
                converter = StringVariableConverter.class)
        String deviceId;

        @CommandLine.Option(
                names = { "-n", "--name" },
                description = {
                    "The name of the command to be executed by the device.",
                    CommandUtils.DESCRIPTION_ENV_VARS
                },
                required = true,
                order = 23,
                converter = StringVariableConverter.class)
        String commandName;

        @CommandLine.Option(
                names = { "--payload" },
                description = {
                    "Arbitrary (text) input data to the command to be executed.",
                    """
                    The values of the '--name=<commandName' and '--content-type=<contentType>' properties may \
                    provide a hint to the device regarding the format, encoding and semantics of the payload data.
                    """,
                    CommandUtils.DESCRIPTION_ENV_VARS
                },
                order = 25,
                converter = StringVariableConverter.class)
        String payload;

        @CommandLine.Option(
                names = { "--content-type" },
                description = {
                    """
                    A Media Type which describes the semantics and format of the command’s input data contained \
                    in the message payload.
                    """,
                    "See https://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.7",
                    """
                    Not all protocol adapters will support this property as not all transport protocols provide means \
                    to convey this information, e.g. MQTT 3.1.1 has no notion of message headers.
                    """,
                    CommandUtils.DESCRIPTION_ENV_VARS
                },
                order = 27,
                converter = StringVariableConverter.class)
        String contentType;
    }

    private void printResponse(final DownstreamMessage<?> response) {
        System.out.println("res %s %d %s %s".formatted(
                response.getDeviceId(),
                response.getStatus(),
                Optional.ofNullable(response.getContentType()).orElse("-"),
                Optional.ofNullable(response.getPayload())
                    .map(Buffer::toString)
                    .orElse("-")));
    }

    @SuppressWarnings("CatchAndPrintStackTrace")
    private void readAndExecuteCommands() {
        AnsiConsole.systemInstall();
        try {
            final Supplier<Path> workDir = () -> Paths.get(System.getProperty("user.dir"));
            // set up JLine built-in commands
            final var builtins = new Builtins(workDir, new ConfigurationPath(null, null), null);
            builtins.rename(Builtins.Command.TTOP, "top");
            builtins.alias("zle", "widget");
            builtins.alias("bindkey", "keymap");

            final var factory = new PicocliCommandsFactory();
            final var cmd = new CommandLine(this, factory);
            cmd.setExecutionExceptionHandler(CommandUtils::handleExecutionException);
            final var picocliCommands = new PicocliCommands(cmd) {
                @Override
                public String name() {
                    return "hono-cli";
                }
            };

            final Parser parser = new DefaultParser();
            try (Terminal terminal = TerminalBuilder.builder().build()) {
                final SystemRegistry systemRegistry = new SystemRegistryImpl(parser, terminal, workDir, null);
                systemRegistry.setCommandRegistries(builtins, picocliCommands);
                systemRegistry.register("help", picocliCommands);

                final LineReader reader = LineReaderBuilder.builder()
                        .terminal(terminal)
                        .completer(systemRegistry.completer())
                        .parser(parser)
                        .variable(LineReader.LIST_MAX, 50)   // max tab completion candidates
                        .build();
                builtins.setLineReader(reader);
                factory.setTerminal(terminal);

                final String prompt = "hono-cli/app/command> ";
                final String rightPrompt = null;

                // start the shell and process input until the user quits with Ctrl-D
                String line;
                while (connected.get()) {
                    try {
                        systemRegistry.cleanUp();
                        line = reader.readLine(prompt, rightPrompt, (MaskingCallback) null, null);
                        systemRegistry.execute(line);
                    } catch (final UserInterruptException e) {
                        // user has typed ctrl-c
                        connected.compareAndSet(true, false);
                    } catch (final EndOfFileException e) {
                        // user has typed ctrl-d
                        connected.compareAndSet(true, false);
                    } catch (final Exception e) {
                        systemRegistry.trace(e);
                    }
                }
            }
        } catch (final Throwable t) {
            t.printStackTrace();
        } finally {
            AnsiConsole.systemUninstall();
        }
    }

    @Override
    public Integer call() {

        try {
            appCommand.getApplicationClient()
                .onSuccess(client -> connected.set(true))
                .toCompletionStage()
                .toCompletableFuture()
                .join();
        } catch (final CompletionException e) {
            System.err.println("Failed to connect to Hono's Command & Control endpoint");
            CommandUtils.printError(e.getCause());
            return CommandLine.ExitCode.SOFTWARE;
        }

        readAndExecuteCommands();
        return CommandLine.ExitCode.OK;
    }

    @CommandLine.Command(
            name = "quit",
            aliases = { "exit", "bye" },
            description = {"Exit Hono client."})
    void quit() {
        connected.set(false);
    }

    private void handleCommandSendingError(final Throwable t) {
        System.err.println("Cannot send command to device.");
        if (t instanceof ServiceInvocationException sie) {
            if (sie.getErrorCode() == HttpURLConnection.HTTP_UNAVAILABLE) {
                System.err.println("""
                        Check if device is connected to a protocol adapter and has subscribed \
                        for commands as described in the protocol adapter user guides.
                        https://www.eclipse.org/hono/docs/user-guide/
                        """);
            }
        }
    }

    @CommandLine.Command(
            name = "ow",
            description = {"Send a one-way command to a device."},
            mixinStandardHelpOptions = true,
            versionProvider = PropertiesVersionProvider.class,
            sortOptions = false)
    int sendOneWay(
            @CommandLine.Mixin
            final CommandSendingOptions options) {

        final var ct = Optional.ofNullable(options.contentType)
                .orElseGet(() -> Optional.ofNullable(options.payload)
                        .filter(p -> p.startsWith("{") && p.endsWith("}"))
                        .map(p -> MessageHelper.CONTENT_TYPE_APPLICATION_JSON)
                        .orElse("text/plain"));

        appCommand.getApplicationClient()
            .compose(c -> c.sendOneWayCommand(
                options.tenantId,
                options.deviceId,
                options.commandName,
                Optional.ofNullable(options.payload).map(Buffer::buffer).orElse(null),
                ct,
                null))
            .onFailure(this::handleCommandSendingError)
            .toCompletionStage()
            .toCompletableFuture()
            .join();
        return ExitCode.OK;
    }

    @CommandLine.Command(
            name = "req",
            description = {"Send a request-response command to a device."},
            mixinStandardHelpOptions = true,
            versionProvider = PropertiesVersionProvider.class,
            sortOptions = false)
    int sendRequestResponse(
            @CommandLine.Mixin
            final CommandSendingOptions options,
            @CommandLine.Option(
                    names = {"-r", "--response-timeout"},
                    description = {"The time (seconds) to wait for a response from the device."},
                    defaultValue = "60",
                    converter = IntegerVariableConverter.class)
            final int responseTimeout) {

        final var ct = Optional.ofNullable(options.contentType)
                .orElseGet(() -> Optional.ofNullable(options.payload)
                        .filter(p -> p.startsWith("{") && p.endsWith("}"))
                        .map(p -> MessageHelper.CONTENT_TYPE_APPLICATION_JSON)
                        .orElse("text/plain"));

        appCommand.getApplicationClient()
            .compose(c -> c.sendCommand(
                options.tenantId,
                options.deviceId,
                options.commandName,
                Optional.ofNullable(options.payload).map(Buffer::buffer).orElse(null),
                ct,
                UUID.randomUUID().toString(),
                Duration.ofSeconds(responseTimeout),
                null))
            .onSuccess(this::printResponse)
            .onFailure(this::handleCommandSendingError)
            .toCompletionStage()
            .toCompletableFuture()
            .join();
        return ExitCode.OK;
    }
}
