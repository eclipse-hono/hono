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

import java.util.HashMap;
import java.util.Optional;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.security.auth.SecurityProtocol;
import org.apache.kafka.common.security.scram.ScramLoginModule;
import org.apache.kafka.common.security.scram.internals.ScramMechanism;
import org.eclipse.hono.application.client.ApplicationClient;
import org.eclipse.hono.application.client.MessageContext;
import org.eclipse.hono.application.client.amqp.ProtonBasedApplicationClient;
import org.eclipse.hono.application.client.kafka.impl.KafkaApplicationClientImpl;
import org.eclipse.hono.cli.util.ConnectionOptions;
import org.eclipse.hono.cli.util.PropertiesVersionProvider;
import org.eclipse.hono.client.amqp.config.ClientConfigProperties;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.kafka.CommonKafkaClientConfigProperties;
import org.eclipse.hono.client.kafka.consumer.MessagingKafkaConsumerConfigProperties;
import org.eclipse.hono.client.kafka.producer.CachingKafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.MessagingKafkaProducerConfigProperties;
import org.eclipse.hono.config.FileFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quarkus.runtime.ShutdownEvent;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;

/**
 * Commands for interacting with Hono's north bound APIs.
 *
 */
@Singleton
@CommandLine.Command(
        name = "app",
        description = { "A client for interacting with Hono's north bound API endpoints." },
        mixinStandardHelpOptions = true,
        versionProvider = PropertiesVersionProvider.class,
        sortOptions = false,
        subcommands = { TelemetryAndEvent.class, CommandAndControl.class })
@SuppressFBWarnings(
        value = "HARD_CODE_PASSWORD",
        justification = """
                We use the default passwords of the Hono Sandbox installation throughout this class
                for ease of use. The passwords are publicly documented and do not affect any
                private installations of Hono.
                """)
public class NorthBoundApis {

    private static final Logger LOG = LoggerFactory.getLogger(NorthBoundApis.class);

    private static final String SANDBOX_AMQP_USER = "consumer@HONO";
    private static final String SANDBOX_AMQP_PWD = "verysecret";
    private static final String SANDBOX_KAFKA_USER = "hono";
    private static final String SANDBOX_KAFKA_PWD = "hono-secret";

    @CommandLine.Mixin
    ConnectionOptions connectionOptions;

    @CommandLine.Option(
            names = {"--amqp"},
            description = {
                "Connect to the AMQP 1.0 based API endpoints",
                "If not set, the Kafka based endpoints are used by default" },
            order = 12)
    boolean useAmqp;

    @Inject
    Vertx vertx;

    @CommandLine.Spec
    CommandSpec spec;

    ApplicationClient<? extends MessageContext> client;

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

    private String scramJaasConfig(final String username, final String password) {
        return """
                %s required username="%s" password="%s";
                """.formatted(ScramLoginModule.class.getName(), username, password);
    }

    Future<KafkaApplicationClientImpl> createKafkaClient() {

        validateConnectionOptions();
        final var commonProps = new HashMap<String, String>();
        final String bootstrapServers;

        connectionOptions.trustStorePath
            .ifPresent(path -> {
                commonProps.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, path);
                connectionOptions.trustStorePassword
                    .ifPresent(pwd -> commonProps.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, pwd));

                Optional.ofNullable(FileFormat.detect(path))
                    .ifPresent(fileFormat -> commonProps.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, fileFormat.name()));
            });

        if (connectionOptions.useSandbox) {
            bootstrapServers = "%s:9094".formatted(ConnectionOptions.SANDBOX_HOST_NAME);
            commonProps.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            commonProps.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.SASL_SSL.name);
            commonProps.put(SaslConfigs.SASL_MECHANISM, ScramMechanism.SCRAM_SHA_512.mechanismName());
            Optional.ofNullable(connectionOptions.credentials)
                .ifPresentOrElse(
                        creds -> commonProps.put(
                                SaslConfigs.SASL_JAAS_CONFIG,
                                scramJaasConfig(creds.username, creds.password)),
                        () -> commonProps.put(
                                SaslConfigs.SASL_JAAS_CONFIG,
                                scramJaasConfig(SANDBOX_KAFKA_USER, SANDBOX_KAFKA_PWD)));
        } else {
            bootstrapServers = "%s:%d".formatted(connectionOptions.hostname.get(), connectionOptions.portNumber.get());
            commonProps.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            if (connectionOptions.disableHostnameVerification) {
                commonProps.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "");
            }

            if (connectionOptions.credentials == null) {
                if (connectionOptions.trustStorePath.isPresent()) {
                    commonProps.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.SSL.name);
                }
            } else {
                if (connectionOptions.trustStorePath.isEmpty()) {
                    commonProps.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.SASL_PLAINTEXT.name);
                } else {
                    commonProps.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.SASL_SSL.name);
                }
                commonProps.put(SaslConfigs.SASL_MECHANISM, ScramMechanism.SCRAM_SHA_512.mechanismName());
                commonProps.put(
                        SaslConfigs.SASL_JAAS_CONFIG,
                        scramJaasConfig(connectionOptions.credentials.username, connectionOptions.credentials.password));
            }
        }

        final var commonClientConfig = new CommonKafkaClientConfigProperties();
        commonClientConfig.setCommonClientConfig(commonProps);

        final var consumerProps = new MessagingKafkaConsumerConfigProperties();
        consumerProps.setCommonClientConfig(commonClientConfig);
        final var producerProps = new MessagingKafkaProducerConfigProperties();
        producerProps.setCommonClientConfig(commonClientConfig);

        System.err.printf("Connecting to Kafka based messaging infrastructure [%s]%n", bootstrapServers);
        final var kafkaClient = new KafkaApplicationClientImpl(
                vertx,
                consumerProps,
                CachingKafkaProducerFactory.sharedFactory(vertx),
                producerProps);
        return startClientAndWaitForReadiness(kafkaClient)
                .map(ok -> {
                    this.client = kafkaClient;
                    return kafkaClient;
                });
    }

    Future<ProtonBasedApplicationClient> createAmqpClient() {

        validateConnectionOptions();
        final var clientConfig = new ClientConfigProperties();
        clientConfig.setReconnectAttempts(5);
        clientConfig.setServerRole("Hono Messaging Infrastructure");
        connectionOptions.trustStorePath.ifPresent(path -> {
            clientConfig.setTrustStorePath(path);
            connectionOptions.trustStorePassword.ifPresent(clientConfig::setTrustStorePassword);
        });
        if (connectionOptions.useSandbox) {
            clientConfig.setHost(ConnectionOptions.SANDBOX_HOST_NAME);
            clientConfig.setPort(15671);
            clientConfig.setTlsEnabled(true);
            clientConfig.setUsername(SANDBOX_AMQP_USER);
            clientConfig.setPassword(SANDBOX_AMQP_PWD);
        } else {
            connectionOptions.hostname.ifPresent(clientConfig::setHost);
            connectionOptions.portNumber.ifPresent(clientConfig::setPort);
            clientConfig.setHostnameVerificationRequired(!connectionOptions.disableHostnameVerification);
            Optional.ofNullable(connectionOptions.credentials)
                .ifPresent(creds -> {
                    clientConfig.setUsername(creds.username);
                    clientConfig.setPassword(creds.password);
                });
        }

        final var amqpClient = new ProtonBasedApplicationClient(HonoConnection.newConnection(vertx, clientConfig));
        System.err.printf("Connecting to AMQP 1.0 based messaging infrastructure [%s:%d]%n",
                clientConfig.getHost(), clientConfig.getPort());
        return startClientAndWaitForReadiness(amqpClient)
                .map(ok -> {
                    this.client = amqpClient;
                    return amqpClient;
                });
    }

    private Future<Void> startClientAndWaitForReadiness(final ApplicationClient<? extends MessageContext> client) {
        final Promise<Void> readyTracker = Promise.promise();
        client.addOnClientReadyHandler(readyTracker);
        return client.start()
                .compose(ok -> readyTracker.future());
    }

    /**
     * Gets a ready to use application client.
     *
     * @return The client.
     */
    public Future<ApplicationClient<? extends MessageContext>> getApplicationClient() {
        final Promise<ApplicationClient<? extends MessageContext>> result = Promise.promise();
        if (client != null) {
            result.complete(client);
        } else if (useAmqp) {
            createAmqpClient()
                .onSuccess(result::complete)
                .onFailure(result::fail);
        } else {
            createKafkaClient()
                .onSuccess(result::complete)
                .onFailure(result::fail);
        }
        return result.future();
    }

    /**
     * Stops the application client.
     *
     * @param ev The event indicating shutdown.
     */
    public void onStop(final @Observes ShutdownEvent ev) {
        if (client != null) {
            LOG.debug("disconnecting from Hono");
            client.stop()
                .onComplete(ar -> LOG.debug("stopped consumers"))
                .toCompletionStage()
                .toCompletableFuture()
                .join();
        }
    }
}
