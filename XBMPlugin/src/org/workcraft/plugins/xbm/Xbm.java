package org.workcraft.plugins.xbm;

import org.workcraft.dom.Container;
import org.workcraft.dom.Node;
import org.workcraft.dom.math.MathNode;
import org.workcraft.exceptions.ArgumentException;
import org.workcraft.observation.PropertyChangedEvent;
import org.workcraft.plugins.fsm.Fsm;
import org.workcraft.plugins.fsm.State;
import org.workcraft.plugins.fsm.Symbol;
import org.workcraft.plugins.xbm.observers.ConditionalSupervisor;
import org.workcraft.plugins.xbm.observers.SignalSupervisor;
import org.workcraft.plugins.xbm.observers.SignalTypeConsistencySupervisor;
import org.workcraft.serialisation.References;
import org.workcraft.utils.Hierarchy;

import java.util.Collection;
import java.util.LinkedHashSet;

public class Xbm extends Fsm {

    public Xbm() {
        this(null, null);
    }

    public Xbm(Container root, References refs) {
        super(root, refs);
        new SignalTypeConsistencySupervisor(this).attach(getRoot());
        new SignalSupervisor(this).attach(getRoot());
        new ConditionalSupervisor(this).attach(getRoot());
    }

    @Override
    public boolean isDeterministicSymbol(Symbol symbol) {
        boolean result = true;
        if (symbol instanceof Burst) {
            Burst bSymbol = (Burst) symbol;
            for (Signal s: bSymbol.getSignals()) {
                result = result && (s.getType() != Signal.Type.DUMMY);
            }
        }
        else result = super.isDeterministicSymbol(symbol);
        return result;
    }

    @Override
    public void remove(MathNode node) {
        if (node != null) {
            getRoot().remove(node);
        }
        else {
            throw new ArgumentException("Cannot delete a null node.");
        }
    }

    @Override
    public XbmState createState(String name) {
        XbmState state = createNode(name, getRoot(), XbmState.class);
        for (Signal signal: Hierarchy.getDescendantsOfType(getRoot(), Signal.class)) {
            state.addOrChangeSignalValue(signal, XbmState.DEFAULT_SIGNAL_STATE);
        }
        return state;
    }

    @Override
    public XbmState getOrCreateState(String name) {
        XbmState state = null;
        Node node = getNodeByReference(name);
        if (node == null) state = createState(name);
        else if (node instanceof XbmState) state = (XbmState) node;
        else if (node instanceof State) return (XbmState) super.getOrCreateState(name);
        else throw new ArgumentException("Node \'" + name + "\' already exists and it is not a state.");
        return state;
    }

    public Signal createSignal(String name) {
        Signal signal = createNode(name, getRoot(), Signal.class);
        signal.setName(getNodeReference(signal));
        for (XbmState state: Hierarchy.getDescendantsOfType(getRoot(), XbmState.class)) {
            state.addOrChangeSignalValue(signal, XbmState.DEFAULT_SIGNAL_STATE);
        }
        return signal;
    }

    public Signal getOrCreateSignal(String name, Signal.Type type) {
        Signal signal = null;
        Node node = getNodeByReference(name);
        if (node == null) {
            signal = createSignal(name);
        }
        else if (node instanceof Signal) {
            signal = (Signal) node;
            if (signal.getType() != type) throw new ArgumentException("Signal " + name + " already exists and its type \'" + signal.getType() + "\' is different from the \'" + type + "\' type.");
            else signal = (Signal) node;
        }
        else {
            throw new ArgumentException("Node " + name + " already exists and it is not a signal.");
        }
        return signal;
    }

    public BurstEvent createBurstEvent(XbmState from, XbmState to, Burst burst) {
        Container container = Hierarchy.getNearestContainer(from, to);
        BurstEvent event = new BurstEvent(from, to, burst);
        container.add(event);
        return event;
    }

    public void removeSignal(Signal s) {
        if (getSignals().contains(s)) {
            this.remove(s);
            for (XbmState state: getXbmStates()) {
                state.sendNotification(new PropertyChangedEvent(state, XbmState.PROPERTY_ENCODING));
            }
        }
    }

    public final Collection<XbmState> getXbmStates() {
        return Hierarchy.getDescendantsOfType(getRoot(), XbmState.class);
    }

    public final Collection<Signal> getSignals() {
        return Hierarchy.getDescendantsOfType(getRoot(), Signal.class);
    }

    public final Collection<Signal> getSignals(final Signal.Type type) {
        return Hierarchy.getDescendantsOfType(getRoot(), Signal.class, signal -> (signal != null) && (signal.getType() == type));
    }

    public final Collection<BurstEvent> getBurstEvents() {
        return Hierarchy.getDescendantsOfType(getRoot(), BurstEvent.class);
    }

    public final Collection<Burst> getBursts() {
        return Hierarchy.getDescendantsOfType(getRoot(), Burst.class);
    }

    public final Collection<BurstEvent> getPresetBursts(XbmState target) {
        Collection<BurstEvent> result = new LinkedHashSet<>();
        for (BurstEvent event: getBurstEvents()) {
            if (event.getBurst().getTo() == target) {
                result.add(event);
            }
        }
        return result;
    }

    public final Collection<BurstEvent> getPostsetBursts(XbmState target) {
        Collection<BurstEvent> result = new LinkedHashSet<>();
        for (BurstEvent event: getBurstEvents()) {
            if (event.getBurst().getFrom() == target) {
                result.add(event);
            }
        }
        return result;
    }

    public final Collection<XbmState> getPresetStates(BurstEvent target) {
        Collection<XbmState> result = new LinkedHashSet<>();
        for (XbmState state: getXbmStates()) {
            if (target.getBurst().getFrom() == state) {
                result.add(state);
            }
        }
        return result;
    }

    public final Collection<XbmState> getPostsetStates(BurstEvent target) {
        Collection<XbmState> result = new LinkedHashSet<>();
        for (XbmState state: getXbmStates()) {
            if (target.getBurst().getTo() == state) {
                result.add(state);
            }
        }
        return result;
    }
}
