# 00-ecosystem-recon · 社区生态盘点：什么已经写好了

> 文档状态：**reference**（盘点快照 2026-09；结论已吸收进 README D11–D17，正文不随 v2 演进）

> 依据：本地源码盘点（create-forge_1.20.1 = Create 6.0.8 / createaddition-forge_1.20.1 = 1.3.3 / create-tfmg-forge_1.20.1 / create-diesel-generators-forge_1.20.1）+ Modrinth API 数据（1714 个 Create 相关项目）+ forge1 生产服 mods 目录实测。
> 结论先行：**平台锁定 Create Forge 6.0.8（forge1 已原生安装）**；液体/配方/热/物流/动能大量现成可用；**气体与多组分流股必须自研**；石油化工赛道社区已占据，我们主打**无机化工**。

---

## 1. 生产服现状（硬证据）

`forge1.20.1/mods/` 实测包含：

| jar | 版本 |
|-----|------|
| `create-1.20.1-6.0.8.jar` | Create Forge 6.0.8 |
| `createaddition-1.20.1-1.3.3.jar` | Create Crafts & Additions 1.3.3 |
| `ftbchunks-create-support-forge-1.0.0.jar` | FTB Chunks × Create 联动（已有！） |

**含义**：化学附属作为 Create Forge 附属，在 forge1 上原生运行、零 Connector 风险；Create 与创想附加的能力直接可用，不必考虑 Fabric 移植版。

## 2. Create 6.0.8 能力盘点（源码证据）

| 能力 | 现状 | 证据（路径为 `src/main/java/com/simibubi/create/...`） |
|------|------|------|
| 多方块流体罐 | **现成**：控制器驱动成型，无框架，最大 3×3 底、高 32，容量按块计；单流体 | `content/fluids/tank/FluidTankBlockEntity.java:45,538` + `ConnectivityHandler.formMulti` |
| 被动锅炉/罐体加热 | **现成且可扩展**：`BoilerHeater.REGISTRY` 注册自定义热源（Blaze Burner SEETHING→2、FADING→1，tag `create:passive_boiler_heaters`） | `content/fluids/tank/BoilerData.java:383` + `BoilerHeaters.java:26` |
| 流体管道/泵/阀 | **现成**：Forge FluidStack 网络，机械泵压力=f(转速)；**同一网络单流体，混液炸管** | `content/fluids/pipes/`、`content/fluids/PipeConnection.java:344`、`FluidReactions.java:27` |
| 气体系统 | **不存在**：6.0.8 无 Gas/GasStack 类；蒸汽是锅炉虚拟状态不可传输 | 全库 grep "Gas" 仅注释；`content/fluids/VirtualFluid.java` |
| 热级 | **现成**：HeatLevel NONE/SMOULDERING/FADING/KINDLED/SEETHING；配方门槛 HeatCondition NONE/HEATED/SUPERHEATED；燃料=物品 tag（`create:blaze_burner_fuel/regular\|special`） | `content/processing/burner/BlazeBurnerBlock.java:300`、`processing/recipe/HeatCondition.java:11` |
| 搅拌+加热反应 | **现成**：Basin（釜）+ Mechanical Mixer（搅拌）+ HeatCondition（加热），MixingRecipe/BasinRecipe/ProcessingRecipe 全管线（item+fluid 输入、概率副产、processingTime） | `content/processing/basin/BasinRecipe.java:37`、`content/kinetics/mixer/MixingRecipe.java:7` |
| 动能 addon 扩展 | **现成**：KineticBlock/KineticBlockEntity + `BlockStressValues.IMPACTS/CAPACITIES/RPM` 公共注册表 + Registrate 模式 | `api/stress/BlockStressValues.java:14`、`foundation/data/CreateRegistrate.java` |
| 物品物流 | **现成**：漏斗/溜槽走 `IItemHandler` capability（自研方块暴露它即被接管）；传送带 `DirectBeltInputBehaviour`；机械臂 `ArmInteractionPointType` 注册表 | `foundation/blockEntity/behaviour/inventory/InvManipulationBehaviour.java:61`、`kinetics/belt/behaviour/DirectBeltInputBehaviour.java:36` |
| 控制 | **现成**：转速控制器、红石链接、阈值开关、智能观察器、显示屏、序列装配 | `content/kinetics/speedController/`、`content/redstone/` |
| 动态装置 | **现成**：ContraptionType + BlockMovementChecks + MovementBehaviour 官方 API；**回转窑 = Mechanical Bearing 装配筒体**（Create 无现成窑） | `api/contraption/ContraptionType.java:14`、`content/contraptions/bearing/MechanicalBearingBlock.java` |
| 风扇处理 | 四型内置（鼓风/鬼魂/烟熏/泼溅），`FanProcessingTypeRegistry` 可扩展 | `content/kinetics/fan/AirCurrent.java:43` |
| 灌装/排出 | Spout/Drain + `BlockSpoutingBehaviour` 注册表（自研方块可挂） | `api/behaviour/spouting/BlockSpoutingBehaviour.java:26` |
| 流体量具 | **6.0.8 无**（只有转速/应力表） | `content/kinetics/gauge/` |
| 注册/数据生成 | CreateRegistrate（connectedTextures、blockModel、BuilderTransformers） | `foundation/data/CreateRegistrate.java` |

## 3. Create Crafts & Additions 1.3.3 能力盘点（源码证据）

| 能力 | 说明 |
|------|------|
| 电动马达 | FE→旋转，转速可调 ±256rpm，Forge Energy API（`ForgeCapabilities.ENERGY`），输入上限 5000 FE/t |
| 交流发电机 | 旋转→FE，效率 75% |
| 线缆配电网 | 接线柱（小 1k/大 5k FE/t）+ 铜/金/电钢线 + 中继器 + **模块化电池仓**（多方块 3×3×5，2M FE/块，可接 Create 阈值开关）+ 创意能源；网络 1 tick 缓冲 |
| 液体烈焰人燃烧室 | 4000 mB 液罐 + 液体燃料配方，注册为 Create **BoilerHeater（SEETHING=2）**——液体燃料直接供热 |
| 特斯拉线圈 | 皮带上给物品充 FE（`ChargingRecipe` 体系）——"耗电处理物品"的现成配方模板 |
| 轧机 | 动能处理机模板：DirectBeltInputBehaviour + 自定义 RecipeType + 转速决定处理速度 |
| **BaseElectricBlockEntity** | 耗电机器基类（邻居缓存、每面输入/输出开关、容量/速率抽象），**化学机器的电力侧直接套** |
| 协议 | MIT，可自由借鉴 |

## 4. 社区主要附属盘点（Modrinth，1.20.1，按下载量）

| 附属 | 下载 | 加载器 | 定位 | 与本计划的关系 |
|------|------|--------|------|----------------|
| Create: Steam 'n' Rails | 1168 万 | 多 | 铁路+蒸汽 | 无关（交通） |
| Create Crafts & Additions | 952 万 | 多 | FE 桥 | **已装**，见 §3 |
| Create: New Age | 489 万 | 多 | 电力+核电 | 竞争项（我们选创想） |
| Create: Diesel Generators | 505 万 | forge | 柴油机+**原油精馏** | **赛道重叠（有机）** |
| Create: The Factory Must Grow (TFMG) | 161 万 | forge | 重工业+石油：**蒸馏塔多方块**（DistillationController）、钢储罐、泵抽机、工业高炉、发动机、Acid/Gas/Hot 流体类型 | **最接近的邻居，必读参考** |
| Create: Liquid Fuel | 147 万 | forge | 液体燃料喂 Blaze Burner | 参考：我们的化学燃料可借其思路 |
| KubeJS Create | 168 万 | 多 | 脚本化配方 | **红利**：配方走 ProcessingRecipe 系则自动支持脚本 |
| Create: High Pressure | 92 万 | 多 | 高压+钻石工厂 | 参考其"压力"包装 |
| Create: Broken Bad ReBroken | 15 万 | fabric | 制毒化学（实验性） | 参考其化学反应玩法包装，实现浅 |
| Create: Power Loader / Ore Excavation / Ultimine | — | — | 区块加载/采矿 | 无关 |

**核心判断**：
1. **有机石油化工（原油→精馏→柴油/燃料）已被 Diesel Generators 与 TFMG 占据**，且有 500 万+ 下载验证。化学附属**不做有机主线**，聚焦**无机化工**（酸碱盐、氯碱、合成氨、索尔维、拜耳法、废料处理），与它们互补而非竞争；如需跨接（如我们的氢气/CO 可作它们的燃料），留联动点。
2. TFMG 的蒸馏塔（控制器+输出块多方块模式）与钢储罐是**最佳结构参考**（同构于我们的釜体/塔设计），源码已在本地。
3. 化学反应玩法包装参考 Broken Bad（小规模），但其实现深度不足，我们走 ProcessingRecipe 系的正规军路线。

## 5. 复用边界决策（生态盘点后新增，更新 README 决策表）

| 编号 | 决策 | 理由 |
|------|------|------|
| D11 | **平台 = Create Forge 6.0.8**（forge1 原生，含 createaddition 1.3.3） | 生产服已装，零部署风险 |
| ~~D12~~ | ~~**全量流体注册 + 组合系统**~~（已被 D19 离子基底取代，见 README） | 61 种物种**全部注册为 Forge Fluid**（气体=负密度流体），运输/储存层全走 Create 管道/罐/泵/喷头；多组分溶液（氨盐水=水+NaCl+NH₃）用**组合系统**表达（借鉴匠魂 JSON 修饰器机制：JSON 定义、组分条目、hook 行为分发、datagen）；**只有进入反应釜才切换自研流股**（反应进度/中间态/温度/压力），釜口⇄Forge Fluid 自动转换 | Create 管道单流体+混液炸管不影响（每种组合都是独立流体物种）；匠魂 SmelteryTank（List<FluidStack> 多流体共存）作釜内容器参考 |
| D13 | **反应配方 = ProcessingRecipe 派生**（自定义 RecipeType `chemical_reaction`），釜体 = 我们的多方块处理机（Basin 的放大版：大容量、多口、密封/压力） | 白拿 Create 配方管线/JEI/datagen/KubeJS-Create 脚本化/ponder；玩家可脚本加反应 |
| D14 | **热需求直接用 HeatCondition**（HEATED/SUPERHEATED）+ 我们的扩展热源（注册进 BoilerHeater.REGISTRY 或自定义）：Blaze Burner/Blaze Cake/液体烈焰人（创想）都当热源；H4/H5 高温热源（电热/化学燃料燃烧）自制 | Create 热源玩家已有认知，教学成本低 |
| D15 | **不做石油/精馏/柴油主线**（TFMG/DG 已做），主攻无机化工；蒸馏塔保留（用于氨水/稀酸分离，无机场景）但结构参考 TFMG 模式 | 差异化，避免重复劳动 |
| D16 | **电力统一走 createaddition 电网（Forge Energy）**：电解槽/电热器耗 FE（基类 BaseElectricBlockEntity），搅拌/泵/压滤走旋转 | 现成电网+电池仓+特斯拉线圈；不引 New Age |
| D17 | **事故机制借 Create 原生行为**：混液炸管 = 禁忌混合的物理表现（设计为"可预期的事故"）；超压爆炸自研 | 少写一套事故渲染 |

## 6. 自研边界（生态盘点后重估）

| 自研内容 | 说明 | 估方块/类量 |
|---------|------|------------|
| 釜体（多方块处理机） | Basin 放大版：3×3 底×高可变，密封/压力等级，多输入输出口，控制器方块 | ~4 方块 + 1 控制器 |
| 流股储罐/罐体 | 多组分/气体/热流股存储（视觉同 Create 罐） | ~3 |
| 气体管 + 气体泵 + 气体阀 | Create 无气体系统，自建（透明管风格同 Create） | ~6 |
| 压缩机 | 旋转驱动，气体增压/液化（压力上限给釜） | 1–2 |
| 换热器 | 双管交叉换热块 | 1 |
| 电解槽 | Forge Energy 耗电 + 电极视觉 | 1–2 |
| 过滤机/压滤机 | 旋转驱动，浆料→清液+滤饼 item | 1–2 |
| 沉淀池逻辑 | 复用罐体 + 沉降行为 | 0–1 |
| 蒸馏塔板 | 竖直堆叠塔板（参考 TFMG 蒸馏塔模式） | 2–3 |
| 冷却塔 | Create Fan 抽风 + 喷淋 | 1–2 |
| 尾气净化塔 | 塔板 + 喷淋 + 引风 | 2 |
| ~~控制面板~~（已取消） | 配方选择/参数显示（釜的 GUI；已按世界内基调取消，见 AGENTS.md） | 0 |
| 仪表（温度/压力/浓度） | Create 无流体量具，自制显示方块/叠加到面板 | 2 |
| 催化剂托盘 | 釜内件 | 1 |
| 化学燃料（氢气/CO/水煤气）烧 Blaze Burner | 物品 tag `create:blaze_burner_fuel/*`（**零代码**） | 0 |

**合计自研方块约 25–30 种**（快照时点数量；v2 加入节拍控制件 S11–S15 后调整为 ~35–39，见 04-machines）；配方/反应引擎复用 ProcessingRecipe 管线后，反应相关代码量减半。

## 7. 交互设计基调调研（2026-10，源码证据）

> 结论先行：**Create 生态的共识是「世界内交互优先，GUI 弱化」**——状态用贴面仪表/物理件/护目镜 HUD 展示，参数用世界内滚动值或物理装置控制，GUI 只做物品槽位、物流配置与创建期选型。本模组 AGENTS.md「设计基调」一节据此立为铁律。

### 7.1 本地源码统计（直接 grep，可靠）

| 模组 | 容器 GUI 类 | 状态展示（世界内） | 参数控制 |
|------|------------|-------------------|---------|
| **Create 本体** | ~40 个，全部为物品/工具管理（Toolbox/Blueprint/Filter/Schematicannon）、物流配置（RedstoneLink/DisplayLink/SequencedGearshift/ThresholdSwitch）、列车时刻表 | 贴面 Gauge（转速表/应力表 `content/kinetics/gauge/`）、护目镜 HUD（`IHaveGoggleInformation` + `GoggleOverlayRenderer`）、Display Board/指示灯 | `ValueSettingsBehaviour` 世界内浮层拖动调值（`foundation/blockEntity/behaviour/ValueSettings*`）+ 物理装置（阀轮/挡板/热源） |
| **createaddition** | **0 个** | EnergyMeterBlock 贴面能量表（`blocks/energy_meter/`）、RedstoneRelay | `ScrollValueBehaviour` 世界内滚动设转速（ElectricMotorBlockEntity） |
| **TFMG**（化工多方块，最接近我们） | 2 个（EngineController 引擎集中管理、ElectriciansWrench 配置）——蒸馏塔/精炼塔等机器**无 GUI** | VoltMeterBlock 贴面电压表、SegmentedDisplay 分段显示屏、Multimeter 万用表 HUD overlay | Potentiometer 电位器方块（世界内旋钮） |
| **匠魂**（对照，重 GUI 风格） | 重 GUI（冶炼炉/合金炉/熔化炉），但液位也提供世界内 GaugeBlock（`smeltery/block/RenderingGaugeBlock`） | — | — |

### 7.2 社区附属源码调研（shallow 子代理，GitHub 直读）

| 模组 | 容器 GUI | 交互方式 |
|------|---------|---------|
| Steam 'n' Rails | 基本为零 | 信号灯用物理臂杆表达红/绿状态；GUI 仅创建期选型（转向架 BogeyMenu）、列车员工具箱/指令轮盘 |
| Create: New Age（核电） | **0 个** | 反应堆状态 = 方块状态 + 物理件（插拔燃料棒、搭建散热通风控制温度） |
| Create: Broken Bad（化工） | **0 个** | 化学处理走 Create 原生配方机器；新增方块也是贴世界交互件（铃/托盘） |
| Create Big Cannons | 炮体本身 0 GUI | 炮架仰角用 Create ValueSettings（世界滚动+右键）；**例外**：引信精确数值（延迟秒数/近炸阈值）用 ScrollInput 滑条（无物理表达手段）、弹匣为纯物品栏 |
| Create: Estrogen | **0 个** | 离心机等纯世界内配方处理 |

### 7.3 例外集（生态中仅此三类，均有明确功能理由）

1. **无物理表达手段的精确数值**（Big Cannons 引信秒数/阈值）→ 允许 ScrollInput 滑条。
2. **物品存取/槽位**（弹匣、列车员工具箱）→ Create 风格槽位交互。
3. **创建期一次性选型**（转向架）→ 非运行时状态，允许配置界面。

### 7.4 对本模组的落点

- S02 温度计/S03 压力表/S04 浓度计 = 贴面仪表（Create Gauge 模式：贴面连接结构、读内部状态、红石输出/阈值）。
- 釜状态信息同步 = `IHaveGoggleInformation` 护目镜 HUD（Create 标准通道，实现该接口即自动获得）。
- 阈值/时序类参数 = `ValueSettingsBehaviour`/`ScrollValueBehaviour` 世界内滚动调值。
- 温度/容量等连续物理量 = 物理装置表达（热源数量与燃烧等级、压缩机/冷却管道）。
- 反应匹配失败 = 状态渲染（仪表/指示灯/粒子）表达「为什么没反应」。

## 8. 待验证（spike，不阻塞计划）

1. SmartFluidTank 单流体假设（未细读，基本可断定）。
2. 我们的釜体要"密封/压力"概念，Basin 无此概念——需确认釜体自研后还能否复用 ProcessingRecipe 执行逻辑（可：ProcessingRecipe 只是配方描述，执行端我们自写）。
3. KubeJS-Create 对自定义 RecipeType 的脚本支持程度。
4. TFMG 蒸馏塔的多方块成型/输出块模式细节（源码在本地，设计阶段细读）。
5. ponder 场景（教程）是否值得做（工作量）。
