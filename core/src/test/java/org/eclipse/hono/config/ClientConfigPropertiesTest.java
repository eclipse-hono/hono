package org.eclipse.hono.config;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.vertx.core.net.ProxyType;

public class ClientConfigPropertiesTest {

    @Test(expected = IllegalArgumentException.class)
    public void testSetInvalidProxyPort() {
        ClientConfigProperties config = new ClientConfigProperties();
        config.setProxyPort(0);
    }

    @Test(expected = NullPointerException.class)
    public void testSetEmptyProxyHost() {
        ClientConfigProperties config = new ClientConfigProperties();
        config.setProxyHost(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidProxyType() {
        ClientConfigProperties config = new ClientConfigProperties();
        config.setProxyType("Unknown");
    }

    @Test(expected = NullPointerException.class)
    public void testEmptyProxyType() {
        ClientConfigProperties config = new ClientConfigProperties();
        config.setProxyType(null);
    }

    @Test
    public void testProxyOptions() {
        ClientConfigProperties config = new ClientConfigProperties();
        assertEquals(ProxyType.SOCKS5, config.getProxyType());
        config.setProxyType("SOCKS4");
        assertEquals(ProxyType.SOCKS4, config.getProxyType());
    }
}
