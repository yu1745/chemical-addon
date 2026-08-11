# 12 · 可实现性评估（Create 架构版）

> 本文件回答「能不能实现」：机制→载体的可表达性、Create 生态对接证据、两大自研核心的成熟先例、风险清单、否决清单。

## 1. 结论

**全部 10 种机制可表达、可实现。** 自研核心收敛为两块：
1. **组合系统**（数据驱动、可组合、可扩展）——架构直接借鉴匠魂 JSON 修饰器系统（已验证其四层架构：JSON 定义/注册表懒解析/条目轻量存储/hook 行为分发，见 00-ecosystem-recon §7 与 03 §5）；
2. **釜内反应模拟**（进度/中间态/温度/压力/催化）——容器结构参考匠魂 SmelteryTank（`List<FluidStack>` 多流体共存，已证实直接实现 Forge IFluidHandler），状态演化算法为常规游戏数值设计。

其余全部是成熟模式：Create 配方管线（ProcessingRecipe）、热级（HeatCondition/BoilerHeater）、动能（KineticBlock/BlockStressValues）、物流（IItemHandler/传送带）、电网（createaddition BaseElectricBlockEntity）、多方块（ConnectivityHandler 罐式成型）。

## 2. 机制 → 可实现性映射

| 机制 | 实现载体 | 先例/证据 | 风险 |
|------|---------|-----------|------|
| M1 温度 | 釜内流股温度 + HeatCondition 门槛 | Create HeatLevel/HeatCondition 现成；BoilerHeater 注册表可挂自研热源 | 低 |
| M2 压力 | 釜内压力字段 + 密封等级 + 泄压/爆炸 | 无先例，但为标量字段+事件表 | 中（调平） |
| M3 相态 | 物种沸点/熔点查表 + 釜内相变 | 查表规则 | 低 |
| M4 浓度 | 组合系统（溶解度 hook 饱和截断） | 匠魂修饰器合并器语义（Max/All）同构 | 低 |
| M5 反应引擎 | chemical_reaction 配方（ProcessingRecipe 派生）+ 釜内模拟 | Create ProcessingRecipe 全管线现成；KubeJS 可脚本化 | 中（执行端自研） |
| M6 沉淀 | 浆料流体固含量 + 沉降行为 | 浆料=高粘流体（流体属性） | 低 |
| M7 分离 | 过滤/离心/蒸馏行为 | 产物=item 走 Create 物流 | 低 |
| M8 换热 | 换热器块（两流股温度交换） | 简单数值 | 低 |
| M9 管网 | Create 管道全复用 | 现成（单流体/网约束天然适配"每种组合独立物种"） | 低 |
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

### 4.1 组合系统（借鉴匠魂修饰器架构）

| 匠魂机制（证据类/行号） | 化学对应 | 可行性 |
|------------------------|---------|--------|
| `SimpleJsonResourceReloadListener` 加载 `tinkering/modifiers/*.json`（ModifierManager.java:46,71,120） | `data/<mod>/chemistry/species/*.json` | 现成 API 模式 |
| `ModifierId extends ResourceLocation`（ModifierId.java:14） | SpeciesId | 现成 |
| 条目 = `(id, level)` NBT 列表（ModifierNBT.java:139-163） | `(speciesId, amount)` 列表 | 现成模式 |
| `ModuleHook<T>` id/filter/merger/default + `register()`（ModifierHooks.java:433-440） | SOLUBILITY/REACTIVITY/PHASE/DANGER hooks | 现成模式 |
| 合并器 All/First/Any/Max/Compose（各 hook 内嵌 record） | 浓度加和/饱和截断/最严危险 | 一行配置 |
| datagen `AbstractModifierProvider`（library/data/tinkering/） | species datagen | 现成模式 |

**结论：组合系统是「换皮」的匠魂架构，无新发明。** 唯一自研：化学语义 hook 定义（溶解度/反应性）与饱和截断规则。

### 4.2 釜内反应模拟

- 容器：`List<FluidStack>` 多流体共存（SmelteryTank.java:17-27 已证）+ 温度/压力/进度字段；
- 配方匹配：白名单 chemical_reaction，低频 tick（10 tick）扫描；
- 进度/中间态/转化率上限/ΔH：纯数值演算；
- 性能：容器数量级 = 玩家工厂数（几十~几百），每容器 10 tick 一次轻量扫描，远低于世界流体模拟开销；
- 存档：容器状态（物种列表+温度+压力+进度）整体 NBT 序列化（10-multiblock §7）。

**结论：釜内模拟是常规 BE tick 逻辑 + 数值表，无不可实现项。**

## 5. 风险与缓解

| 风险 | 等级 | 缓解 |
|------|------|------|
| Create 6.0.8 API 锁定（不追新版本） | 低 | 锁定依赖版本，参考源码在本地 |
| 61 流体贴图/模型工作量 | 中 | 程序化流体纹理（颜色+透明度+相态模板） |
| 组合系统 JSON 加载与 hook 注册复杂度 | 中 | 完全照匠魂模式；先做最小集（溶解度/危险） |
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
| 流股管道网络（v1 方案） | 全量流体注册后失去必要性；热物料就近加工是设计约束 |
| 每物种×每状态注册（冷/热/浓/稀全独立流体） | 组合爆炸；状态由釜内流股表达 |
| 世界流体格子模拟 | 化学语义是容器内均质相；格子模型慢且反直觉 |

## 7. 实现顺序建议（与 11-content-scope 里程碑一致）

M0 地基（流体注册+组合系统数据层+Create 工程）→ M1 釜体模板 → M2 分离件 → M3 电解 → M4 索尔维（验收）→ M5 高压 → M6 铝线 → M7 零排放。

**每个里程碑独立可玩、可测，风险前置（模板与组合系统最先验证）。**
