package org.workcraft.plugins.circuit.commands;

import org.workcraft.dom.visual.VisualNode;
import org.workcraft.plugins.circuit.VisualCircuit;
import org.workcraft.plugins.circuit.VisualFunctionComponent;

public abstract class AbstractGateTransformationCommand extends AbstractComponentTransformationCommand {

    @Override
    public boolean isApplicableTo(VisualNode node) {
        return (node instanceof VisualFunctionComponent) && ((VisualFunctionComponent) node).isGate();
    }

    @Override
    public void transformComponent(VisualCircuit circuit, VisualFunctionComponent component) {
        if (component.isGate()) {
            transformGate(circuit, component);
        }
    }

    public abstract void transformGate(VisualCircuit circuit, VisualFunctionComponent gate);

}
