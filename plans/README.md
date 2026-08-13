# 化学附属（Chemical Addon）计划书

> **状态：规划中，未实现，未写任何代码。**
> 本目录是完整设计计划书，先回答「玩家玩什么 / 做什么 / 内容多少 / 机制多少 / 是否可实现」，再谈实现。
> **平台已定：Create Forge 6.0.8 原生附属**（forge1 生产服已装 Create 6.0.8 + createaddition 1.3.3），见 [00-platform-decision.md](00-platform-decision.md)。

---

## 0. 五个核心问题的直接回答

| # | 问题 | 答案（详见文件） |
|---|------|------------------|
| 1 | 玩家要玩什么？ | 采矿选料 → 搭建多方块化工厂 → 铺设管线 → 调温调压跑反应 → **把副产物和废料回收成闭环**。成就感来源是「一条零排放的完整产线」和「规模化的化学帝国」。[01-core-design.md](01-core-design.md) |
| 2 | 要做哪些东西？ | 基于 Create 生态：**釜体等约 25–30 种自研方块** + 52 条化学反应 + 61 种化学物质 + 5 种新矿物。罐/管道/泵/搅拌/热源/传送带/电网大量复用 Create 与创想附加。[00-ecosystem-recon.md](00-ecosystem-recon.md)、[04-machines.md](04-machines.md) |
| 3 | 游戏内容有多少？ | **61 种化学物质、52 条化学反应、5 种新矿物、自研方块约 25–30 种、5 个阶段（T0–T4）**。单人主流程约 40–60 小时。[08-substance-catalog.md](08-substance-catalog.md)、[07-reaction-catalog.md](07-reaction-catalog.md)、[11-content-scope.md](11-content-scope.md) |
| 4 | 要构建多少种机制？ | **10 种**：温度传热、压力密封、相态相变、浓度溶解、反应引擎、沉淀悬浮、固液分离、换热回收、管网输运、危险事故。在 [05-mechanics.md](05-mechanics.md) 逐一拆解。 |
| 5 | 能否实现？ | **全部机制都可表达**，前提是：61 种物种全部注册为 Forge Fluid（气体=负密度流体），运输/储存层 100% 复用 Create；多组分溶液用组合系统（借鉴匠魂 JSON 修饰器机制，数据驱动）；**只有进入反应釜才切换自研流股**（反应进度/中间态/温度/压力/催化）；反应采用白名单配方（ProcessingRecipe 派生）。[03-substance-model.md](03-substance-model.md)、[12-feasibility.md](12-feasibility.md) |

---

## 1. 核心设计决策（D1–D17）

| 编号 | 决策 | 一句话 |
|------|------|--------|
| D1 | **物质模型** | 61 种物种全部注册为 Forge Fluid（气体=负密度流体）；多组分溶液用**组合系统**（匠魂修饰器式 JSON 组合）；**流股只存在于反应釜内**（进度/中间态/温度/压力/浓度/催化） |
| D2 | **反应制** | 反应为**白名单配方**（约 52 条），玩家通过控制面板指定反应；**禁忌组合**（如浓硫酸遇水）触发事故（含 Create 混液炸管） |
| D3 | **温度/压力** | 连续数值（°C / kPa）+ 设备密封等级上限；不做真实气体定律模拟，压力用简化累积规则 |
| D4 | **相态** | 由物种属性（沸点/熔点）+ 温度 + 压力推导，允许气液固共存于同一容器 |
| D5 | **多方块** | 先搭外壳后激活；结构件材质×内衬决定耐温/耐压/隔热/耐腐蚀；尺寸按 Create 罐式（最大 3×3 底）与塔式堆叠 |
| D6 | **机器分工** | **化学反应只发生在多方块机器（釜体等）里**；单方块只做辅助（泵/阀/仪表/控制） |
| D7 | **规模** | 61 物种 / 52 反应 / 自研方块约 25–30 / 10 机制 / 5 阶段。再大则维护成本失控 |
| D8 | **旗舰流程** | 索尔维制纯碱闭环（盐+石灰石 → 纯碱，氨与 CO₂ 循环复用）是 T2 的里程碑与教学关卡 |
| D9 | **平台** | **Create Forge 6.0.8 原生附属**（forge1 已装）；IC2 降级为可选联动，见 [00-platform-decision.md](00-platform-decision.md) |
| D10 | **性能** | 管网按「图网络」每 tick 结算，反应低频结算（1–5 秒一次），不逐格模拟 |
| D11 | **复用边界** | 罐/管/泵/搅拌/热源/物流/电网/配方管线直接复用 Create+创想；气体与多组分流股自研，见 [00-ecosystem-recon.md](00-ecosystem-recon.md) |
| D12 | **全量流体注册** | 61 种物种**全部注册为 Forge Fluid**（气体=负密度流体），走 Create 管道/罐/泵；多组分溶液用**组合系统**（借鉴匠魂 JSON 修饰器机制，JSON 数据驱动、可组合、可扩展）；**流股只存在于反应釜内**（反应进度/中间态/温度/压力/催化） |
| D13 | **配方管线** | 反应 = ProcessingRecipe 派生（自定义 RecipeType），白拿 JEI/datagen/KubeJS 脚本化/ponder |
| D14 | **热级对齐** | 热需求用 HeatCondition（HEATED/SUPERHEATED）+ 扩展热源注册 BoilerHeater；Blaze Burner/液体烈焰人直接可用 |
| D15 | **无机主线** | 不做石油/精馏/柴油（TFMG/Diesel Generators 已占据），主攻酸碱盐/氯碱/合成氨/索尔维/拜耳法 |
| D16 | **电力** | 统一走 createaddition Forge Energy 电网（电解槽/电热器耗 FE，搅拌/泵/压滤走旋转） |
| D17 | **事故机制** | Create 混液炸管等原生行为直接用作禁忌混合的事故表现，少写一套事故渲染 |
| D18 | **互溶性（Miscibility）** | 液-液能否混溶由「互溶组（miscibilityGroup，数据驱动）」决定：同组互溶 → 合并成单一 `mixture`；跨组不互溶 → 分相分层、抽出时按密度**先后**抽出（非混合）。互溶性决定能否共线输送（不互溶共管=混液炸管），并给 M7 增添「倾析/分相」分离手段。详见 [03-substance-model.md §5.4](03-substance-model.md) |

> D1–D10 为初版设计；D11–D17 为 2026-09 社区生态盘点后新增/修订，完整论证见 [00-ecosystem-recon.md](00-ecosystem-recon.md)；D18 为 2026-08 新增（互溶性概念）。详细可行性论证见 [12-feasibility.md](12-feasibility.md)。

---

## 2. 文件索引

| 文件 | 内容 |
|------|------|
| [00-ecosystem-recon.md](00-ecosystem-recon.md) | **社区生态盘点**：Create/创想/主要附属源码盘点与 Modrinth 数据，复用边界 D11–D17 |
| [00-platform-decision.md](00-platform-decision.md) | 平台决策（已定：Create Forge 6.0.8 原生附属） |
| [01-core-design.md](01-core-design.md) | 定位、设计目标、玩家玩法循环、成就感支柱、反设计清单 |
| [02-progression.md](02-progression.md) | 进度线 T0–T4：每阶段新机器/新机制/里程碑/卡点 |
| [03-substance-model.md](03-substance-model.md) | **物质抽象模型**：全量 Forge Fluid 注册、组合系统（匠魂修饰器式）、釜内流股边界 |
| [04-machines.md](04-machines.md) | 机器与方块全清单与规格（自研部分 + 复用部分） |
| [05-mechanics.md](05-mechanics.md) | 10 种机制的完整定义、玩家交互、边界与简化 |
| [06-reaction-system.md](06-reaction-system.md) | 反应引擎设计：配方结构、条件窗口、平衡简化、放热吸热、催化、禁忌组合 |
| [07-reaction-catalog.md](07-reaction-catalog.md) | 52 条化学反应全目录（8 个模块，含条件/放热/机器/阶段） |
| [08-substance-catalog.md](08-substance-catalog.md) | 61 种物质全目录（气体/液体/固体/热媒/矿物，含危险属性） |
| [09-resources-worldgen.md](09-resources-worldgen.md) | 原材料与资源链：5 种新矿物、世界生成、与 Create/原版复用 |
| [10-multiblock.md](10-multiblock.md) | 多方块设计：成型、尺寸、材质分级、端口、状态可视化、故障 |
| [11-content-scope.md](11-content-scope.md) | 内容量统计、里程碑拆分、工作量估算、风险清单 |
| [12-feasibility.md](12-feasibility.md) | 可实现性评估：机制→模型映射表、否决清单、性能/存档/同步考量 |

---

## 3. 开放决策（需要拍板，不阻塞阅读）

1. **项目形态**：独立仓库（Forge mod，Kotlin 可选）已倾向；计划书暂存 `ic2-fabric/plans/chemical-addon/`，定稿后随仓库迁移。
2. **与 IC2 的联动**：跨加载器成本高，默认不联动；若要做（如 EU→FE 桥）单独立项。
3. **温度单位**：游戏内显示 °C（直觉）还是 K（化学感）；内部建议整数 °C。
4. ~~电解铝 F4 是否做~~ **已定（c）**：首版不做熔融电解；氧化铝为铝线终点（T4 耐火结构件/陶瓷建材），F4 配方表预留后续版本（见 07 反应目录 F4 行、02 进度线 T3）。
5. **手册**：用 Create ponder 场景还是独立手册/仅 JEI 展示（KubeJS 脚本化能力已白拿）。
6. **难度取向**：事故惩罚强度（爆炸破坏 vs 仅损失物料）影响整体体验基调。

---

## 4. 术语表

| 术语 | 含义 |
|------|------|
| 流股（Stream） | 设备/管道中流动的物质数据对象：`{物种, 量, 温度, 压力, 浓度, 固含量}` |
| 物种（Species） | 一种化学物质的注册表条目（H₂O、NaCl、H₂SO₄……），定义沸点/溶解度/危险等属性 |
| 相态（Phase） | 气 / 液 / 固 / 溶液 / 浆料，由状态推导 |
| 配方制 | 反应必须先在反应釜控制面板「登记配方」才能发生 |
| 禁忌组合 | 未登记但物理上危险的两物质并存（浓硫酸+水），触发事故（含 Create 混液炸管） |
| 密封等级 | 常压 / 密封 / 高压密封，决定容器能承受的压力上限 |
| 热媒/冷媒 | 只负责运热/运冷的循环介质（导热油、冷盐水、氨制冷循环） |
| 热级 HeatLevel | Create 原生热级：NONE/SMOULDERING/FADING/KINDLED/SEETHING；配方门槛用 HeatCondition（HEATED/SUPERHEATED） |
| 互溶 / 不互溶（Miscibility） | 两种液体能否混成一杯均相（液-液）。互溶→合并成单一 `mixture` 流体；不互溶→分相分层、按密度先后抽出。由互溶组（miscibilityGroup）数据驱动判定，见 [03 §5.4](03-substance-model.md) |
| 分相 / 倾析（Decantation） | 不互溶相在罐内按密度分层；底口抽重相、顶口撇轻相即得液-液分离（M7 的液-液分离手段） |
| 互溶组（miscibilityGroup） | 物种 JSON 字段，标注液体所属相族（aqueous/nonpolar/mercury…）；同组互溶，跨组不互溶 |
