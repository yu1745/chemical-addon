# Known Limitations

## 1. ~~NO₂ `delta_h` 暂为 0~~（已解决）

- 已通过 `projectExact` 的通用 secondary 批量削减修复
- NO₂ 现使用非零 `delta_h`

## 2. ~~固定 `pe` 外部电子库会破坏电荷平衡~~（已解决）

- 已移除固定 `pe` 外部电子库路径
- Redox 只使用多电对 + 电子守恒，电荷平衡由真实电子转移维持

## 3. ~~多库权威数据未合入~~（已解决）

- `tools/phreeqc.dat`（通用参考库）+ `tools/llnl.dat`（热力学自洽）+ `tools/minteq.v4.dat` 均已纳入仓库（官方 PHREEQC 仓库 `database/`）
- ✅ 权威 `delta_h` 已全量合入并标注来源（24 项：8 项直用 + 12 项 Hess 换算 + 4 项 NIST/NBS 生成焓；另有早期批次 8 项——NH₃/NH₄⁺/CO₃²⁻/HCO₃⁻/方解石/石膏/重晶石/毒重石——为 phreeqc.dat 推导值，已补齐 `delta_h_source` / `delta_h_derivation`），由 `tools/apply_phreeqc_delta.py` 统一换算与校验
- ✅ 测试已分批重校准，75/75 全绿（含 `PhysicsAuditTest` 回归防线）

### 已合入清单（含换算方式）

| 我们的反应 | 来源 | 合入值 kJ/mol（旧值） | 换算 |
|---|---|---|---|
| AgCl 溶解 | llnl Chlorargyrite | 65.74（65.0） | 直用 |
| Ag₂CO₃ 溶解 | minteq.v4 Ag2CO3 | 42.15（20.0） | 直用 |
| NaHCO₃(s) 溶解 | llnl Nahcolite | 17.02（−5.0） | 直用 |
| Zn(NH₃)₄²⁺ 生成 | llnl Zn(NH3)4++ | −54.90（−40.0） | 直用 |
| MgCO₃ 溶解 | llnl Magnesite | −29.60（−5.0） | 酸溶+HCO₃⁻ 解离 |
| Mg(OH)₂ 溶解 | llnl Brucite | 0.47（−5.0） | 酸溶+2×自电离 |
| Ca(OH)₂ 溶解 | llnl Portlandite | −16.87（−5.0） | 酸溶+2×自电离 |
| Fe(OH)₃ 溶解 | llnl Fe(OH)3 | 83.64（30.0） | 酸溶+3×自电离 |
| Al(OH)₃ 溶解 | llnl Gibbsite | 64.93（20.0） | 酸溶+3×自电离 |
| Cu(OH)₂ 溶解 | minteq.v4 Cu(OH)2 | 55.39（−10.0） | 酸溶+2×自电离 |
| Cu₂(OH)₂CO₃ 溶解 | llnl Malachite | 50.43（−10.0） | 酸溶+HCO₃⁻+2×自电离 |
| Zn(OH)₂ 溶解 | llnl Zn(OH)2(ε) | 30.03（−10.0） | 酸溶+2×自电离 |
| SO₃ 水吸收 | NIST/NBS ΔfH° Hess | −227.72（−100.0） | SO₃(g)/H₂O(l)/SO₄²⁻(aq) |
| NO₂ 水吸收 | NIST/NBS ΔfH° Hess | −138.18（−50.0） | NO₂(g)/NO(g)/NO₃⁻(aq)/H₂O(l) |
| Al(OH)₃ + OH⁻ = [Al(OH)₄]⁻ | NIST/NBS ΔfH° Hess | 20.6（−20.0） | Al(OH)₄⁻(aq)/gibbsite/OH⁻ |
| Ag⁺ + 2NH₃ = [Ag(NH₃)₂]⁺ | NIST/NBS ΔfH° Hess | −56.3（−30.0） | Ag(NH₃)₂⁺(aq)/Ag⁺/NH₃(aq) |

### 仍为估算（无任何权威来源，JSON 已标注 `estimated`）

| 反应 | 原因 |
|---|---|
| Fe³⁺ + SCN⁻ = FeSCN²⁺ | 所有 PHREEQC 系数据库与 NIST 均无此物种焓数据 |
| Cu²⁺ + 4NH₃ = Cu(NH₃)₄²⁺ | llnl 只有 2/3 级（−45.1/−67.3），第 4 级无数据 |
| HCl(aq) = H⁺ + Cl⁻ | 权威值 ≈ 0（llnl.dat 强电解质 ΔH=0），但引擎禁止 delta_h=0，保留遗留值 −75.0（旧 heat_kj 来源） |

### 换算规则（不能直接抄库值）

- PHREEQC 多为酸溶形式（`固相 + nH⁺ = 离子 + mH₂O`），我们为直接溶解（`固相 = 离子`）：`ΔH_我们 = ΔH_PHREEQC + n×55.9066 kJ/mol`（水自电离热，取自 phreeqc.dat）
- 涉及 HCO₃⁻ 产物时再加 `ΔH(HCO₃⁻ = CO₃²⁻ + H⁺) = 14.8992 kJ/mol`
- llnl 与 minteq 对同一相可能互为反方向，必须按反应字符串对齐后再取号
- 无库覆盖的物种用 NIST/NBS 标准生成焓 Hess 计算，并在 JSON 中标注来源与推导式

## 4. ~~NO₂ 吸收在 20°C 下求解器收敛异常~~（已解决）

- **症状**：NO₂ 吸收反应（3NO₂+H₂O=2H⁺+2NO₃⁻+NO）的 Q 是 5 次非线性，20°C 下 Newton 会收敛到非物理状态（旧值 −50 时 H⁺ 可达化学计量的数百倍、质量不守恒；新值 −138.18 时卡在零反应）
- **根因**：Newton 从"纯反应物"初值出发，产物侧主导的平衡（K≈1410–2587）下 n_NO 按质量作用定律爆炸到 ~1e54；NO2 质量平衡行雅可比降到 ~1e-8（残差为 −1），线搜索无法接受任何步长，卡在"NO2 塌缩到 0"的伪盆地
- **修复**：`Solver.newtonSolve` 新增**化学计量预推进 seed**——每个 secondary 先推进到其限制反应物总量的一半（产物侧、质量可行），Newton 从产物侧平滑下降；作为多起点之一，其余 seed 保留
- **验证**：20°C/25°C、小/大输入（10 MB/100 MB NO₂）、新旧 ΔH 值全部收敛到精确质量/电荷守恒平衡（100 MB NO₂ → 66 MB H⁺ + 33 MB NO + 6.3 MB NO₂）；`nitrogenDioxideAbsorbsToNitricAcid` 恢复 20°C 原场景并作为回归保护

## 5. 物理一致性审计与全量测试核查（2026-08 完成）

- **审计机制**：`Solver` 内置 `-Dchemengine.audit=true` 诊断——每个投影后状态校验：净电荷（≥1 MB 才报）、非限速 secondary 的 Q=K、非限速矿物 SI；违规打印 `[audit] PHYSICS VIOLATION` 并收集到 `Solver.auditViolations`。参与物种 < 1 MB 的量子受限状态跳过（亚量子平衡舍入 0/1 量子是格点伪影，绝对误差 < 1 MB）。回归防线：`PhysicsAuditTest`（10 个场景）。
- **全量核查结论**（65 个测试 × 每个 solve）：状态级审计全绿；发现并修复/分类的问题：
  1. **真·垃圾状态**（守恒对但热力学错）：NO₂ 吸收 20°C —— 旧值曾输出 H⁺ = 33,540 MB（输入 100 MB），旧测试一直跑在垃圾上；已修（见第 4 条）。
  2. **测试夹具 bug**（2 处，已修）：`MalachiteTest.ironHydroxideOutcompetesMagnesiumForHydroxide` 输入净 −300 MB、`RealDataRateTest.smallCleanupIsStillInstant` 输入净 −40 MB —— 引擎忠实保留输入电荷，输出当然不中性。
  3. **量子格点伪影**（无害、有界 < 1 MB）：亚量子 H⁺/OH⁻（碳酸盐 Q/K 偏差 2–4 阶但参与物种 < 1 MB）、e- 伪物种余数（redox 电对在整数态不可检查，已跳过）、微小体系（InvariantsTest water=1000 量子）。
  4. **已知相装配局限**（非新问题）：粗盐精炼第 2 步联合 Newton 残差 65–210，MgCO₃ 相发散后回滚标记失败（"保守回退 + 失败相跳过"），状态为有效次优子相集（MgCO₃ 过饱和未沉淀 SI≈2.9），守恒与化学意图正确；真正联合相搜索见 PLAN.md 后续优化。
- **附带修复**：`projectExact` 不再丢弃 e-（改以离子写入状态），redox 输出净电荷精确为 0。
- **审计边界（已知盲区）**：审计对参与物种 < 1 MB 的平衡一律跳过（量子受限）、Q/K 容差 100×、SI 容差 10×——它是**数量级级守卫**，不是浓度级校验；粗盐精炼第 2 步的残差 65–210 非收敛相装配即属"审计干净但热力学次优"的已知案例。浓度级断言属于场景测试，不要把审计干净读作质量作用律严格成立。

## 6. 限速矿物"溶解即时"是 feasibility clamp 的结构性副作用（2026 审计确认）

- **现象**：`rate` 限速的矿物（如 limestone rate 0.0001）在**溶解方向**几乎总是单 tick 全溶
  （scale=100/300 实测全溶），预算 clamp（rate×water×drive）似乎不生效；沉淀方向严格受限。
- **根因**：`projectKinetic` 对每个慢条目做可行域钳制（防负量）。溶解时平衡态的自由产物组分
  （如 CO3-2）通常 ≈ 0 量子，`minNeg = -sp[CO3]` 把回拉步钳到 ~0，预算 clamp 被抢先。
  "溶解即时"是这一钳制的结构性副作用，不是预算公式的设计结果。
- **影响**：对游戏语义（除垢/溶解即时）是期望行为；但若未来需要"慢溶解"，需在 feasibility
  钳制与预算之间重新设计（例如对负步长放宽 minNeg 到预算尺度）。
- **回归防线**：`IndustrialChemistryTest.hclDescalesLimestone`（Ca==100 精确、limestone==0）
  与 `RealDataRateTest.smallCleanupIsStillInstant` 锁定该行为。

## 7. 金属 + 酸的"金属溶解成自由电子"Ksp 模型在电子守恒引擎中数学上不可行（2026 审计确认）

- **现象**：`zinc_metal(s) = Zn+2 + 2 e-`（logK 25.7，E°_ox 0.76 V）与 SHE 平衡
  `H2 = 2H+ + 2e-`（logK 0）组合时，Zn(s) + 2H+ → Zn2+ + H2 并不会发生：
  实测 Zn 全溶但 H+ 剩 99.8 mB、e- 悬空 99.8 mB、H2 仅 0.1 mB。
- **根因**：SHE 的 [e-] 是虚拟活度（pe），可任意取值；引擎把 e- 当真实组分（总量守恒）。
  H2 次级量 ∝ [H+]²[e-]²（4 次方浓度积），在 [H+]/[e-] ≤ 0.1 浓度时上限仅 ~0.1 mB，
  大根不存在——电子无法被 H2 吸收，悬空在溶液里。
- **结论**：金属溶解成**自由电子**的 Ksp 模型（含 e-）不可行；但 D1a 的**净反应位移条目**
  （无自由电子，如 `Zn(s)+2H+ = Zn+2 + H2(g)`，气体产物位移，logK = nE0/0.05916）**已实现并可行**
  （见 MetalDisplacementTest.zincDissolvesInAcid_releasingHydrogen，锌守恒精确、电荷 0）。
  **L3 共享金属池多重置换已实现**：同一金属同时用于 Cu 置换与酸置换（如 Fe 既是 Fe+Cu 又是
  Fe+acid 的反应物）、或多金属顺序竞争（Zn 先置换 Cu 后 Fe）时，金属池按 logK 降序贪心分配
  （Zn+Cu 37.2 优先于 Zn+Fe 10.8；Fe+Cu 26.5 优先于 Fe+acid 14.9），守恒精确至量子级
  （金属恰耗尽边界处有 1–2 量子整数投影精度伪影）。见
  MetalDisplacementTest.zincDisplacesCopperPreferentiallyOverIron / ironDisplacesCopper_inAcid_phCoupled。
- **可行的 redox 形态**：电子在**溶液电对间转移**（Fe3+/Fe2+、NO3-/NO2-、Cl2 歧化等，
  输入物种自带价态电子含量）；以及**净反应位移条目**（无自由 e-，金属+金属/金属+酸，D1a）。

## 8. 电子中性输入的 Cl2/Fe2+ 氧化数值失败（2026 审计确认，已修复）

- **历史现象**：`Cl2 + 2e- = 2Cl-`（logK 46）与 Fe3+/Fe2+ 电对组合模拟 2FeCl2 + Cl2 -> 2FeCl3 时，
  输入 t[e-] = 0（Fe+2 的 +1 与 Cl2 的 -2 电子含量抵消）→ e- 组分不激活 →
  Cl2 次级量被钳为 0 → Cl 组分平衡矛盾 → `projectExact` 抛
  "negative component remainder"（实测 Fe2 100/Cl2 50 与 100/40 均崩溃）。
- **根因（两个独立的数值缺陷，均已修复）**：
  1. `Solver.activeComponents` 只激活 `|t[c]| > 0` 的组分。t[e-] = 0 时 e- 不进入基组，而
     唯一会激活它的 Fe2+ 半反应又要求 e- 已激活才"可行"——死锁，还原型次级被钳成 0。
     **修复**：当模型存在 redox 电对（即 e- 是组分）时无条件激活 e- 组分，让 pe = -log[e-]
     成为自由涌现变量，两个电对在内部共享同一 pe 平衡。对封闭批次求解，这与
     "pe 自由变量"（PHREEQC 式）在物理上等价，且不破坏既有 t[e-] ≠ 0 路径。
  2. `Solver.projectExact` 的整数投影对 e- 的负余量无法修复：泛用可行性修复只削减
     **正系数**次级，而 e--耦合次级（如 Fe+3 = Fe+2 - e-，coeff[e] = -1）是负系数，
     其一量子取整误差会让 e- 余量成 -1。**修复**：当 e- 余量小幅为负（≤ 1024 量子，纯取整
     伪影，牛顿残差已 ~1e-14）时钳到 0，与审计对 e- 伪物种亚量子余量的处理一致；
     真正的不可行（如只有氧化剂无还原剂，牛顿无法收敛）远大于该容差，不会被掩盖。
- **意义**：这是"电子中性氧化还原"一整类问题的根治——任何氧化剂+还原剂恰好按化学计量比、
  净电子收支为 0 的组合（卤素氧化 Fe2+、Cr2O7²⁻/MnO4⁻ 氧化 Fe2+、Cl2 歧化等）此前都受困于
  同一死锁/投影缺陷。连原先"能跑"的 NO3-/Fe2+ 测试也只是靠非化学计量比输入留下的 t[e-] ≠ 0
  意外绕开，本就不稳。现按精确计量比输入亦可求解。
- **回归防线**：`RedoxSolverTest.ferrousChlorideOxidisedByChlorine`（投影修复判别：
  关闭修复即复现 "negative component remainder"）与
  `ferrousChlorideAlone_isNotSpontaneouslyOxidised`（无受体时铁不自发氧化，e- 处理行为锁）。
  主路线 Fe2O3 + 6HCl -> 2FeCl3 不受影响。

## 9. 结晶氧化物相（Tenorite/Hematite）颠覆氢氧化物相竞争（2026 数据实验撤回）

- **现象**：加入 `copper_oxide`（llnl Tenorite）与 `iron_oxide`（llnl Hematite）后，
  现有场景全部反转：FeSCN 测试的铁沉淀从 Fe(OH)3 变 Fe2O3（44 mB）、铜氨测试的
  Cu(OH)2 变 CuO（98 mB）。撤回数据后恢复。
- **根因**：引擎只做**平衡相装配**（最不溶优先），没有亚稳相动力学——室温沉淀实际
  生成无定形 Fe(OH)3/Cu(OH)2，赤铁矿/黑铜矿是老化产物；平衡选择更稳的氧化物。
- **教训**：新增矿物数据是**全局相竞争变更**，必须先跑全套场景确认；氢氧化物的
  Ksp 数据实际上代表"无定形/工业沉淀相"，与结晶氧化物不可共存于同一平衡模型。
- **结论**：CuO + H2SO4、Fe2O3 + 6HCl 作为**平衡相装配**会颠覆氢氧化物竞争（需亚稳相机制），
  因此不加入平衡矿物相；但已可用 **D3 纯计量净反应**表达（`Electrolysis.advance`，氧化物按
  化学计量消耗、金属离子释放，不参与平衡相装配）——绕开 §9。见
  ThermalProcessTest.ferricOxideAcidDissolution / cupricOxideAcidDissolution / zincOxideAcidDissolution
  / manganeseDioxideAcidDissolution / silverDissolvesInNitricAcid / bayerDissolution_formsAluminate。

## 10. Track E 纯氧化剂/纯还原剂输入的电子边界不可达（2026 Track E 确认）

- **现象**：小主基模型（Track E）下，"纯 Fe³⁺ 输入（无还原剂）"或"纯 NH₄⁺ 输入（无
  氧化剂）"在连续 Newton 中无法收敛到边界态（n[e-] = 0，氧化还原物种不转化）——前者抛
  "negative component remainder"、后者卡死。这是**真实化学**：Fe³⁺ 无还原剂不会自发成
  Fe²⁺，NH₄⁺ 无氧化剂不会自发成 NO₃⁻；电子池总量（t[e-] = −输入氧化态×量）与
  n[e-] ≥ 0 在边界联合无内点解。
- **与 legacy §8 的关系**：同一类"电子中间体"数值病态的镜像——legacy 通过"无条件激活
  e- + 电子中性输入"规避；Track E 需要同样的**输入纪律**：redox 系统按电子配平的净反应
  输入（如 Fe³⁺+Cu⁺ 对），或让沉淀/络合反应提供电子汇/源（Fe³⁺+OH⁻→Fe(OH)₃ 可收敛）。
- **产物**：`FreeEnergyChemistryTest` 用电子配平的 Fe³⁺+Cu⁺ 验证净氧化还原（K=10^10.4，
  反应近乎完全），NH₄⁺/NH₃、Cu(NH₃)₄²⁺ 的**常数推导**已由模型验证（logK(NH₄⁺)=119.03
  ↔ E°=0.88 V ✓），但其**求解**待边界处理（未来工作：外部 pe 库或边界 Newton 投影）。
- **回归防线**：ChaosProbe 的状态生成限定主基离子、元素+电子守恒（不产生纯氧化剂输入）。

---

# Track G（IPhreeqc 内核）已知限制

> 以下为 2026-08-17 引擎切换（tag self-engine-final → IPhreeqc+JNA）后的已知限制档案。
> 标记约定：〔策展〕= 数据/内容层可解，无引擎缺陷；〔语义〕= 需要架构决策；
> 〔数据〕= sit.dat 缺物种/相；〔引擎〕= 原生行为，需调用侧防御。

## G1. 介稳池体系电子绝缘——氧化事件的"铁泥"不可见〔语义〕

- **现象**：Fero 伪元素池探针（2026-08-17）实测：`-formula Fero -2 Fe 2 Hyp -1 Cl 1`
  原子转移精确（Fero 10mmol→0、Fe 池 +10mmol、Hyp/Cl 1:1），但新生 Fe 的氧化态不受控——
  全池氧化还原平衡把 pe 拉到 -6.6，Fe 全部回到 Fe(+2)，Ferrihydrite 不沉淀（1.2e-7 mol）。
- **根因**：KINETICS `-formula` 只搬原子不搬电子（元素记账无 e⁻ 项），OCl⁻→Cl⁻ 释放的
  两个电子当量在池间转移时被吞掉，新生 Fe 找不到氧化剂当量。与"-formula 价态 token
  结构性不可能"（见 PLAN.md G1b 补充实验）同根：**介稳池体系对电子绝缘**。
- **后果**：漂白液被 Fe²⁺ 污染的真实结局（红棕 Fe(OH)₃ 泥）引擎不可见；现状只有
  `HypOxidisesFerrous` 的"Hyp 消耗"是可感知的，且 Fe²⁺ 速率门理论上永不耗竭（催化式）。
- **候选解**（未决，需架构决策）：
  1. **Ferr 伪元素池**：再加 Fe(+3) 池，formula `Fero -2 Ferr 2 Hyp -1 Cl 1` 纯池间
     计量转移（1:2 计量本身携带电子当量），铁泥由 mod 层读 Ferr 池渲染。结构可行
     （同 Hyp/Sul/Nitra 模式），代价：第 5 个池 + Fe 输入双轨制（Fero/Fe）需要策展指南；
  2. 接受现状：Hyp 消耗是玩家主可感知现象，铁泥省略，零成本；
  3. 全平衡 Fe 电对：会毁介稳（见 PLAN.md 五条死路），已排除。
- **回溯兼容**：`FerrousReducesNitrate` 同病（Nitra→Nitri 的电子去向我未声明，Nitri
  池是"原子态正确、氧化态悬置"的近似）。

## G2. Fe⁰ 金属置换不可演〔数据+策展〕

- Track C #20（Fe+H₂SO₄→FeSO₄+H₂↑、Fe+CuSO₄→FeSO₄+Cu）双缺口：
  - sit.dat **无 Fe(cr) 相**（有 Cu(cr)）——金属铁需补相数据（log_k 可由 E°(Fe²⁺/Fe)=−0.44 V 推）；
  - 金属溶解本质动力学+钝化过程：平衡装配会把 1 mol 金属铁瞬间"溶解"到 10⁻⁹ 残余
    （热力学最强方向），荒谬——即使补了 Fe(cr) 也不能进 EQUILIBRIUM_PHASES 白名单，
    置换/酸溶必须走策展 KINETICS（Fero 产物 + 气相产物白名单反应）。
- 与 legacy §10 的"电子边界"不同：此处是表达层缺口，不是数值病态。

## G3. 硫化物体系缺失〔数据〕

- sit.dat 无 Pyrite/FeS/FeS₂ 相（只有 FeS₂O₃⁺ 溶解配合物、FeSe₂），H₂S+Fe²⁺→FeS↓
  （湿法脱硫下游）不可演。补 PHASES 数据即可（S²⁻/HS⁻ 物种 sit.dat 已有），无引擎问题。
  低优先级：等具体场景需要再补。

## G4. Fe 氯配合物浓盐酸形态未直接验证〔测试补遗〕

- MnO₂+浓盐酸测试顺带实证了 Mn 以 MnCl₃⁻/MnCl₄²⁻ 等配合物留液（游离 Mn²⁺ <1e-5）；
  Fe 同族（FeCl₄⁻、FeCl₂⁺ 等 sit.dat 有数据）但未直接断言过。半分钟一条测试可锁死，
  尚未补。

## G5. 高温/强离子强度外推静默〔引擎〕（六锅已归档，此处汇总）

- 150°C：-analytic 项外推无警告，化学方向未专家审（锅5）；
- >6 molal：SIT 活度模型有效域外静默外推，收敛但结果置信度未知（锅6）；
- 调用侧防御：远离这两个区间的场景无需处理；mod 侧将来可加"有效域提示"UI。
