/**
 * Copyright (c) 2016,2017 Bosch Software Innovations GmbH.
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
 * Server connection options
 */
public class HonoServerOptionsPanel extends VerticalPanel {

    private final JLabeledTextField host           = new JLabeledTextField("Host");
    private final JLabeledTextField port           = new JLabeledTextField("Port");
    private final JLabeledTextField user           = new JLabeledTextField("User");
    private final JLabeledTextField pwd            = new JLabeledTextField("Password");
    private final JLabeledTextField trustStorePath = new JLabeledTextField("Truststore path");

    public HonoServerOptionsPanel(final String title) {
        setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), title));
        add(host);
        add(port);
        add(user);
        add(pwd);
        add(trustStorePath);
    }

    public JLabeledTextField getHost() {
        return host;
    }

    public JLabeledTextField getPort() {
        return port;
    }

    public JLabeledTextField getUser() {
        return user;
    }

    public JLabeledTextField getPwd() {
        return pwd;
    }

    public JLabeledTextField getTrustStorePath() {
        return trustStorePath;
    }

    public void clearGui() {
        host.setText("");
        port.setText("");
        user.setText("");
        pwd.setText("");
        trustStorePath.setText("");
    }
}
