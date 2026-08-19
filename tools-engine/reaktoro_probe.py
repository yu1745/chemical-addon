# -*- coding: utf-8 -*-
"""Reaktoro 探针（精选物种集版）：验证零外挂能否正确解代表性场景。

教训1: 全库 1526 物种进 GEM 会卡死（GEM=全物种联立优化，LMA 天然降维是 PHREEQC 快的原因）。
       Reaktoro 正常用法就是按体系精选物种。以下每个场景用最小充分集。
"""
from reaktoro import *
import sys, time, math
sys.stdout.reconfigure(encoding='utf-8')

DB = PhreeqcDatabase.fromFile('C:/Users/wangyu/Desktop/server/chem-engine/src/main/resources/db/sit.dat')

def solve_closed(state, sys_):
    specs = EquilibriumSpecs(sys_)
    specs.temperature(); specs.pressure()
    cond = EquilibriumConditions(specs)
    cond.temperature(25.0, "celsius"); cond.pressure(1.0, "bar")
    return EquilibriumSolver(specs).solve(state, cond)

def show(state, species):
    for s in species:
        try:
            v = state.speciesAmount(s) * 1000
            print(f"    {s:14s} = {v:11.5f} mmol")
        except Exception:
            pass

# Fe/Cl/S/Ba 水相形态精选（含 sit.dat 中真实存在的络合物）
AQ_A = "H2O H+ OH- Na+ Cl- SO4-2 H(SO4)- Fe+2 Fe+3 Fe(OH)+ Fe(OH)+2 FeCl+ FeCl+2 Fe(SO4)+ Fe(SO4)2- Ba+2 Ba(SO4) Cl2 O2"

print("=" * 66)
print("A. 全家锅纯平衡（精选物种, 闭合, 零外挂）")
print("=" * 66)
phases = Phases(DB)
phases.add(AqueousPhase(AQ_A))
phases.add(CondensedPhase("Barite"))
sys_a = ChemicalSystem(phases)

for tag, cl2 in [("A1 基线", 0.0), ("A2 通Cl2 2mmol", 2.0)]:
    st = ChemicalState(sys_a)
    st.set("H2O", 1.0, "kg")
    st.set("Fe+2", 10.0, "mmol"); st.set("Ba+2", 5.0, "mmol")
    st.set("Na+", 20.0, "mmol");  st.set("Cl-", 10.0, "mmol")
    st.set("SO4-2", 10.0, "mmol")
    if cl2:
        st.set("Cl2", cl2, "mmol")
    t0 = time.time()
    r = solve_closed(st, sys_a)
    p = ChemicalProps(st)
    print(f"  {tag}: ok={r.succeeded()} pH={-math.log10(p.speciesActivity("H+")):.2f}  ({time.time()-t0:.2f}s)")
    show(st, ["Fe+2", "Fe+3", "FeCl+2", "Fe(SO4)2-", "Ba+2", "SO4-2", "Barite"])
    print()

# ============================================================
print("=" * 66)
print("B. 漂白液：OCl-/HOCl 真实物种 + ΔGf°，闭合纯平衡（长期终态）")
print("=" * 66)
db2 = PhreeqcDatabase.fromFile('C:/Users/wangyu/Desktop/server/chem-engine/src/main/resources/db/sit.dat')
eO, eH, eCl = db2.element("O"), db2.element("H"), db2.element("Cl")
db2.addSpecies(
    Species().withName("OCl-").withElements(ElementalComposition([(eO, 1.0), (eCl, 1.0)]))
        .withCharge(-1).withAggregateState(AggregateState.Aqueous)
        .withStandardGibbsEnergy(-36800.0))
db2.addSpecies(
    Species().withName("HOCl").withElements(ElementalComposition([(eH, 1.0), (eO, 1.0), (eCl, 1.0)]))
        .withCharge(0).withAggregateState(AggregateState.Aqueous)
        .withStandardGibbsEnergy(-79900.0))

AQ_B = "H2O H+ OH- Na+ Cl- OCl- HOCl"
phases_b = Phases(db2)
phases_b.add(AqueousPhase(AQ_B))
sys_b = ChemicalSystem(phases_b)   # 先闭合水相（无 O2 出口——看它自身是否稳定）

stb = ChemicalState(sys_b)
stb.set("H2O", 1.0, "kg")
stb.set("Na+", 150.0, "mmol")
stb.set("Cl-", 100.0, "mmol")
stb.set("OCl-", 50.0, "mmol")
t0 = time.time()
rb = solve_closed(stb, sys_b)
pb = ChemicalProps(stb)
print(f"  纯平衡(无O2出口): ok={rb.succeeded()} pH={-math.log10(pb.speciesActivity("H+")):.2f} ({time.time()-t0:.2f}s)")
show(stb, ["OCl-", "HOCl", "Cl-", "OH-"])
