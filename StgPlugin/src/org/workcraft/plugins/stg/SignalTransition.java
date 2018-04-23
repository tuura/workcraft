package org.workcraft.plugins.stg;

import org.workcraft.annotations.DisplayName;
import org.workcraft.annotations.VisualClass;
import org.workcraft.observation.PropertyChangedEvent;
import org.workcraft.serialisation.xml.NoAutoSerialisation;

@DisplayName("Signal transition")
@VisualClass(org.workcraft.plugins.stg.VisualSignalTransition.class)
public class SignalTransition extends NamedTransition {
    public static final String PROPERTY_SIGNAL_TYPE = "Signal type";
    public static final String PROPERTY_SIGNAL_NAME = "Signal name";
    public static final String PROPERTY_DIRECTION = "Direction";

    public enum Type {
        INPUT("input"),
        OUTPUT("output"),
        INTERNAL("internal");

        private final String name;

        Type(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        public Type mirror() {
            switch (this) {
            case INPUT: return OUTPUT;
            case OUTPUT: return INPUT;
            default: return this;
            }
        }

        public Type toggle() {
            switch (this) {
            case INPUT: return INTERNAL;
            case OUTPUT: return INPUT;
            case INTERNAL: return OUTPUT;
            default: return this;
            }
        }
    }

    public enum Direction {
        PLUS("+"),
        MINUS("-"),
        TOGGLE("~");

        private final String name;

        Direction(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        public Direction mirror() {
            switch (this) {
            case PLUS: return MINUS;
            case MINUS: return PLUS;
            default: return this;
            }
        }

        public Direction toggle() {
            switch (this) {
            case PLUS: return TOGGLE;
            case MINUS: return PLUS;
            case TOGGLE: return MINUS;
            default: return this;
            }
        }
    }

    private Type type = Type.INTERNAL;
    private Direction direction = Direction.TOGGLE;
    private String signalName = "";

    public Type getSignalType() {
        return type;
    }

    public void setSignalType(Type value) {
        if (type != value) {
            type = value;
            sendNotification(new PropertyChangedEvent(this, PROPERTY_SIGNAL_TYPE));
        }
    }

    public Direction getDirection() {
        return direction;
    }

    // FIXME: As direction is part of the node reference use Stg.setDirection(Node) instead!
    // This method is only to be used from StgNameManager.
    public void setDirection(Direction value) {
        if (direction != value) {
            direction = value;
            sendNotification(new PropertyChangedEvent(this, PROPERTY_DIRECTION));
        }
    }

    @NoAutoSerialisation
    public String getSignalName() {
        return signalName;
    }

    @NoAutoSerialisation
    // FIXME: As signal name is part of the node reference use Stg.setName(Node, String) instead!
    // This method is only to be used from StgNameManager.
    public void setSignalName(String value) {
        if (value == null) value = "";
        if (!value.equals(signalName)) {
            signalName = value;
            sendNotification(new PropertyChangedEvent(this, PROPERTY_SIGNAL_NAME));
        }
    }

    @NoAutoSerialisation
    @Override
    public String getName() {
        return signalName + direction;
    }

}
