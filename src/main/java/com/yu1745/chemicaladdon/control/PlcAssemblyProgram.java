package com.yu1745.chemicaladdon.control;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Compact textual instruction-table program. One line is one PLC instruction. */
public final class PlcAssemblyProgram {
	public static final int MAX_INSTRUCTIONS = 64;
	public static final int WATCHDOG = 256;
	private final List<Instruction> code;

	private PlcAssemblyProgram(List<Instruction> code) { this.code = List.copyOf(code); }

	public static PlcAssemblyProgram compile(String source) {
		List<String[]> raw = new ArrayList<>();
		Map<String, Integer> labels = new HashMap<>();
		for (String sourceLine : source.replace('\r', '\n').split("\n")) {
			String line = sourceLine.replaceFirst("[;#].*$", "").trim();
			if (line.isEmpty()) continue;
			if (line.endsWith(":")) { labels.put(line.substring(0, line.length() - 1).trim().toLowerCase(Locale.ROOT), raw.size()); continue; }
			raw.add(line.split("[\\s,]+"));
			if (raw.size() > MAX_INSTRUCTIONS) throw new IllegalArgumentException("more than 64 instructions");
		}
		List<Instruction> code = new ArrayList<>();
		for (String[] p : raw) code.add(parse(p, labels));
		return new PlcAssemblyProgram(code);
	}

	public void scan(PlcMachine m) {
		int pc = 0, steps = 0;
		while (pc >= 0 && pc < code.size()) {
			if (++steps > WATCHDOG) { m.fault(PlcFault.WATCHDOG); return; }
			pc = code.get(pc).run(m, pc);
			if (m.fault() != PlcFault.NONE) return;
		}
	}

	private static Instruction parse(String[] p, Map<String, Integer> labels) {
		String op = p[0].toUpperCase(Locale.ROOT);
		return switch (op) {
			case "MOV" -> binary(p, (m, a, b) -> write(m, a, read(m, b)));
			case "ADD" -> ternary(p, (m, d, a, b) -> write(m, d, read(m, a) + read(m, b)));
			case "SUB" -> ternary(p, (m, d, a, b) -> write(m, d, read(m, a) - read(m, b)));
			case "MUL" -> ternary(p, (m, d, a, b) -> write(m, d, read(m, a) * read(m, b)));
			case "DIV" -> ternary(p, (m, d, a, b) -> write(m, d, read(m, b) == 0 ? 0 : read(m, a) / read(m, b)));
			case "MIN" -> ternary(p, (m, d, a, b) -> write(m, d, Math.min(read(m, a), read(m, b))));
			case "MAX" -> ternary(p, (m, d, a, b) -> write(m, d, Math.max(read(m, a), read(m, b))));
			case "AND" -> ternary(p, (m, d, a, b) -> write(m, d, bool(read(m, a) > 0 && read(m, b) > 0)));
			case "OR" -> ternary(p, (m, d, a, b) -> write(m, d, bool(read(m, a) > 0 || read(m, b) > 0)));
			case "NOT" -> binary(p, (m, d, a) -> write(m, d, bool(read(m, a) <= 0)));
			case "GT" -> ternary(p, (m, d, a, b) -> write(m, d, bool(read(m, a) > read(m, b))));
			case "GE" -> ternary(p, (m, d, a, b) -> write(m, d, bool(read(m, a) >= read(m, b))));
			case "LT" -> ternary(p, (m, d, a, b) -> write(m, d, bool(read(m, a) < read(m, b))));
			case "LE" -> ternary(p, (m, d, a, b) -> write(m, d, bool(read(m, a) <= read(m, b))));
			case "EQ" -> ternary(p, (m, d, a, b) -> write(m, d, bool(read(m, a) == read(m, b))));
			case "CLAMP" -> quad(p, (m, d, v, lo, hi) -> write(m, d, Math.max(read(m, lo), Math.min(read(m, hi), read(m, v)))));
			case "MAP" -> sext(p, (m, d, v, inLo, inHi, outLo, outHi) -> {
				int span = read(m, inHi) - read(m, inLo);
				write(m, d, span == 0 ? read(m, outLo) : read(m, outLo) + (read(m, v) - read(m, inLo)) * (read(m, outHi) - read(m, outLo)) / span);
			});
			case "HYST" -> quad(p, (m, d, v, lo, hi) -> { int old = read(m, d); int x = read(m, v); write(m, d, x <= read(m, lo) ? 15 : x >= read(m, hi) ? 0 : old); });
			case "TON" -> ternary(p, (m, d, enable, preset) -> write(m, d, bool(m.timer(index(d, 'R'), read(m, enable) > 0, read(m, preset)))));
			case "PULSE" -> ternary(p, (m, d, trigger, duration) -> write(m, d, bool(m.pulse(index(d, 'R'), read(m, trigger) > 0, read(m, duration)))));
			case "RISE" -> binary(p, (m, d, input) -> write(m, d, bool(m.rising(index(input, 'I')))));
			case "SET" -> unary(p, (m, d) -> write(m, d, 15));
			case "RST" -> unary(p, (m, d) -> write(m, d, 0));
			case "JMP" -> jump(p, labels, null);
			case "JNZ" -> jump(p, labels, true);
			case "JZ" -> jump(p, labels, false);
			case "FAULT" -> (m, pc) -> { m.fault(PlcFault.RUNTIME_ERROR); return codeEnd(); };
			default -> throw new IllegalArgumentException("unknown instruction " + op);
		};
	}

	private interface Instruction { int run(PlcMachine m, int pc); }
	private interface U { void run(PlcMachine m, String a); }
	private interface B { void run(PlcMachine m, String a, String b); }
	private interface T { void run(PlcMachine m, String a, String b, String c); }
	private interface Q { void run(PlcMachine m, String a, String b, String c, String d); }
	private interface S { void run(PlcMachine m, String a, String b, String c, String d, String e, String f); }
	private static Instruction unary(String[] p, U f) { arity(p, 2); return (m, pc) -> { f.run(m,p[1]); return pc+1; }; }
	private static Instruction binary(String[] p, B f) { arity(p, 3); return (m, pc) -> { f.run(m,p[1],p[2]); return pc+1; }; }
	private static Instruction ternary(String[] p, T f) { arity(p, 4); return (m, pc) -> { f.run(m,p[1],p[2],p[3]); return pc+1; }; }
	private static Instruction quad(String[] p, Q f) { arity(p, 5); return (m, pc) -> { f.run(m,p[1],p[2],p[3],p[4]); return pc+1; }; }
	private static Instruction sext(String[] p, S f) { arity(p, 7); return (m, pc) -> { f.run(m,p[1],p[2],p[3],p[4],p[5],p[6]); return pc+1; }; }
	private static Instruction jump(String[] p, Map<String,Integer> labels, Boolean when) {
		arity(p, when == null ? 2 : 3);
		String condition = when == null ? null : p[1]; String label = p[when == null ? 1 : 2].toLowerCase(Locale.ROOT);
		Integer target = labels.get(label); if (target == null) throw new IllegalArgumentException("unknown label " + label);
		return (m, pc) -> when == null || ((read(m, condition) > 0) == when) ? target : pc + 1;
	}
	private static int read(PlcMachine m, String token) {
		char k = Character.toUpperCase(token.charAt(0));
		return switch(k) { case 'I' -> m.input(index(token,'I')); case 'O' -> m.output(index(token,'O')); case 'R' -> m.register(index(token,'R')); default -> Integer.parseInt(token); };
	}
	private static void write(PlcMachine m, String token, int value) { char k=Character.toUpperCase(token.charAt(0)); if(k=='O')m.output(index(token,'O'),value); else if(k=='R')m.register(index(token,'R'),value); else throw new IllegalArgumentException("not writable: "+token); }
	private static int index(String token, char expected) { if(Character.toUpperCase(token.charAt(0))!=expected)throw new IllegalArgumentException("expected "+expected+": "+token); return Integer.parseInt(token.substring(1)); }
	private static int bool(boolean b) { return b ? 15 : 0; }
	private static void arity(String[] p,int n){if(p.length!=n)throw new IllegalArgumentException(p[0]+" expects "+(n-1)+" operands");}
	private static int codeEnd(){return Integer.MAX_VALUE;}
}
