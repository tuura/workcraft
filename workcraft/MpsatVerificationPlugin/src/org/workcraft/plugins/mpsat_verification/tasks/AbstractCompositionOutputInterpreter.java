package org.workcraft.plugins.mpsat_verification.tasks;

import org.workcraft.plugins.mpsat_verification.utils.MpsatUtils;
import org.workcraft.plugins.pcomp.tasks.PcompOutput;
import org.workcraft.plugins.stg.StgModel;
import org.workcraft.plugins.stg.utils.StgUtils;
import org.workcraft.tasks.ExportOutput;
import org.workcraft.traces.Solution;
import org.workcraft.traces.Trace;
import org.workcraft.utils.LogUtils;
import org.workcraft.utils.TextUtils;
import org.workcraft.workspace.WorkspaceEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class AbstractCompositionOutputInterpreter extends ReachabilityOutputInterpreter {

    private static final Pattern DEAD_SIGNAL_PATTERN = Pattern.compile(
            "Warning: signal (\\w+) is dead");

    AbstractCompositionOutputInterpreter(WorkspaceEntry we, ExportOutput exportOutput,
            PcompOutput pcompOutput, MpsatOutput mpsatOutput, boolean interactive) {

        super(we, exportOutput, pcompOutput, mpsatOutput, interactive);
    }

    @Override
    public String getMessage(boolean propertyHolds) {
        String result = super.getMessage(propertyHolds);
        String mpsatStderr = getOutput().getStderrString();
        Matcher matcher = DEAD_SIGNAL_PATTERN.matcher(mpsatStderr);
        List<String> signals = new ArrayList<>();
        while (matcher.find()) {
            signals.add(matcher.group(1));
        }
        if (!signals.isEmpty()) {
            if (propertyHolds) {
                result += TextUtils.wrapMessageWithItems("\nYet composition has dead signal", signals);
                result += "\n\nWarning: dead signals may indicate design issues!";
            } else {
                result += " Also composition has dead signals.";
            }
        }
        return result;
    }

    @Override
    public void reportSolutions(String message, List<Solution> solutions) {
        writeBrief(solutions);
        super.reportSolutions(message, solutions);
    }

    public void writeBrief(List<Solution> solutions) {
        boolean needsMultiLineMessage = solutions.size() > 1;
        if (needsMultiLineMessage) {
            LogUtils.logMessage("Violation traces of the composition:");
        }
        StgModel compositionStg = getCompositionStg();
        for (Solution solution : solutions) {
            Trace compositionTrace = MpsatUtils.fixTraceToggleEvents(compositionStg, solution.getMainTrace());
            if (needsMultiLineMessage) {
                LogUtils.logMessage("  " + compositionTrace);
            } else {
                LogUtils.logMessage("Violation trace of the composition: " + compositionTrace);
            }
        }
    }

    public StgModel getCompositionStg() {
        return StgUtils.importStg(getExportOutput().getFile());
    }

}
