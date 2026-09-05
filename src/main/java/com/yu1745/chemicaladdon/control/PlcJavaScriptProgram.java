package com.yu1745.chemicaladdon.control;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/** Sandboxed Rhino program used by the PLC's in-game JavaScript mode. */
public final class PlcJavaScriptProgram {
	public static final int MAX_SOURCE_BYTES = 8192;
	private static final int INSTRUCTION_BUDGET = 20_000;
	private final BudgetFactory factory = new BudgetFactory();
	private final Script script;
	private Scriptable scope;
	private Function scan;

	private PlcJavaScriptProgram(Script script) { this.script = script; }

	public static PlcJavaScriptProgram compile(String source) {
		if (source.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_SOURCE_BYTES)
			throw new IllegalArgumentException("JavaScript source exceeds 8192 bytes");
		BudgetFactory factory = new BudgetFactory();
		Script compiled = factory.call(cx -> cx.compileString(source, "plc", 1, null));
		return new PlcJavaScriptProgram(compiled);
	}

	public void scan(PlcMachine machine) {
		factory.budget.set(INSTRUCTION_BUDGET);
		try {
			factory.call(cx -> {
				if (scope == null) initialise(cx);
				Object api = api(cx, scope, machine);
				scan.call(cx, scope, scope, new Object[] { api });
				return null;
			});
		} catch (WatchdogError e) {
			machine.fault(PlcFault.WATCHDOG);
		} catch (RuntimeException e) {
			machine.fault(PlcFault.RUNTIME_ERROR);
		} finally {
			factory.budget.remove();
		}
	}

	private void initialise(Context cx) {
		scope = cx.initSafeStandardObjects();
		// Defence in depth: safe globals plus an opaque ClassShutter mean scripts
		// cannot reach LiveConnect even if a Rhino global changes in a future release.
		for (String key : new String[] { "Packages", "java", "javax", "org", "com", "edu", "net", "JavaAdapter", "getClass" })
			ScriptableObject.deleteProperty(scope, key);
		script.exec(cx, scope);
		Object entry = ScriptableObject.getProperty(scope, "scan");
		if (!(entry instanceof Function function)) throw new IllegalArgumentException("define function scan(plc)");
		scan = function;
	}

	private static Scriptable api(Context cx, Scriptable scope, PlcMachine m) {
		NativeObject api = new NativeObject();
		api.setParentScope(scope);
		put(api, "input", 1, a -> m.input(intArg(a, 0)));
		put(api, "output", 2, a -> { m.output(intArg(a, 0), intArg(a, 1)); return Context.getUndefinedValue(); });
		put(api, "register", 1, a -> m.register(intArg(a, 0)));
		put(api, "setRegister", 2, a -> { m.register(intArg(a, 0), intArg(a, 1)); return Context.getUndefinedValue(); });
		put(api, "timer", 3, a -> m.timer(intArg(a, 0), Context.toBoolean(a[1]), intArg(a, 2)));
		put(api, "rising", 1, a -> m.rising(intArg(a, 0)));
		put(api, "fault", 0, a -> { m.fault(PlcFault.RUNTIME_ERROR); return Context.getUndefinedValue(); });
		api.sealObject();
		return api;
	}

	private interface Call { Object invoke(Object[] args); }
	private static void put(NativeObject target, String name, int arity, Call call) {
		BaseFunction f = new BaseFunction() {
			@Override public int getArity() { return arity; }
			@Override public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
				if (args.length < arity) throw Context.reportRuntimeError(name + " expects " + arity + " arguments");
				return call.invoke(args);
			}
		};
		ScriptableObject.putProperty(target, name, f);
	}
	private static int intArg(Object[] args, int index) { return (int) Context.toNumber(args[index]); }

	private static final class BudgetFactory extends ContextFactory {
		final ThreadLocal<Integer> budget = new ThreadLocal<>();
		@Override protected Context makeContext() {
			Context cx = super.makeContext();
			cx.setLanguageVersion(Context.VERSION_ES6);
			cx.setOptimizationLevel(-1); // instruction observer is reliable in interpreted mode
			cx.setInstructionObserverThreshold(500);
			cx.setClassShutter(new OpaqueShutter());
			return cx;
		}
		@Override protected void observeInstructionCount(Context cx, int count) {
			Integer left = budget.get();
			if (left != null && left - count <= 0) throw new WatchdogError();
			if (left != null) budget.set(left - count);
		}
	}
	private static final class OpaqueShutter implements ClassShutter {
		@Override public boolean visibleToScripts(String fullClassName) { return false; }
	}
	private static final class WatchdogError extends Error { private static final long serialVersionUID = 1L; }
}
