# 单测审计报告（手动逐条审计）

审计日期：2025（本会话）。审计范围：`src/test/java` 全部 19 个测试类 + `Harness`/`TestMain`（75 → 77 个用例），逐条核对：

1. **断言是否符合现实**（真实化学/物理 + 数据文件的 logK/delta_h/溶解度曲线）；
2. **断言是否完整**（能否暴露错误，会不会"测试绿但断言空转"）。

方法：除通读源码外，用探针程序（已删除）对每个场景打印完整输出状态、逐条对比断言阈值与实际值的余量，并数值验证 van't Hoff 方向、质量作用律、组分守恒。测试基线：修复前 75/75 全绿，修复后 77/77 全绿。

---

## A. 断言方向与物理现实相悖（最严重，已修复）

### A1. ThermoTest —— 断言方向与现实相反，注释物理错误

**原文**：`ammoniaHydrolysisIsExothermicSoColdFavoursProducts`，断言 `nh4Cold > nh4Hot`，注释声称"NH3 水合放热"。

**审计发现**（数值验证）：

| 温度 | NH4+（引擎输出） |
|---|---|
| 0 °C | 3.44 mB |
| 50 °C | 0.59 mB |

- 数据文件 `ammonia.json` 的氨水水解条目 ΔH = **+3.69 kJ/mol（吸热）**；NBS 真实值 ΔH° ≈ **+3.3 kJ/mol（吸热）**。两者都意味着"热 → 更多 NH4+"，与断言方向相反。
- 真实氨水 Kb 随温度**上升**（Kb(50°C)/Kb(0°C) ≈ 1.25）。
- 引擎输出反向且强度放大 5.8 倍，根因见 **F1**（Kw 无温度依赖 + 水解条目被冗余丢弃）。
- 引擎把存活条目 `NH4+ + H2O = NH3 + H+`（ΔH 52.22）应用得**完全正确**（数值验证：0°C 时 K_diss = 10^-10.09、50°C 时 10^-8.54，与 van't Hoff 公式精确一致）——这正是真实化学（加热铵盐释放 NH3）。

**修复**：重写为 `nh4DissociationIsEndothermicSoHotFavoursAmmonia`（NH4Cl 100 mB，热 → 更多 NH3），断言真实且引擎正确实现的方向。Kw 温度问题已于 **Track F1 修复**（见下文 F1 ✅ 段）：氨水解现已正确为吸热（+3.69 kJ/mol），新增正向断言 `ammoniaHydrolysisIsEndothermicSoHotFavoursAmmonium`。

### A2. IndustrialRedoxTest.ferrousIronReducesNitrateToNitrite —— 数据不现实，断言检查微量残差

**原文**：`NO3- + 2H+ + 2e- = NO2-` logK **15**，断言 `fe3 > 0`、`no2 > 0`。

**审计发现**：真实 E°(NO3⁻/NO2⁻, NO2⁻ 产物形式) ≈ +0.83 V → logK ≈ **28**。logK 15 使联合反应 NO3⁻ + 2Fe²⁺ + 2H⁺ = NO2⁻ + 2Fe³⁺ 的 K = 10^15/(10^13)² = **10^-11**——热力学不利，反应实际程度 **fe3 = 0.0093 mB、no2 = 0.0046 mB**（输入的 0.005%）。断言 `> 0` 只是在一个近乎为零的残差上做刀刃检查：K 偏离 4 个数量级照样通过，改真实数据反而可能因舍入归零而失败。

**修复**：logK → **28**（真实），断言 `fe3 > 50`、`no2 > 25`（实际 65.7 / 32.9 mB），并补 Fe、N 守恒。

### A3. IndustrialRedoxTest.ferrousIronIsOxidisedByOxygen —— 电对选错，体系不可行

**原文**：碱性电对 `O2 + 4e- = 4OH-` logK 20（E° = +0.40 V），断言 `fe3 > 0`、`oh > 0`。

**审计发现**：
- 碱性 O2 电对 E° = 0.40 V < Fe³⁺/Fe²⁺ 0.77 V——**热力学上不能氧化 Fe²⁺**（无沉淀耦合时）。
- 数值验证：电子记账 t[e⁻] = +100 − 4×100 = **−300 mB**，与电荷平衡不可同时满足 → 体系不可行，Newton 落在一个次优点：状态电荷泄漏 **+0.017 mB**（投影把 H 的负余量 −167,650 量子静默丢弃），反应程度仅 0.033 mB。
- 真实酸性氧化（锈蚀的酸式电对）：O2 + 4H+ + 4e- = 2H2O，E° = +1.229 V → logK = **83.1**。

**修复**：改用酸式电对 logK 83.1 + 输入 H+ 100 mB/Cl⁻ 400 mB；断言 `fe3 > 90`、H+ 消耗（< 50 mB）、O2 消耗（< 90 mB）。数值验证修复后：fe3 = 99.94 mB、O2 = 75.0 mB、H+ = 0.057 mB、电荷 −3 量子、各组分精确守恒、Newton 收敛。

### A4. 相关：Fe/Cu/Ce 合成 logK 与真实值偏差（未改，见 F4）

---

## B. 缺失的守恒断言（断言不完整，已补）

### B1. InvariantsTest —— 注释声称锁定"组分守恒"，实际没有该断言

注释原文："This test locks charge neutrality + determinism + **exact component conservation**"，但断言只有：电荷中性 + 两次运行相等（确定性）。**确定性相等不验证守恒**——确定性丢质量同样通过。

**修复**：新增 `assertComponentConservation`——对每个非 H 组分用 `model.speciesCoeff`/`minerals.coeff` 计算输入/输出总并精确断言相等（200 个随机汤料 × 全部组分，探针验证 0 违反）；同时断言无 sediment 产生（曲线盐不可能从这些汤料析出，若出现需扩展守恒检查）。

### B2. RedoxSolverTest / IndustrialRedoxTest（Fe/Cu/Ce 电对）—— 无金属/电子/氯守恒

原断言只有方向性（fe2 > 1 等）+ 电荷。丢 30 mB 的 Fe 与 Cl⁻ 照样电荷中性、断言全过。

**修复**：补 `fe2+fe3 = 100`、`cu1+cu2 = 100`、`Cl⁻ = 400`、**电子守恒**（`fe2 + cu1 + e-余量 = 初始电子总量`——注意 e- 组分自身的整数余量要计入，实测余量 4 量子）。

---

## C. 断言过弱（阈值远离实际值，已收紧）

| 测试 | 断言 | 实际 | 修复后 |
|---|---|---|---|
| Solvay step1 | nahco3≥80 / nh4≥200 / hco3≥100 | 145.6 / 294.2 / 147.6 | ≥120 / ≥260 / ≥120 + Na、C 守恒 |
| Solvay step5 | nh3≥90 / ca≥40 | 97.9 / 49.5 | ≥95 / ≥45 |
| NH4Cl 冷却结晶 | sed≥100 / NH4<400 | 206 / 294（恰为 0°C 溶解度曲线 294） | ≥190 / <310 |
| NH4Cl 盐析 | sed≥15 / NH4<190 | 20.9 / 180.1（恰为 [NH4][Cl]=294² 共同离子平衡） | ≥18 / <185 |
| SO3 吸热 | 仅 energyJ>0 | 284.6 kJ | 250k–320k J（=100 mB × 227.72 kJ/mol ÷ 80 g/mol 量级校验） |
| DegasTest malachite@scale | 仅 malachite>0 + Cu 守恒 | 500,000 mB | + hydroxide<1 mB、C 守恒（对齐 MalachiteTest） |

Solvay step1 的 hco3≥100 是唯一间接守碳的断言（余量仅 47 mB）；现在碳、钠直接守恒。

---

## D. 审计自身的空转（已修复）

### D1. PhysicsAuditTest —— 1 mB 量子阈值使质量作用检查几乎全部跳过

审计的 quantum-limited 跳过规则：任何参与组分 < 1 mB 就跳过该平衡检查。数值验证（新增 `Solver.auditChecksRun` 计数器）：

| 场景 | 实际跑过的质量作用检查数 |
|---|---|
| pureWater / causticSoda / 铝 / malachite / 石灰垢 | **0**（H+ 亚 mB） |
| nitricAcid / NO2 / SO3 | 1（OH⁻ 的"缺失物种"检查） |
| 酸过量（H+ 48.8 mB，HCl 1.2 mB） | **2**（OH⁻ + HCl 弱电解质） |

即原 PhysicsAuditTest 绝大多数场景只验证了电荷中性 + 不崩溃；历史 NO2 缺陷是被**电荷检查**抓住的（H+ = 335× 输入必然破坏电荷），但"质量作用律"守卫在大部分场景是空转的。

**修复**：
- `Solver.auditChecksRun` 静态计数器（每次 auditState 重置，统计实际执行的 secondary/mineral 检查数）；
- 新增场景 `acidExcessWeakElectrolyteMassActionIsChecked`（酸过量）并强制 `checksRun ≥ 1`；
- javadoc 如实说明：审计本质是电荷守卫 + 强酸场景的质量作用守卫。

### D2. 顺带发现：审计打印的 "newton residual=187663" 是度量伪影

malachite 场景审计打印 `[audit] newton residual=187663`，但完整残差向量实测：质量平衡行 ~1e-15、矿物行 7e-15、H 行 1.67e-6 量子（= 1.7e-13 mB，物理上完全收敛）。原因是 H 行在 t[H]=0 时除以 `max(|t|,1)=1` **不缩放**，phaseAssemble 中间"加相"迭代的 0.019 mB 电荷偏差被放大显示。打印噪音（不影响正确性），建议后续把 H 行按水体积缩放（见 F5）。

---

## E. 数据死条目（已修复）

### E1. ammonia.json 的氨水水解条目是死数据

`NH3 + water = NH4+ + OH-`（logK -4.75，ΔH +3.69）与 `NH4+ + water = NH3 + H+` + Kw 线性相关，被 leaf elimination 以 "dropping redundant equilibrium" 静默丢弃——**其 log_k 与 delta_h 从未被求解器使用**，而 DataIntegrityTest 之前还在为它的 delta_h（3.69）背书"权威数据"。这正是"测试绿但数据是死的"。

**修复**：
- `SystemModel.droppedEquilibria()` 记录被丢弃的条目；
- `DataIntegrityTest.noEquilibriumIsDroppedAsRedundant` 要求为空；
- 从 `ammonia.json` 移除该条目（行为中性：数值验证丢弃前后的模型完全一致，NH4+ 解离条目依然承载氨化学）。

---

## F. 引擎级遗留问题（未改，需决策）

### F1. ⭐ Kw 无温度依赖（✅ 已修复，Track F1 落地）

**修复前**：`SpeciesDatabase.allEquilibria()` 硬编码 `"H+1 + OH-1 = water", logK 14`，无 delta_h。数据里的自电离换算项在引擎中从未兑现：氨水解净焓 = −52.2 kJ/mol（放热，与作者 +3.69/真实 +3.3 符号相反）；纯水 pH 恒为 7；碳盐水解方向正确与否取决于哪条等价条目存活（隐蔽不自洽）。

**修复内容**（commit 见 git log）：
1. Kw 条目携带 ΔH = −55.91 kJ/mol（书写方向：形成；= phreeqc.dat "H2O = OH- + H+" +55.9066 反向）。
2. 关键架构修复：leaf-elimination 代数原先只把 logK 折算到组分空间、ΔH 原样透传，导致消除方向与书写方向相反/复合的条目范霍夫符号错（OH- = −H+ 存 −55.91 实应为 +55.91；HCO3- 存水解 41.01 实应为质子化 −14.9；Mg(OH)₂ 溶解存 0.47 实应为酸溶 −111.3）。新增 **`exprDeltaH`：ΔH 与 logK 走同一套消除代数**，每个次级/矿物拿到组分空间有效焓（`deltaHVan`）用于 van't Hoff；书写方向焓（`deltaH`）仍用于能量核算；Kw 次生生从能量路径剔除（热量由 NEUTRALISATION_J_PER_PAIR 拢合）。数据 JSON 无需重写。
3. 效果：氨水解有效焓 = +55.91 − 52.22 = **+3.69 kJ/mol**（吸热，与真实一致）；Kw_diss 随温度升高（pKw ≈14.9@0°C、13.3@50°C）；探针验证 NH4+ 12.4M@0°C → 13.2M@25°C → 14.0M@50°C（原为 34.4 → 13.2 → 5.9，反向并放大）。
4. 测试重校准：F1 哨兵 `ammoniaHydrolysisDirectionIsKnownLimitationSnapshot` 翻转升级为正向断言 `ammoniaHydrolysisIsEndothermicSoHotFavoursAmmonium`；Solvay step1 NaHCO3 阈值 120→100 MB（20°C 新物理值 110.5 MB）。155/155 全绿。

### F2. 边界依赖断言（已复核，一处撤回）

- ~~`hclDescalesLimestone` 是刀刃断言~~（**撤回，独立重审实测否定**）：原推断"溶解预算 rate×water×1000 恰好 = 100 mB，rate 降到 0.00009 即失败"是错的。插桩 projectKinetic 实测：溶解方向的预算 clamp 被 **feasibility clamp 抢先**——平衡态自由 CO3-2 ≈ 0 量子时，minNeg = -sp[CO3] 把回拉步钳到 ~0，**溶解实际上总是即时**（scale=100/300 单 tick 全溶，scale=1000 才因平衡自由量 0.17 mB 残留 0.17 mB）。rate 预算只约束沉淀方向；"溶解即时"是 feasibility clamp 的结构性副作用（已记入 known_limitations）。
- `DegasTest.openVessel`：vented 恰好 = 49 mB（floor 边界，retention = 1 mB）——保留（确定性），已加注释。

### F3. PhysicsAuditTest 并行安全

静态 `auditViolations` / `auditChecksRun` + JVM 系统属性：依赖默认串行执行（javadoc 已注明）。

### F4. 合成 redox logK 与真实值

Fe/Cu/Ce = 13/6/15 vs 真实 13.0/2.6/29.1：排序正确、机制测试有效，但 Ce 若改真实值 29.1，"not all Ce4+ consumed" 断言会失效（反应将完全进行）。数据扩展（Track B4）时按真实值重写并同步断言。

### F5. 审计残差度量噪音

H 行未缩放导致 phaseAssemble 中间迭代打印巨大的假残差（D2）。建议 H 行按 `water` 缩放，或仅对最终解打印。

---

## G. 经审计确认合理（未改）

- **MalachiteTest**：守恒断言完整（Cu、C、电荷），hydroxide<1 断言正确锁定相竞争（探针验证 1× 与 10000× 尺度行为一致）；"SI=-576" 是量子舍入伪影（Cu、H 亚量子），连续解的质量作用行实测收敛（矿物行 7e-15），审计跳过是**正确**的。
- **KineticsTest**：KNO3 冷却精确收敛于溶解度曲线（sed=184 = 500−316，cap=31.6 g/100g×1000）；中和热断言与 `NEUTRALISATION_J_PER_PAIR` 精确一致（317,200.0 J）；vanthoff 单元测试正确。
- **RateLimitTest**（合成慢盐）：首 tick 精确 8 mB 预算断言正确锁定机制；尺度相对性、温度/搅拌方向均验证通过。
- **JacobianTest**：解析 Jacobian vs 有限差分（单配置点，5 维），是真正的交叉验证。
- **CoverageTest / IndustrialChemistryTest / Solvay step5 / DegasTest closed**：阈值与实际值匹配、方向正确。
- **slaked_lime 逆向溶解度**（热→更难溶）方向正确（Ca²⁺：0°C 3.09 → 50°C 2.10 mB）——矿物直溶 ΔH 约定在引擎中是自洽的。

## 独立子代理重审（干净记忆，对抗式）

第一轮审计完成后，另派了一个**无上下文子代理**从零独立重审（读代码/数据、跑 77 测试、/tmp 探针实测），
结论与本报告对照：

### 独立复现（可信度确认）

- A1–A3（ThermoTest 方向、硝酸盐/O₂ 电对）、D1–D2（审计空转 + checksRun 计数）、F4、G 全部独立复现，判定"可信且被低估"。
- Redox 修复实测一致：logK 15 → fe3=0.009 mB（旧缺陷），logK 28 → 65.7 mB；碱性 O₂ 电对 fe3=0.00，酸式 83.1 → 99.94 mB；守恒精确。

### 子代理新发现（本报告遗漏，已处理/回应）

1. **严重——Ksp 数值静默盲区**：`solid>=99 && 离子<1mB` 类断言只验证"沉淀吃光限制离子"，对 Ksp 数量级免疫（AgCl logK 放 4 个数量级仍绿）。根因：平衡残留 < 1 mB 落在量子格点之下，不可直接断言。**已部分修复**：(a) slaked_lime 是全引擎唯一平衡残留可测（>1 mB）的矿物（Ksp=10^-7.2 → [Ca]≈2.5 mB），新增 `CoverageTest.slakedLimeSaturationAnchorsKsp` 锚定 Ksp 数值本身（±20% 即 logK ±0.08）；(b) `DataIntegrityTest` 新增数值带宽检查（|delta_h|≤300 kJ/mol、|log_k|≤100、isFinite），挡住来源标注查不出的位数/符号笔误类；其余难溶盐的 Ksp 数值防线 = 数据来源标注 + 未来连续层浓度断言。
2. **中——F1（氨水解方向反转）无回归护栏**：已新增哨兵测试
   `ThermoTest.ammoniaHydrolysisDirectionIsKnownLimitationSnapshot`——快照当前（物理反转的）方向，
   Kw ΔH 修复落地时该断言会失败并提示翻转，而不是静默改变行为；根治待 Track A 级修复（Kw ΔH）。
3. **中——刀刃断言**：KNO3 上界余量 1.1%、Solvay step5（我收紧后 3%）、NH4Cl 盐析 2.8%。**已加厚**：KNO3 带放宽 [176,187]、step5 nh3 回 90（8%）、盐析 <187（4%）。
4. **中——neutralisationReleasesHeat 同义反复**（energyJ 用同一常量断言）：保留（精确性有价值），heatRiseC 断言依赖独立的热容路径，构成部分独立锚。

### 一处分歧（子代理对，本报告撤回）

- 本报告原 F2-① 推断"hclDescalesLimestone 是 100 mB 预算刀刃"被实测否定（见上 F2 修订）：溶解不受预算限制，该断言余量厚实。

子代理总体裁定：套件可靠（守恒断言精确、redox 修复有效、Jacobian/随机汤料/scale 提供真正交叉验证），
三类系统性开口已分别处理或记录（Ksp 盲区部分锚定、F1 护栏、刀刃加厚）。

## 第二次独立重审（干净记忆，排除 Track C 蓝图类）

再审子代理（范围=原 19 测试类 79 用例 + 引擎 + 数据，blueprint 类排除）独立实测后裁定：
**守恒断言精确且完整**（Solvay Na/C/Cl、Malachite Cu/C 双尺度、redox 金属/Cl⁻/电子含 e- 整数余量、
Invariants 无假阳性），运行期数值声明全部复现。发现并已修复：

1. **文档事实错误（2 处）**：
   - "有效水解焓 = 52.22−55.8 = −3.6" 是混淆值——引擎（Kw 无 ΔH）实际有效焓 **−52.2 kJ/mol**
     （实测 K_hyd(50°C)/K_hyd(0°C) = 0.0285，与 −52.2 的 van't Hoff 精确吻合）；
     +3.6 是"Kw 加 ΔH=+55.8 之后"的正确值。ThermoTest javadoc / 审计报告 F1 / PLAN 三处已统一修正。
   - slakedLime 锚定 javadoc "±20% ⇒ logK ±0.08" 错误：[Ca]∝Ksp^(1/3)，实际 **±0.24**。已改。
2. **刀刃上界（2 处，文档"≥4%"虚报）**：KNO3 上界 187（实测 1.6%）→ **190**（3.3%）；
   NH4Cl 盐析 <187（3.9%）→ **<190**（5.5%）并新增 **NH4 载体守恒断言**（NH4+ + NH3 + 固体
   = 输入 201 mB，含种子；冷却场景 = 500 mB）作为真正的主防线。
3. **补守恒**：O2 酸式场景补铁（±1 量子，投影 rint 半舍入）+ **电子守恒**（e- + Fe2+ − 4·O2 =
   −300 mB）；硝酸盐场景补电子守恒（e- + Fe2+ − 2·NO3 = −100 mB）。
4. **同义反复清理**：slakedLime 的 `oh=2Ca`（与 netCharge 同义）删除；neutralisation 精确断言
   保留但补**物理锚**（5.56 mol × 57.1 kJ/mol = 317 kJ 量级断言 + 注释）。
5. **DataIntegrity 波段前瞻风险**：|log_k| ≤ 100 会误拒合法强多电子电对（MnO4⁻/Mn²⁺ logK≈128）
   → 放宽至 **±150**，语义明确为"位数笔误存在性保护"而非物理上界，注释要求加超强电对前先放宽。

## 结论

修复前 75 个测试全绿，但存在 3 个方向/数据与现实相悖的断言（A1–A3）、2 处缺失守恒断言（B1–B2）、1 处空转的物理审计（D1）、1 处死数据（E1）——均属"测试通过但未暴露错误"。修复后 **77/77 全绿**，并新增 2 个用例（数据死条目检查、真实质量作用检查）。引擎级遗留：**Kw 无温度依赖**（F1）是唯一需要计划级决策的根因问题。
