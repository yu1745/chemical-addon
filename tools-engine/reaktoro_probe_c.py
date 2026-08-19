# -*- coding: utf-8 -*-
"""Reaktoro 探针 C（修正版）：GeneralReaction + setRateModel —— 官方 kinetics 范式。
O2 在场（OCl- 热力学应分解），OCl-/HOCl 走动力学。看短期/长期是否都对。"""
from reaktoro import *
import sys, time, math
sys.stdout.reconfigure(encoding='utf-8')

DB = PhreeqcDatabase.fromFile('C:/Users/wangyu/Desktop/server/chem-engine/src/main/resources/db/sit.dat')
eO, eH, eCl = DB.element("O"), DB.element("H"), DB.element("Cl")
DB.addSpecies(
    Species().withName("OCl-").withElements(ElementalComposition([(eO, 1.0), (eCl, 1.0)]))
        .withCharge(-1).withAggregateState(AggregateState.Aqueous)
        .withStandardGibbsEnergy(-36800.0))
DB.addSpecies(
    Species().withName("HOCl").withElements(ElementalComposition([(eH, 1.0), (eO, 1.0), (eCl, 1.0)]))
        .withCharge(0).withAggregateState(AggregateState.Aqueous)
        .withStandardGibbsEnergy(-79900.0))

AQ = "H2O H+ OH- Na+ Cl- SO4-2 Fe+2 Fe+3 Fe(OH)+ Fe(OH)+2 FeCl+ FeCl+2 OCl- HOCl O2"

# 速率模型：接收 ChemicalProps 返回净速率 (mol/s)
def rate_fe(props: ChemicalProps):
    k = 1.0e2   # 快反应（游戏节奏）
    aOCl = props.speciesActivity("OCl-")
    aFe2 = props.speciesActivity("Fe+2")
    return k * aOCl * aFe2          # 正向为主（强有利反应），逆反应可忽略

def rate_decay(props: ChemicalProps):
    k = 1.0e-8  # 慢分解（月-年尺度）
    aHOCl = props.speciesActivity("HOCl")
    return k * aHOCl * aHOCl

system = ChemicalSystem(DB,
    AqueousPhase(AQ).set(ActivityModelDebyeHuckelPHREEQC()),
    GeneralReaction("OCl- + H+ + H+ + Fe+2 + Fe+2 = Cl- + H2O + Fe+3 + Fe+3").setRateModel(rate_fe),
    GeneralReaction("HOCl + HOCl = Cl- + Cl- + O2 + H+ + H+").setRateModel(rate_decay),
)

solver = KineticsSolver(system)
state = ChemicalState(system)
state.temperature(25.0, "C")
state.pressure(1.0, "bar")
state.set("H2O", 1.0, "kg")
state.set("Na+", 150.0, "mmol")
state.set("Cl-", 100.0, "mmol")
state.set("Fe+2", 8.0, "mmol")
state.set("OCl-", 50.0, "mmol")

def report(tag, st):
    p = st.props()
    ph = -math.log10(p.speciesActivity("H+"))
    print(f"  {tag:16s} pH={ph:6.2f}  OCl-={st.speciesAmount('OCl-')*1000:9.4f}  "
          f"HOCl={st.speciesAmount('HOCl')*1000:8.4f}  Fe+2={st.speciesAmount('Fe+2')*1000:8.4f}  "
          f"Fe+3={st.speciesAmount('Fe+3')*1000:8.4f}  O2={st.speciesAmount('O2')*1000:9.5f}  "
          f"Cl-={st.speciesAmount('Cl-')*1000:9.3f}")

t0 = time.time()
prev = 0.0
report("t=0", state)  # 初始（未反应）
for t in [1.0, 10.0, 100.0, 1000.0, 1.0e4, 1.0e5, 1.0e6, 1.0e7, 1.0e8]:
    r = solver.solve(state, t - prev)
    prev = t
    ok = r.succeeded() if r else "?"
    report(f"t={t:>9.0f}s", state)
print(f"  耗时 {time.time()-t0:.2f}s")
print()
print("  判读: 短期 OCl- 50->46(氧化 8mmol Fe2+), Fe+3->8; 长期 HOCl 慢分解出 O2")
