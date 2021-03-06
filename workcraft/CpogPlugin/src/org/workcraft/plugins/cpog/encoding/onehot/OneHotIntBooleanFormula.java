package org.workcraft.plugins.cpog.encoding.onehot;

import java.util.List;

import org.workcraft.formula.BooleanVariable;

public class OneHotIntBooleanFormula {

    private final List<BooleanVariable> vars;

    public OneHotIntBooleanFormula(List<BooleanVariable> vars) {
        this.vars = vars;
    }

    public BooleanVariable get(int index) {
        return vars.get(index);
    }

    public int getRange() {
        return vars.size();
    }

}
