package org.eclipse.hono.adapter.rest;

public interface PayloadEncoder {

    byte[] encode(Object payload);

    Object decode(byte[] payload);

}
