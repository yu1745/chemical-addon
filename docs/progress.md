# 开发进度

> 最后更新：2026-08（M0–M2.5 完成，规则引擎 v1 + v2 离子基底 S1–S3g + D18 互溶性分相 + D18.5 分液口/软管 + S02 温度计（双形态）+ **U1 容器状态层**（多相加热/放热全体/压力模型/S03 压力表/datagen 修复）+ S02/S03 动态量程与 itemstack 指针 + **U3 模板抽取**（vessel/ 结构层基类/沉淀池去拷贝/控制器拆类 573 行），77/77 GameTest 通过）
> 里程碑定义见 `plans/11-content-scope.md`；设计计划书主索引 `plans/README.md`。

## 状态总览

| 里程碑 | 内容 | 状态 |
|--------|------|------|
| M0 | 工程接入 + 61 物种 + 组合系统骨架 | ✅ 完成 |
| M1 | 釜体模板 + 反应引擎 + 首条产线（硫磺→稀硫酸） | ✅ 完成 |
| M2 | 过滤机 + 沉淀池 + 釜高度参数化 | ✅ 完成 |
| M2.5 | 釜可玩性改造：世界内交互基调落地（护目镜 HUD/诊断/槽位 GUI/成型反馈） | ✅ 完成 |
| U1 容器状态层 | 多相加热 / 放热全体 / 压力模型 / S03 压力表 | ✅ 完成（M3 首单元） |
| U3 模板抽取 | vessel/ 结构层基类 / 沉淀池去拷贝 / 控制器拆类 / 内部件 allowlist | ✅ 完成 |
| U13 规则引擎 v2 | equilibria 质量作用求解器 + 物品投料 + 蒸发浓缩 + speciation | ✅ 完成（插队） |
| U14 引擎测试层 | JUnit 剥离 MC + 10⁴ 细网格 + 动力学 + 21 物种 | ✅ 完成（插队） |
| U15 晶粒投种混合固体 | 晶粒 1/16 + 投种 + mixed_residue 整坨取出 + 苦卤盐曲线 | ✅ 完成（插队） |
| U16 反应热能量记账 | J/unit 账本 + ΔT=Q/(feedUnits×c) + 蒸发潜热 + deltaHeat 质量耦合 | ✅ 完成（插队） |
| U16.5 湿饼夹带与洗涤 | 残液率夹带 + residue 母液相 + 再浆/置换洗涤 + S18 电导率计 | ✅ 完成（插队） |
| U17 分析化学层 | Kw 读数层 + S16 pH/S04 波美/S17 浊度 + 试纸族 + SI 降级 + M08 终点结晶器 | ✅ 完成（插队） |
| M3+ 其余 | FE 接线 / 竖窑 / 索尔维 / 连续流 / 高压 / 零排放 | ⏳ 未开始（顺序见 plans/11 §2.1） |

**自动化测试**：`./gradlew runGameTestServer` → **115/115 必测通过**（+1 optional 悬置）；`./gradlew test` → JUnit 全绿（含内核策展/性能哨兵）。

## P6 · 化学权威全量切换（2026-08-20，用户决策：旧引擎从未正常工作，直接切新引擎）

**IPhreeqc 内核成为唯一化学权威，U13 RulesEngine 退役出运行时**（类保留：物流常量仍是生产依赖；GameTest 直调作为退役回归锁）。

- **假绿事件（务必记住）**：P2–P5 提交信息里的「108 全绿」是假的——`ParityGameTests` 缺 `@GameTestHolder(MODID)` 注解，13 个整合链测试被命名空间过滤器**静默排除**（实际只跑了 102 个）；补注解后 11/13 失败，暴露四层从未验证过的 bug。教训：新增 GameTest 类必须带 holder 注解；提交信息里的绿灯数要与日志里的 `tests are now running` 核对。
- **修复的四层真 bug**（均为整合链路首次真跑暴露）：① 进料产 H/O 元素总量（PHREEQC 非法输入→不收敛）；② KINETICS 脚本 punch 时序错（SELECTED_OUTPUT 定义在首模拟后，不回溯 i_soln → 池 delta 恒 0）；③ 纯水进料 -totals 空列表非法；④ WriteBack 双计+标度混乱（分子域电解质不清除→与离子域双计、S/N 换算用离子质量→逐拍膨胀）。
- **单位桥终诺（单一真相）**：**1 unit = 1e-7 g 水 = 1e-7 mol 离子/物种 formula unit**（即 1 mB = 1e-3 mol，`EngineBridge.UNITS_PER_MOL = 1e7`）；legacy 浓度比（离子 units/水 units）恰为 millimolal——旧读数/配方浓度/写回往返全部精确自洽。进料：H/O 永不作元素输入（pH charge 涌现）；伪池（OCl→Hyp/SO3→Sul/NO3→Nitra/NO2→Nitri）优先于元素归并。写回（增量迁移）：已迁移电解质物种从分子域清除、无法存储的物种（含 H/O 组分=酸类）保留、存储离子用 dominant-ion 近似（S→SO4-2、N→NH4+1、C→HCO3-1）、电荷兑底 H+1/OH-1、幂等（二次进料不膨胀）。
- **性能回归与修复**：切换初版每拍 `IPhreeqc.create()`+重装载 460KB sit.dat（158ms/拍）+ 失败测试烧满超时 → GameTest 5–10 分钟；修：`Kernel` 共享会话（9ms/拍，JVM 一次装库）+ 正确性收敛（全绿测试提前 succeed）。现状 **83 秒/115 测试**。
- **主循环**（`ReactorControllerBlockEntity.stepKernelChemistry`，反应釜与 M08 共用）：PressureFeed（气相分压→interface 反应）→ TickDriver（游戏 0.5s/拍→KINETICS，温度贯通）→ WriteBack（增量迁移）→ EngineReadings.publish（pH 表计共享快照，零额外求解）。EngineArchive 无条件写档（DUMP↔NBT）。
- **已知缺口（内核路径未覆盖，待后续恢复）**：① 开口蒸发浓缩/冷凝回收（M08 终点闭环，crystallizermultiblock 测试 required=false 悬置）；② 投料溶解/投种；③ 沉淀悬浮域（固相写回待相映射）；④ 护目镜 speciation 化验行；⑤ 反应热记账。旧行为参考 RulesEngine（退役锁测试仍在验证其自身语义）。
- 顺手修正：pH 滴定/吸收/产酸/耗酸四测试改引擎真值断言（旧断言是 legacy 虚构数值）；`ChemEngineConfig` 开关类删除（迁移期结束）。

## 已完成明细

### M0 · 地基
- Forge 47.4.0 / Create 6.0.8-289 / parchment 2023.06.26 / Java 17 / CreateRegistrate 工程
- 38 种流体（13 气体=负密度流体 + 24 液体溶液 + 导热油）+ 18 种固体 item + 创造标签
- 组合系统骨架：`data/chemicaladdon/chemistry/species/*.json` 数据包加载（匠魂修饰器式），盐水/氨盐水组合示例
- `tools/gen_species.py` 单一数据源 → 注册代码/纹理/语言文件

### M1 · 釜体模板与反应引擎
- **釜体模板**：化工砖 + 控制器多方块，3×3 底 × 高 3–6（容量随内部方块数 1 桶/方块，原 16 桶/方块——后改为 1 桶/方块，使倒几桶即见液面升降），成型校验（4 面尝试、内部空心、拆砖失效）
- **多流体罐** ReactorTank：匠魂式多流体共存，`IFluidHandler`（Create 管道直连），存储归一化为 source 实例（配方匹配可靠）
- **物品 IO**：4 槽缓冲 + `ITEM_HANDLER`
- **加热**：Blaze Burner 釜底下方（KINDLED 500°C / SEETHING 900°C），温度松弛演化，放热反应升温
- **反应引擎**：`chemicaladdon:chemical_reaction`（ProcessingRecipe 派生 + `deltaHeat`），10 tick 自动匹配白名单配方 → 进度累积 → 结算（消耗/产出/温升）
- **首条产线**：`S+O₂→SO₂`（需加热）、`SO₂+H₂O→稀硫酸`
- **控制面板 GUI**：结构/温度/内容/物品/反应进度

### M2 · 分离件
- **过滤机**（单方块）：`chemicaladdon:filtering` RecipeType + 共享 FilteringLogic（输入罐→滤液罐+滤饼 item）
- **沉淀池**（池式模板实例）：3×3 开放池，1/4 速慢速沉降
- 配方：重碱浆/石膏浆 → 水 + 滤饼

### M2.5 · 反应釜可玩性改造（世界内交互优先，GUI 弱化）

- **设计基调落地**：交互哲学写入 AGENTS.md（世界内交互优先、GUI 弱化，生态证据见 plans/archive/00-ecosystem-recon.md §7（冻结）：Create 本体 ~40 GUI 类全为配置/物品用途、createaddition 0 GUI、TFMG 仅 2 配置 GUI、New Age/Broken Bad/Estrogen 0 GUI）
- **护目镜 HUD**：釜实现 `IHaveGoggleInformation`——戴护目镜看控制器显示：温度+热级（无/加热/超级加热）、诊断状态、多流体内容、物品、反应进度（Create 标准通道）
- **诊断状态**：`ReactorStatus` 枚举——未成型/反应中/温度不满足/输出已满/无匹配配方（每 10 tick 自动判定，原因可诊断）
- **成型反馈**：`tryAssemble()` 返回结构化结果（面+问题类型+坐标），失败 chat 报具体缺砖位置；成功音效+粒子
- **GUI 取消（彻底）**：釜无任何 GUI——物品渲染进釜内（Create Basin 模式：环形悬浮+旋转+堆叠散落，控制器 Renderer 实现），存取走漏斗/管道；右键改为世界内 chat 诊断；ReactorMenu/Screen/AllMenuTypes 删除
- **釜升级 SmartBlockEntity**（Create 基类：行为系统、自动序列化 write/read、CachedRenderBB）——后续仪表/ValueSettings 直接挂 behaviour
- **开口/闭口变体**：成型时顶面全封=闭口、全空=开口（内部物品从上方可见）、半封=报错；控制器 blockstate `open` 属性+纹理变体（开口金边）
- **修复真 bug**：釜容量不持久化（高釜重进世界回退 16 桶）→ tankCapacity 入 NBT
- 测试 7/7 → **11/11**（新增：容量序列化往返、诊断状态 NO_RECIPE/TEMPERATURE、开口成型、半封顶拒绝）
- 环境：`~/.gradle/gradle.properties`（用户级，不进 git）覆盖 Linux JDK 路径 + 代理 192.168.5.138:7777；build.gradle 补 maven.minecraftforge.net 仓库（ForgeAutoRenamingTool）

### 规则引擎 / 涌现化学 v1（plans/03 §8）

- **物种 schema 扩展**（`composition/Species.java`）：新增 `ions`（电解质解离离子组成）、`ksp`（溶度积，沉淀候选）、`solubility`（溶解度曲线 g/100g×°C，线性插值）、`solute`/`concentration`（冷却析出目标 + 固定浓度）、`miscibilityGroup`、`phaseTransition`。离子是**内部求解量**（`composition/Ion.java`），不注册成物种/流体/物品。
- **求解器**（`composition/Solution.java`）：瞬态快照，每 tick 重建，流水线 `crystallise`（过饱和析出）→ `dissociate`（解离）→ `neutralise`（H⁺+OH⁻→H₂O，放热 +50°C/1000mB）→ `precipitate`（难溶物按 Ksp 升序析出）→ `recombine`（剩余离子贪心重组为溶液物种，**优先还原原物种**以保稀/浓酸身份）。
- **编排器**（`reactor/RulesEngine.java`）+ `ReactorTank.setContents`：每反应 tick 在**白名单配方之前**跑（`tickReaction`），读取全部 stack 的分子物种 → 求解 → 写回 + 沉淀物出 item（1 item/1000mB）；组成无变化时跳过（惰性流体零开销）。
- **化学数据**：补 16 个物种 JSON（6 酸 / 3 碱 / 5 盐溶液 / 2 沉淀物），brine 补离子；数据包可覆盖。
- **GameTest +4**：`rulesEngineNeutralisesAcidAndBase`（HCl+NaOH→brine+H₂O，升温）、`rulesEnginePrecipitatesLimestone`（CaCl₂+Na₂CO₃→CaCO₃↓+brine，无白名单配方）、`rulesEngineCrystallisesOnCooling`（热 NH₄NO₃ 溶液冷却析出）、`reactorRunsEmergentChemistry`（釜 tick 集成验证）。
- **已知限制（v1）**：离子重组为贪心 + 固定优先级（非完整平衡求解）；Ksp 仅用于析出排序（未做离子积门槛）；「饱和母液」近似为水；浓/稀酸同离子签名靠「优先原物种」保身份；复分解为有限离子集（H/OH/Na/Ca/Cl/SO4/CO3/NO3/NH4）。

### 混合度（MixDegree）删除（v2 物质模型先行项）

- **判定**：MixDegree 是「容器局部瞬态」被错误建模成「流体属性」——物理上不存在「还有 40% 没混匀」的可抽流体。
- **删除**：`Mixture` 的 MixDegree 键/方法、`ReactorTank` 的传输冻结标志 + 加权合并、`tickMixture/mixRate/MIX_TICK/NATURAL_MIX_RATE`、色带渲染 `renderMixtureSurface`、护目镜 mix% 显示、4 个 GameTest。
- **保留内核**：搅拌→反应速率下沉为釜局部 `stirring` 字段（未来接 Create 机械混合器）；分层语义交给 D18 互溶性。
- 决策见 [plans/03-substance-model.md §5.1](../../plans/03-substance-model.md)。

### S1 · 离子基底存储（v2 物质模型，plans/03 §11）

- **`Mixture` key 空间重构**：`Ratios`（物种 id → parts）→ **`Molecules`（分子物种）+ `Ions`（离子，电中性）** 两个域；联合比例空间，`deriveAmounts`（分子）/ `deriveIonAmounts`（离子）共享一次精确分配，分子+离子绝对量恒等于总量。
- **电中性硬校验**：`Mixture.setIons` 拒绝 `Σ(电荷×量)≠0` 的离子集（`Ion.chargeOf` 从规范 id 解析电荷）；违反即 `LOGGER.error` 并拒绝写入。
- **`IonColors`**（新建）：离子色表占位（主线全无色）；`blendColor` 对分子/离子双域加权混色。
- **GameTest +2**：`mixtureRejectsNonNeutralIons`（非中性拒绝）、`mixtureWithIonsDerivesAndTransfers`（联合派生 + 传输保真）。
- 生产端（`collapseIfNeeded`/`RulesEngine`）仍写分子域，离子域留待 S2 切换；现有 32 测试行为不变。

### S2 · 生产端切离子（v2 物质模型，plans/03 §11）

- **Species 加 `solventRatio`**（水 parts / 公式单位，= 浓度数据化）；`isSolution()` 判定溶液物种。浓/稀靠不同水比区分（浓酸 3、稀酸 20）。
- **`Solution` 删 `dissociate`/`recombine`**：持久态就是离子集，求解器直改 `{离子 + 分子}`（crystallise/neutralise/precipitate）。
- **`collapseIfNeeded` 展开**：溶液物种（含单 stack）进釜即展开成「溶质离子（整数公式单位，严格电中性）+ 溶剂水」，余数全给水（避免整数分配破坏 2:1 配比）。
- **`RulesEngine` 直改离子集**：先 settle 到单 mixture，读 `deriveAmounts`+`deriveIonAmounts`，求解，`setContents` 双域写回。
- **数据**：15 个溶液物种 JSON 补 `solventRatio`。
- **GameTest +1 改 6**：新增 `collapseExpandsSolutionToIons`；rules engine 测试改为直接构造离子 mixture；旧 mixture 测试改用 thermal_oil（非溶液物种）。
- **修复的 bug**：Solution 构造参数遮蔽字段（ions 恒空）、collapseIfNeeded size≤1 不展开、整数分配破坏电中性。

### S3a · 配方层离子感知（v2 物质模型，plans/03 §11）

- **`Species` 加公共展开/折算**：`expand(amount, molecules, ions)`（公共展开入口）、`formulaUnitMb()`（1 公式单位 mB = Σcount + solventRatio）、`equivalentFromIons(ions)`（离子集 → 溶液物种当量 mB）。
- **`countIngredient` 离子感知**：溶液物种 ingredient 能「看到」釜里的溶解离子（折算成当量 mB）。
- **`drainIngredient` 离子感知**：消耗溶液物种 = 移除整数公式单位的离子 + 溶剂水（保持电中性），用 `Mixture.create` 重建重盖章。
- **`completeRecipe` 产出离子感知**：配方产出溶液物种时直接展开成离子 mixture（不再先 fill 溶液流体再 collapse 展开）。
- **GameTest +1**：`ingredientMatchesDissolvedIons`（稀硫酸 ingredient 能匹配/消耗 100 H⁺ + 50 SO₄²⁻ 离子）。

### S3b · 配方层引用溶液模式（v2 物质模型，plans/03 §11）

- **`SolutionIngredient`**（新）：配方引用溶液物种（`"species"` + `"amount"`），不查流体注册表，匹配釜里的溶解离子。
- **`ChemicalReactionRecipe`** 加 `solutions` 字段（JSON `"solutions"` 数组，readAdditional/writeAdditional 解析）。
- **`ReactorTank.countSolution`/`drainSolution`**：按物种 id 折算/消耗离子集（复用 S3a 的离子感知，直接版本）。
- **引擎接入**：`matchesIngredients` 检查 solutions、`completeRecipe` 消耗 solutions。
- **`solutionOutputs` 产出**：`ChemicalReactionRecipe` 加 `solutionOutputs` 字段，`completeRecipe` 直接展开成离子+水（不再先 fill 溶液流体）；测试配方 `concentrate_sulfuric_acid`（稀 2300 → 浓 600 + 水 1700）。
- **测试配方** `neutralize_sulfuric_acid`（solutions 输入浓硫酸）+ **GameTest** `reactorConsumesSolutionIngredient`（端到端消耗验证）、`reactorProducesSolutionIngredient`（端到端产出验证）。
- **溶剂水感知折算**：`Species.equivalentFromIons(ions, water)` / `formulaUnitsAvailable(ions, water)` 新增——溶液当量不仅受离子数限制，还受溶剂水（`water/solventRatio`）限制。这修复了「浓/稀同离子签名、两个配方同时匹配」的歧义（`concentrate` 与 `neutralize` 不再争抢同一离子汤），也顺带堵住 `drainSolution` 无水上限导致的过度抽取。
- 这是「23 个溶液流体迁为别名」的前置：配方现在可以不依赖流体注册表而引用溶液模式。

### S3c · 连续浓度 + Suspended 悬浮固相域（v2 物质模型，plans/03 §4.1/§5/§12）

- **连续浓度（去浓/稀二值）**：浓度 = 釜内**运行时**「离子单位 / 水单位」，无量纲连续值；不再是 `solventRatio` 身份字段。
  - `Species.isSolution()` = 液相电解质（`phase==LIQUID && isElectrolyte()`，沉淀物/气体天然排除）；`ionCount()`（1 公式单位离子当量）、`formulaUnits(ions)`、`equivalentIonMb(ions)`（溶质侧 mB，无水上限）、`expand(ionAmount, concentration, …)`（按目标浓度打包离子+水）、`defaultConcentration()`（桶默认配比）。
  - `SolutionIngredient` 加**连续浓度区间** `minConcentration`/`maxConcentration`（`concentration` 单值 = 输出打包浓度）；`amount` 按**溶质（离子）侧**计量。
  - `ReactorTank.countSolution`（离子 mB）/ `concentrationOf`（连续浓度）/ `drainSolution`（只耗溶质离子、水作溶剂不动）；`matchesIngredients` 对 solutions 做区间判定。
- **浓/稀合并单签名**：`dilute/concentrated_{sulfuric,hydrochloric,nitric}_acid`（6 个 JSON）→ 单模式 `sulfuric_acid`/`hydrochloric_acid`/`nitric_acid`；配方用浓度区间区分（`neutralize` 用 `minConcentration 0.5`=浓，`so2_absorption` 产出 `concentration 0.15`=稀）。
- **配方迁移**：`so2_absorption` 产出改 `solutionOutputs`（稀酸）；新增 `absorb_sulfur_trioxide`（SO₃+水→浓酸）；删 `concentrate_sulfuric_acid`（「浓缩=物理除水」非反应，不做）。
- **`Suspended` 第三域（浆料=选项 1）**：mixture NBT 加 `Suspended`（固体物种 id → 份），`deriveSuspendedAmounts`/`getSuspended`/`setSuspended`/三域混色（`SolidColors` 新建，gen_species.py 从 SOLIDS 生成）；`collapseIfNeeded`/`setContents` 携带该域。
- **沉淀先入釜内悬浮**：`RulesEngine.apply` 不再直接吐 item——沉淀/析晶写入 `Suspended` 域（釜内成浆料）；过滤机在 S3d 接上「抽 Suspended 吐固体」。
- **GameTest**：+1（`mixtureWithSuspendedDerivesAndTransfers`），改 5（`solutionExpandsAtConcentration`、`solutionMatchingIsConcentrationAware`、`reactorConsumes/ProducesSolutionIngredient`、`reactorAbsorbsSulfurDioxide`、沉淀/析晶测试改为断言 Suspended 域）。
- **修复**：Create ProcessingRecipe 要求 `results` 数组——`solutionOutputs` 配方须带 `"results": []`。

### S3d · 溶液/浆料流体注销 + 过滤机抽 Suspended（v2 物质模型，plans/03 §4.1）

- **23 个溶液/浆料流体注销**：`gen_species.py` FLUIDS 表从 38 收窄到 **15 纯流体**（水 + 13 气体 + 导热油）；酸/碱/盐/氨水/brine/浆料降级为「模式」（species JSON 保留，不注册流体、无桶 item）。
- **过滤机/沉淀池抽 Suspended**：`ReactorTank.extractSuspended(sink, mbPerItem)` 移除悬浮固相→吐固体 item（1 item/1000mB，min 1）；`FilteringLogic.tick` 先走**通用悬浮过滤**（浆料→固体+液体，液体过到输出罐，`collapseIfNeeded` 把单组分余液降级为纯流体），无悬浮才回落到 FILTERING 配方。
- **配方迁移**：删 `gypsum_slurry`/`sodium_bicarbonate_slurry` 过滤配方（浆料不再是流体）；`filterPressFiltersSlurry` 测试改为「mixture 带 Suspended → 过滤机出 cake + 水」。
- **清死代码**：`emitItem`、`expandSolution`/`expandSingleSolution`（溶液流体展开）、`collapseIfNeeded` 溶液分支——注销后无溶液流体，全部移除。
- **AGENTS.md 核心架构段同步改写**：全量流体注册/组合系统/釜内流股 → 离子基底单一混合物 / 物种=模式 / 沉淀入悬浮。

### S3e · 创造栏打包混合液桶（进 JEI）

- **`SolutionBucketItem`**（新）：`FluidHandlerItemStack` 容器，`getDefaultInstance()` 预填 1000 mB `Mixture`（物种离子签名 + 默认浓度水，`Species.packBucket`），可倒进釜/罐；溶液不是注册流体，标准桶装不了，故用打包混合液桶。
- **12 个溶液模式各一个桶**（sulfuric/hydrochloric/nitric_acid、brine、烧碱/纯碱/氯化铵/氯化钙液、氨水、石灰乳、硫酸铵/硝酸铵液），`AllContainers.SOLUTION_BUCKETS` 注册，进创造栏 + JEI；模型/zh_cn 由 gen_species.py `SOLUTIONS` 表生成。
- **GameTest +1**：`solutionBucketPacksMixture`（硫酸桶默认实例预填 H⁺/SO₄²⁻ + 水，1000 mB）。

### S3f · 浆料物种化（`suspended` 字段）+ 创造栏修复

- **species schema 加 `suspended`**（`Species.SuspendedComponent`）：浆料模式 = 水 + 悬浮固相（`"suspended": [{"species":"…","count":1}]`），`isSlurry()` 与 `isSolution()` 互斥；`packBucket` 对浆料打包「悬浮固体 + 水」，对溶液打包「离子 + 水」。
- **`milk_of_lime`（石灰乳）由溶液改浆料**：删 `ions`，改 `suspended: slaked_lime`——倒进釜是一杯悬浮浆料，过滤机抽 Suspended → 熟石灰 item。
- **重新引入 3 个浆料模式**：`gypsum_slurry`（悬浮石膏）、`sodium_bicarbonate_slurry`（悬浮重碱）、`calcium_sulfite_slurry`（悬浮亚硫酸钙，SOLIDS 补 `calcium_sulfite` 固体）；各配打包浆料桶。
- **创造栏预填修复**：`AllCreativeModeTabs` 从 `output.accept(item)`（= 无 NBT 的 `new ItemStack`）改为 `output.accept(item.getDefaultInstance())`——否则溶液/浆料桶出栏是空桶、倒不进釜。
- **GameTest +1**：`slurryBucketPacksSuspendedSolid`（石灰乳桶预填悬浮熟石灰 + 水，无溶解离子）。11 溶液 + 4 浆料桶，共 15 个打包混合液桶。

### S3g · 固相二分：混悬（Suspended）vs 沉底（Sediment）+ 渲染

- **固相拆两域**：`Mixture` NBT 加第四域 `Sediment`（沉底固相，与 `Suspended` 混悬并列）；`KEY_SEDIMENT`/`get/set/deriveSedimentAmounts`/`deriveJoint`/`create`（5 参）全链路四域。`Sediment` **不进流体 tint**（独立成层），`Suspended` 仍进 tint（浑浊贡献）。
- **归属按生成规则**（不靠 JSON 手标）：`Solution` 单一 `precipitates` 拆成 `suspended()`（`precipitate()` Ksp 快速双置换 → 混悬）+ `sediment()`（`crystallise()` 溶解度降温 → 沉底）。
- **`RulesEngine`** 读/写两个固相域（`beforeSuspended`/`beforeSediment`，`setContents` 5 参）；`ReactorTank` 的 `setContents`/`collapseIfNeeded`/`mergeGroup`/`extractSuspended` 全程携带 `Sediment`；**`drainIngredient`/`drainSolution` 重建时保留全部四域**（顺带修掉既有的「消耗组分丢 Suspended」隐性丢料）。
- **渲染**：沉底层 = 脱色纹理（`mixture_still`）× 固相色 tint 的底部盒子（`renderTintedBox`，高度 ∝ 沉积量，非纯色平涂）；混悬 = 流体 tint 换成悬浮固相色并强制不透明（CaCO₃ → 不透明白）；化验 HUD 补「混悬/沉底」两行明细。
- **颜色修正**：无色离子/纯水 tint 从 `0xFFFFFFFF`（不透明白）→ `IonColors.CLEAR_TINT = 0x48FFFFFF`（淡白 ~28%，后由 `0x28FFFFFF` 上调）——纯水与 CaCO₃ 混悬白可分辨，且液面仍可见（非全透明）。
- **GameTest**：析晶断言迁 `deriveSedimentAmounts`；沉淀测试补「快速沉淀不沉底」；`solventWaterContributesNoColor` 断言改 `CLEAR_TINT`。

### 溶解度量纲约定（降温结晶，替代 S2 固定浓度）

- **问题**：现实溶解度 g/100g 是**质量比**，引擎算的是「分子式单元/水」，两者量纲不一致；旧 `crystallise` 用写死的 `concentration` 字段（g/100g）判析出，与「浓/稀 = 运行时连续浓度」的基调冲突。
- **约定（声明性，不引摩尔质量）**：`1 mB 水 ≡ 1 g`、`1 分子式单元 ≡ 1 g`（忽略摩尔质量）；`Solution.SOLUBILITY_SCALE = 1.0` 是唯一全局缩放旋钮。**阈值 = `gPer100g / 100 × SOLUBILITY_SCALE`（分子式单元 / 水 mB）**。
- **`crystallise()`** 改用运行时连续浓度 `分子式单元 / 水`（`maxFormable(s) / water`，不再离子单位、不读 JSON 固定浓度）；`Species` 删 `concentration` 字段/解析/`isCrystallisable` 要求；`ammonium_nitrate_solution.json` 删 `"concentration": 400`。
- **默认桶不饱和**：溶液桶 `solventRatio=10` → NH₄NO₃ 仅 0.1 分子式单元/水 ≈ 10 g/100g，远低于 0°C 溶解度 118 g/100g，故**默认桶永远不饱和、降温不结晶**；触发结晶需配方高 `targetConcentration` 输出（或未来蒸发/溶解固体）。这是连续浓度的自然结果，非量纲 bug。
- **GameTest**：`rulesEngineCrystallisesOnCooling` 改用 2.5 分子式单元/水的浓溶液（100°C 阈值 8.71 不析、20°C 阈值 1.92 析出 500 mB 硝酸铵）。

### 渲染 · 溶剂无色 + 水复用原版（去重复）

- **水不再自注册**：`chemicaladdon:water` 流体/桶/纹理/`FluidColors` 条目整体移除——原版已有 `minecraft:water`，重复注册正是「两个水桶、两种水色」的根源（D12 全量注册的遗留）。`Solution.WATER` 现指向 `minecraft:water`，进釜即溶剂；`so2_absorption`/`absorb_sulfur_trioxide`/`neutralize_sulfuric_acid` 3 个配方与 `brine.json` 的 water 引用同步改。
- **溶剂无色**：`Mixture.blendColor` 排除溶剂（`Mixture.SOLVENT` = `minecraft:water`）——溶剂贡献**零**颜色（分子溶质 CO₂/SO₂/NH₃/HCl 仍照常带色），传导到釜/打包桶/JEI 全部 mixture 渲染。
- **纯水在釜内渲无色**：原版水纹理是蓝的，`ReactorControllerRenderer.renderBox` 对水（溶剂）换用中性 mixture 纹理 + 白 tint——世界/管道里水仍是原版蓝（惯例、可辨识），釜内是清水（plans/03 §6）。
- **兑现**：plans/03 §3.1「主线无机离子全是无色：怎么混都是清水」；将来 Cu²⁺/Fe³⁺ 有色离子加进来时，颜色才第一次从溶质冒出，不再被水压死。
- **GameTest 45/45**：新增 `solventWaterContributesNoColor`；水相关断言统一走 `Solution.WATER`（`hasSpecies` helper 对 water 特判）。

### D18 · 互溶性分相（按密度抽相）

- **互溶模型**：声明式溶剂族标签（`Species.miscibilityGroup`），非结构推导——`Miscibility.groupOf()`：mixture 恒 aqueous、原版水 aqueous、纯液体查物种 JSON、无声明回退 `unknown`（fail-closed，与万物不互溶）。当前 2 组：`aqueous`（水+全部溶液/浆料）+ `nonpolar`（导热油，补 `thermal_oil.json`）。
- **`collapseIfNeeded` 按组合并**：气体（lighter-than-air）独立相、跨组液体独立相，只合并同互溶组；输出按密度排序（重相在前、气体最后）。
- **`drain` 按密度抽相**：通用 `drain(int)` 取最重相先抽（底口），气体（负密度）最后——`Miscibility.densityOf` 排序。
- **规则引擎相位化**：`RulesEngine.apply` 只求解 aqueous 相，气体/非极液体作为旁观相不读入也不写回（离子不跨相界，plans/03 §8）；`setContents` 写回后重新 append 旁观相。
- **渲染**：气体相独立后，renderer 的「气体挂顶」分支（`isLighterThanAir`）成为活代码。
- **GameTest +3（45/45）**：新增 `immiscibleLiquidsStaySeparate`/`drainPullsDenserPhaseFirst`/`gasStaysSeparateFromLiquid`/`miscibleAqueousMerge`；3 个旧 water+oil「混合物」测试改写为离子混合物。

### S02 · 温度计（两种形式：墙块 + 薄板，世界内化）

- **两种形式**：①`ThermometerBlock`（方块，`extends ChemicalBrickBlock`）——可填入釜壳墙位（进 `vessel_walls` 标签），成型时像砖一样被绑定、代理 FLUID/ITEM、拆砖报破口，读**自己所在釜**的温度；②`ThermometerPanelBlock`（薄板，`DirectionalBlock`）——2px 厚贴墙仪表，读**贴附面后方**的砖/控制器/墙温度计（经 `IMasterBound`）。共享基类 `AbstractThermometerBlockEntity`。
- **`IMasterBound` 接口**：把「绑定到多方块 master」从 `ChemicalBrickBlockEntity` 抽成接口（砖/玻璃/分液口/墙温度计都实现），装配绑定/解绑/拆砖报破口/软管寻釜统一走接口，墙温度计因此能作为一等壳块。
- **护目镜 HUD**：温度（带热级配色）+ 报警阈值 + 报警/未连接状态（`IHaveGoggleInformation`）。
- **世界内调阈值（缩放修复）**：`ScrollValueBehaviour` 阈值存**粗粒度单位**（2°C/格，0–500 格 = 0–1000°C，默认 400°C = 200 格，刻度每 100°C）——原先 0–1000 逐度会让 Create 数值板 ~1400px 宽**溢出屏幕**（最左只能拉到 320°、最右 640°）；改用 2°C 步后数值板 ~546px 宽、完整放下且刻度清晰，数值板/世界内浮层都换算回 °C 显示。
- **读数服务端算、同步到客户端**：温度/连接状态由**服务端** `tick()` 求解（服务端有完整的砖 → master → 反应釜链），`attached`/`temperature` 入 NBT 同步；护目镜 HUD 直接读同步值，**不再客户端重新解析 master 链**——修复「薄板贴在壳砖上（而非控制器）时客户端判为未连接」的问题。
- **红石**：比较器读模拟温度信号（0–1000°C → 0–15）；温度达阈值时输出强信号 15（报警）。
- **GameTest（64/64）**：`thermometerPanelReadsReactorTemperature`（薄板读控制器 500°C、报警、强信号+比较器）、`wallThermometerReadsOwnReactor`（墙温度计作壳块：成型绑定、读自身釜温、代理 FLUID_HANDLER）。
- 这是 S02–S04/S11 贴面仪表族的第一个实例（且是双形态范本），S03 压力表 / S04 浓度计 / S11 液位计照此复制。

### S02/S03 · 表盘活指针（渲染增强，Create Gauge / TFMG VoltMeter 模式）

- **问题**：表盘指针烘在贴图里（温度计红针 / 压力表蓝针，x7-8、y3-8，指向 12 点），渲染是死的——读数只走护目镜 HUD 和红石，指针不动（S02/S03 同病）。
- **活指针**：新 `VesselGaugeRenderer`（4 个 BE 共用：墙块/薄板 × 温度计/压力表），partial model `block/gauge_needle`（白色 2×5px 针，枢轴在底端，12 点 = +Y），按 `SuperByteBuffer` tint 上色（温度计红 `C42C2C` / 压力表蓝 `486CBC`，**报警时变亮红**）。
- **读值→角度**：`AbstractVesselGaugeBlockEntity` 新增客户端 `LerpedFloat.angular()` 追 `value`（0.06 EXP，与釜内液面同款追逐动画，不序列化）；角度映射：温度计 20°C=12 点、1000°C=满偏 270°；压力表 0 kPa=12 点、1500 kPa=满偏 270°（`needleTargetAngle()` 每子类实现）。
- **朝向框架（关键推导）**：面板表盘面 = 块中心 − 3/8·FACING（薄板 2px 挂在格内、VoxelShape 佐证），墙块表盘面 = 中心 + 1/2·FACING（贴齐面）；12 点 = 水平面 +Y、朝上=南、朝下=北（由 `BlockModelRotation` 的 `rotateYXZ(-y,-x,0)` 负向旋转约定推出，与烘贴图逐面吻合）；扫掠 = 绕面法线 −角度（从表盘正视顺时针）。
- **贴图去死针**：`tools/gen_gauge_needle.py` 生成白色针贴图，并把 4 张表盘贴图的烘死针剥掉（按色值校验 12 像素条后回填表盘面色），避免活针离开 12 点后露出底下死针。

### S02/S03 · 动态量程 + itemstack 表盘渲染（BEWLR）

- **动态量程（取代上文固定映射）**：报警阈值 = 表盘满量程。`needleTargetAngle()` 收敛进 `AbstractVesselGaugeBlockEntity`：`(读数 − analogZero()) × 270° / (阈值 − analogZero())`，满偏 270° 恰在阈值处，比较器按同一动态 span 映射 0..15；量程随世界内滚轮阈值整体缩放。**零点 = 物理零**（温度计 0°C / 压力表 0 kPa，`analogZero()`），不再假定最小位置是室温——压缩机/冷却结晶低于室温的读数扫进 12 点以下的负角度段。span ≤ 0（阈值低于零点）时表盘塌缩：未报警针停 12 点、报警针打满偏。
- **itemstack 指针（Forge BEWLR 机制）**：物品栏/手中/掉落物图标此前只有表盘没有指针。修复走 Forge 专门处理「BE 渲染进 itemstack」的通道：item 模型改 `builtin/entity` 父级（烘焙为 `BuiltInModel`，`isCustomRenderer()==true`），`GaugeBlockItem#initializeClient` 注入 `IClientItemExtensions.getCustomRenderer()` → 新 `VesselGaugeItemRenderer`（`BlockEntityWithoutLevelRenderer`）在 `ItemRenderer` 应用完显示变换+居中后画：①原块模型（`getBlockModel(defaultState)`，与原 block 父级 item 图标逐像素一致）②`VesselGaugeRenderer.renderNeedle` 停在**零位**（12 点，南面 +½ 处，1px 浮于表盘面，GUI 相机看到的正是 +Z 面未镜像表盘）。渲染器惰性单例（`initializeClient` 在注册期触发，早于 Minecraft 实例）。接线照 Create `WrenchItem#initializeClient` 同款模式；datagen 同步改 `withExistingParent(builtin/entity)`，旧生成 item 模型删除。
- **墙温度计绑定修复**：`bindBricks` 曾跳过控制器所在的整个 s/d 列（`if (s==0 && d==0) continue`），控制器正上/正下的壳块（含墙温度计）成型成功却从不绑定 → 改为只跳过控制器自身格 `(s==0 && d==0 && y==0)`；回归测试 `wallThermometerAboveControllerBinds`（**72/72**）。

### 反应釜结构生命周期（自动扩展 / 破口分级洒漏 / 残液可见 / 缩小溢流）

统一原则：**内容物（流体）是结构层面的持久资产——结构变动只做迁移，不做删除**（除 <1 桶的按块量化损耗）。

- **自动扩展**：`ChemicalBrickBlock.onPlace` 对已装配控制器调用新增 `tryExtend(placedPos)`——放一块结构方块到釜旁即重校验，仅在**严格更大**（或 open 状态变化，如封顶）时采纳；不缩、不换朝向、不洒内容（Create FluidTank「放相邻块即长大」心智：先小后大、逐步加高）。
- **拆砖分级处置（放/拆都不抽搐）**：`ChemicalBrickBlock.onRemove` 改走新增 `handleStructuralBlockRemoved`——拆**已绑定**砖按破坏性从小到大：①完整重校验出合法壳（更小/同形状）→ 采纳；②拆**顶盖层**砖 → **高度不变、只变敞口**（盖子层废弃、砖解绑成游离，釜高度=环数≠盖子）；③拆**最高环层**砖 → `tryShrink` 降一层（废弃顶盖+最高环）；④都不行才 `invalidateStructure` 分级洒漏。`tryAssemble` 加 `allowShrink` 参数：**放砖路径禁止收缩**（封顶半程不许把釜拽矮，消除"先长高又缩回"的抽搐），拆砖路径允许。平局守卫 `<=`→`==`（体积平局且 open 不变才保持）。收缩/扩展采纳时先 `clearShellMasters` 再重绑（掉出壳的砖停止代理）。
- **破口分级洒漏**：`invalidateStructure` 按破口环层算保留量 `capacity × ring/height`——破口以上洒出、以下留釜内（兑现 plans/04 §8.1「内部流体保留在 NBT，重建可恢复」）；**控制器被拆时回退全量洒漏**（保留份存控制器 NBT、随方块消亡会凭空消失）；底破=全洒；顶盖层拆砖走收缩（不再走失效）。
- **破后残液可见**：失效保留 `size/height/inward` 作 lastGeometry（逻辑路径仍以 `isAssembled()` 为门），渲染器/渲染包围盒放松守卫——剩余壳内的残液面照常渲染，液面自然「降到破口以下」（capacity 未重置，`getFillState` 直接给出降低后的表面）。
- **重建缩小溢流**：`tryAssemble` 成功后 `total > capacity` 时按比例抽出超量走 `SpillLogic` 渐进溢出（漏点取新内腔顶中心），釜不再因 `canFitOutputs` 恒 false 永久 `OUTPUT_FULL` 卡死。
- **支撑改动**：`SpillLogic.queueFluids(List<FluidStack>)` 重载（分解逻辑复用）、`ReactorTank.pruneEmpty()` 公开；装配成功（从破坏恢复时）清旧泄漏队列（扩展/平局采纳不清，避免误删本次溢流）。
- **GameTest +9（60/60）**：加高自动扩展（敞口 3×3×3→密封 3×3×5，内容保留）、封顶不缩高（逐块封顶高度保持 3）、拆顶盖变敞口保高度（5×5×5 高度容量不变）、最小釜 3×3×3 拆顶盖敞口不失效、拆最高环降层（3×3×5→3×3×4+溢出 1000）、中层破保留 9000/洒 18000、底破全洒、拆控制器全洒、破后重建缩小溢出 8000。

### 控制器任意环层下的液面/吸收基准（ringLayer 修复）

控制器可装在任意环层（Tinkers 式），内腔底在其下方 `ringLayer` 格（新增统一基准 `getInteriorBottomRelY() = -ringLayer`）。但渲染、液面数学、开口吸收轮询三处原先都以**控制器自身层**为 y=0 基准——控制器不在底层环时：液面/漂浮物整体偏高 `ringLayer` 格、分液软管悬在真实液面之上、倒进控制器以下内腔层的水源**永不被吸收**（不止渲染问题）。

- **渲染**：物品+流体两个 pass 统一 `translate(0, -ringLayer, 0)` 从内腔底起绘，光照采样点同步下移到内腔中心；`ringLayer=0`（控制器在底层环）时行为不变。
- **液面数学**：`getLiquidSurfaceY` 改为内腔底起算的世界 Y（空釜=内腔底、有液=底+绝对液面高×液相占比），分液软管跟踪恢复到真实表面。
- **吸收轮询**：`absorbFromWorld` 的 y 范围从「控制器层 .. 高度+1」改为「内腔底 .. 顶沿+1」（`getRoofRelY()+1`），AABB 同步——控制器以下内腔层的流体/物品恢复吸收。
- **GameTest +3（64/64）**：`reactorSurfaceMeasuredFromInteriorFloor`（控制器在中环：空釜液面=内腔底而非控制器层；半釜绝对液面 1.5 格 → 控制器+0.5）、`openVesselAbsorbsFluidBelowControllerRing`（控制器以下内腔层/以上内腔层/顶沿三处倒入全部吸收）、`decantHoseFindsVesselWithHighController`（实机抓到的回归：底砖绑定与软管查找，见下）。

**实机回归（控制器抬高即软管失效）**：ringLayer 修复实测时发现 `bindBricks` 的绑定 y 范围写死了控制器在底层环（`-1..rings`，即底=`-1`、盖=`rings`）。控制器抬到第 k 环后底砖在 `-k-1`、盖在 `rings-k`：**底砖不被绑定** → 分液软管 `findReactorBelow` 沿开口内腔柱下扫、穿过未绑定底砖一路扫到釜底之下，永远找不到釜（软管不下垂）；同时范围上端越过真实顶盖一层，会把顶盖上方的游离砖错误绑定。修复：y 范围改 `-ringLayer-1 .. rings-ringLayer`（与 k 无关）；顺带 `clearShellMasters` 半径从只按底宽改为 `max(底宽, 环数)+1`（高瘦釜重绑/失效时够得到旧底旧盖，不再残留脏绑定）。

### U1 · 容器状态层（多相加热 / 放热全体 / 压力模型 / S03 压力表 / datagen 修复）

M3 首单元（plans/11 §2.1）：修掉 G1/G2/G3 三条公共地基缺口，四条下游线（吸收/氯碱/碳化、相变冷凝、连续流、高压）解堵。

- **G1 多相加热**：`updateHeat` 删「恰好单相才松弛」的 early-return——D18 后气体是永久旁观相，旧逻辑下液+气共存时**谁都不升温**（装料即冻结在入釜温度）。现在每个相独立向热源目标松弛（每 HEAT_TICK 1/10，与旧单相行为逐位一致，零回归）。
- **G2 容器温度 = 全相加权 + 放热全体**：`getTemperature()` 从「entry 0 的温度」改为**全相按量加权平均**（存储仍 per-stack NBT，运输身份不动）；`completeRecipe` 的 deltaHeat 从只打 `fluids.get(0)` 改为**作用全体相**（每相 +Δ，保相位差、加权均值恰好抬 Δ）。规则引擎的放热维持相内语义（旁观相不参与求解，由 updateHeat 统一供热）。
- **G3 压力模型**：釜级 `getPressure()`（kPa 表压）——闭口线性模型 `P_abs = 1atm × 气相体积分数 × T/T_amb`（充气升压、加热升压、满液恒 0、开口/未成型恒 0，下限 0 不做真空）。**派生读值不入 NBT**（与 getFillState 同信任模型：内容物/结构态本就同步，存副本反而会漂移）；釜 HUD 加压力行（开口显示常压）。密封本单元保持开/闭二值，材质分级耐压留 U11。
- **温度窗口速率系数**：`rateCoefficient(recipe) = (1 + 窗口内过温/400) × stirringCoefficient`——高于配方门槛最多加速 2×，**门槛处恒 1.0**（配得上温度=满速，既有节奏永不放慢）；搅拌系数占位 1.0（MixDegree 删除时保留的钩子，接 Create 搅拌在 U5/U12）。
- **S03 压力表（双形态）**：照 S02 范本——墙块形态（入 `vessel_walls` 标签，可填壁位、绑定控制器、代理能力）+ 薄板形态（贴面读身后壳块）。新增通用基类 `AbstractVesselGaugeBlockEntity`（阈值 ScrollValueBehaviour 粗粒度单位、服务端求解读值同步、报警红石 15、比较器按满量程映射、护目镜 HUD），S02 温度计改为其薄子类（公开 API 不变、温度计方块零改动），S04 浓度计/S11 液位计后续照抄。压力表满量程 1500 kPa（阈值 25 kPa/格、默认 250 kPa），为 U11 高压线留头寸。
- **datagen 修复（G7 旧账）**：`runData` 自 D18.5 起一直跑不通（decant_hose 贴图缺失→方块状态生成即炸）——本次修通并固化：gen_species.py 补 `decant_hose` 程序化线圈贴图 + 拨盘纹理参数化（`make_dial_texture`，温度计红针/压力表蓝针同源）+ BLOCKS 表收录压力表两形态 + 压力语言键；mixture 自动桶补 `.bucket().model(空)` 抑制与 `forge:fluid_container` 模型；温度调试棒补模型抑制；薄板 FACING 变体与 Create 软管模型引用改由注册处显式 provider 生成（`plateVariants` 北锚定 yaw；Create 是 `:slim` 无 assets，须 `UncheckedModelFile`）。顺带清掉 M0 时代过期溶液 blockstate（v2 后溶液不再是流体）。
- **GameTest +7（71/71）**：`reactorHeatsAndReadsAllPhases`（水+油两相都松弛 + 加权读数 600）、`exothermicDeltaHeatsAllPhases`（SO₂ 吸收放热到达油旁观相）、`sealedVesselBuildsPressure`（满充气常压 0 → 900°C 约 303 kPa）、`liquidFullVesselStaysAtZeroPressure`、`openVesselKeepsAmbientPressure`、`pressureGaugePanelReadsAndAlarms`（读数/阈值 250/报警 15/比较器映射）、`wallPressureGaugeReadsOwnReactor`（成型绑定/读自身釜/代理能力）。

### U3 · 模板抽取（vessel/ 结构层 / 沉淀池去拷贝 / 控制器拆类）

修 G5（1429→1548 行控制器内联结构逻辑 + 沉淀池手写拷贝），解锁 U4 竖窑及全部塔式实例。纯迁移为主，77/77 全绿。

- **`vessel/VesselBlockEntity`（951 行，新基类，Create FluidTank 层级模式）**：从釜控制器**逐段迁出**结构层——四面×W×环数成型引擎（`AssembleIssue`/`AssembleResult` 随迁，公开 API 经继承原样保留）、生长/收缩四级阶梯（`tryExtend`/`handleStructuralBlockRemoved`/`tryShrink`）、`IMasterBound` 绑定（`bindBricks` y 范围/`clearShellMasters`）、`invalidateStructure` 破口分级洒漏 + lastGeometry、洒漏滴流、几何访问器、渲染包围盒、LerpedFloat 液面数学、能力代理、SmartBlockEntity write/read（NBT 键名不变，釜存量兼容）。形状参数化钩子：`minSize/maxSize/minRings/maxRings/roofMode(OPTIONAL|FORBIDDEN)/capacityFor`。
- **内部件 allowlist**：`isInteriorAllowed(state)` 钩子（默认空气/流体，行为不变）+ 静态 `INTERIOR_OVERRIDES`（生产恒空；U4 窑内件/U6 填料注册处，GameTest 用后即清）。`absorbInteriorOnAssemble` 跳过 allowlist 固体。
- **釜纯迁移 + 拆类**：`ReactorControllerBlockEntity` 1548→**573 行**（<600 达标），只留热/压力/反应编排/HUD；配方匹配与结算（~250 行）抽 `reactor/ReactionLogic`（静态、`rateCoefficient` 随迁）。
- **沉淀池去拷贝**：293→143 行，删全部手写样板（校验/绑定/洒漏/同步/NBT/能力），只留形状参数（3×3/单环/FORBIDDEN 屋顶/容量 8000 平）+ `FilteringLogic` 0.25 速挂钩；SmartBlockEntity 化（免费获得平滑液面/行为同步）。**修复真 bug**：旧校验要求控制器自身格是空气 → **沉淀池自 M2 起从未能成型**（零测试所以未暴露）；且改认 `vessel_walls` 标签（玻璃/仪表墙块对池生效，与釜一致）。池洒漏随统一基座修通（旧代码破拆后 pendingSpill 永不滴流）。
- **绑定统一**：三处 `getValidMaster()` instanceof 阶梯（砖/温度计/压力表 BE）统一为 `instanceof VesselBlockEntity && isAssembled()`；`ChemicalBrickBlock.onPlace/onRemove` 同样按基类统一（未来塔式零改动即接入）；分液口 `vesselTank()` 改读任意容器（池上也可装分液口）。
- **玻璃生命周期修复**：`ChemicalGlassBlock` 改继承 `ChemicalBrickBlock`——旧版无 onPlace/onRemove，**敲碎玻璃墙块后釜带洞保持成型**（潜在事故源），现有回归测试钉死。
- **GameTest +5（72→77）**：`basinAssemblesAndProxiesFluid`（池成型/8000 容量/地砖代理）、`basinSettlesSlurry`（浆料沉降出碱饼+清水）、`brokenBasinSpillsContents`（破壁洒漏）、`solidInteriorBlocksUntilAllowlisted`（实心内腔拒装 + allowlist 放行，INTERIOR_BLOCKED 路径首次直测）、`glassBreakDisassemblesVessel`（玻璃破碎回归）。
- 顺带修复（非 U3 范围、用户连接纹理工作被挡编译）：`AllBlocks` 玻璃 blockstate 的 `ModelBuilder<?>` 通配符捕获使 `ConnectedModelBuilder::new` 推断失败 → 改具体 `BlockModelBuilder`。

### U15 · 晶粒、投种与混合固体物品

「物品↔流体边界」的中间面额 + 整坨取出语义（plans/03 §5/§12）。GameTest 89/89 + JUnit 56/56 全绿。**这一单元把「纯」变成玩家挣来的东西**：未分离的混合沉底只能整体取出为混合盐渣，纯物品三条挣取路线（时序分批/化学除杂/重结晶）从此有真实的对手盘。

- **①苦卤盐数据先行**：`magnesium_chloride_solution` 新物种（曲线 52.9→72.7 g/100g，`magnesium_chloride` 物品注册）、`calcium_chloride_solution` 补曲线（59.5→159）——蒸干苦卤不再有干离子残留，`BitternTest`：NaCl+MgCl₂ 双沉淀、CaCl₂ 深冷结晶（68% 过饱和过成核门槛）vs 浅过饱和亚稳（18% 低于门槛，晶种塌缩）。
- **②晶粒与投种**：8 可结晶固体（岩盐/KNO₃/KCl/NH₄Cl/CuSO₄/CaCl₂/MgCl₂/明矾）各注册晶粒变体（`GRAINS` 表驱动 `gen_species.py`，1/16 物品 = 62.5 mB），Create 粉碎轮 1 粉末→16 晶粒（8 条 crushing 配方）。`RulesEngine.dissolveItems`：过饱和分支 = **投种**（过饱和液里固体溶不进去 → 晶粒优先按 625,000 units 面额入 Sediment 域，动力学以有种速率塌缩亚稳液）；未饱和时晶粒按 62.5 mB 面额溶解（整物品仍是 1000 mB/次）。取出只发整物品 ⇒ **<1000 mB 沉底余量留锅 = 传家宝种**终身自接种（62.5 mB 在 10⁴ 网格精确保存）。
- **③mixed_residue 混合盐渣**：`MixedResidueItem` NBT 存 GCD 约分成分比例（复用 mixture ratio-tag 身份机制，tag 相等才堆叠）；`ReactorTank.extractSolids(sink, domain)` **整坨取出、禁止选物种**——严格判定：域内单物种（且有注册物品）→ 纯物品；含任何第二物种 → 盐渣（1 物品 = 1000 mB 当量，比例减除、余量留锅）。可见信息守测量诚实性：统一名「混合盐渣」+ 按成分染色（ItemColor→SolidColors 混合，颜色=物理可观测量）；成分百分比仅 `Chemistry.ASSAY` dev 模式（新旋钮，U17 接管）；**溶解即化验**——投回水中按 NBT 精确展开回离子域（饱和则拒绝溶解；无曲线矿物入 Suspended 由 equilibria 接管）。
- **④过滤机/沉淀池升级**：`FilteringLogic` generic 路径切 `extractSolids`，删旧 `extractSuspended`（按物种逐个吐纯物品的上帝视角——正是 §12 否决的行为）。
- **GameTest +3**：真实晶粒投种塌缩（0.45 vs 0.36 亚稳 + 1 岩盐晶粒 → 80 mB 沉淀 + 饱和母液 72 mB）；整坨取出双路（3000 mB 混合沉底→3 盐渣（NBT 双成分）/ 2500 mB 单物种→2 纯物品 + 500 mB 传家宝 / 62.5 mB 不可取出）；盐渣溶解展开守恒（Na⁺/Mg²⁺/Cl⁻ 精确、电中性）。
- 踩坑记录：`ItemStack(Item,int,CompoundTag)` 在 1.20.1 第三参是 **capNBT** 不是物品 tag（盐渣 NBT 全空）→ 改 `copy()+setCount`；mixture 构造时 total 与 parts 和不一致会整体重归一化（绝对量被 ratio 洗掉），测试需令 Σparts = total mB；datagen 的 en extra lang 与 registrate `.lang()` 撞键直接崩——`.lang()` 已覆盖的键不进 EXTRA_LANG_EN。

### U16 · 反应热能量记账

热从「集总 °C 常数」改为 **J/unit 能量记账**（plans/03 §12 修法落地）。GameTest 90/90 + JUnit 60/60 全绿。**这一单元让「热失控 vs 大釜稀释」两个方向都判对**：同一份反应热，浓料小锅自沸、大釜稀释只升个位数；开口釜排汽带潜热，无热源自熄。

- **三常数声明口径**（`composition/Chemistry`，声明 1 unit ≡ 1 g，与溶解度曲线声明同族）：`HEAT_CAPACITY_PER_UNIT=4.18` J/unit·°C（水）、`NEUTRALISATION_J_PER_PAIR=3172`（57.1 kJ/mol ÷ 18 g/mol）、`VAPORISATION_J_PER_UNIT=2260`（水汽化潜热）。
- **Solution 账本**：`energyJ`（double，负值=冷却需求）替换旧 `heat`（°C 集总，删 `NEUTRALISATION_HEAT_PER_UNIT=0.05/10⁴`）；`heatRiseC() = Q/(feedUnits×c)`，**feed 基准=构造时四域总量**（吸热的是当时那锅料——1:1:1 中和 ΔT=3172/(3×4.18)≈253°C，正是计划书验收锚点）；直接中和对与 `driveWeakElectrolytes` 驱动对**同价计热**（滴定链每求解步记账）。
- **蒸发潜热自限**：`evaporateWater` 每排 1 unit 汽记 −2260 J——开口釜排 50 mB 使 2000 mB 体冷却 ~13°C，**无热源自熄**（降到沸点下停排）、有热源（烈焰人放松弛回温）持续沸腾但被钳在 ~100–130°C 振荡，不再免费越过 100°C。煮干出盐链不变（热源供能）。
- **配方 deltaHeat 同口径**：JSON 语义=「参考一桶（10⁷ units）体的温升」，实际 `ΔT = deltaHeat × (一桶/釜内总量)` 质量反比缩放——现有 150/100 两个配方 JSON **零改动**，满釜行为与旧版一致、大釜自动降为个位数。
- **测试**：新增 `EnergyLedgerTest` 4 例（1:1:1→253±2 / 稀释线性 63.25=253×(3000/12000) / 潜热 −13.5 / 弱碱滴定全程计热 54.2——fixpoint 跨步累计 ΔT，正是釜每 tick 的记账方式）；GameTest +1 `neutralisationExothermScalesWithConcentration`（浓 1:1:1 实测 273°C vs 96:1:1 大釜 28°C 双向）；基线重定：`rulesEngineNeutralisesAcidAndBase` 温升 >20 → 140–152（ΔT≈126 质量耦合），蒸发测试改写为「无热源排 50 mB 冷 13°C 停沸 → 热源回温煮干蒸干出盐」三段。
- 踩坑记录：`solveToFixpoint` 每轮新建 Solution，**返回的是静息轮账本（=0）**——跨步放热要在测试里累计 `heatRiseC()`，不能用末轮快照。

### U16.5 · 湿饼夹带与洗涤

「取出不完美 = 机械夹带」（plans/03 §12 落地）。GameTest 96/96 全绿。**这一单元把「洗净」变成玩家挣来的东西**：未洗的饼带着母液，洗（再浆/置换）才换回纯物品——索尔维洗 NaHCO₃ 饼、食盐粗盐→洗盐→精盐从此有真实对手盘。

- **夹带结算**：`RulesEngine.CAKE_LIQUOR_FRACTION = 0.3`（湿饼 30% 体积=晶隙母液）+ `WASH_DISPLACEMENT = 0.75`（置换效率）+ `MAX_WASH_PORE_VOLUMES = 13`（有效洗水上限）。`ReactorTank.extractSolids(sink, domain, washTank, filtrate)`：取出时按当时液相浓度比例分摊夹带进取出物；带洗水罐时 `factor=(1−ε)^(W/V)` 几何缩放保留母液（整数取整做诚实截断——机器只判成分不判浓度，洗到低于单位网格才算净）；洗水耗量入滤液。
- **residue NBT 母液相**：`MixedResidueItem` 新增 `Liquor` map（`water`/离子 id/`s:`前缀分子溶质），与 `Solids` **联合 GCD**（未洗饼与洗后饼永不堆叠）；`colorOf` 并入 IonColors 染色；「溶解即化验」扩为两相精确展开（母液水回溶剂、离子入离子域、饱和可行性把母液离子也计入）。**纯度判定语义不变**：单物种固体 + 夹带仅剩水 → 纯物品。
- **再浆洗涤（釜内）**：新原语 `ReactorTank.decantClear`——**只抽清液域（水+溶质+离子），固相永不动，抽到床孔隙线为止**；`clearLiquidAvailable` = 液量 −（沉底+悬浮）×30%。分液口/软管对 mixture 的抽取全部切到 decantClear（旧的比例取样会把沉底床也抽走——那其实是「浆料泵送」语义，普通泵 drain 保留）。加清水→再悬浮→再滗 = 几何衰减（测试两轮 500→150→35，床 2000 mB 不动）；清水回溶的产率损失由回溶引擎免费涌现（NaCl 床洗后 2000→1322，石灰石 ≥1995）。
- **置换洗涤（机上）**：过滤机新增洗水罐（清水管道注入自动路由到洗水，其余进料）+ `FilteringLogic` generic 路径带洗水调用 extractSolids——13 孔体积洗水把母液打到单位网格以下，单物种饼出**纯物品**，洗水 3900 mB 入滤液。
- **S18 电导率计**（仪表族第三台，plans/04 §9.2）：读数=10×(Σ离子 units/水 units) 声明 mS 标尺——**分子溶质不导电**（氨水 0 mS vs 铵盐高，波美计做不了的区分）；方块/面板双形式（S03 模式复制），绿针表盘（gen_species）；**报警方向反转**（基类新钩子 `alarmWhenBelow`）：信号=电导率**降至**设定点以下——洗涤完成/水净的终点事件，默认 5 mS。
- 踩坑：mixture mB 视图经比例 tag 往返有 ±1 重分配（units 精确）——床完好断言放宽 ±2；裸 tank 补清水后要 `collapseIfNeeded()` 并相（真釜里规则引擎每 tick 做）。

### U17 · 分析化学层 + 终点控制

「测量诚实性」（plans/03 §6）落地：玩家常态无 SI——护目镜 SI 行降级为 `ChemicalAddon.ASSAY_ON` dev 旋钮，化学身份靠仪表/试纸挣。GameTest 102/102 + JUnit 66/66 全绿。

- **试纸/试剂族 7 件**（`TestPaperItem`，对釜控制器/壁砖右键蘸取消耗）：pH 系列（石蕊二点/酚酞 ≈8.2/广泛 ±1 档）+ 离子检验（AgNO₃ 检 Cl⁻/BaCl₂ 检 SO₄²⁻/KSCN 检 Fe³⁺）+ 蓝钴玻璃焰色镜（K 紫透过镜/Na 黄/Ca 砖红，K 优先）。
- **物理量仪表三台**（仪表族唯一定义 plans/04 §9）：**S16 pH 计**（`Analyte.ph`：酸侧直读/碱侧 Kw=1e-14 换算/中和定点 pH 7；固定中心零点表盘 pH7=12 点钟、1 级=1 pH、空手右键切跌破/升破触发）；**S04 波美计**（溶解 units/水 units 声明式线性换算，锚=曲线饱和盐水 0.72→30°Bé、1 级=2°Bé）；**S17 浊度计**（4 档 1%/5%/20%，沉底床不计、初浑报警、比较器 0/5/10/15）——全部方块/面板双形式进 vessel_walls。**Kw 决策**：落读数层常数而非求解器条目（真实自电离对会改 GCD ratio-tag 身份，见 12 §5）。
- **M08 终点结晶器**（`CrystallizerControllerBlockEntity` 反应釜子类）：°Bé 设定点世界内滚动 + 到点切热（`heatTarget` 覆写）+ 终点强充能/比较器 °Bé 进度 + 排汽冷凝回收为馏出水产物（`RulesEngine` 通气量累加→内置罐被动外推）——「只析 A 不析 B」由设定点低于 B 饱和线挣得（KNO₃/NaCl 分步结晶端到端）。
- **溶液物种直读**：H⁺ 换算（HCl/烧碱 packed 桶 → pH 1/13）。验收要点：pH 滴定终点（碱 13→中和 7→报警→方向切换→比较器 1:1）；波美锚点；浊度 4 档；试纸 7 种 verdictKey 阳性/阴性矩阵；M08 引擎级选择性结晶（24°Bé、冷后亚稳、投种只析 KNO₃≈94 mB、NaCl 全留母液）+ 方块级（组装/设定点 24/终点红石 15/馏出水 ≥400 mB）；`AnalyteTest` 6 例（pH 三例穷尽/两侧对数/等当点 ±1 mB 跳 11↔7↔3/氨水弱碱/波美锚/浊度档）。

### U18 · 定点分数（量子网格 10⁷/mB）

把规则引擎的求解刻度从 10⁴/mB 细化到 10⁷/mB（`Chemistry.QUANTA_PER_UNIT=1000`、`QUANTA_PER_MB=10⁷`），亚单位平衡残差在 ratio-tag 里存活而不是每次求解被截断。GameTest 102/102 + JUnit 66/66 全绿。后续（焓记账退休最后两条热护栏）见 plans/13。

- **Mixture long 通道**：parts 存取 `putLong`/`getLong`（legacy int tag 经 `contains(key,99)` 升级），四域 get*/set* 与混合视图 `deriveLongView`/`distributeLong` 全 long 域；`blendColorLong`/`createLong` 为 long 核、Integer 版为兼容委托（泛型擦除不能重载）；量子视图 `deriveQuanta*Amounts`（求解器往返用）、unit/mB 视图保留（运输/显示/测试）。
- **RulesEngine 量子往返**：读 `deriveQuanta*` → 求解 → `ReactorTank.setContentsLong`（镜像 Integer 版，含 `repairTraceChargeImbalanceLong`）写回；`ITEM_UNITS`/蒸发/纯水入料同步量子化。
- **修三个真 bug**：①`distributeLong` 的 `total×part` 在量子刻度达 ~1e19 **溢出 long**（症状：沉底份额变负、状态错乱）→ BigInteger 精确 floor；②`coupleDeficits` 每次只动 1 量子、迭代护栏固定 1e5 → 网格越细真实吞吐越低（malachite 在釜内只沉 19%）→ 验证单事件后**按矿物可吸收量分块**（supplier 分块 + 矿物 bisect 边界，网格无关且不 overshoot）；③`extractSolids` 聚合 unit 视图却除以量子化后的 `ITEM_UNITS`（1000× 错位 → 整坨取出全灭）→ 局部 unit 刻度物品面额。
- **平衡重钉**：细网格解到真质量作用平衡——釜内 malachite 与 JUnit 一致为 50/0/CO₃⁻49/HCO₃⁻101（旧 35/30 混合碱式碳酸盐是粗网格截断伪影），GameTest 钉随之更新。



测量诚实性（plans/03 §6）全面落地：玩家仪器只读物理间接量，SI/speciation 降级 dev 化验。GameTest 102/102 + JUnit 66/66 全绿。**这一单元把「终点判断」从上帝视角还给化学**：反应完了没，由波美计/pH 计/浊度计/试纸回答，不由机器看穿成分回答——三酸两碱、索尔维碳化（pH≈8.2）、食盐精制的终点从此都有世界内的物理表达。

- **引擎读数层 `composition/Analyte`**（纯函数，JUnit 直测）：pH（酸侧直读 / 碱侧 [H⁺]=Kw/[OH⁻] / 中和定点两离子皆零 → pH 7，三例穷尽）、波美（溶解 units/水 units 声明式线性换算，锚=曲线饱和盐水 2×0.36=0.72 → 30°Bé，离子计数系数进锚点）、浊度（悬浮份额 1%/5%/20% 四档，**沉底床不计**——清液盖晶床读清）。**Kw 决策**：`Chemistry.KW=1e-14` 落读数层常数而非求解器 equilibria 条目——真实自电离对（1000 mB 中恰好 1 unit）会改 mixture GCD ratio-tag 身份（已解液 vs 新装桶永不堆叠），且对一切机制不可见；解析计算免费获得等价语义。
- **S16 pH 计**（方块+面板，双形式照 S03 复制）：**固定中心零点表盘**（pH 0–14 线性满刻度、pH 7=12 点钟——对数标尺线性分箱语义天然正确）；比较器 1 级=1 pH；**空手右键切触发方向**（跌破/升破——碳化终点「pH≤8 停」与碱化终点「pH≥10 停」都要）；阈值滚动步进 1 pH。等当点一滴跳 pH 即红石跳变（AnalyteTest：±1 mB 跳 11↔7↔3）。
- **S04 波美计**：物种盲密度读数（1 级=2°Bé，量程 0–30），默认设定点 24°Bé（近饱和盐田终点）；到点报警=浓缩终点。
- **S17 浊度计**：4 档针位（0/90/180/270°）、比较器 0/5/10/15、默认阈值=微浑——**初浑报警**（除杂「刚不再浑」的逆运用：该清不清=杂质穿透，断料止损；亚稳区+慢动力学是止损窗）。
- **试纸/试剂族 7 件**（`TestPaperItem`，gen_species TEST_PAPERS 表单一数据源）：石蕊（<5 红/>8 蓝/其间紫）、酚酞（pH≥8 粉红——索尔维碳化终点历史判据）、广泛 pH（±1 档）、AgNO₃ 检 Cl⁻、BaCl₂ 检 SO₄²⁻、KSCN 检 Fe³⁺、蓝钴玻璃焰色镜（K 紫透过镜优先于 Na 黄/Ca 砖红）。对釜控制器或任意壁砖右键蘸取 → 消耗 1 张 → 动作栏反馈；空釜不消耗。**定性归化学、定量归仪器**——试纸永不升级出「浓度读数」。
- **护目镜 SI 行降级**：speciation 饱和态行包进 `ChemicalAddon.ASSAY_ON`（dev 化验指令），玩家常态只有温度/压力/状态/总量四行 + 世界内仪表。
- **M08 终点结晶器**（`CrystallizerControllerBlockEntity`，ReactorControllerBlockEntity 子类 + `CrystallizerControllerBlock`）：°Bé 设定点世界内滚动（步进 2 与 S04 对齐，默认 24）+ **到点切热**（`heatTarget()` 覆写回环境温度——浓缩自动停在设定点）+ 终点强充能 15 / 比较器 °Bé 进度 + **冷凝回收**（`RulesEngine.apply` 新增通气量累加参数；内置 4000 mB 馏出罐，被动推向相邻非容器流体消费方——蒸出水为产物）。「只析 A 不析 B」由设定点低于 B 的饱和线挣得：KNO₃+NaCl 母液浓缩到 30°Bé → 冷却 → 亚稳 → 投种只析 KNO₃（~81 mB），NaCl 全留母液——**机器零物种知识，选择性来自物理量+化学**。
- 踩坑：①GameTest 里 `level.getBlockEntity` 需绝对坐标（`helper.absolutePos`），结构坐标只用于 helper 自家 API；②KNO₃ 20°C 曲线是 31.6 g/100g（13 是 0°C）——终点母液浓度算错会「不过饱和、投种无效」；③**钉温调试棒优先于 heatTarget 切热**：方块级测试若钉 100 放任不管会把釜煮干（水干后 `baumeOf` 归零、终点事件反跳）——测试须轮询到终点即撤钉，让真正的切热路径（降温弛豫）跑一段再钉 20 快进冷却。

### U14 · JUnit 引擎测试层 + 常用无机材料矩阵（引擎数据层 only）

composition 层（Solution/Equilibrium/Species/SpeciesManager/Ion + blendColor 静态）剥离 MC 跑纯 JUnit（`./gradlew test`），GameTest 86/86 + JUnit 52/52 全绿。含后续追加的**动力学层**与 **10⁴ 细网格**。

- **JUnit 基建**：build.gradle JUnit 5 + `useJUnitPlatform()`；唯一 MC 类型 ResourceLocation 可 headless。**修真 bug**：composition 类引用 `ChemicalAddon.LOGGER/MODID` 触发主类静态初始化（registry 未 bootstrap 即炸）→ 新 `composition/Chemistry`（常量+独立 logger），composition 层不再反向依赖模组主类。`EngineHarness`：classpath 真数据加载、solve/solveToFixpoint、域/守恒/色彩断言。
- **内部求解网格 1/10000 mB（`Chemistry.UNIT_PER_MB = 10⁴`）**：求解器/比例 tag/规则引擎全量 unit 域，浓度分辨率 10⁻⁷——定标锚点＝最弱可建模水解 Mg²⁺（Ka 10⁻¹¹·⁴ → [H⁺]~6e-7）。细粒度持久化在比例 tag（ratio parts 无量纲），mB 视图照旧是传输/显示粒度；`Mixture` 加 unit 派生（软钳制防溢出）、`ReactorTank.setContents` 单位感知（mB 量=round(Σunits/scale)）。**修真 bug ×2**（细网格放大暴露）：①「强碱赶氨」分支会把弱碱平衡自身的电离产物拆光（旧网格 1-2 单位被测试容差掩盖）→ 删除（反向二分本就收敛到真平衡）；②中和热逐单位 `(int)` 累加全量化为 0 → heat 改 double 记账。`FineGridTest` 对账解析解：石膏饱和母液恰好 5000 units（旧网格 0/1）、氨电离 19000 units（旧 1-2）。
- **两旋钮制**：`MINERAL_LOG_OFFSET=-2` 仅矿物条目（Ksp 量纲归一）；水相条目（络合 β/弱电解质 Kb）用原始 log_k——统一 offset 会把 Kb 压到整数分辨率下限以下成死条目。
- **弱电解质首条解封**：`NH₃+H₂O⇌NH₄⁺+OH⁻（Kb 10⁻⁴·⁷⁵）`。**修真卡点**：滴定链「电离 1 单位 OH→中和吃掉→再电离」在整数二分处过冲 0.3 log 单位而死锁 → 中和步骤加**弱电解质通道**（强酸滴弱碱/酸化铝酸盐按化学计量完成，化学依据=Kw 介导推动力近无限）；`equilibrate` 轮内穿插 `neutralise`；裸 `water` token 解析为溶剂（原被当成离子 id，Kb 条目永远不可动——JUnit 首夜抓出的第二个真 bug）。
- **引擎数据 +21 物种（零游戏注册）**：矿物 AgCl/BaSO₄/BaCO₃/Ag₂CO₃(2:1)/Mg(OH)₂/MgCO₃/Cu(OH)₂/Zn(OH)₂/Fe(OH)₃(1:3)/Al(OH)₃；络合 [Ag(NH₃)₂]⁺/[Zn(NH₃)₄]²⁺/[FeSCN]²⁺/Al(OH)₃+OH⁻=[Al(OH)₄]⁻（**两性**）；曲线 KNO₃/KCl/NH₄Cl/明矾（复盐 1:1:2 离子集）/绿矾（60°C 以上**逆行溶解**）；IonColors Fe³⁺ 黄褐/Fe²⁺ 浅绿/[FeSCN]²⁺ 血红。
- **动力学层（两速化学，结晶域默认动力学）**：快平衡瞬时解（PHREEQC 立场，缺省语义）；结晶生长亲和律限速（`0.1×水×(c/c_sat−1)×搅拌`，几何逼近永不过冲）+ **成核门槛 0.5**（无晶种且过饱和 <50% = 亚稳，骤冷不析出；一粒晶种塌缩——接种玩法）+ 首晶 0.05 慢成核 + 回溶瞬时 + **蒸干规则**（水尽全析，煮干出盐）；平衡条目可选 `"rate"`（亲和律 + 粗 Arrhenius 每 25°C 翻倍 ×搅拌，每求解步一推，缺省瞬时=零回归）。`Speciation.rateLimited` 报告"热力学该动但动力学卡住"。**修真 bug**：①动力学条目被外层 pass 偷跑两倍（pass 感知）；②**mB↔unit 往返的余数分配使 NH₄⁺/NO₃⁻差 1 单位 → 电中性校验拒收整个离子集 → `Mixture.create` 静默扔掉全部离子、质量塌成水**（GameTest 循环里暴露）——`setContents` 单位路径加**痕量电荷修复**（±几单位刮掉，大失衡照旧拒收报警）；③自接种让成核惩罚只慢一步——改为硬成核门槛才是真亚稳。搅拌系数从 BE 贯通到求解器。测试重定基线：结晶断言全部改定点语义（solveToFixpoint 预算 4000），GameTest 冷却测试改写成「骤冷亚稳 + 投种塌缩」演示、蒸发测试改「蒸干出盐」；新增 `KineticsTest` 8 例（几何逼近单调、亚稳门槛、晶种塌缩、蒸干、rate 条目每步一推、Arrhenius 4×、瞬时平衡不受影响）。
- **测试套 9×（52 用例）**：`InvariantsTest`（fuzz 守恒 + 定点稳定性）、`PrecipitationTest`、`CrystallisationTest`（定点语义）、`ComplexationTest`、`WeakElectrolyteTest`、`FineGridTest`（细网格 vs 解析解对账）、`SpeciationReportTest`、`KineticsTest` + Smoke。
- 氨水行为变化：进釜后「完全电离」的 NH₄⁺OH⁻ 松弛为绝大部分分子氨——素尔维中间体行为后续 U6 调参时复核。
- **反应热量纲分析定案（03 §12）**：现状集总常数（ΔT∝反应量、无热容、无潜热）不能正确表达自持反应（两个方向都判错）；修法已设计（能量记账 J/unit + ΔT=Q/(Σunits×4.18) + 蒸发潜热 2260 J/unit）——**U16 已落地**（见上节）。

### U13 · 规则引擎 v2（统一 equilibria 条目 + 质量作用求解器，PHREEQC 语义）

删 `ksp` 专用字段与「Ksp 仅排序、整组搬走」的 v1 简化，换成**一条数据形状解所有常数平衡**（语义与 log_k 出处：PHREEQC，USGS 公有领域，见 THIRD_PARTY.md）。80→86 全绿。

- **`Equilibrium`（新）**：条目 = 反应式 + log_k（+delta_h 预留，v1 不算）。token 微语法：`" + "` 连接、`=` 分侧、系数前缀、`(s)` 固相后缀；离子 id（`Cu+2`、`[Cu(NH3)4]+2`）/ 分子 id（`chemicaladdon:ammonia`，短名默认本模组命名空间）/ 固相三分类。**符号约定 PHREEQC 同款**：log_k 为按书写方向的常数（固/溶剂活度=1），矿物按溶解方向书写（`limestone(s) = Ca+2 + CO3-2, log_k −8.3` ⇒ Ksp）、络合按生成方向（log_k = β）。条目挂任意物种 JSON、`SpeciesManager.allEquilibria()` 汇总（水相先、矿物后按 log_k 升序——络合先于成核，氨掩蔽铜即此顺序的涌现）。
- **`Solution` v2 求解器**：全 log 空间比较（`log Q vs log_k + LOG_K_OFFSET`，全局旋钮 −2，防下溢）；每条目二分求「移到平衡的整数单位数」；**整数分辨率下限 = 半单位浓度**（耗尽物种记 0.5/water，消灭 NaN/±∞ 边界态；K 极小者析到 0 残差，近溶者留饱和痕量）；管线 equilibrate→neutralise→curveBalance 外层 2 轮（同离子效应/掩蔽由迭代涌现）。结晶改**部分析出**（只析 `form − floor(阈值×水)` 过剩，留饱和母液），欠饱和时沉底/混悬**回溶**。speciation 报告（每矿物 SI + 净移动）。
- **`RulesEngine`**：物品投料溶解（带曲线物种的固体物品 1 个/tick 溶入至饱和余量，1 物品=1000 单位；**修真 bug**：溶解原地改 before-map 使跳过写回判定永远相等，溶解结果从未写回——改为返回 consumed 标志）；开口釜 100°C 蒸发浓缩（50 mB/reaction tick，闭口抑制）；写回语义从「合并」改「替换」（回溶可缩减固相域）；预存固相进求解器（欠饱和即溶）。
- **引擎边界入档（plans/03 §8.1）**：自发的归规则引擎 / 需驱动的归配方引擎（红氧=pe 平衡否决：会消灭 H₂/O₂ 亚稳态=无条件点火；活度系数再否决；盐类水解后置但弱电解质条目格式已通）。**萃取决策**：独立系统后置（互溶性分相已有，液-液分配需 `(o)` 相条目 + 多液相联立 + 溶在油里存储语义，不与釜内反应同釜）。
- **数据**：limestone/gypsum 迁 equilibria（log_k −8.3/−4.6，摘自 PHREEQC/minteq 量级）；**新增 `copper_carbonate`（碱式碳酸铜，孔雀石绿 0x2FA896，log_k −10）**；铜氨络合条目（β 12.6）+ `IonColors` 铜氨深蓝；brine 补 NaCl 曲线 + solute rock_salt（岩盐投料可溶）；BUILTIN_SPECIES 补硫酸铜三件套。
- **护目镜**：饱和态行（SI ≥ −3 或有移动的矿物列出，`名称 SI x.x`，青=过饱和析出/黄=接近/绿=平衡）——「为什么没反应」从猜测变读数。
- **GameTest +6（80→86）**：回溶（冷却饱和→加热全溶）、碱式碳酸铜（蓝绿混悬+孔雀石绿渲染+ spectators 保留）、氨掩蔽（铜全入络离子、零沉淀）、同离子效应（硫酸过量 Ca 析尽 vs 1:1 留 1 单位母液）、投料溶解（1 物品/tick + 余量 <1000 拒溶整件）、蒸发浓缩→析晶（闭口对照不蒸）。校对 3 个旧规则测试（部分结晶 116/500、石灰石完全析出、中和改不饱和浓度避开 NaCl 结晶干扰）。

### 质量与工具
- **GameTest 7/7**：成型/拒错/硫磺燃烧（含加热）/SO₂ 吸收/过滤/能力暴露/砖代理

- **run-server.sh**：冒烟脚本（透传输出、纯 PID 三级关闭、启动前截断日志防假阳性）
- **mc_source_forge_1.20.1**：Forge 47.4.0 反编译源码（5453 文件，含 net/minecraftforge），MC/Forge API 查询首选
- 开发环境完整入 server monorepo submodule（工程 + create/创想/匠魂/TFMG/柴油机 + mc_source×2）

## 修复记录（近期）

| 问题 | 根因 | 修复 |
|------|------|------|
| 客户端启动崩溃 `Registry Object not present` | DistExecutor 立即执行先于注册 | FMLClientSetupEvent |
| GUI 打不开 | `openMenu(MenuProvider)` 不带额外数据，菜单读 BlockPos 空缓冲区 | `NetworkHooks.openScreen` |
| GUI 容量恒 0（Jade 正常） | `sendBlockUpdated` 不同步 BE NBT | `getUpdatePacket`/`onDataPacket` + 显式推送 BE 数据包 |
| 砖块无法灌流体/加原料 | 结构砖无能力代理 | 砖 BE 代理到控制器（Create FluidTank 模式，7/7 含砖代理测试） |
| 反应永不匹配 | 罐存 Flowing 实例 vs 配方 Source 实例 | ReactorTank source 归一化 |
| 成型校验失败 | 内部空洞位置算错（`(s=0,d=1)` 非 `(s=0,d=0)`） | 修正环校验 |
| 容量 0 | `height-1` 公式错误 | 改为 `height` |
| 加热不生效 | 检测 `below()` 应为 `below(2)`（烧炉在结构底部之下） | 修正 |
| 服务端冒烟失败（dev） | Create mixin refmap + Mixinextras 缺失 | run 配置 + jarJar 依赖 |
| 冒烟假阳性 | 日志残留上次 `Done (` | 启动前截断日志 |
| 运行依赖 | 第三方 jar 直接丢 run/mods 在 dev 崩（refmap 不匹配） | CurseForge Maven `fg.deobf` 声明 |
| `reactorConsumesSolutionIngredient` 失败 | 浓/稀同离子签名，`concentrate` 与 `neutralize` 同时匹配、`findRecipe` 取先者 | `equivalentFromIons`/`drainSolution` 按溶剂水（`water/solventRatio`）限制折算，浓/稀配方可区分 |
| 创造栏溶液桶显示空方框 | 误用 `item/generated`（贴半透明样品瓶轮廓） | 改 `forge:fluid_container`（桶底+流体遮罩、`fluid: minecraft:empty`）+ 给溶液桶注册 `DynamicFluidContainerModel.Colors` 染色器 |
| 气体桶未倒置 | `forge:fluid_container` 默认不翻转 | 桶模型加 `"flip_gas": true`（Forge `DynamicFluidContainerModel` 对 lighter-than-air 流体翻转渲染） |
| 溶液桶倒不进反应釜 | 创造栏 `output.accept(item)` 生成的是无 NBT 的 `new ItemStack(item)`，`getDefaultInstance()` 的预填混合物没带出来 → 桶是空的 | 创造栏改 `output.accept(item.getDefaultInstance())`，预填容器直接携带流体 NBT |
| 溶液/浆料桶启动期空 | `SpeciesManager` 只在世界数据包重载时填充，创造栏/JEI 构建更早 → 桶空 | `SpeciesManager.loadBuiltin()` 启动期从自身资源预读 21 个内置物种 |
| 溶液/浆料桶全渲染成蓝水、无法区分 | 无色离子+水的混合色恒为淡蓝 | 物种加 `color` 字段（沿用注销前各自流体色），桶打包时写进 mixture 的 `Color` 键；悬停加 tooltip（`1000 mB`/`空`）诊断 |
| 抽分层流体釜停停走走、很慢 | `collapseIfNeeded` 每 tick 重建单成员相（derive 绝对量 → `Mixture.create` GCD 还原），总量不被比例和整除时 ratio tag 抖动，Create `isFluidEqual` 流身份被打破、流每 tick 被切断重启 | 单成员组分相**原样保留**（不重建）；`sameContents` 对比，无变化则跳过重写 + sync |
| 分液软管不渲染（釜里有液也不下垂） | `DecantHoseRenderer` 沿 DOWN 扫砖找釜，但砖 BE 的 `masterPos` 只在服务端成型时设置、从不同步到客户端——现场成型（不重新加载区块）时客户端砖 `masterPos=null`，渲染器找不到釜 → `offset=0` 软管收回 | 砖 BE 补 `getUpdateTag`/`getUpdatePacket`，`setMaster` 时广播 `ClientboundBlockEntityDataPacket`，客户端即时拿到 master 指针 |
| 破釜全量洒漏，重建后釜是空的 | `invalidateStructure` → `SpillLogic.queueFluids(tank)` 用 `it.remove()` 把釜清空 100% 转实体，违背 plans/04 §8.1「内部流体保留在 NBT，重建可恢复」 | 破口分级：保留破口以下体积（`capacity × ring/height`），只洒破口以上；控制器被拆时回退全量洒漏（保留份随控制器 NBT 消亡） |
| 重建变小后釜永久 OUTPUT_FULL 卡死 | `setCapacity` 只改数值不裁剪内容，`total > capacity` 时 `canFitOutputs` 恒 false | 重建装配时按比例抽出超量走 `SpillLogic` 渐进溢出（漏点=新内腔顶中心），釜恢复可反应 |
| 破坏/放回液面以上的墙砖时液面抽搐（总量明明不变） | `renderedLevel` 追的是**填充比例**，渲染高度=比例×内腔高：拆上方环砖釜缩层（高度/容量**瞬间**变）而总量不变 → 比例目标跳变，LerpedFloat 过渡帧渲染「旧比例×新高度」≠真实表面，液面先跌/先冲再回弹（Create FluidTank 追比例无此问题，因其几何永不变） | 改追**绝对液面高度**（fill×内腔高）：环数变化时目标恒为 `总量/(1000·(w−2)²)` 与高度无关，目标不动即零动画，只有真实进出料才缓动；渲染器/`getLiquidSurfaceY` 直接用绝对值；并按 FluidTank 模式首帧 `startWithValue` 定位真表面，消除区块加载从 0 升起的假动画 |
| 控制器装在非底层环时：液面/漂浮物偏高 `ringLayer` 格、软管悬在真实液面之上、控制器以下内腔层倒入的水不被吸收 | 渲染/液面数学/吸收轮询三处都以控制器自身层为 y=0 基准，而内腔底在控制器下方 `ringLayer` 格（Tinkers 式任意环层装配） | 新增统一基准 `getInteriorBottomRelY()=-ringLayer`：渲染两 pass `translate` 下移到内腔底（光照采样同步）、`getLiquidSurfaceY` 内腔底起算世界 Y、`absorbFromWorld` 轮询范围改 `[内腔底, 顶沿+1]` |
| 控制器抬高到非底层环后分液软管彻底不下垂（找不到釜） | `bindBricks` 绑定 y 范围写死 `-1..rings`（控制器在底层环的假设）：控制器在第 k 环时底砖在 `-k-1` 不被绑定，`findReactorBelow` 沿开口内腔柱下扫穿过未绑定底砖扫到釜底之下，永远找不到釜；上端还越过真实顶盖一层、错绑顶盖上方的游离砖 | y 范围改 `-ringLayer-1 .. rings-ringLayer`（与 k 无关）；`clearShellMasters` 半径改 `max(底宽, 环数)+1`（高瘦釜重绑/失效够得到旧底旧盖） |

## 待办 / 下一步

> **M3+ 开工顺序以 `plans/11-content-scope.md` §2 为唯一定义**（U1 容器状态层 → U2 FE 接线 → U3 模板抽取 → U4 竖窑 → …，单元序列见该文件 §2.1）。下列条目为现状索引。

1. **客户端实机验证**（用户）：护目镜 HUD 显示、釜内物品渲染（开口釜）、成型失败提示、开口/闭口切换、quickPlay 自动进档
2. **贴面仪表**：✅ S02 温度计、✅ S03 压力表（U1，仪表族基类 `AbstractVesselGaugeBlockEntity` 就位）——剩余 S04 浓度计 / S11 液位计（照基类复制即可）
3. **M3**：电解槽（FE）、吸收塔（塔式实例）、换热器、压缩机——氯碱 + 硫酸厂。电解槽拟要求**去离子水（纯净水）**投料（避免杂质副反应）；纯净水是**未来新物质**，判定在「浓度/杂质」层（非再注册一个 H₂O 流体物种），届时再落地
4. **M4 旗舰**：索尔维制碱闭环（吸收塔氨盐水 → 碳化 → 煅烧 → 氨回收）
5. **基础设施**：流体桶（S08）、GUI 美化、datagen 接入（配方/模型 provider）、Jade 集成（流体显示/温度/进度 tooltip）、JEI 配方展示
6. **混合物流体系统（Mixture）**：✅ 互溶性（D18）已落地——`miscibilityGroup` 声明式溶剂族、按组合并、按密度分相抽出。**剩余**：液-液分离手段见新增 **D18.5 分液软管** 条目；给 M9 加「不互溶共管=混液炸管」的输送约束
7. **已知限制**：沉淀池/过滤机无 GUI；方块纹理为程序生成色块；砖无连接纹理（多变体方案待做）；压力/相态/催化未实现（计划 M3+）。（~~反应热集总常数~~ ✅ U16 已改能量记账；底面尺寸已参数化为任意 W×W×H 3..7；轻相抽出已由 D18.5 分液软管落地）
8. **设计定案待实施（2026-08 讨论批，plans/11 §2.1）**：~~**U15 晶粒、投种与混合固体物品**~~（✅ 已完成，见「已完成明细」；~~MgCl₂/CaCl₂ 苦卤盐数据首位~~ 一并落地）；~~**U16 反应热能量记账**~~（✅ 已完成，见「已完成明细」——J/unit 账本 + ΔT=Q/(feedUnits×4.18) + 蒸发潜热自限 + deltaHeat 质量耦合）；~~**U16.5 湿饼夹带与洗涤**~~（✅ 已完成，见「已完成明细」——残液率夹带 + residue 母液相 + 再浆/置换洗涤两路 + 电导率计 S18 落地；否决共沉淀，03 §12）；~~**U17 分析化学层 + 终点控制**~~（✅ 已完成，见「已完成明细」——Kw 读数层常数 + S16/S04/S17 三表 + 试纸族 7 件 + SI 降级 + M08 终点结晶器）；远期弹性见 plans/11（rate 数据授权 / 底排口零头打包晶粒 / 萃取独立系统）。
9. **D18.5 分液（分液口 + 软管滑轮）**：✅ **已实现**——`decant_port`（壁块，只抽最重相，锁相）+ `decant_hose`（Create 软管滑轮装**开口釜上方** → Forge `EntityPlaceEvent` 转化为分液软管；`FLUID_HANDLER` 只抽最轻相/锁相，扳手切「只抽上层/全部抽」，敲掉/中键掉回原版 `create:hose_pulley`）。**视觉已实现**：`DecantHoseRenderer` 照抄 Create `AbstractPulleyRenderer`（coil 滚动 + 下垂 rope + magnet，复用 Create 的 hose_pulley 部分模型与 `HOSE_PULLEY_COIL` sprite shift），块体直接引用 `create:block/hose_pulley/block` 模型（占位贴图废弃）；软管 `offset`（BE 内 `LerpedFloat`，客户端 tick 用 `Chaser.EXP` 缓动追 `ReactorControllerBlockEntity.getLiquidSurfaceY`）从 0 **慢慢下放**到液面、液面升降自动跟随、无手动收放；转化瞬间播**铁砧放置音**（`SoundEvents.ANVIL_PLACE`）提示。**剩余**：Ponder 提示。详见 plans/05 §M7。

## 常用命令

```bash
./gradlew build              # 构建
./gradlew test               # 引擎 JUnit（composition 层，无需 MC 启动）
./gradlew runGameTestServer  # 102/102 自动化测试
./run-server.sh              # 服务端冒烟（自动关闭）
./gradlew runClient          # 客户端（自动进 "New World"，-PquickPlayWorld= 覆盖）
python3 tools/gen_species.py # 改物种后重新生成资源/注册代码
```
