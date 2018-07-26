/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.jmeter.ui;

import java.awt.BorderLayout;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.apache.jmeter.gui.util.VerticalPanel;
import org.apache.jmeter.samplers.gui.AbstractSamplerGui;
import org.apache.jorphan.gui.JLabeledChoice;

/**
 * Base class for implementing GUI components for Hono samplers.
 */
public abstract class HonoSamplerUI extends AbstractSamplerGui {

    private static final long serialVersionUID = 5233638873794953551L;

    private final JPanel optsPanel;

    /**
     * Creates a new GUI component.
     */
    public HonoSamplerUI() {

        setLayout(new BorderLayout());
        setBorder(makeBorder());
        add(makeTitlePanel(), BorderLayout.NORTH);
        optsPanel = new VerticalPanel();
        add(optsPanel, BorderLayout.CENTER);
    }

    /**
     * Adds a component for configuring an option to the GUI.
     * <p>
     * The component will be added to the bottom of the existing options panel.
     * 
     * @param component The component to add.
     */
    protected final void addOption(final JComponent component) {
        optsPanel.add(component);
    }

    @Override
    public String getLabelResource() {
        return null;
    }

    protected JPanel getWrapperPanelToFixAlignment(final JLabeledChoice labeledChoice) {
        // wrap the JLabeledChoice in extra panel to align it on the right
        final JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.add(labeledChoice, BorderLayout.WEST);
        // fix superfluous outer indents of JLabeledChoice
        labeledChoice.setLayout(new BoxLayout(labeledChoice, BoxLayout.X_AXIS));
        labeledChoice.getComponentList().get(0).setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
        return wrapperPanel;
    }
}
