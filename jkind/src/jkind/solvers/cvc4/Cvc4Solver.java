package jkind.solvers.cvc4;

import java.util.List;

import jkind.sexp.Symbol;
import jkind.solvers.smtlib2.SmtLib2Solver;

public class Cvc4Solver extends SmtLib2Solver {
	public Cvc4Solver(String scratchBase) {
		super(scratchBase);
	}

	@Override
	protected String getSolverName() {
		return "CVC4";
	}

	@Override
	protected String[] getSolverOptions() {
		return new String[] { "--lang", "smt" };
	}

	@Override
	public void initialize() {
		send("(set-option :produce-models true)");
		send("(set-option :incremental true)");
		send("(set-option :rewrite-divk true)");
		send("(set-logic AUFLIRA)");
	}

	@Override
	protected List<Symbol> getUnsatCore(List<Symbol> activationLiterals) {
		// CVC4 does not yet support unsat-cores
		return activationLiterals;
	}
}
