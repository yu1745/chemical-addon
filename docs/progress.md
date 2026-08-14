# 开发进度

> 最后更新：2026-08（M0–M2.5 完成，规则引擎 v1 + v2 离子基底 S1–S3g + D18 互溶性分相 + D18.5 分液口/软管 + S02 温度计（双形态）+ **U1 容器状态层**（多相加热/放热全体/压力模型/S03 压力表/datagen 修复），71/71 GameTest 通过）
> 里程碑定义见 `plans/11-content-scope.md`；设计计划书主索引 `plans/README.md`。

## 状态总览

| 里程碑 | 内容 | 状态 |
|--------|------|------|
| M0 | 工程接入 + 61 物种 + 组合系统骨架 | ✅ 完成 |
| M1 | 釜体模板 + 反应引擎 + 首条产线（硫磺→稀硫酸） | ✅ 完成 |
| M2 | 过滤机 + 沉淀池 + 釜高度参数化 | ✅ 完成 |
| M2.5 | 釜可玩性改造：世界内交互基调落地（护目镜 HUD/诊断/槽位 GUI/成型反馈） | ✅ 完成 |
| U1 容器状态层 | 多相加热 / 放热全体 / 压力模型 / S03 压力表 | ✅ 完成（M3 首单元） |
| M3+ 其余 | FE 接线 / 模板抽取 / 竖窑 / 索尔维 / 连续流 / 高压 / 零排放 | ⏳ 未开始（顺序见 plans/11 §2.1） |

**自动化测试**：`./gradlew runGameTestServer` → **71/71 通过**。

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

- **设计基调落地**：交互哲学写入 AGENTS.md（世界内交互优先、GUI 弱化，生态证据见 plans/00-ecosystem-recon.md §7：Create 本体 ~40 GUI 类全为配置/物品用途、createaddition 0 GUI、TFMG 仅 2 配置 GUI、New Age/Broken Bad/Estrogen 0 GUI）
- **护目镜 HUD**：釜实现 `IHaveGoggleInformation`——戴护目镜看控制器显示：温度+热级（无/加热/超级加热）、诊断状态、多流体内容、物品、反应进度（Create 标准通道）
- **诊断状态**：`ReactorStatus` 枚举——未成型/反应中/温度不满足/输出已满/无匹配配方（每 10 tick 自动判定，原因可诊断）
- **成型反馈**：`tryAssemble()` 返回结构化结果（面+问题类型+坐标），失败 chat 报具体缺砖位置；成功音效+粒子
- **GUI 取消（彻底）**：釜无任何 GUI——物品渲染进釜内（Create Basin 模式：环形悬浮+旋转+堆叠散落，控制器 Renderer 实现），存取走漏斗/管道；右键改为世界内 chat 诊断；ReactorMenu/Screen/AllMenuTypes 删除
- **釜升级 SmartBlockEntity**（Create 基类：行为系统、自动序列化 write/read、CachedRenderBB）——后续仪表/ValueSettings 直接挂 behaviour
- **开口/闭口变体**：成型时顶面全封=闭口、全空=开口（内部物品从上方可见）、半封=报错；控制器 blockstate `open` 属性+纹理变体（开口金边）
- **修复真 bug**：釜容量不持久化（高釜重进世界回退 16 桶）→ tankCapacity 入 NBT
- 测试 7/7 → **11/11**（新增：容量序列化往返、诊断状态 NO_RECIPE/TEMPERATURE、开口成型、半封顶拒绝）
- 环境：`~/.gradle/gradle.properties`（用户级，不进 git）覆盖 Linux JDK 路径 + 代理 192.168.5.138:7777；build.gradle 补 maven.minecraftforge.net 仓库（ForgeAutoRenamingTool）

### 规则引擎 / 涌现化学 v1（plans/06 §9）

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
- **颜色修正**：无色离子/纯水 tint 从 `0xFFFFFFFF`（不透明白）→ `IonColors.CLEAR_TINT = 0x28FFFFFF`（淡白 ~16%）——纯水与 CaCO₃ 混悬白可分辨，且液面仍可见（非全透明）。
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
- **规则引擎相位化**：`RulesEngine.apply` 只求解 aqueous 相，气体/非极液体作为旁观相不读入也不写回（离子不跨相界，plans/06 §9.6）；`setContents` 写回后重新 append 旁观相。
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

### 反应釜结构生命周期（自动扩展 / 破口分级洒漏 / 残液可见 / 缩小溢流）

统一原则：**内容物（流体）是结构层面的持久资产——结构变动只做迁移，不做删除**（除 <1 桶的按块量化损耗）。

- **自动扩展**：`ChemicalBrickBlock.onPlace` 对已装配控制器调用新增 `tryExtend(placedPos)`——放一块结构方块到釜旁即重校验，仅在**严格更大**（或 open 状态变化，如封顶）时采纳；不缩、不换朝向、不洒内容（Create FluidTank「放相邻块即长大」心智：先小后大、逐步加高）。
- **拆砖分级处置（放/拆都不抽搐）**：`ChemicalBrickBlock.onRemove` 改走新增 `handleStructuralBlockRemoved`——拆**已绑定**砖按破坏性从小到大：①完整重校验出合法壳（更小/同形状）→ 采纳；②拆**顶盖层**砖 → **高度不变、只变敞口**（盖子层废弃、砖解绑成游离，釜高度=环数≠盖子）；③拆**最高环层**砖 → `tryShrink` 降一层（废弃顶盖+最高环）；④都不行才 `invalidateStructure` 分级洒漏。`tryAssemble` 加 `allowShrink` 参数：**放砖路径禁止收缩**（封顶半程不许把釜拽矮，消除"先长高又缩回"的抽搐），拆砖路径允许。平局守卫 `<=`→`==`（体积平局且 open 不变才保持）。收缩/扩展采纳时先 `clearShellMasters` 再重绑（掉出壳的砖停止代理）。
- **破口分级洒漏**：`invalidateStructure` 按破口环层算保留量 `capacity × ring/height`——破口以上洒出、以下留釜内（兑现 plans/10 §2.2「内部流体保留在 NBT，重建可恢复」）；**控制器被拆时回退全量洒漏**（保留份存控制器 NBT、随方块消亡会凭空消失）；底破=全洒；顶盖层拆砖走收缩（不再走失效）。
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
| 破釜全量洒漏，重建后釜是空的 | `invalidateStructure` → `SpillLogic.queueFluids(tank)` 用 `it.remove()` 把釜清空 100% 转实体，违背 plans/10 §2.2「内部流体保留在 NBT，重建可恢复」 | 破口分级：保留破口以下体积（`capacity × ring/height`），只洒破口以上；控制器被拆时回退全量洒漏（保留份随控制器 NBT 消亡） |
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
7. **已知限制**：沉淀池/过滤机无 GUI；方块纹理为程序生成色块；砖无连接纹理（多变体方案待做）；压力/相态/催化未实现（计划 M3+）。（底面尺寸已参数化为任意 W×W×H 3..7；轻相抽出已由 D18.5 分液软管落地）
8. **D18.5 分液（分液口 + 软管滑轮）**：✅ **已实现**——`decant_port`（壁块，只抽最重相，锁相）+ `decant_hose`（Create 软管滑轮装**开口釜上方** → Forge `EntityPlaceEvent` 转化为分液软管；`FLUID_HANDLER` 只抽最轻相/锁相，扳手切「只抽上层/全部抽」，敲掉/中键掉回原版 `create:hose_pulley`）。**视觉已实现**：`DecantHoseRenderer` 照抄 Create `AbstractPulleyRenderer`（coil 滚动 + 下垂 rope + magnet，复用 Create 的 hose_pulley 部分模型与 `HOSE_PULLEY_COIL` sprite shift），块体直接引用 `create:block/hose_pulley/block` 模型（占位贴图废弃）；软管 `offset`（BE 内 `LerpedFloat`，客户端 tick 用 `Chaser.EXP` 缓动追 `ReactorControllerBlockEntity.getLiquidSurfaceY`）从 0 **慢慢下放**到液面、液面升降自动跟随、无手动收放；转化瞬间播**铁砧放置音**（`SoundEvents.ANVIL_PLACE`）提示。**剩余**：Ponder 提示。详见 plans/05 §M7。

## 常用命令

```bash
./gradlew build              # 构建
./gradlew runGameTestServer  # 71/71 自动化测试
./run-server.sh              # 服务端冒烟（自动关闭）
./gradlew runClient          # 客户端（自动进 "New World"，-PquickPlayWorld= 覆盖）
python3 tools/gen_species.py # 改物种后重新生成资源/注册代码
```
