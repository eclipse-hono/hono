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
package org.eclipse.hono.dispatcher.amqp;

import java.util.Arrays;
import java.util.Map;

/**
 * Wraps AMQP message.
 */
public final class AmqpMessage {
    private final byte[]              body;
    private final String              routingKey;
    private final String              exchange;
    private final String              replyTo;
    private final String              correlationId;
    private final String              contentType;
    private final Map<String, Object> headers;

    public byte[] getBody() {
        return Arrays.copyOf(body, body.length);
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getExchange() {
        return exchange;
    }

    public Map<String, Object> getHeaders() {
        return headers;
    }

    public String getReplyTo() {
        return replyTo;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public String getContentType() {
        return contentType;
    }

    private AmqpMessage(final byte[] body, final String correlationId, final String exchange,
            final Map<String, Object> headers, final String replyTo,
            final String routingKey, final String contentType) {
        this.body = body;
        this.correlationId = correlationId;
        this.exchange = exchange;
        this.headers = headers;
        this.replyTo = replyTo;
        this.routingKey = routingKey;
        this.contentType = contentType;
    }

    /**
     * Builder for {@link AmqpMessage}.
     */
    public static class Builder {
        private byte[]              body;
        private String              correlationId;
        private String              exchange;
        private Map<String, Object> headers;
        private String              replyTo;
        private String              routingKey;
        private String              contentType;

        /**
         * @param body the content of the {@link AmqpMessage} as byte-array
         * @return this builder
         */
        public Builder body(final byte[] body) {
            this.body = body;
            return this;
        }

        /**
         * @param body the content of the {@link AmqpMessage} as String
         * @return this builder
         */
        public Builder body(final String body) {
            this.body = body.getBytes();
            return this;
        }

        /**
         * @param correlationId the {@code correlationId} message property of the {@link AmqpMessage}
         * @return this builder
         */
        public Builder correlationId(final String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        /**
         * @param exchange the exchange the {@link AmqpMessage} should be published to
         * @return this builder
         */
        public Builder exchange(final String exchange) {
            this.exchange = exchange;
            return this;
        }

        /**
         * @param headers the {@code headers} message property of the {@link AmqpMessage}
         * @return this builder
         */
        public Builder headers(final Map<String, Object> headers) {
            this.headers = headers;
            return this;
        }

        /**
         * @param replyTo the {@code replyTo} message property of the {@link AmqpMessage}
         * @return this builder
         */
        public Builder replyTo(final String replyTo) {
            this.replyTo = replyTo;
            return this;
        }

        /**
         * @param routingKey the routing key the {@link AmqpMessage} should be send with
         * @return this builder
         */
        public Builder routingKey(final String routingKey) {
            this.routingKey = routingKey;
            return this;
        }

        public Builder contentType(final String contentType) {
            this.contentType = contentType;
            return this;
        }

        /**
         * @return the {@link AmqpMessage} configured before with this builder
         */
        public AmqpMessage build() {
            return new AmqpMessage(body, correlationId, exchange, headers, replyTo, routingKey, contentType);
        }
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder("AmqpMessage");
        result.append("{ routingKey=").append(AmqpMessage.thisOrElseNullString(routingKey));
        result.append(", exchange=").append(AmqpMessage.thisOrElseNullString(exchange));
        result.append(", replyTo=").append(AmqpMessage.thisOrElseNullString(replyTo));
        result.append(", correlationId=").append(AmqpMessage.thisOrElseNullString(correlationId));
        result.append(", contentType=").append(AmqpMessage.thisOrElseNullString(contentType));
        result.append(", headers=").append(headersToString());
        result.append("}");
        return result.toString();
    }

    private String headersToString() {
        final StringBuilder result = new StringBuilder("[");
        if (headers != null) {
            headers.forEach((s, o) -> result.append(s).append("=").append(o).append(", "));
        }
        return result.append("]").toString();
    }

    private static Object thisOrElseNullString(final Object arg) {
        return arg != null ? arg : "null";
    }
}
