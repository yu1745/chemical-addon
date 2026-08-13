# 开发进度

> 最后更新：2026-08（M0–M2 完成，规则引擎 v1 + v2 离子基底 S1–S3f + D18 互溶性分相 + D18.5 分液速通档，47/47 GameTest 通过）
> 里程碑定义见 `plans/11-content-scope.md`；设计计划书主索引 `plans/README.md`。

## 状态总览

| 里程碑 | 内容 | 状态 |
|--------|------|------|
| M0 | 工程接入 + 61 物种 + 组合系统骨架 | ✅ 完成 |
| M1 | 釜体模板 + 反应引擎 + 首条产线（硫磺→稀硫酸） | ✅ 完成 |
| M2 | 过滤机 + 沉淀池 + 釜高度参数化 | ✅ 完成 |
| M2.5 | 釜可玩性改造：世界内交互基调落地（护目镜 HUD/诊断/槽位 GUI/成型反馈） | ✅ 完成 |
| M3+ | 电解/索尔维/高压/零排放 | ⏳ 未开始 |

**自动化测试**：`./gradlew runGameTestServer` → **47/47 通过**。

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

## 待办 / 下一步

1. **客户端实机验证**（用户）：护目镜 HUD 显示、釜内物品渲染（开口釜）、成型失败提示、开口/闭口切换、quickPlay 自动进档
2. **贴面仪表**：S02 温度计（Gauge 模式：贴面连接釜、温度/热级/红石阈值输出）——釜状态世界内化的下一步
3. **M3**：电解槽（FE）、吸收塔（塔式实例）、换热器、压缩机——氯碱 + 硫酸厂。电解槽拟要求**去离子水（纯净水）**投料（避免杂质副反应）；纯净水是**未来新物质**，判定在「浓度/杂质」层（非再注册一个 H₂O 流体物种），届时再落地
4. **M4 旗舰**：索尔维制碱闭环（吸收塔氨盐水 → 碳化 → 煅烧 → 氨回收）
5. **基础设施**：流体桶（S08）、GUI 美化、datagen 接入（配方/模型 provider）、Jade 集成（流体显示/温度/进度 tooltip）、JEI 配方展示
6. **混合物流体系统（Mixture）**：✅ 互溶性（D18）已落地——`miscibilityGroup` 声明式溶剂族、按组合并、按密度分相抽出。**剩余**：液-液分离手段见新增 **D18.5 分液软管** 条目；给 M9 加「不互溶共管=混液炸管」的输送约束
7. **已知限制**：沉淀池/过滤机无 GUI；方块纹理为程序生成色块；砖无连接纹理（多变体方案待做）；底面尺寸固定 3×3（参数化待做）；压力/相态/催化未实现（计划 M3+）；分相抽出目前只有底口重相（D18 `drain(int)`），轻相需 D18.5 分液软管
8. **D18.5 分液（分液口 + 软管滑轮）**：✅ **分液口已实现**——`decant_port` 方块（`vessel_walls` 壁块、非凸出、可接管），其 `FLUID_HANDLER` 只暴露最重相（锁相：首次出料锁最重相、抽干即停，浮球阀语义）；普通管道接砖 = 排空；`drainLightest` 保留（软管用）。**剩余**：软管滑轮转化新块（Forge `EntityPlaceEvent` 换 ID、继承 `HorizontalKineticBlock`、敲掉掉回原版）+ 软管追虚拟液面 + 扳手「只抽上层（锁相）/全部抽」两模式 + 咬合音效/Ponder。详见 plans/05 §M7。

## 常用命令

```bash
./gradlew build              # 构建
./gradlew runGameTestServer  # 41/41 自动化测试
./run-server.sh              # 服务端冒烟（自动关闭）
./gradlew runClient          # 客户端（自动进 "New World"，-PquickPlayWorld= 覆盖）
python3 tools/gen_species.py # 改物种后重新生成资源/注册代码
```
