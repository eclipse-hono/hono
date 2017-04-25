package org.eclipse.hono.server;

/**
 * Constants/metric names
 */
public class MetricConstants {

    public static String metricNameDownstreamConnections() {
        return "hono.server.connections.downstream";
    }

    public static String metricNameDownstreamLinkCredits(String address) {
        return "hono.server.link.downstream.credits." + address;
    }

    public static String metricNameDownstreamSenders(String address) {
        return "hono.server.senders.downstream." + address;
    }

    public static String metricNameUpstreamLinks(String address) {
        return "hono.server.receivers.upstream.links." + address;
    }

    public static String metricNameProcessedMessages(String address) {
        return "histogram.hono.server.messages." + address + ".processed";
    }

    public static String metricNameDiscardedMessages(String address) {
        return "histogram.hono.server.messages." + address + ".discarded";
    }

}
