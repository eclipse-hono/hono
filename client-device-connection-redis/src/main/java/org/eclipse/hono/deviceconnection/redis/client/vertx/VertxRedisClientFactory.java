/**
 * TODO.
 */
package org.eclipse.hono.deviceconnection.redis.client.vertx;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.hono.deviceconnection.redis.client.config.NetConfig;
import org.eclipse.hono.deviceconnection.redis.client.config.RedisConfig;
import org.eclipse.hono.deviceconnection.redis.client.config.TlsConfig;

import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.vertx.core.runtime.SSLConfigHelper;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.ProxyOptions;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisClientType;
import io.vertx.redis.client.RedisOptions;

/**
 * Creates Vert.x Redis client for a given {@link RedisConfig}.
 */
public class VertxRedisClientFactory {

    public static final String CLIENT_NAME = "hono-deviceconnection";

    private VertxRedisClientFactory() {
        // Avoid direct instantiation.
    }

    /**
     * TODO.
     * @param vertx TODO.
     * @param config TODO.
     * @return TODO.
     * @throws ConfigurationException TODO.
     */
    public static Redis create(final Vertx vertx, final RedisConfig config) throws ConfigurationException {
        final RedisOptions options = new RedisOptions();

        final List<URI> hosts;
        if (config.hosts().isPresent()) {
            hosts = new ArrayList<>(config.hosts().get());
            for (URI uri : config.hosts().get()) {
                options.addConnectionString(uri.toString().trim());
            }
        } else {
            throw new ConfigurationException("Redis host not configured");
        }

        if (RedisClientType.STANDALONE == config.clientType()) {
            if (hosts.size() > 1) {
                throw new ConfigurationException("Multiple Redis hosts supplied for non-clustered configuration");
            }
        }

        config.masterName().ifPresent(options::setMasterName);
        options.setMaxNestedArrays(config.maxNestedArrays());
        options.setMaxPoolSize(config.maxPoolSize());
        options.setMaxPoolWaiting(config.maxPoolWaiting());
        options.setMaxWaitingHandlers(config.maxWaitingHandlers());

        options.setProtocolNegotiation(config.protocolNegotiation());
        //config.preferredProtocolVersion().ifPresent(options::setPreferredProtocolVersion);
        options.setPassword(config.password().orElse(null));
        config.poolCleanerInterval().ifPresent(d -> options.setPoolCleanerInterval((int) d.toMillis()));
        options.setPoolRecycleTimeout((int) config.poolRecycleTimeout().toMillis());
        options.setHashSlotCacheTTL(config.hashSlotCacheTtl().toMillis());

        config.role().ifPresent(options::setRole);
        options.setType(config.clientType());
        config.replicas().ifPresent(options::setUseReplicas);

        options.setNetClientOptions(toNetClientOptions(config));

        options.setPoolName(CLIENT_NAME);
        // Use the convention defined by Quarkus Micrometer Vert.x metrics to create metrics prefixed with redis.
        // and the client_name as tag.
        // See io.quarkus.micrometer.runtime.binder.vertx.VertxMeterBinderAdapter.extractPrefix and
        // io.quarkus.micrometer.runtime.binder.vertx.VertxMeterBinderAdapter.extractClientName
        options.getNetClientOptions().setMetricsName("redis|" + CLIENT_NAME);

        return Redis.createClient(vertx, options);
    }

    private static NetClientOptions toNetClientOptions(final RedisConfig config) {
        final NetConfig tcp = config.tcp();
        final TlsConfig tls = config.tls();
        final NetClientOptions net = new NetClientOptions();

        tcp.alpn().ifPresent(net::setUseAlpn);
        tcp.applicationLayerProtocols().ifPresent(net::setApplicationLayerProtocols);
        tcp.connectionTimeout().ifPresent(d -> net.setConnectTimeout((int) d.toMillis()));

        final String verificationAlgorithm = tls.hostnameVerificationAlgorithm();
        if ("NONE".equalsIgnoreCase(verificationAlgorithm)) {
            net.setHostnameVerificationAlgorithm("");
        } else {
            net.setHostnameVerificationAlgorithm(verificationAlgorithm);
        }

        tcp.idleTimeout().ifPresent(d -> net.setIdleTimeout((int) d.toSeconds()));

        tcp.keepAlive().ifPresent(b -> net.setTcpKeepAlive(true));
        tcp.noDelay().ifPresent(b -> net.setTcpNoDelay(true));

        net.setSsl(tls.enabled()).setTrustAll(tls.trustAll());

        SSLConfigHelper.configurePemTrustOptions(net, tls.trustCertificatePem().convert());
        SSLConfigHelper.configureJksTrustOptions(net, tls.trustCertificateJks().convert());
        SSLConfigHelper.configurePfxTrustOptions(net, tls.trustCertificatePfx().convert());

        SSLConfigHelper.configurePemKeyCertOptions(net, tls.keyCertificatePem().convert());
        SSLConfigHelper.configureJksKeyCertOptions(net, tls.keyCertificateJks().convert());
        SSLConfigHelper.configurePfxKeyCertOptions(net, tls.keyCertificatePfx().convert());

        net.setReconnectAttempts(config.reconnectAttempts());
        net.setReconnectInterval(config.reconnectInterval().toMillis());

        tcp.localAddress().ifPresent(net::setLocalAddress);
        tcp.nonProxyHosts().ifPresent(net::setNonProxyHosts);
        if (tcp.proxyOptions().host().isPresent()) {
            final ProxyOptions po = new ProxyOptions();
            po.setHost(tcp.proxyOptions().host().get());
            po.setType(tcp.proxyOptions().type());
            po.setPort(tcp.proxyOptions().port());
            tcp.proxyOptions().username().ifPresent(po::setUsername);
            tcp.proxyOptions().password().ifPresent(po::setPassword);
            net.setProxyOptions(po);
        }
        tcp.readIdleTimeout().ifPresent(d -> net.setReadIdleTimeout((int) d.toSeconds()));
        tcp.reconnectAttempts().ifPresent(net::setReconnectAttempts);
        tcp.reconnectInterval().ifPresent(v -> net.setReconnectInterval(v.toMillis()));
        tcp.reuseAddress().ifPresent(net::setReuseAddress);
        tcp.reusePort().ifPresent(net::setReusePort);
        tcp.receiveBufferSize().ifPresent(net::setReceiveBufferSize);
        tcp.sendBufferSize().ifPresent(net::setSendBufferSize);
        tcp.soLinger().ifPresent(d -> net.setSoLinger((int) d.toMillis()));
        tcp.secureTransportProtocols().ifPresent(net::setEnabledSecureTransportProtocols);
        tcp.trafficClass().ifPresent(net::setTrafficClass);
        tcp.noDelay().ifPresent(net::setTcpNoDelay);
        tcp.cork().ifPresent(net::setTcpCork);
        tcp.keepAlive().ifPresent(net::setTcpKeepAlive);
        tcp.fastOpen().ifPresent(net::setTcpFastOpen);
        tcp.quickAck().ifPresent(net::setTcpQuickAck);
        tcp.writeIdleTimeout().ifPresent(d -> net.setWriteIdleTimeout((int) d.toSeconds()));

        return net;
    }
}
