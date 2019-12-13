/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.service.tenant;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.amqp.RequestResponseEndpoint;
import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TenantConstants;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.DecodeException;

/**
 * An {@code AmqpEndpoint} for managing tenant information.
 * <p>
 * This endpoint implements Hono's <a href="https://www.eclipse.org/hono/docs/api/tenant/">Tenant API</a>. It receives AMQP 1.0
 * messages representing requests and sends them to an address on the vertx event bus for processing. The outcome is
 * then returned to the peer in a response message.
 */
public class TenantAmqpEndpoint extends RequestResponseEndpoint<ServiceConfigProperties> {

    /**
     * Creates a new tenant endpoint for a vertx instance.
     *
     * @param vertx The vertx instance to use.
     */
    @Autowired
    public TenantAmqpEndpoint(final Vertx vertx) {
        super(Objects.requireNonNull(vertx));
    }

    @Override
    public final String getName() {
        return TenantConstants.TENANT_ENDPOINT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected final String getEventBusServiceAddress() {
        return RegistryManagementConstants.EVENT_BUS_ADDRESS_TENANT_IN;
    }

    /**
     * Checks if the client is authorized to invoke an operation.
     * <p>
     * If the request does not include a <em>tenant_id</em> application property
     * then the request is authorized by default. This behavior allows clients to
     * invoke operations that do not require a tenant ID as a parameter. In such
     * cases the {@link #filterResponse(HonoUser, EventBusMessage)} method is used
     * to verify that the response only contains data that the client is authorized
     * to retrieve.
     * <p>
     * If the request does contain a tenant ID parameter in its application properties
     * then this tenant ID is used for the authorization check together with the
     * endpoint and operation name.
     *
     * @param clientPrincipal The client.
     * @param resource The resource the operation belongs to.
     * @param request The message for which the authorization shall be checked.
     * @return The outcome of the check.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    @Override
    protected Future<Boolean> isAuthorized(final HonoUser clientPrincipal, final ResourceIdentifier resource, final Message request) {

        Objects.requireNonNull(request);

        final String tenantId = MessageHelper.getTenantId(request);
        if (tenantId == null) {
            // delegate authorization check to filterResource operation
            return Future.succeededFuture(Boolean.TRUE);
        } else {
            final ResourceIdentifier specificTenantAddress =
                    ResourceIdentifier.fromPath(new String[] { resource.getEndpoint(), tenantId });

            return getAuthorizationService().isAuthorized(clientPrincipal, specificTenantAddress, request.getSubject());
        }
    }

    /**
     * Verifies that a response only contains tenant information that the
     * client is authorized to retrieve.
     * <p>
     * If the response does not contain a tenant ID nor a payload, then the
     * returned future will succeed with the response <em>as-is</em>.
     * Otherwise the tenant ID is used together with the endpoint and operation
     * name to check the client's authority to retrieve the data. If the client
     * is authorized, the returned future will succeed with the response as-is,
     * otherwise the future will fail with a {@link ClientErrorException} containing a
     * <em>403 Forbidden</em> status.
     */
    @Override
    protected Future<EventBusMessage> filterResponse(
            final HonoUser clientPrincipal,
            final EventBusMessage response) {

        Objects.requireNonNull(clientPrincipal);
        Objects.requireNonNull(response);

        if (response.getTenant() == null || response.getJsonPayload() == null) {
            return Future.succeededFuture(response);
        } else {
            // verify that payload contains tenant that the client is authorized for
            final ResourceIdentifier resourceId = ResourceIdentifier.from(TenantConstants.TENANT_ENDPOINT, response.getTenant(), null);
            return getAuthorizationService().isAuthorized(clientPrincipal, resourceId, response.getOperation())
                    .map(isAuthorized -> {
                        if (isAuthorized) {
                            return response;
                        } else {
                            throw new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN);
                        }
                    });
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Future<EventBusMessage> createEventBusRequestMessage(
            final Message requestMessage,
            final ResourceIdentifier targetAddress,
            final HonoUser clientPrincipal) {

        try {
            return Future.succeededFuture(EventBusMessage.forOperation(requestMessage)
                    .setCorrelationId(requestMessage)
                    .setTenant(requestMessage)
                    .setJsonPayload(requestMessage));
        } catch (DecodeException e) {
            logger.debug("failed to create EventBusMessage from AMQP request message", e);
            return Future.failedFuture(
                    new ClientErrorException(
                            HttpURLConnection.HTTP_BAD_REQUEST,
                            "request message body contains malformed JSON"));
        }
    }

    @Override
    protected boolean passesFormalVerification(final ResourceIdentifier linkTarget, final Message msg) {
        return TenantMessageFilter.verify(linkTarget, msg);
    }

    @Override
    protected final Message getAmqpReply(final EventBusMessage message) {
        return TenantConstants.getAmqpReply(TenantConstants.TENANT_ENDPOINT, message);
    }

    /**
     * Checks if a resource identifier constitutes a valid reply-to address
     * for the Tenant service.
     * 
     * @param replyToAddress The address to check.
     * @return {@code true} if the address contains two segments.
     */
    @Override
    protected boolean isValidReplyToAddress(final ResourceIdentifier replyToAddress) {

        if (replyToAddress == null) {
            return false;
        } else {
            return replyToAddress.getResourcePath().length >= 2;
        }
    }
}
