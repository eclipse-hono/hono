/**
 * Copyright (c) 2018 Red Hat Inc and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Red Hat Inc - initial creation
 */

package org.eclipse.hono.service.metric;

import org.eclipse.hono.service.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.codahale.metrics.MetricRegistry;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;
import io.prometheus.client.vertx.MetricsHandler;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;

/**
 * A metrics reporter for Prometheus binding to a Vertx HTTP endpoint.
 */
public class PrometheusMetricsReporter implements Lifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(PrometheusMetricsReporter.class);

    private final Vertx vertx;
    private final int port;

    private final MetricRegistry metricRegistry;

    private HttpServer server;

    /**
     * Create a new instance of the reporter.
     * 
     * @param vertx The vertx context to create the HTTP server on.
     * @param port The port to bind the HTTP server to.
     * @param metricRegistry The metrics registry to export.
     */
    public PrometheusMetricsReporter(final Vertx vertx, final int port, final MetricRegistry metricRegistry) {
        this.vertx = vertx;
        this.port = port;
        this.metricRegistry = metricRegistry;
    }

    /**
     * Start the component.
     * 
     * @return A future notifying of the completion of the start call.
     */
    @Override
    public Future<Void> start() {

        final Future<Void> result = Future.future();

        CollectorRegistry.defaultRegistry.register(new DropwizardExports(this.metricRegistry));

        final Router router = Router.router(this.vertx);

        router.route("/metrics")
                .handler(new MetricsHandler());

        this.server = this.vertx
                .createHttpServer()
                .requestHandler(router::accept)
                .listen(this.port, startAttempt -> {

                    if (startAttempt.succeeded()) {
                        LOG.info("prometheus metrics server available on http://0.0.0.0:{}/metrics", this.port);
                        result.complete();
                    } else {
                        LOG.warn("failed to start prometheus metrics server", startAttempt.cause());
                        result.fail(startAttempt.cause());
                    }

                });

        return result;
    }

    /**
     * Stop the reporter and close the endpoint.
     * 
     * @return A future notifying of the completion of the stop call.
     */
    @Override
    public Future<Void> stop() {

        if (this.server == null) {
            return Future.succeededFuture();
        }

        final Future<Void> result = Future.future();
        this.server.close(result.completer());
        this.server = null;

        return result;
    }
}
