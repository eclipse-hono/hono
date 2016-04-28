/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial API and implementation and initial documentation
 */
package org.eclipse.hono.mom.rabbitmq;

import static org.eclipse.hono.util.MessageHelper.*;

import java.io.IOException;
import java.util.Optional;

import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.mom.rabbitmq.Exchange.Type;
import org.eclipse.hono.telemetry.impl.BaseTelemetryAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Connection;

import io.vertx.core.Future;

/**
 * 
 */
@Service
@Profile("rabbitmq-telemetry")
public final class RabbitMqTelemetryAdapter extends BaseTelemetryAdapter {

    public static final String      TELEMETRY_UPLOAD_EXCHANGE = "telemetry";
    private static final Logger     LOGGER                    = LoggerFactory.getLogger(RabbitMqTelemetryAdapter.class);
    private Connection              brokerConnection;
    private String                  brokerUri                 = "amqp://rabbitmq:5672";
    private RabbitMqHelper          channel;

    @Override
    public void doStart(final Future<Void> startFuture) throws Exception {
        connectToBroker();
        declareTelemetryUploadExchange();
        LOGGER.info("telemetry adapter started");
        startFuture.complete();
    }

    /**
     * Gets the URI of the RabbitMQ broker to use for storing telemetry data.
     * 
     * @return the URI
     */
    public String getBrokerUri() {
        return brokerUri;
    }

    /**
     * Sets the URI of the RabbitMQ broker to use for storing telemetry data.
     * 
     * @param uri the RabbitMQ connection URI
     */
    @Value(value = "${hono.rabbitmq.brokerUri}")
    public void setBrokerUri(String uri) {
        this.brokerUri = uri;
    }

    @Override
    public void doStop(final Future<Void> stopFuture) {
        if (brokerConnection != null) {
            try {
                brokerConnection.close(2000);
                LOGGER.info("disconnected from RabbitMQ broker [{}]", brokerUri);
            } catch (IOException e) {
                LOGGER.error("problem disconnecting from RabbitMQ broker [{}]", brokerUri);
            }
        }
        stopFuture.complete();
    }

    private void connectToBroker() throws IOException {
        if (brokerUri == null) {
            throw new IllegalStateException("RabbitMQ broker URI must be set");
        }
        brokerConnection = RabbitMqConnection.connect(brokerUri);
        LOGGER.info("connected to RabbitMQ broker [{}]", brokerUri);
        channel = RabbitMqHelperImpl.getInstance(brokerConnection);
    }

    private void declareTelemetryUploadExchange() {
        if (channel == null) {
            throw new IllegalStateException("Channel to RabbitMQ broker must be initialized");
        }
        channel.declareExchange(TELEMETRY_UPLOAD_EXCHANGE, Type.fanout);
    }

    @Override
    public boolean processTelemetryData(final Message msg) {
        String tenantId = getTenantId(msg);
        Data body = (Data) msg.getBody();
        LOGGER.trace("forwarding telemetry message [id: {}, tenant-id: {}] to RabbitMQ broker", msg.getMessageId(), tenantId);
        channel.publishMessage(createProperties(msg), body.getValue().getArray(),
                TELEMETRY_UPLOAD_EXCHANGE,
                tenantId);
        return true;
    }

    @SuppressWarnings("unchecked")
    private AMQP.BasicProperties createProperties(final Message msg) {
        BasicProperties.Builder b = new BasicProperties.Builder();
        Optional.ofNullable(msg.getApplicationProperties()).ifPresent(props -> b.headers(props.getValue()));
        Optional.ofNullable(msg.getCorrelationId())
                .ifPresent(correlationId -> b.correlationId((String) correlationId));
        Optional.ofNullable(msg.getReplyTo()).ifPresent(replyTo -> b.replyTo(replyTo));
        Optional.ofNullable(msg.getContentType()).ifPresent(contentType -> b.contentType(contentType));
        Optional.ofNullable(msg.getContentEncoding()).ifPresent(contentEnc -> b.contentEncoding(contentEnc));
        Optional.ofNullable(msg.getMessageId()).ifPresent(messageId -> b.messageId((String) messageId));
        return b.build();
    }
}
