package org.eclipse.hono.example.protocoladapter.interfaces;

public interface ICommandHandler {
    /**
     * Pass through function to handle commands and return response body
     *
     * @param commandPayload body of command
     * @param subject        subject of command
     * @param contentType    HTML content type
     * @param isOneWay       signals if response string necessary
     * @return
     */
    String handleCommand(String commandPayload, String subject, String contentType, boolean isOneWay);
}