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

import java.awt.*;
import java.util.Arrays;
import java.util.stream.Stream;

import javax.swing.*;

import org.apache.jmeter.gui.util.JLabeledRadioI18N;
import org.apache.jmeter.gui.util.VerticalPanel;
import org.apache.jmeter.samplers.gui.AbstractSamplerGui;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jorphan.gui.JLabeledChoice;
import org.apache.jorphan.gui.JLabeledTextField;
import org.eclipse.hono.jmeter.HonoSampler;

/**
 * Base Hono sampler UI
 */
abstract public class HonoSamplerUI extends AbstractSamplerGui {

    private final JLabeledTextField host           = new JLabeledTextField("Host");
    private final JLabeledTextField port           = new JLabeledTextField("Port");
    private final JLabeledTextField user           = new JLabeledTextField("User");
    private final JLabeledTextField pwd            = new JLabeledTextField("Password");
    private final JLabeledTextField container      = new JLabeledTextField("Name");
    private final JLabeledTextField tenant         = new JLabeledTextField("Tenant");
    private final JLabeledTextField trustStorePath = new JLabeledTextField("Truststore path");
    private final JLabeledChoice    endpoint       = new JLabeledChoice("Endpoint",
            Stream.of(HonoSampler.Endpoint.values()).map(HonoSampler.Endpoint::name).toArray(String[]::new));

    private JPanel optsPanel;

    public HonoSamplerUI() {
        setLayout(new BorderLayout());
        setBorder(makeBorder());

        add(makeTitlePanel(), BorderLayout.NORTH);
        JPanel mainPanel = new VerticalPanel();
        add(mainPanel, BorderLayout.CENTER);
        mainPanel.add(createHonoOptions());
    }

    private JPanel createHonoOptions() {
        JPanel optsPanelCon = new VerticalPanel();
        optsPanelCon.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Hono options"));
        optsPanel = new VerticalPanel();
        optsPanel.add(host);
        optsPanel.add(port);
        optsPanel.add(user);
        optsPanel.add(pwd);
        optsPanel.add(container);
        optsPanel.add(endpoint);
        optsPanel.add(tenant);
        optsPanel.add(trustStorePath);
        optsPanelCon.add(optsPanel);
        return optsPanelCon;
    }

    void addOption(final JComponent component) {
        optsPanel.add(component);
    }

    @Override
    public String getLabelResource() {
        return "";
    }

    @Override
    public void modifyTestElement(final TestElement testElement) {
        this.configureTestElement(testElement);
        HonoSampler sampler = (HonoSampler) testElement;
        sampler.setHost(host.getText());
        int portI = 0;
        try {
            portI = Integer.parseInt(port.getText());
        } catch (NumberFormatException e) {
            port.setText("0");
        }
        sampler.setPort(portI);
        sampler.setUser(user.getText());
        sampler.setPwd(pwd.getText());
        sampler.setEndpoint(HonoSampler.Endpoint.valueOf(endpoint.getText()));
        sampler.setTenant(tenant.getText());
        sampler.setContainer(container.getText());
        sampler.setTrustStorePath(trustStorePath.getText());
    }

    @Override
    public void configure(final TestElement element) {
        super.configure(element);
        HonoSampler sampler = (HonoSampler) element;
        host.setText(sampler.getHost());
        port.setText(sampler.getPort() + "");
        user.setText(sampler.getUser());
        pwd.setText(sampler.getPwd());
        endpoint.setText(sampler.getEndpoint());
        tenant.setText(sampler.getTenant());
        container.setText(sampler.getContainer());
        trustStorePath.setText(sampler.getTrustStorePath());
    }

    @Override
    public void clearGui() {
        super.clearGui();
        host.setText("");
        port.setText("");
        user.setText("");
        pwd.setText("");
        endpoint.setSelectedIndex(0);
        tenant.setText("");
        container.setText("");
        trustStorePath.setText("");
    }

}
