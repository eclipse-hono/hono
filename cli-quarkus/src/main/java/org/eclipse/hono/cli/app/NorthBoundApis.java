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


package org.eclipse.hono.cli.app;

import java.util.HashMap;
import java.util.Optional;

import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Singleton;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.runtime.ShutdownEvent;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
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
        if (!connectionOptions.useSandbox && (connectionOptions.hostname.isEmpty() || connectionOptions.portNumber.isEmpty())) {
            throw new ParameterException(
                    spec.commandLine(),
                    """
                    Missing required option: both '--host=<hostname>' and '--port=<portNumber> need to \
                    be specified if not using '--sandbox'.
                    """);
        }        
    }

    private String scramJaasConfig(final String username, final String password) {
        return """
                %s required username="%s" password="%s";
                """.formatted(ScramLoginModule.class.getName(), username, password);
    }

    Future<KafkaApplicationClientImpl> createKafkaClient() {
        final var commonProps = new HashMap<String, String>();
        if (connectionOptions.useSandbox) {
            commonProps.put(
                    CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG,
                    "%1$s:9092,%1$s:9094".formatted(ConnectionOptions.SANDBOX_HOST_NAME));
            commonProps.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.SASL_PLAINTEXT.name);
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
            validateConnectionOptions();
            commonProps.put(
                    CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG,
                    "%s:%d".formatted(connectionOptions.hostname.get(), connectionOptions.portNumber.get()));

            connectionOptions.trustStorePath
                .ifPresent(path -> {
                    commonProps.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, path);
                    connectionOptions.trustStorePassword
                        .ifPresent(pwd -> commonProps.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, pwd));
                });

            if (connectionOptions.credentials == null) {
                if (connectionOptions.trustStorePath.isPresent()) {
                    commonProps.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.SSL.name);
                }
            } else {
                if (connectionOptions.trustStorePath.isEmpty()) {
                    commonProps.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.SASL_PLAINTEXT.name);
                } else {
                    commonProps.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.SASL_SSL.name);
                    ;
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

        final var kafkaClient = new KafkaApplicationClientImpl(
                vertx,
                consumerProps,
                CachingKafkaProducerFactory.sharedFactory(vertx),
                producerProps);
        this.client = kafkaClient;
        return Future.succeededFuture(kafkaClient);
    }

    Future<ProtonBasedApplicationClient> createAmqpClient() {

        final var clientConfig = new ClientConfigProperties();
        clientConfig.setReconnectAttempts(5);
        connectionOptions.trustStorePath.ifPresent(path -> {
            clientConfig.setTrustStorePath(path);
            connectionOptions.trustStorePassword.ifPresent(clientConfig::setTrustStorePassword);
        });
        if (connectionOptions.useSandbox) {
            clientConfig.setHost(ConnectionOptions.SANDBOX_HOST_NAME);
            clientConfig.setPort(connectionOptions.trustStorePath.map(s -> 15671).orElse(15672));
            clientConfig.setUsername(SANDBOX_AMQP_USER);
            clientConfig.setPassword(SANDBOX_AMQP_PWD);
        } else {
            validateConnectionOptions();
            connectionOptions.hostname.ifPresent(clientConfig::setHost);
            connectionOptions.portNumber.ifPresent(clientConfig::setPort);
            connectionOptions.trustStorePath.ifPresent(clientConfig::setTrustStorePath);
            Optional.ofNullable(connectionOptions.credentials)
                .ifPresent(creds -> {
                    clientConfig.setUsername(creds.username);
                    clientConfig.setPassword(creds.password);
                });
        }

        final var amqpClient = new ProtonBasedApplicationClient(HonoConnection.newConnection(vertx, clientConfig));
        return amqpClient.connect()
                .onSuccess(con -> {
                    this.client = amqpClient;
                })
                .map(amqpClient);
    }

    /**
     * Gets a ready to use application client.
     *
     * the AMQP 1.0 based endpoint has been reestablished.
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
