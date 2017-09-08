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

import java.awt.BorderLayout;
import java.util.stream.Stream;

import javax.swing.JComponent;
import javax.swing.JPanel;

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

    private final JLabeledTextField tenant         = new JLabeledTextField("Tenant");
    private final JLabeledTextField container      = new JLabeledTextField("Name");
    private final JLabeledChoice    endpoint       = new JLabeledChoice("Endpoint",
            Stream.of(HonoSampler.Endpoint.values()).map(HonoSampler.Endpoint::name).toArray(String[]::new));
    private final HonoServerOptionsPanel honoServerOptions;
    private final JPanel optsPanel = new VerticalPanel();

    public HonoSamplerUI(final String serverTitle) {
        honoServerOptions = new HonoServerOptionsPanel(serverTitle);
        setLayout(new BorderLayout());
        setBorder(makeBorder());
        add(makeTitlePanel(), BorderLayout.NORTH);
        add(optsPanel, BorderLayout.CENTER);
    }

    protected void addDefaultOptions() {
        optsPanel.add(honoServerOptions);
        optsPanel.add(endpoint);
        optsPanel.add(tenant);
        optsPanel.add(container);
    }

    protected void addOption(final JComponent component) {
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
        sampler.setHost(honoServerOptions.getHost().getText());
        sampler.setPort(honoServerOptions.getPort().getText());
        sampler.setUser(honoServerOptions.getUser().getText());
        sampler.setPwd(honoServerOptions.getPwd().getText());
        sampler.setEndpoint(HonoSampler.Endpoint.valueOf(endpoint.getText()));
        sampler.setTenant(tenant.getText());
        sampler.setContainer(container.getText());
        sampler.setTrustStorePath(honoServerOptions.getTrustStorePath().getText());
    }

    @Override
    public void configure(final TestElement element) {
        super.configure(element);
        HonoSampler sampler = (HonoSampler) element;
        honoServerOptions.getHost().setText(sampler.getHost());
        honoServerOptions.getPort().setText(sampler.getPort());
        honoServerOptions.getUser().setText(sampler.getUser());
        honoServerOptions.getPwd().setText(sampler.getPwd());
        endpoint.setText(sampler.getEndpoint());
        tenant.setText(sampler.getTenant());
        container.setText(sampler.getContainer());
        honoServerOptions.getTrustStorePath().setText(sampler.getTrustStorePath());
    }

    @Override
    public void clearGui() {
        super.clearGui();
        honoServerOptions.clearGui();
        endpoint.setSelectedIndex(0);
        tenant.setText("");
        container.setText("");
    }

}
