# AGENTS.md - chemical-addon（Create 化学附属）

本仓库是 **Create Forge 6.0.8 的无机化工附属**（Forge 1.20.1，Java 17），设计计划书在 `plans/`（主索引 `plans/README.md`，生态盘点与平台决策 `plans/00-ecosystem-recon.md`），**开发进度在 `docs/progress.md`**（M0–M2.5 + **U1 容器状态层** 完成（多相加热/放热全体/压力模型/S03 压力表），71/71 GameTest 通过；**M3+ 开工顺序以 `plans/11-content-scope.md` §2 为唯一定义**；改代码前先看它了解现状）。

## 核心架构（改动前必读 plans/03-substance-model.md）

- **离子基底单一混合物**：溶液/浆料只注册一个 `chemicaladdon:mixture` 元流体，FluidStack NBT 承载四个域——`Molecules`（分子物种）+ `Ions`（电中性离子多重集，硬不变量）+ `Suspended`（悬浮固相=浆料）+ `Sediment`（沉底固相=降温结晶沉底）。纯物质（水、13 气体、导热油）照旧注册 Forge Fluid（气体=负密度）。
- **物种 = 模式**：species JSON 是「命名组成模式」，只用于配方匹配/名字/颜色/创造栏桶默认配比，**不参与釜内存储与显示**。浓/稀是**连续浓度**（离子单位/水单位，运行时算），不是身份、不做二值判断。
- **沉淀先入釜内悬浮**：规则引擎（crystallise→neutralise→precipitate）把沉淀写入 `Suspended` 域成浆料、析晶沉入 `Sediment` 域；过滤机/沉淀池抽走固相域吐固体 item。
- 自研多方块模板（釜/塔/池）是后续所有容器结构的范本（plans/10-multiblock.md）。

## 构建

```bash
./gradlew build          # 编译 + 打包（JDK 17 已由 gradle.properties 的 org.gradle.java.home 钉定）
./gradlew runData        # 未来 datagen（配方/模型 provider 接入后使用）
./run-server.sh          # 冒烟测试：启动 dev 服务器、输出透传，识别到 "Done (" 后
                         # 三级关闭（组 SIGTERM → 轮询 → SIGKILL）并退出 0；
                         # 环境变量 WAIT_DONE_TIMEOUT / SHUTDOWN_GRACE_SECONDS
./gradlew runGameTestServer   # 跑 GameTest（chemicaladdon/gametest/，71 个测试，需先 build）
```

> ⚠️ runServer 永不自行返回：不要用 `cmd | script` 或 `cmd && script` 形式调用；`run-server.sh` 自行负责启动与收尾。关闭策略为纯 PID 方案（`$!` → PGID → 整组信号），**禁止**在脚本里用进程名/路径匹配（会误伤容器内的生产服 forge1 JVM）。

### 运行时依赖（mod）怎么加

- **不要**把第三方 mod 的原版 jar 直接丢进 `run/mods/`：dev 环境是 parchment 映射，第三方 mod 打包的 mixin refmap（官方映射）对不上，运行时崩（如 Jade 的 `StringRenderOutputMixin @Shadow f_92940_`）。
- 正确方式：在 `build.gradle` 用 `runtimeOnly(fg.deobf("..."))` 声明，由 ForgeGradle 反混淆（JEI 用官方 maven，Jade 用 CurseForge Maven `curse.maven:jade-324717:8479276`）。
- Jade 是客户端 mod：服务端测试不加载它（服务端启动正常），真正的 mixin 仍可能只在 `runClient` 触发；若 dev 客户端仍崩，回退到不在 dev 加 Jade（生产服 forge1 已原生运行，不受影响）。

- 依赖：Forge 47.4.0、Create 6.0.8-289（`:slim`，maven.createmod.net）、Registrate MC1.20-1.3.3、Ponder、Flywheel、JEI（compileOnly）。

## 参考成熟实现（重要规则）

**本模组是 Create 的原生附属：Create 及其依赖库（catnip 等）就是我们的基础设施，能依赖的都应依赖，不要自己重新实现。** 写机制时优先考虑直接继承/复用 Create 基类与工具（SmartBlockEntity/SmartBlockEntityRenderer、LerpedFloat、AnimationTickHolder、CreateLang、IHaveGoggleInformation、ValueSettingsBehaviour、ProcessingRecipe 管线等），不要重复造轮子；自研只覆盖 Create 没有的部分（多流体流股、反应引擎、化学物种）。

**写任何机制/结构/能力对接之前，先读成熟 mod 的同款实现，照它的模式写，不要自己发明。** 尤其是多方块、能力代理、网络同步这类容易踩坑的领域。已参考过的成熟实现：

| 参考源 | 位置 | 参考内容 |
|--------|------|---------|
| **Create 本体** | `../create-forge_1.20.1/` | 多方块能力代理（FluidTank 的 `handlerForCapability` 惰性递归到 controller、每个结构方块都是 BE）；流体管道端点识别（`FluidPropagator.hasFluidCapability`）；ProcessingRecipe 配方管线；动能/热级/BoilerHeater 对接 |
| **Create Crafts & Additions** | `../createaddition-forge_1.20.1/` | addon 工程模板（build.gradle 依赖、run 配置）、`BaseElectricBlockEntity` 耗电机器基类、电动马达/电网对接 |
| **Tinkers Construct（匠魂）** | `../TinkersConstructForge/` | JSON 修饰器组合机制（数据驱动+可组合+可扩展，化学组合系统架构来源）；熔炼炉多流体罐（SmelteryTank List<FluidStack>）；多方块主仆（ServantTileEntity masterPos 模式，注意其结构件不代理能力，代理参考 Create） |
| **Create TFMG** | `../create-tfmg-forge_1.20.1/` | 蒸馏塔/钢储罐/工业机器多方块结构参考 |

> 规则：新增机制前先到上表找对应实现读一遍；若参考了新的 mod 实现，把参考源追加到本表。

## 源码参考（MC / Forge API）

- **`../mc_source_forge_1.20.1/`**（本机相对路径：`/home/wangyu/server/develop/mc_source_forge_1.20.1/`）——Forge 47.4.0 反编译源码，official+parchment 2023.06.26 命名，含 `net/minecraft`（Forge patch 版）与 `net/minecraftforge`（Forge API），**与本工程编译环境逐字节一致**。查 MC 类、Forge API（Capability/IFluidHandler/NetworkHooks/事件）的精确签名与实现时，**直接 grep 这里**（5453 个 Java 文件）。重生成方法见下方上游参考条目的 mc_source_forge 说明。
- `../mc_source_1.20.1_neoforge/`——vanilla 1.20.1 反编译源码（官方映射命名），仅 net/minecraft，作交叉参考。
- 上游参考：`/home/wangyu/server/develop/create-forge_1.20.1`（Create 本体源码）、`createaddition-forge_1.20.1`（addon 工程模板）、`TinkersConstructForge`（组合系统参考）、`create-tfmg-forge_1.20.1`（蒸馏塔/储罐结构参考）、**`mc_source_forge_1.20.1`**（Forge 47.4.0 反编译源码，official+parchment 2023.06.26 命名，含 net/minecraftforge Forge API 源码，与本工程编译环境逐字节一致——查 MC/Forge API 类首选，5453 个 Java 文件；由本工程构建产物经 Vineflower 产出，如需重生成：`/tmp/chemical-addon-shell` 空壳工程 + `java -jar vineflower.jar` 反编译 `~/.gradle/caches/forge_gradle/minecraft_user_repo/.../forge-1.20.1-47.4.0_mapped_parchment_2023.06.26-1.20.1.jar`）。

## 物种资源生成（重要）

**不要手写** 流体/固体注册代码、纹理、语言文件——单一数据源在 `tools/gen_species.py`（15 纯流体 + 18 固体 + 5 方块），修改物种先改该脚本的数据表，再运行：

```bash
python3 tools/gen_species.py
```

会重新生成：`registry/AllFluids.java`、`registry/AllItems.java`、纹理 PNG、item 模型、zh_cn/en_us 语言文件。

## 物种定义 JSON（数据包）

- 位置：`src/main/resources/data/chemicaladdon/chemistry/species/*.json`（可被数据包覆盖，/reload 生效）。
- schema 见 `composition/Species.java`（formula/phase/boilingPointC/meltingPointC/components/maxConcentration/dangers）。
- 组合示例：`brine.json`（water+rock_salt）、`ammoniated_brine.json`（brine+ammonia）。

## 注册约定

- 方块/物品/流体用 CreateRegistrate（`ChemicalAddon.registrate()`），创造模式标签自动收录本 mod 命名空间物品。
- 流体用 `REGISTRATE.standardFluid(id, props -> new ChemFluidType(props, id, isGas))` + `.properties(b -> b.density/viscosity/temperature)`；纹理约定 `textures/fluid/<id>_still.png` / `_flow.png`。

## 设计基调：世界内交互优先，GUI 弱化（全模组铁律）

> **有生态源码证据支撑，不是拍脑袋**：Create 本体 ~40 个 GUI 类全部是物品/物流/配置用途、createaddition 0 GUI、TFMG 仅 2 个配置 GUI、New Age/Broken Bad/Estrogen 0 GUI、Big Cannons/Steam 'n' Rails 仅在引信/物品栏有例外；匠魂重 GUI 但也提供世界内 gauge。完整证据见 `plans/00-ecosystem-recon.md §7`。任何新机制/新方块在动手前先过一遍下面四条。

1. **状态展示 = 世界内仪表，不是 GUI 面板**。温度/容量/进度/压力等状态用贴面仪表方块展示（参照 Create Gauge 转速/应力表、createaddition EnergyMeter、TFMG VoltMeter 模式：贴面连接结构、读内部状态、可红石输出/阈值报警，如 S02 温度计/S03 压力表/S04 浓度计）。机械信息同步走护目镜 HUD（Create `IHaveGoggleInformation` 标准通道，实现接口即自动获得）。GUI 不承担信息面板职责。
2. **无配方选择**。反应引擎保持自动匹配（匹配到什么就是什么）；玩家通过控制输入（投什么料、加热到多少度、加压多少）定向反应。匹配失败必须可诊断：用状态渲染（仪表/指示灯/粒子）表达「为什么没反应」（缺料/温度不足/无匹配配方/产物满），不做配方选择列表。
3. **控制 = 世界内/物理表达，不做 GUI 数字输入**。连续物理量（温度/转速/应力/功率）由物理装置决定（热源数量与燃烧等级、压缩机/冷却管道、电位器式方块）；离散数值参数（阈值/时序）用 Create `ValueSettingsBehaviour`/`ScrollValueBehaviour` 世界内滚动调值（对准方块滚动+右键，浮层显示数值），不写 GUI 数字输入框/slider。
4. **例外仅三类，均有功能理由**：①无物理表达手段的精确数值（如引信延迟秒数）允许 ScrollInput 滑条；②物品存取槽位（Create 风格槽位交互）；③创建期一次性选型界面。

> 注意：此基调修订了 plans/04-machines.md 中 S01 控制面板的旧规格（"配方选择、参数设定（温度目标/压力上限/搅拌转速）"）——配方选择改为自动匹配+诊断渲染，参数设定改为世界内物理表达。后续实现以本文为准。

## 规范

- 反应配方未来接入 Create ProcessingRecipe 管线（自定义 RecipeType `chemical_reaction`），不手写分散配方入口。
- 生产服 forge1 已装 Create 6.0.8 + createaddition 1.3.3；本 mod 上线走 `deploy-waiting/forge1/` 部署流程（见 server/AGENTS.md），**未经要求不得重启服务器**。
- 提交前：`./gradlew build` 必须通过。
