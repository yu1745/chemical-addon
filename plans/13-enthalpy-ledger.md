# 13 — 焓记账（U19）：分数化之后的下一步

> 状态：**计划**（U18 定点分数已落地，本单元是其直接后继）。
> 前置：U13 规则引擎 v2（equilibria 统一条目）、U16 反应热能量记账（J/unit 账本）、
> U18 定点分数（量子网格 10⁷/mB）。

## 1. 动机：退休最后两条热护栏

引擎里现在有两条与热相关的**尺度护栏**（`Solution.java`），它们的存在不是因为化学，
而是因为**平衡移动没有热记账**——同一个弱电解质条目在被强酸环境"倒转"时，
引擎无法区分"真平衡"与"账没记"，只能一刀切冻结：

| 护栏 | 位置 | 行为 | 为什么要退休 |
|------|------|------|--------------|
| **强酸冻结** | `equilibrate()` 中 `hasTerm(eq.right(), H) && bulkAcid()` → `continue` | H⁺ 宏观（> feed×`STRONG_ION_FRACTION`≈pH 4）时，产酸条目（如 NH₄⁺ 水解）整体不参与松弛 | 真实体系中水解照样进行（只是被 Le Chatelier 压到痕量）；冻结把它变成了全有/全无的开关。`STRONG_ION_FRACTION` 本身也要删 |
| **体相中和阈值** | `neutralise()`/`neutraliseDirect` 的 both-bulk 守卫 + `driveWeakElectrolytes` 的 bulkAcid 守卫 | 只有 H⁺/OH⁻ 都"宏观"时才允许绕过逐对配对直接体相中和 | 它防止的是弱电解质自产 H⁺/OH⁻ 被"免费"中和、每轮泵出 `NEUTRALISATION_J_PER_PAIR` 的假热。焓记账之后，热随每一次 `applyMove` 走，这个守卫失去存在理由 |

退休后的正确行为：**所有条目始终参与平衡**；任何方向、任何耦合的移动都按 ΔH 记热；
Le Chatelier 效应由 U19 的温度耦合（van't Hoff，可选）或至少由热账本的 ΔT 自然表达。

## 2. 机制：每条目 ΔH，记在每一次移动上

`Equilibrium` 已解析并保留 `delta_h`（kJ/mol，`Equilibrium.java` 的 `deltaH` 字段，
现 v1 未用）。U19 启用它：

1. **记账点下沉到 `applyMove`**：`applyMove(eq, m)` 是所有平衡移动的唯一入口
   （松弛 `solveEntry`、协同移动 `coupleDeficits`、中和 `neutralise` 全走它）。
   在这里累加 `energyJ += m × deltaH(eq) × J_PER_EVENT`（符号随 m），
   就不存在"哪条路径漏了记账"的问题——强酸冻结当年防的"静默质子化不记账"
   从结构上消失。
   - 中和的 `NEUTRALISATION_J_PER_PAIR` 改为水条目 `H2O = H+1 + OH-1` 的 delta_h
     （Kw 条目已有，补 ΔH≈+57.3 kJ/mol 即正向离解吸热、中和放热），
     删掉 `Chemistry.NEUTRALISATION_J_PER_PAIR` 特例。
2. **配方层不改**：红氧/热解/电化学仍是配方层（plans/03 §8.1 边界），
   配方的 `deltaHeat` 走 U16 既有通道，两边在 `energyJ` 汇合。
3. **数据表**：给全部既有 equilibria 条目补 `delta_h`（真实值，
   来源与 log_k 同口径：手册 ΔH° / ΔHf 表）。强酸强碱中和、弱酸弱碱电离、
   沉淀（≈0 或小）、络合（逐级）分别覆盖。缺省 NaN = 0 J（无热效应），
   引擎不因缺数据卡住。
4. **（可选，验收后评估）van't Hoff**：`effectiveLogK` 已是所有亲和度计算的
   单点，加 `logK(T) = logK(T0) − ΔH/(2.303R) × (1/T − 1/T0)` 即可让温度
   真正移动平衡（结晶冷却曲线从"查表"变"涌现"）。先做记账、后做耦合，
   两步分开验收。

## 3. 验收（DoD）

- JUnit：删除 `STRONG_ION_FRACTION`/`bulkAcid`/`neutraliseDirect` 守卫后，
  既有 66 用例 + 新增用例全绿：
  - 强酸中 NH₄⁺ 水解条目参与计算且被压到痕量（不是开关式消失）；
  - 滴定/缓冲、Zn/Ag 掩蔽、malachite 50/0、能量账本 rise 用例数值不变
    （或按新账本更新后更准）。
- GameTest 102 全绿；EnergyLedger 相关用例的 ΔT 与新账本一致。
- 性能：`applyMove` 每次一次乘加，不新增循环；跑分不明显回退。
- 文档：`Chemistry`/`Solution` 头注释更新；plans/03 §8 引用本文件。

## 4. 风险

- **数据量**：~30 条 equilibria 要查 ΔH；缺值默认 0 会把放热/吸热抹平，
  先覆盖教学主线（索尔维七条 + 三酸两碱 + 水解/络合），其余渐进。
- **双重记账**：`applyMove` 记账后，`neutralise()` 里的特例必须同 PR 删除，
  否则热翻倍（测试会立刻暴露——这是把账本做成单入口的意义）。
- **van't Hoff 与溶解度曲线并存**：溶解度曲线是查表结晶（U14 动力学），
  van't Hoff 只作用于 equilibria 条目，两者作用域不重叠，无需合并。
