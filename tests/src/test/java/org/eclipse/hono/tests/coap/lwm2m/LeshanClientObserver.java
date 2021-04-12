/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.tests.coap.lwm2m;

import org.eclipse.leshan.client.observer.LwM2mClientObserver2;
import org.eclipse.leshan.client.servers.ServerIdentity;
import org.eclipse.leshan.core.ResponseCode;
import org.eclipse.leshan.core.request.BootstrapRequest;
import org.eclipse.leshan.core.request.DeregisterRequest;
import org.eclipse.leshan.core.request.RegisterRequest;
import org.eclipse.leshan.core.request.UpdateRequest;

/**
 * A LeshanClientObserver.
 *
 */
public class LeshanClientObserver implements LwM2mClientObserver2 {

    /**
     * {@inheritDoc}
     */
    @Override
    public void onBootstrapStarted(final ServerIdentity bsserver, final BootstrapRequest request) {
        // TODO Auto-generated method stub

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onBootstrapSuccess(final ServerIdentity bsserver, final BootstrapRequest request) {
        // TODO Auto-generated method stub

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onBootstrapFailure(
            final ServerIdentity bsserver,
            final BootstrapRequest request,
            final ResponseCode responseCode,
            final String errorMessage,
            final Exception cause) {
        // TODO Auto-generated method stub

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onBootstrapTimeout(final ServerIdentity bsserver, final BootstrapRequest request) {
        // TODO Auto-generated method stub

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRegistrationStarted(final ServerIdentity server, final RegisterRequest request) {
        // TODO Auto-generated method stub

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRegistrationSuccess(final ServerIdentity server, final RegisterRequest request,
            final String registrationID) {
        // TODO Auto-generated method stub

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRegistrationFailure(final ServerIdentity server, final RegisterRequest request,
            final ResponseCode responseCode,
            final String errorMessage, final Exception cause) {
        // TODO Auto-generated method stub

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRegistrationTimeout(final ServerIdentity server, final RegisterRequest request) {
        // TODO Auto-generated method stub

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onUpdateStarted(final ServerIdentity server, final UpdateRequest request) {
        // TODO Auto-generated method stub

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onUpdateSuccess(final ServerIdentity server, final UpdateRequest request) {
        // TODO Auto-generated method stub

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onUpdateFailure(final ServerIdentity server, final UpdateRequest request,
            final ResponseCode responseCode,
            final String errorMessage, final Exception cause) {
        // TODO Auto-generated method stub

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onUpdateTimeout(final ServerIdentity server, final UpdateRequest request) {
        // TODO Auto-generated method stub

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDeregistrationStarted(final ServerIdentity server, final DeregisterRequest request) {
        // TODO Auto-generated method stub

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDeregistrationSuccess(final ServerIdentity server, final DeregisterRequest request) {
        // TODO Auto-generated method stub

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDeregistrationFailure(final ServerIdentity server, final DeregisterRequest request,
            final ResponseCode responseCode,
            final String errorMessage, final Exception cause) {
        // TODO Auto-generated method stub

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDeregistrationTimeout(final ServerIdentity server, final DeregisterRequest request) {
        // TODO Auto-generated method stub

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onUnexpectedError(final Throwable unexpectedError) {
        // TODO Auto-generated method stub

    }

}
