/**
 * Copyright (c) 2018, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.coap;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.Executor;

import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.observe.ObserveRelation;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.californium.core.server.resources.ResourceAttributes;
import org.eclipse.californium.core.server.resources.ResourceObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Context;

/**
 * A vert.x friendly wrapper around a {@code CoapResource}.
 * <p>
 * This class delegates all method invocations to the wrapped resource.
 * The <em>handleRequest</em> method is executed on the vert.x context
 * passed in to the constructor.
 */
class VertxCoapResource implements Resource {

    private static final Logger LOG = LoggerFactory.getLogger(VertxCoapResource.class);

    /**
     * Vert.x context to process requests on.
     */
    private final Context adapterContext;
    private final Resource resource;

    /**
     * Wraps a resource.
     *
     * @param resource The resource to wrap.
     * @param adapterContext The vert.x context to run the request handler on.
     * @throws NullPointerException if any of the params are {@code null}.
     */
    VertxCoapResource(final Resource resource, final Context adapterContext) {
        this.resource = Objects.requireNonNull(resource);
        this.adapterContext = Objects.requireNonNull(adapterContext);
    }

    final Resource getWrappedResource() {
        return resource;
    }

    final Context getContext() {
        return adapterContext;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method invokes the wrapped resource's {@code CoapResource.handleRequest()}
     * method on the vert.x context.
     */
    @Override
    public final void handleRequest(final Exchange exchange) {

        LOG.debug("running handler for resource [/{}] on vert.x context", resource.getName());
        adapterContext.runOnContext(s -> resource.handleRequest(exchange));
    }

    @Override
    public Resource getChild(final String name) {
        return resource.getChild(name);
    }

    @Override
    public String getName() {
        return resource.getName();
    }

    @Override
    public void setName(final String name) {
        resource.setName(name);
    }

    @Override
    public String getPath() {
        return resource.getPath();
    }

    @Override
    public void setPath(final String path) {
        resource.setPath(path);
    }

    @Override
    public String getURI() {
        return resource.getURI();
    }

    @Override
    public boolean isVisible() {
        return resource.isVisible();
    }

    @Override
    public boolean isCachable() {
        return resource.isCachable();
    }

    @Override
    public boolean isObservable() {
        return resource.isObservable();
    }

    @Override
    public ResourceAttributes getAttributes() {
        return resource.getAttributes();
    }

    @Override
    public void add(final Resource child) {
        resource.add(child);
    }

    @Override
    public boolean delete(final Resource child) {
        return resource.delete(child);
    }

    @Override
    public Collection<Resource> getChildren() {
        return resource.getChildren();
    }

    @Override
    public Resource getParent() {
        return resource.getParent();
    }

    @Override
    public void setParent(final Resource parent) {
        resource.setParent(parent);
    }

    @Override
    public void addObserver(final ResourceObserver observer) {
        resource.addObserver(observer);
    }

    @Override
    public void removeObserver(final ResourceObserver observer) {
        resource.removeObserver(observer);
    }

    @Override
    public void addObserveRelation(final ObserveRelation relation) {
        resource.addObserveRelation(relation);
    }

    @Override
    public void removeObserveRelation(final ObserveRelation relation) {
        resource.removeObserveRelation(relation);
    }

    @Override
    public Executor getExecutor() {
        return resource.getExecutor();
    }

}
