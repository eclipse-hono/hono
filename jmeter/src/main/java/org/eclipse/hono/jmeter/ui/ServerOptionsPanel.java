/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.jmeter.ui;

import javax.swing.BorderFactory;

import org.apache.jmeter.gui.util.VerticalPanel;
import org.apache.jorphan.gui.JLabeledTextField;

/**
 * A panel for configuring options required for connecting to a service.
 */
public class ServerOptionsPanel extends VerticalPanel {

    private static final long serialVersionUID = -6612048623116543990L;

    private final JLabeledTextField host           = new JLabeledTextField("Host");
    private final JLabeledTextField port           = new JLabeledTextField("Port");
    private final JLabeledTextField user           = new JLabeledTextField("User");
    private final JLabeledTextField pwd            = new JLabeledTextField("Password");
    private final JLabeledTextField trustStorePath = new JLabeledTextField("Truststore path");

    /**
     * Creates a new panel for configuring options required to
     * connect to an AMQP server.
     * 
     * @param title The title of the panel.
     */
    public ServerOptionsPanel(final String title) {

        setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), title));
        add(host);
        add(port);
        add(user);
        add(pwd);
        add(trustStorePath);
    }

    public String getHost() {
        return host.getText();
    }

    public void setHost(final String hostname) {
        this.host.setText(hostname);
    }

    public String getPort() {
        return port.getText();
    }

    public void setPort(final String port) {
        this.port.setText(port);
    }

    public String getUser() {
        return user.getText();
    }

    public void setUser(final String username) {
        this.user.setText(username);
    }

    public String getPwd() {
        return pwd.getText();
    }

    public void setPwd(final String pwd) {
        this.pwd.setText(pwd);
    }

    public String getTrustStorePath() {
        return trustStorePath.getText();
    }

    public void setTrustStorePath(final String path) {
        this.trustStorePath.setText(path);
    }

    public void clearGui() {
        host.setText("");
        port.setText("");
        user.setText("");
        pwd.setText("");
        trustStorePath.setText("");
    }
}
