# 12 · 可实现性评估（Create 架构版）

> 文档状态：**current**（2026-08 整理：两大自研核心改为「离子基底 mixture + 釜内反应/规则引擎」）
> 本文件回答「能不能实现」：机制→载体的可表达性、Create 生态对接证据、两大自研核心的成熟先例、风险清单、否决清单。

## 1. 结论

**全部 10 种机制可表达、可实现。** 自研核心收敛为两块：
1. **离子基底单一 `mixture`**（离子多重集 + 水 + 分子溶质 + Suspended，电中性硬不变量）——单一注册流体承载任意浓度；物种退化为「命名组成模式」（species JSON 数据包，匠魂修饰器式加载），只用于匹配/名字/颜色/桶配比（见 03 §2–§4）；
2. **釜内反应模拟**（白名单配方 + 规则引擎涌现化学）——容器结构参考匠魂 SmelteryTank（`List<FluidStack>` 多流体共存，已证实直接实现 Forge IFluidHandler）；白名单走 ProcessingRecipe 管线，涌现（沉淀/中和/结晶）走规则引擎直改离子集（06 §9）；运行模式 BATCH/连续流（13-flow-modes）。

其余全部是成熟模式：Create 配方管线（ProcessingRecipe）、热级（HeatCondition/BoilerHeater）、动能（KineticBlock/BlockStressValues）、物流（IItemHandler/传送带）、电网（createaddition BaseElectricBlockEntity）、多方块（ConnectivityHandler 罐式成型）。

## 2. 机制 → 可实现性映射

| 机制 | 实现载体 | 先例/证据 | 风险 |
|------|---------|-----------|------|
| M1 温度 | 釜内流股温度 + HeatCondition 门槛 | Create HeatLevel/HeatCondition 现成；BoilerHeater 注册表可挂自研热源 | 低 |
| M2 压力 | 釜内压力字段 + 密封等级 + 泄压/爆炸 | 无先例，但为标量字段+事件表 | 中（调平） |
| M3 相态 | 物种沸点/熔点查表 + 釜内相变 | 查表规则 | 低 |
| M4 浓度 | 连续浓度（离子单位/水单位）+ 溶解度查表 | 03 §5：浓度由 mixture 组成推导，无独立字段 | 低 |
| M5 反应引擎 | chemical_reaction 配方（ProcessingRecipe 派生）+ 釜内模拟（BATCH/连续流，见 13） | Create ProcessingRecipe 全管线现成；KubeJS 可脚本化 | 中（执行端自研） |
| M6 沉淀 | Suspended 悬浮固相域 + 沉降行为 | 浆料=mixture 第三域（03 §12 已定） | 低 |
| M7 分离 | 过滤/离心/蒸馏行为 | 产物=item 走 Create 物流 | 低 |
| M8 换热 | 换热器块（两流股温度交换） | 简单数值 | 低 |
| M9 管网 | Create 管道全复用 | 现成（单流体/网约束天然适配单一 mixture + 纯流体） | 低 |
| M10 事故 | 事件表 + Create 混液炸管 + 爆炸 API | 现成 + 事件表 | 中（平衡） |

## 3. Create 对接点（源码证据，见 00-ecosystem-recon §2–3）

- 配方：`ProcessingRecipe`/`BasinRecipe` 派生 → 自定义 RecipeType（Mixing 同构）；
- 热级：`HeatCondition`（HEATED/SUPERHEATED）→ 配方门槛；`BlazeBurnerBlock.HeatLevel` 枚举；燃料=物品 tag（零代码）；
- 热源扩展：`BoilerHeater.REGISTRY` 注册自研高温加热器；
- 多方块：`ConnectivityHandler.formMulti`（罐式成型模式，无框架）——釜体模板可复用同模式；
- 动能：`KineticBlock`/`KineticBlockEntity` + `BlockStressValues.IMPACTS/CAPACITIES/RPM`；
- 物流：`IItemHandler` capability（漏斗/溜槽/机械臂自动接入）+ `DirectBeltInputBehaviour`（传送带）;
- 电网：createaddition `BaseElectricBlockEntity`（耗电机器基类）+ `IWireNode`（接线网络）；
- 数据：`CreateRegistrate`（datagen/blockstate/模型）;
- 装置：`ContraptionType` + `BlockMovementChecks`（回转窑用 Mechanical Bearing）。

## 4. 两大自研核心的可行性论证

### 4.1 离子基底 mixture + 物种=模式（数据驱动，借鉴匠魂修饰器加载架构）

| 匠魂机制（证据类/行号） | 化学对应（v2） | 可行性 |
|------------------------|---------|--------|
| `SimpleJsonResourceReloadListener` 加载 `tinkering/modifiers/*.json`（ModifierManager.java:46,71,120） | `data/<mod>/chemistry/species/*.json`（数据包可覆盖，/reload 生效） | 现成 API 模式 |
| `ModifierId extends ResourceLocation`（ModifierId.java:14） | SpeciesId | 现成 |
| 条目 = `(id, level)` NBT 列表（ModifierNBT.java:139-163） | mixture NBT 双域 `{Molecules + Ions}` + `Suspended`（03 §2.1/§12） | 现成模式 |
| `ModuleHook<T>` id/filter/merger/default + `register()`（ModifierHooks.java:433-440） | species JSON 字段（ions/ksp/solubility/miscibilityGroup/…）+ 规则引擎流水线 | 现成模式 |
| 合并器 All/First/Any/Max/Compose（各 hook 内嵌 record） | 规则引擎求解序（crystallise→neutralise→precipitate）+ 电中性硬校验 | 一行配置 |
| datagen `AbstractModifierProvider`（library/data/tinkering/） | `tools/gen_species.py` 单一数据源 → 注册/纹理/语言 | 已实现（M0） |

**结论：数据驱动层是「换皮」的匠魂架构，无新发明。** 唯一自研：离子基底存储（电中性硬不变量）、物种=模式展开/折算（S3a–S3c）与规则引擎化学语义。

### 4.2 釜内反应模拟

- 容器：`List<FluidStack>` 多流体共存（SmelteryTank.java:17-27 已证）+ 温度/压力/进度字段；
- 配方匹配：白名单 chemical_reaction + 规则引擎（涌现化学），低频 tick（10 tick）扫描；运行模式 BATCH/连续流（见 13-flow-modes）；
- 进度/中间态/转化率上限/ΔH：纯数值演算；
- 性能：容器数量级 = 玩家工厂数（几十~几百），每容器 10 tick 一次轻量扫描，远低于世界流体模拟开销；
- 存档：容器状态（物种列表+温度+压力+进度）整体 NBT 序列化（10-multiblock §7）。

**结论：釜内模拟是常规 BE tick 逻辑 + 数值表，无不可实现项。**

## 5. 风险与缓解

| 风险 | 等级 | 缓解 |
|------|------|------|
| Create 6.0.8 API 锁定（不追新版本） | 低 | 锁定依赖版本，参考源码在本地 |
| 38 流体贴图/模型工作量（v2 后溶液别名不重复注册） | 中 | 程序化流体纹理（颜色+透明度+相态模板） |
| species JSON schema 与规则引擎复杂度 | 中 | 完全照匠魂加载模式；先做最小集（溶解度/析出） |
| 釜内模拟性能 | 低-中 | 低频结算 + 同区块调度器合并（10-multiblock §7） |
| 气体流体视觉（管道内像液体） | 低 | 半透明+粒子纹理；可接受 |
| 事故平衡（爆炸范围） | 中 | 配置化，默认温和；FTB Chunks 保护白拿 |
| KubeJS 对自定义 RecipeType 支持深度 | 低 | datagen+JEI 先行，脚本化是红利 |
| 与 TFMG/柴油发电机并存 | 低 | 无机主线无重叠；流体前缀隔离 |

## 6. 否决清单（维持）

| 方案 | 否决理由 |
|------|---------|
| 任意混合即反应 | 组合爆炸、不可预测（保持白名单+禁忌组合安全网） |
| 真实热力学/动力学连续模拟 | 性能与复杂度失控；游戏要可预测的配方 |
| 流股管道网络（v1 方案） | 溶液=单一 mixture 后失去必要性；热物料就近加工是设计约束 |
| 每物种×每状态注册（冷/热/浓/稀全独立流体） | 组合爆炸；v2 更进一步：浓/稀连物种都不是（03 §10） |
| 世界流体格子模拟 | 化学语义是容器内均质相；格子模型慢且反直觉 |

## 7. 实现顺序建议（与 11-content-scope 里程碑一致）

已完成：M0 地基 → M1 釜体模板+反应引擎 → M2 分离件 → M2.5 可玩性改造 → v2 子系统（规则引擎/离子基底/D18，见 docs/progress.md）。

**接下来（M3+）**：电解/氯碱 → 索尔维（含 13-flow-modes F2/F3 连续流）→ 高压合成氨 → 零排放；每阶段独立可玩、可测，风险前置（数据层与模板最先验证）。
