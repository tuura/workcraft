package org.workcraft.plugins.xbm;

import org.workcraft.Framework;
import org.workcraft.plugins.Plugin;
import org.workcraft.plugins.PluginInfo;
import org.workcraft.plugins.PluginManager;
import org.workcraft.plugins.xbm.commands.DistinguishabilityConstraintVerification;
import org.workcraft.plugins.xbm.commands.MaximalSetPropertyVerification;
import org.workcraft.plugins.xbm.commands.NonEmptyInputBurstsVerification;
import org.workcraft.plugins.xbm.commands.UniqueStateEncodingVerification;
import org.workcraft.plugins.xbm.serialisation.*;
import org.workcraft.utils.ScriptableCommandUtils;

//TODO Serialisation and deserialisation of the model needs work:
//TODO EventSerialisation can read the burstevents, but sets everything as empty; also true when creating a separate serialiser/deserialiser

public class XbmPlugin implements Plugin {

    @Override
    public String getDescription() {
        return "Xbm Model";
    }

    @Override
    public void init() {
        final Framework framework = Framework.getInstance();
        final PluginManager pm = framework.getPluginManager();
        pm.registerModelDescriptor(XbmDescriptor.class);

        pm.registerXmlSerialiser(EncodingSerialiser.class);
        pm.registerXmlDeserialiser(EncodingDeserialiser.class);

        pm.registerXmlSerialiser(BurstEventSerialiser.class);
        pm.registerXmlDeserialiser(BurstEventDeserialiser.class);

        ScriptableCommandUtils.register(UniqueStateEncodingVerification.class, "checkXbmStateEncoding",
                "check the XBM model for any non-unique state encodings");

        ScriptableCommandUtils.register(MaximalSetPropertyVerification.class, "checkXbmMaximalSetProperty",
                "check the XBM model for any input changes found in more than one burst");

        ScriptableCommandUtils.register(NonEmptyInputBurstsVerification.class, "checkXbmNonEmptyInputBursts",
                "check the XBM model for any empty input bursts");

        ScriptableCommandUtils.register(DistinguishabilityConstraintVerification.class, "checkXbmDistinguishabilityConstraint",
                "check the XBM model for any conflicts of conditionals and instances of the same bursts");
    }
}
