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


package org.eclipse.hono.cli.adapter.amqp;

import java.net.HttpURLConnection;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.cli.util.ClientCertInfo;
import org.eclipse.hono.cli.util.CommandUtils;
import org.eclipse.hono.cli.util.ConnectionOptions;
import org.eclipse.hono.cli.util.IntegerVariableConverter;
import org.eclipse.hono.cli.util.PropertiesVersionProvider;
import org.eclipse.hono.cli.util.StringVariableConverter;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.amqp.config.ClientConfigProperties;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.command.CommandConsumer;
import org.eclipse.hono.client.device.amqp.AmqpAdapterClient;
import org.eclipse.hono.util.QoS;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quarkus.runtime.ShutdownEvent;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import picocli.shell.jline3.PicocliCommands;
import picocli.shell.jline3.PicocliCommands.PicocliCommandsFactory;

/**
 * Commands for interacting with Hono's AMQP adapter.
 *
 */
@Singleton
@CommandLine.Command(
        name = "amqp-device",
        aliases = { "amqp" },
        description = { "A client for interacting with Hono's AMQP adapter." },
        mixinStandardHelpOptions = true,
        versionProvider = PropertiesVersionProvider.class,
        sortOptions = false)
@SuppressFBWarnings(
        value = "HARD_CODE_PASSWORD",
        justification = """
                We use the default passwords of the Hono Sandbox installation throughout this class
                for ease of use. The passwords are publicly documented and do not affect any
                private installations of Hono.
                """)
public class AmqpAdapter implements Callable<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(AmqpAdapter.class);

    private static final String SANDBOX_DEFAULT_DEVICE_AUTH_ID = "sensor1@DEFAULT_TENANT";
    private static final String SANDBOX_DEFAULT_DEVICE_PWD = "hono-secret";

    @CommandLine.Mixin
    ConnectionOptions connectionOptions;

    @CommandLine.ArgGroup(exclusive = false)
    ClientCertInfo clientCertInfo;

    @Inject
    Vertx vertx;

    @CommandLine.Spec
    CommandSpec spec;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final Map<String, CommandConsumer> activeConsumers = new HashMap<>();

    private AmqpAdapterClient client;

    /**
     * Common options for sending telemetry/event messages.
     *
     */
    @CommandLine.Command
    static class TelemetrySendingOptions {

        @CommandLine.Option(
                names = { "-t", "--tenant" },
                description = {
                        "The tenant that the device belongs to.",
                        """
                        If not set explicitly, the tenant is determined from the device that has authenticated to \
                        the AMQP adapter.
                        """,
                        """
                        Unauthenticated clients must provide a non-null value to indicate the tenant of the \
                        device that the message originates from.
                        """,
                        """
                        It is an error to specify this option but to omit specifying a device identifier using \
                        the '-d=<deviceId>' option.
                        """,
                        CommandUtils.DESCRIPTION_ENV_VARS
                },
                order = 20,
                converter = StringVariableConverter.class)
        String tenantId;

        @CommandLine.Option(
                names = { "-d", "--device" },
                description = {
                        "The device that the message originates from.",
                        """
                        If not set explicitly, the message is assumed to originate from the device that has \
                        authenticated to the AMQP adapter.
                        """,
                        """
                        This option can be used by authenticated gateway devices to send a message on behalf of \
                        another device. It can also be used by unauthenticated clients to indicate the device that \
                        the message originates from.
                        """,
                        CommandUtils.DESCRIPTION_ENV_VARS
                },
                order = 22,
                converter = StringVariableConverter.class)
        String deviceId;

        @CommandLine.Option(
                names = { "--payload" },
                description = {
                        "The (text) payload to include in the message.",
                        CommandUtils.DESCRIPTION_ENV_VARS
                        },
                order = 25,
                converter = StringVariableConverter.class)
        String payload;

        @CommandLine.Option(
                names = { "--content-type" },
                description = {
                        "A Media Type describing the content of the message.",
                        "See https://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.7",
                        CommandUtils.DESCRIPTION_ENV_VARS
                        },
                order = 27,
                converter = StringVariableConverter.class)
        String contentType;
    }

    private void validateConnectionOptions() {
        if (!connectionOptions.useSandbox
                && (connectionOptions.hostname.isEmpty() || connectionOptions.portNumber.isEmpty())) {
            throw new ParameterException(
                    spec.commandLine(),
                    """
                    Missing required option: both '--host=<hostname>' and '--port=<portNumber> need to \
                    be specified when not using '--sandbox'.
                    """);
        }
    }

    /**
     * Gets a ready to use client factory.
     *
     * @return The factory.
     */
    private Future<AmqpAdapterClient> getClient() {

        if (client != null) {
            return Future.succeededFuture(client);
        }

        validateConnectionOptions();
        final var clientConfig = new ClientConfigProperties();
        clientConfig.setReconnectAttempts(5);
        clientConfig.setServerRole("Hono AMQP Adapter");
        connectionOptions.trustStorePath.ifPresent(path -> {
            clientConfig.setTrustStorePath(path);
            connectionOptions.trustStorePassword.ifPresent(clientConfig::setTrustStorePassword);
        });
        if (connectionOptions.useSandbox) {
            clientConfig.setHost(ConnectionOptions.SANDBOX_HOST_NAME);
            clientConfig.setPort(5671);
            clientConfig.setTlsEnabled(true);
            Optional.ofNullable(connectionOptions.credentials).ifPresentOrElse(
                    creds -> {
                        clientConfig.setUsername(creds.username);
                        clientConfig.setPassword(creds.password);
                    },
                    () -> {
                        clientConfig.setUsername(SANDBOX_DEFAULT_DEVICE_AUTH_ID);
                        clientConfig.setPassword(SANDBOX_DEFAULT_DEVICE_PWD);
                    });
        } else {
            connectionOptions.hostname.ifPresent(clientConfig::setHost);
            connectionOptions.portNumber.ifPresent(clientConfig::setPort);
            clientConfig.setHostnameVerificationRequired(!connectionOptions.disableHostnameVerification);
            if (clientCertInfo != null) {
                clientConfig.setCertPath(clientCertInfo.certPath);
                clientConfig.setKeyPath(clientCertInfo.keyPath);
            } else if (connectionOptions.credentials != null) {
                clientConfig.setUsername(connectionOptions.credentials.username);
                clientConfig.setPassword(connectionOptions.credentials.password);
            }
        }
        final var clientFactory = AmqpAdapterClient.create(HonoConnection.newConnection(vertx, clientConfig));
        return clientFactory.connect()
                .onSuccess(con -> {
                    this.client = clientFactory;
                })
                .map(clientFactory);
    }

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

                final String prompt = "hono-cli/amqp-device> ";
                final String rightPrompt = null;

                // start the shell and process input until the user quits
                while (connected.get()) {
                    try {
                        systemRegistry.cleanUp();
                        final String line = reader.readLine(prompt, rightPrompt, (MaskingCallback) null, null);
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
            System.err.println("catch Throwable");
            t.printStackTrace();
        } finally {
            AnsiConsole.systemUninstall();
        }
    }

    /**
     * Disconnects from the adapter.
     *
     * @param ev The event indicating shutdown.
     */
    public void disconnectFromAdapter(final @Observes ShutdownEvent ev) {
        if (client != null) {
            final Promise<Void> result = Promise.promise();
            LOG.debug("disconnecting from AMQP adapter");
            client.disconnect(result);
            result.future()
                .onComplete(ar -> LOG.debug("closed connection"))
                .toCompletionStage()
                .toCompletableFuture()
                .join();
        }
    }

    private void handleCommandMessage(
            final Message msg,
            final String deviceId,
            final Integer responseStatusCode) {

        final String commandPayload = AmqpUtils.getPayloadAsString(msg);
        final boolean isOneWay = msg.getReplyTo() == null;
        System.out.println("%s %s %s %s %s".formatted(
                isOneWay ? "ow" : "req",
                deviceId,
                msg.getSubject(),
                Optional.ofNullable(msg.getContentType()).orElse("-"),
                Optional.ofNullable(commandPayload).orElse("-")));

        if (!isOneWay && responseStatusCode != null) {

            vertx.runOnContext(sendResponse -> getClient()
                    .compose(client -> client.sendCommandResponse(
                                msg.getReplyTo(),
                                msg.getCorrelationId().toString(),
                                responseStatusCode,
                                Buffer.buffer("automatic response to [%s] command".formatted(msg.getSubject())),
                                "text/plain",
                                null))
                    .onFailure(t -> {
                        System.err.println("Could not send command response to Hono's AMQP adapter");
                        CommandUtils.printError(t);
                    }));
        }
    }

    private String getConsumerKey(final String tenantId, final String deviceId) {
        if (deviceId == null) {
            return "@@@self@@@";
        }
        final var b = new StringBuilder();
        b.append(deviceId);
        if (tenantId != null) {
            b.append("@").append(tenantId);
        }
        return b.toString();
    }

    @Override
    public Integer call() {

        try {
            getClient()
                .onSuccess(client -> connected.set(true))
                .toCompletionStage()
                .toCompletableFuture()
                .join();
        } catch (final CompletionException e) {
            System.err.println("Failed to connect to Hono's AMQP adapter");
            CommandUtils.printError(e.getCause());
            return CommandLine.ExitCode.SOFTWARE;
        }

        readAndExecuteCommands();
        return CommandLine.ExitCode.OK;
    }

    private void handleError(final Throwable t, final String deviceId) {
        if (t instanceof ServiceInvocationException e) {
            switch (e.getErrorCode()) {
            case HttpURLConnection.HTTP_FORBIDDEN:
                System.err.println("""
                        The currently connected device is not authorized to act on behalf of device [id: %1$s].
                        In order to authorize the connected device, its device identifier needs to be added to
                        the list of (gateway) devices that may act on behalf of device [id: %1$s].
                        Please refer to https://www.eclipse.org/hono/docs/concepts/connecting-devices/#connecting-via-a-device-gateway
                        for details regarding connecting devices via gateways.
                        """.formatted(deviceId));
                break;
            default:
                System.err.println("The AMQP protocol adapter was not able to process the request.");
            }
        }
    }

    private void checkDeviceSpec(final String tenantId, final String deviceId) {
        if (tenantId != null && deviceId == null) {
            throw new ParameterException(
                    spec.commandLine(),
                    """
                    Missing required option: '--device=<deviceId>'.
                    """);
        }
    }

    @CommandLine.Command(
            name = "sub",
            description = {"Start receiving commands for a device."},
            mixinStandardHelpOptions = true,
            versionProvider = PropertiesVersionProvider.class,
            sortOptions = false)
    void subscribe(
            @CommandLine.Option(
                    names = { "-t", "--tenant" },
                    description = {
                            "The tenant that the device belongs to.",
                            """
                            If not set explicitly, the tenant is determined from the device that has authenticated to \
                            the AMQP adapter.
                            """,
                            """
                            Unauthenticated clients must provide a non-null value to indicate the tenant of the \
                            device to start receiving commands for.
                            """,
                            """
                            It is an error to specify this option but to omit specifying a device identifier using \
                            the '-d=<deviceId>' option.
                            """,
                            CommandUtils.DESCRIPTION_ENV_VARS
                    },
                    order = 20,
                    converter = StringVariableConverter.class)
            final String tenantId,
            @CommandLine.Option(
                    names = { "-d", "--device" },
                    description = {
                            "The identifier of the device to start receiving commands for.",
                            """
                            If not set explicitly, the identifier of the device that has authenticated to the AMQP \
                            adapter will be used.
                            """,
                            """
                            Authenticated gateway devices can use this parameter to start receiving commands for another \
                            device that the gateway is authorized to act on behalf of.
                            """,
                            """
                            Unauthenticated clients must provide a non-{@code null} value to indicate the device to \
                            start receiving commands for.
                            """,
                            CommandUtils.DESCRIPTION_ENV_VARS
                    },
                    order = 21,
                    converter = StringVariableConverter.class)
            final String deviceId,
            @CommandLine.Option(
                    names = { "-s" },
                    description = {
                            "Automatically respond with a status code.",
                            "The status code must be in the range [200,600).",
                            CommandUtils.DESCRIPTION_ENV_VARS
                            },
                    order = 25,
                    converter = IntegerVariableConverter.class)
            final Integer responseStatusCode) {

        if (responseStatusCode != null) {
            if (responseStatusCode < 200 || responseStatusCode >= 600) {
                throw new ParameterException(
                        spec.commandLine(),
                        """
                        Unsupported value for option: '-s=<responseStatusCode>' must be an HTTP status \
                        code in the range [200,600).
                        """);
            }
        }
        getClient()
            .compose(client -> {
                final Consumer<Message> commandHandler = msg -> {
                    final String id = Optional.ofNullable(AmqpUtils.getDeviceId(msg))
                            .orElseGet(() -> Optional.ofNullable(deviceId).orElse("-"));
                    handleCommandMessage(msg, id, responseStatusCode);
                };
                return Optional.ofNullable(deviceId)
                        .map(id -> client.createDeviceSpecificCommandConsumer(tenantId, id, commandHandler))
                        .orElseGet(() -> client.createCommandConsumer(commandHandler));
            })
            .onSuccess(consumer -> activeConsumers.put(getConsumerKey(tenantId, deviceId), consumer))
            .onFailure(t -> {
                System.err.println("Cannot subscribe to commands for device");
                handleError(t, deviceId);
            })
            .toCompletionStage()
            .toCompletableFuture()
            .join();
    }

    @CommandLine.Command(
            name = "unsub",
            description = {"Stop receiving commands for a device."},
            mixinStandardHelpOptions = true,
            versionProvider = PropertiesVersionProvider.class,
            sortOptions = false)
    void unsubscribe(
            @CommandLine.Option(
                    names = { "-t", "--tenant" },
                    description = {
                            "The tenant that the device belongs to.",
                            """
                            If not set explicitly, the tenant is determined from the device that has authenticated to \
                            the AMQP adapter.
                            """,
                            """
                            Unauthenticated clients must provide a non-null value to indicate the tenant of the \
                            device to stop receiving commands for.
                            """,
                            """
                            It is an error to specify this option but to omit specifying a device identifier using \
                            the '-d=<deviceId>' option.
                            """,
                            CommandUtils.DESCRIPTION_ENV_VARS
                    },
                    order = 20,
                    converter = StringVariableConverter.class)
            final String tenantId,
            @CommandLine.Option(
                    names = { "-d", "--device" },
                    description = {
                            "The identifier of the device to stop receiving commands for.",
                            """
                            If not set explicitly, the identifier of the device that has authenticated to the AMQP \
                            adapter will be used.
                            """,
                            """
                            Authenticated gateway devices can use this parameter to stop receiving commands for another \
                            device that the gateway is authorized to act on behalf of.
                            """,
                            """
                            Unauthenticated clients must provide a non-{@code null} value to indicate the device to \
                            stop receiving commands for.
                            """,
                            CommandUtils.DESCRIPTION_ENV_VARS
                    },
                    order = 21,
                    converter = StringVariableConverter.class)
            final String deviceId) {
        Optional.ofNullable(activeConsumers.remove(getConsumerKey(tenantId, deviceId)))
            .ifPresent(consumer -> consumer.close(null));
    }

    @CommandLine.Command(
            name = "telemetry",
            description = {"Send a telemetry message."},
            mixinStandardHelpOptions = true,
            versionProvider = PropertiesVersionProvider.class,
            sortOptions = false)
    void sendTelemetry(
            @CommandLine.Mixin
            final TelemetrySendingOptions options) {

        checkDeviceSpec(options.tenantId, options.deviceId);
        getClient()
            .compose(client -> client.sendTelemetry(
                    QoS.AT_MOST_ONCE,
                    Optional.ofNullable(options.payload).map(Buffer::buffer).orElse(null),
                    options.contentType,
                    options.tenantId,
                    options.deviceId,
                    null))
            .onFailure(t -> {
                System.err.println("Cannot send telemetry message.");
                handleError(t, options.deviceId);
            })
            .toCompletionStage()
            .toCompletableFuture()
            .join();
    }

    @CommandLine.Command(
            name = "event",
            description = {"Send an event message."},
            mixinStandardHelpOptions = true,
            versionProvider = PropertiesVersionProvider.class,
            sortOptions = false)
    void sendEvent(
            @CommandLine.Mixin
            final TelemetrySendingOptions options) {

        checkDeviceSpec(options.tenantId, options.deviceId);
        getClient()
            .compose(f -> f.sendEvent(
                    Optional.ofNullable(options.payload).map(Buffer::buffer).orElse(null),
                    options.contentType,
                    options.tenantId,
                    options.deviceId,
                    null))
            .onFailure(t -> {
                System.err.println("Cannot send event message.");
                handleError(t, options.deviceId);
            })
            .toCompletionStage()
            .toCompletableFuture()
            .join();
    }
}
