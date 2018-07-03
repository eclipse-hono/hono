/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */
package org.eclipse.hono.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;

import org.apache.qpid.proton.engine.Link;
import org.apache.qpid.proton.engine.Session;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonLink;
import io.vertx.proton.ProtonSession;
import io.vertx.proton.impl.ProtonReceiverImpl;
import io.vertx.proton.impl.ProtonSenderImpl;
import io.vertx.proton.impl.ProtonSessionImpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods for working with Proton objects.
 */
public final class HonoProtonHelper {

    private static final Logger LOG = LoggerFactory.getLogger(HonoProtonHelper.class);

    private static Method getReceiverMethod;
    private static Method getSenderMethod;
    private static Field sessionField;

    static {
        try {
            getReceiverMethod = ProtonReceiverImpl.class.getDeclaredMethod("getReceiver");
            getReceiverMethod.setAccessible(true);
            getSenderMethod = ProtonSenderImpl.class.getDeclaredMethod("sender");
            getSenderMethod.setAccessible(true);
            sessionField = ProtonSessionImpl.class.getDeclaredField("session");
            sessionField.setAccessible(true);
        } catch (final NoSuchMethodException | SecurityException e) {
            LOG.error("cannot get accessors for Proton Link objects using reflection", e);
        } catch (final NoSuchFieldException e) {
            LOG.error("cannot get field for Proton Session object using reflection", e);
        }
    }

    private HonoProtonHelper() {
        // prevent instantiation
    }

    /**
     * Sets a handler on a link that is invoked when an AMQP <em>detach</em> frame
     * with its <em>close</em> property set to {@code false} is received from the peer.
     * <p>
     * The resources maintained for the link will be freed up after the given handler has
     * been invoked.
     * 
     * @param <T> The type of link.
     * @param link The link to set the handler on.
     * @param handler The handler to invoke.
     * @return The wrapper that has been created around the given handler.
     * @throws NullPointerException if link or handler are {@code null}.
     */
    public static <T extends ProtonLink<T>> Handler<AsyncResult<T>>  setDetachHandler(
            final ProtonLink<T> link,
            final Handler<AsyncResult<T>> handler) {

        Objects.requireNonNull(link);
        Objects.requireNonNull(handler);

        final Handler<AsyncResult<T>> wrappedHandler = remoteDetach -> {
            handler.handle(remoteDetach);
            freeLinkResources(link);
        };
        link.detachHandler(wrappedHandler);
        return wrappedHandler;
    }

    /**
     * Sets a handler on a link that is invoked when an AMQP <em>detach</em> frame
     * with its <em>close</em> property set to {@code true} is received from the peer.
     * <p>
     * The resources maintained for the link will be freed up after the given handler has
     * been invoked.
     * 
     * @param <T> The type of link.
     * @param link The link to set the handler on.
     * @param handler The handler to invoke.
     * @return The wrapper that has been created around the given handler.
     * @throws NullPointerException if link or handler are {@code null}.
     */
    public static <T extends ProtonLink<T>> Handler<AsyncResult<T>> setCloseHandler(
            final ProtonLink<T> link,
            final Handler<AsyncResult<T>> handler) {

        Objects.requireNonNull(link);
        Objects.requireNonNull(handler);

        final Handler<AsyncResult<T>> wrappedHandler = remoteClose -> {
            handler.handle(remoteClose);
            freeLinkResources(link);
        };
        link.closeHandler(wrappedHandler);
        return wrappedHandler;
    }

    /**
     * Sets a default handler on a link that is invoked when an AMQP <em>detach</em> frame
     * with its <em>close</em> property set to {@code true} is received from the peer.
     * <p>
     * The default handler sends a <em>detach</em> frame if the link has not been closed
     * locally already and then frees up the resources maintained for the link by invoking
     * {@link #freeLinkResources(ProtonLink)}.
     * 
     * @param <T> The type of link.
     * @param link The link to set the handler on.
     * @throws NullPointerException if link is {@code null}.
     */
    public static <T extends ProtonLink<T>> void setDefaultCloseHandler(final ProtonLink<T> link) {

        link.closeHandler(remoteClose -> {
            if (link.isOpen()) {
                // peer has initiated closing
                // respond with our detach frame
                link.close();
            }
            freeLinkResources(link);
        });
    }

    /**
     * Sets a default handler on a session that is invoked when an AMQP <em>end</em> frame
     * is received from the peer.
     * <p>
     * The default handler sends an <em>end</em> frame and then frees up the resources
     * maintained for the session by invoking {@link #freeSessionResources(ProtonSession)}.
     * 
     * @param session The session to set the handler on.
     * @throws NullPointerException if session is {@code null}.
     */
    public static void setDefaultCloseHandler(final ProtonSession session) {

        session.closeHandler(remoteClose -> {
            session.close();
            freeSessionResources(session);
        });
    }

    /**
     * Frees resources concerning the given link.
     * <p>
     * This method uses reflection to get at the link's underlying Proton {@code Link}
     * instance and invoke its <em>free</em> method. In vert.x 3.5.3 the
     * {@code ProtonLink} class will expose this method publicly so the implementation
     * of this method can then be changed accordingly.
     * 
     * @param protonLink the link to free resources for.
     * @see <a href="https://github.com/vert-x3/vertx-proton/issues/82">vertx-proton#83</a>
     */
    public static void freeLinkResources(final ProtonLink<?> protonLink) {
        if (protonLink != null) {
            if (getReceiverMethod == null || getSenderMethod == null) {
                throw new IllegalStateException("no access to Proton Link objects");
            } else {
                try {
                    if (protonLink instanceof ProtonReceiverImpl) {
                        final Link link = (Link) getReceiverMethod.invoke(protonLink);
                        link.free();
                        ((ProtonReceiverImpl) protonLink).getSession().getConnectionImpl().flush();
                        LOG.trace("freed up resources of receiver link [{}]", protonLink.getName());
                    } else if (protonLink instanceof ProtonSenderImpl) {
                        final Link link = (Link) getSenderMethod.invoke(protonLink);
                        link.free();
                        ((ProtonSenderImpl) protonLink).getSession().getConnectionImpl().flush();
                        LOG.trace("freed up resources of sender link [{}]", protonLink.getName());
                    } else {
                        LOG.debug("cannot free up resources for non-ProtonLinkImpl instance");
                    }
                } catch (final Exception e) {
                    LOG.error("error freeing link resources", e);
                }
            }
        }
    }

    /**
     * Frees up resources held by a session.
     * <p>
     * This method uses reflection to get at the session's underlying Proton {@code Session}
     * instance and invoke its <em>free</em> method. In vert.x 3.5.3 the
     * {@code ProtonSession} class will expose this method publicly so the implementation
     * of this method can then be changed accordingly.
     * 
     * @param protonSession The session to free resources for.
     * @see <a href="https://github.com/vert-x3/vertx-proton/issues/82">vertx-proton#83</a>
     */
    public static void freeSessionResources(final ProtonSession protonSession) {
        if (protonSession != null) {
            if (sessionField == null) {
                throw new IllegalStateException("no access to Proton Session object");
            } else {
                try {
                    if (protonSession instanceof ProtonSessionImpl) {
                        final Session session = (Session) sessionField.get(protonSession);
                        session.free();
                        ((ProtonSessionImpl ) protonSession).getConnectionImpl().flush();
                        LOG.trace("freed up resources of session");
                    } else {
                        LOG.debug("cannot free up resources for non-ProtonSessionImpl instance");
                    }
                } catch (final Exception e) {
                    LOG.error("error freeing session resources", e);
                }
            }
        }
    }

}
