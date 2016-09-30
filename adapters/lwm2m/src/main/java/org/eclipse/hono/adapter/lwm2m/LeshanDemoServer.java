/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.adapter.lwm2m;

import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.leshan.core.node.codec.DefaultLwM2mNodeDecoder;
import org.eclipse.leshan.core.node.codec.DefaultLwM2mNodeEncoder;
import org.eclipse.leshan.core.node.codec.LwM2mNodeDecoder;
import org.eclipse.leshan.core.node.codec.LwM2mNodeEncoder;
import org.eclipse.leshan.server.californium.CaliforniumObservationRegistry;
import org.eclipse.leshan.server.californium.LeshanServerBuilder;
import org.eclipse.leshan.server.californium.impl.CaliforniumObservationRegistryImpl;
import org.eclipse.leshan.server.californium.impl.InMemoryLwM2mObservationStore;
import org.eclipse.leshan.server.californium.impl.LeshanServer;
import org.eclipse.leshan.server.californium.impl.LwM2mObservationStore;
import org.eclipse.leshan.server.client.ClientRegistry;
import org.eclipse.leshan.server.client.ClientRegistryListener;
import org.eclipse.leshan.server.demo.servlet.ClientServlet;
import org.eclipse.leshan.server.demo.servlet.EventServlet;
import org.eclipse.leshan.server.demo.servlet.ObjectSpecServlet;
import org.eclipse.leshan.server.demo.servlet.SecurityServlet;
import org.eclipse.leshan.server.model.LwM2mModelProvider;
import org.eclipse.leshan.server.observation.ObservationRegistryListener;
import org.eclipse.leshan.server.security.SecurityRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.EmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.jetty.JettyEmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.jetty.JettyServerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * The <em>leshan</em> project's demo LWM2M server.
 * <p>
 * The server can be configured using the setters directly or (more conveniently) using Spring Boot's autowiring capabilities.
 */
@Component
public class LeshanDemoServer {

    private static final Logger LOG = LoggerFactory.getLogger(LeshanDemoServer.class);

    private final LwM2mObservationStore observationStore = new InMemoryLwM2mObservationStore();
    private final LwM2mNodeEncoder encoder = new DefaultLwM2mNodeEncoder();
    private final LwM2mNodeDecoder decoder = new DefaultLwM2mNodeDecoder();

    private final Set<ClientRegistryListener> clientRegistryListeners = new HashSet<>();
    private final Set<ObservationRegistryListener> observationRegistryListeners = new HashSet<>();
    private LeshanConfigProperties leshanConfig;
    private CaliforniumObservationRegistry observationRegistry;
    private ClientRegistry clientRegistry;
    private LwM2mModelProvider modelProvider;
    private SecurityRegistry securityRegistry;

    @Autowired
    public void setClientRegistry(final ClientRegistry clientRegistry) {
        this.clientRegistry = Objects.requireNonNull(clientRegistry);
        LOG.info("using ClientRegistry: {}", clientRegistry.getClass().getName());
    }

    @Autowired
    public void setModelProvider(final LwM2mModelProvider modelProvider) {
        this.modelProvider = Objects.requireNonNull(modelProvider);
        LOG.info("using LwM2mModelProvider: {}", modelProvider.getClass().getName());
    }

    @Autowired
    public void setLeshanConfigProperties(final LeshanConfigProperties leshanConfig) {
        this.leshanConfig = Objects.requireNonNull(leshanConfig);
    }

    @Autowired
    public void setSecurityRegistry(final SecurityRegistry securityRegistry) {
        this.securityRegistry = Objects.requireNonNull(securityRegistry);
        LOG.info("using SecurityRegistry: {}", securityRegistry.getClass().getName());
    }

    @Autowired(required = false)
    public void setClientRegistryListeners(final Set<ClientRegistryListener> listeners) {
        Objects.requireNonNull(listeners);
        this.clientRegistryListeners.addAll(listeners);
    }

    @Autowired(required = false)
    public void setObservationRegistryListeners(final Set<ObservationRegistryListener> listeners) {
        Objects.requireNonNull(listeners);
        this.observationRegistryListeners.addAll(listeners);
    }

    @Bean
    public EmbeddedServletContainerFactory servletContainer() {
        JettyEmbeddedServletContainerFactory factory = new JettyEmbeddedServletContainerFactory(leshanConfig.getHttpPort());
        factory.addServerCustomizers(new JettyServerCustomizer() {

            @Override
            public void customize(final Server server) {
                WebAppContext root = new WebAppContext();
                root.setContextPath("/");
                root.setResourceBase(getClass().getClassLoader().getResource("webapp").toExternalForm());
                root.setParentLoaderPriority(true);
                server.setHandler(root);
                LOG.info("set root context resource base");
                try {
                    registerServlets(root);
                } catch (GeneralSecurityException e) {
                    LOG.error("could not start leshan server", e);
                }
            }
        });
        return factory;
    }

    @PostConstruct
    public void startup() {
        observationRegistry = new CaliforniumObservationRegistryImpl(observationStore,
                clientRegistry, modelProvider, decoder);

        for (ObservationRegistryListener listener : observationRegistryListeners) {
            LOG.debug("adding observation listener: {}", listener);
            observationRegistry.addListener(listener);
        }

        for (ClientRegistryListener listener : clientRegistryListeners) {
            LOG.debug("adding client registry listener: {}", listener);
            clientRegistry.addListener(listener);
        }
    }

    private LeshanServer createServer() throws GeneralSecurityException {

        // Prepare LWM2M server
        LeshanServerBuilder builder = new LeshanServerBuilder()
                .setLocalAddress(leshanConfig.getCoapBindAddress(), leshanConfig.getCoapPort())
                .setLocalSecureAddress(leshanConfig.getCoapsBindAddress(), leshanConfig.getCoapsPort())
                .setEncoder(encoder)
                .setDecoder(decoder)
                .setObservationRegistry(observationRegistry)
                .setObjectModelProvider(modelProvider)
                .setSecurityRegistry(securityRegistry)
                .setClientRegistry(clientRegistry);

        return builder.build();
    }

    private void registerServlets(final ServletContextHandler root) throws GeneralSecurityException {

        LeshanServer lwServer = createServer();

        EventServlet eventServlet = new EventServlet(lwServer, lwServer.getSecureAddress().getPort());
        root.addServlet(new ServletHolder(eventServlet), "/event/*");

        ClientServlet clientServlet = new ClientServlet(lwServer, lwServer.getSecureAddress().getPort());
        root.addServlet(new ServletHolder(clientServlet), "/api/clients/*");

        SecurityServlet securityServlet = new SecurityServlet(lwServer.getSecurityRegistry());
        root.addServlet(new ServletHolder(securityServlet), "/api/security/*");

        ObjectSpecServlet objectSpecServlet = new ObjectSpecServlet(lwServer.getModelProvider());
        root.addServlet(new ServletHolder(objectSpecServlet), "/api/objectspecs/*");

        lwServer.start();
    }
}
