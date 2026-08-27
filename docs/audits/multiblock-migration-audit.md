# 四类多方块迁移审计

> 日期：2026-08-26。范围：只审查现有源码与新计划的差距，不实施重构。新设计见 `plans/README.md`，完成态见 `docs/progress.md`。

## 1. 结论

现有代码已经有可靠的“方形空心容器结构基类”，但还没有真正的“四类多方块共用层”。`VesselBlockEntity` 同时承担了几何、`ReactorTank` 水相库存、物品库存、流体能力、液面渲染和液体洒漏，因此只能自然支持釜和当前简化池；塔的分段库存与炉的料层/熔体无法安全继承它。

迁移不应先写 `TowerBlockEntity extends VesselBlockEntity`。正确顺序是先把几何生命周期与过程库存分开，再让釜/池保留兼容适配，最后新增塔/炉过程状态。

## 2. 当前职责图

```text
VesselBlockEntity
├─ 方形壳体扫描、伸缩、主从绑定       ← 应进共用结构层
├─ 几何、开口/密封、渲染包围盒         ← 大部分共用，液面部分釜/池专属
├─ ReactorTank + ItemStackHandler       ← 过程库存，不应在共用根类
├─ Forge fluid/item capability          ← 应由端口/过程库存提供
├─ 破口按液面洒漏                       ← 釜/池策略，不是炉/塔通用
└─ NBT tank/items/geometry              ← 结构与过程存档混在一起

ReactorControllerBlockEntity
├─ 釜加热、压力、开口吸收世界流体/物品
├─ IPhreeqc/PhysicalSteps
├─ ChemicalReactionRecipe 进度状态
└─ HUD/状态/引擎 dump

CrystallizerControllerBlockEntity
└─ 釜式终点控制 + 冷凝水副罐

SettlingBasinBlockEntity
└─ 固定 3×3×1 容器 + 0.25 倍 FilteringLogic
```

## 3. 可直接保留的共用资产

### 3.1 结构生命周期

`tryAssemble`、伸缩、任意环层控制器、结构件 master 绑定、拆损清理和客户端同步已经有大量 GameTest 锁定，可以迁入新根类而保持算法不变。

### 3.2 几何坐标

`size/height/ringLayer/inward`、内腔底/顶坐标和渲染包围盒可保留为第一种 `CuboidShellTopology`。塔和炉首版也可以使用方形壳体，但池最终需要独立的宽浅约束。

### 3.3 结构错误

`AssembleResult/AssembleIssue` 值得保留，但错误集合需可扩展：塔增加内部件顺序/端口高度，池增加溢流/底排，炉增加耐火壳/熔体出口。

### 3.4 主从能力代理

`IMasterBound`、结构砖同步和惰性代理模式可作为四模板共同基础。代理目标应从“控制器万能 tank”迁到按端口请求的 capability provider。

## 4. 必须从共用根类移出的内容

| 当前内容 | 原因 | 新归属 |
|---|---|---|
| `ReactorTank tank` | 只适合均混多相液体 | `MixedProcessInventory`，釜/池持有 |
| `ItemStackHandler items` | 各模板槽位和端口语义不同 | 各过程库存或组合库存 |
| `fluidCap/itemCap` | 所有面暴露同一能力，绕过端口语义 | `ProcessPort`/模板端口路由 |
| 液面 `LerpedFloat` | 塔分段、炉料层/熔池渲染不同 | 釜/池渲染状态 |
| `absorbWorldContents` | 开口釜便利交互 | 釜式过程行为 |
| 按破口高度比例洒漏 | 不适合塔段、固体料层和熔体 | `BreachPolicy` 策略 |
| `tank/items` NBT 键 | 把过程格式写死在结构根类 | `structure` + `process` 分区 NBT |
| 全局 `INTERIOR_OVERRIDES` | 测试/模组全局可变，无法表达模板与部件约束 | 每模板 `InteriorPartPolicy` |

## 5. 配方耦合审计

`ChemicalReactionRecipe` 目前只有 Create 原料/产物、溶液输入输出、热级、时长与 `deltaHeat`。`ReactionLogic` 的入口类型是 `ReactorControllerBlockEntity`，直接调用其 tank/items/temperature；匹配不到结构能力、压力、内部件、材料、端口或运行模式。

后果：

- 任意釜只要原料与温度相符即可执行硫燃烧、气体吸收等互不相同的过程。
- 新塔/炉无法复用配方执行而不依赖反应釜类。
- 当前 item 输入固定每 ingredient 消耗 1 个，不能表达通用连续比例。
- `canFitOutputs` 只检查一个均混 tank 和 item buffer，不能检查塔顶/塔底、清液/底泥、熔体/炉渣出口。

## 6. 建议的代码接口

名称是草案，职责边界是本审计的结论。

```java
interface MultiblockController {
    StructureSnapshot structure();
    ProcessContext process();
    Set<ProcessCapability> processCapabilities();
    Optional<PortEndpoint> portAt(BlockPos pos);
}

interface StructureTopology {
    AssembleResult scan(StructureScanContext context);
    StructureSnapshot adopt(...);
    void bindParts(...);
    void invalidate(...);
}

interface ProcessInventory {
    void load(CompoundTag tag);
    void save(CompoundTag tag);
    void onCapacityChanged(StructureSnapshot old, StructureSnapshot next);
    BreachResult breach(BreachContext context);
}

interface ProcessContext {
    ProcessInventory inventory();
    ProcessReadings readings();
    ProcessOutputs simulate(ProcessRecipe recipe, long scale);
    void execute(ProcessRecipe recipe, long scale);
}
```

建议能力枚举首批只包含已经有明确消费者的项：

```text
MIXED_VOLUME, OPEN_TOP, SEALED, PRESSURIZED,
AGITATED, GAS_DISPERSED, CATALYST_BED,
SETTLING_AREA, CLEAR_OVERFLOW, SLUDGE_UNDERFLOW,
COUNTER_CURRENT, STAGED_CONTACT,
SOLID_BED, REFRACTORY_CHAMBER, MOLTEN_BATH
```

不要一开始做通用字符串万能能力。用稳定 enum/ResourceLocation 注册和结构化参数，例如 pressure limit、stage count、settling area。

## 7. 配方迁移格式

在现有 `chemical_reaction` 上增量增加可选结构要求，不创建“高压釜配方”“塔配方”等平行类型：

```json
{
  "requiredCapabilities": ["chemicaladdon:mixed_volume", "chemicaladdon:gas_dispersed"],
  "requiredParts": ["chemicaladdon:gas_distributor"],
  "conditions": {
    "temperature": {"min": 303, "max": 333},
    "pressureKpa": {"max": 300},
    "agitation": {"min": 0.5}
  }
}
```

旧 JSON 缺省 `requiredCapabilities=[mixed_volume]`，确保现有四条配方继续由釜执行。等数据迁移完成后再收紧硫燃烧等不应发生在普通釜中的过程。

执行器应从 `ReactionLogic(ReactorControllerBlockEntity)` 改成 `ProcessRecipeExecutor(ProcessContext)`。这是后续塔/炉复用配方管线的关键，但不应与第一步结构拆分同时大改。

## 8. 存档兼容方案

### 第一阶段：零格式迁移

- 保留 `VesselBlockEntity` 名称、公有 `getTank/getItems`、现有 NBT 键和注册 ID。
- 新增共用结构对象作为内部委托，不改变已有继承树。
- GameTest 和仪表继续工作。

### 第二阶段：双读单写

- 新控制器写 `structureVersion`、`structure` 和 `process` 子标签。
- 旧釜/池仍读取 `tank/items/size/height/...`；若新标签不存在，转换到新对象。
- 一到两个版本内保留旧键写出，确认生产存档迁移后再停止。

### 第三阶段：适配器

- `getTank()` 在釜/池返回 `MixedProcessInventory` 的兼容视图。
- 仪表从 `ReactorControllerBlockEntity` 类型判断迁到 `ProcessReadings`，但保留旧路径回退。
- 分液软管/端口迁到 `LiquidPhaseAccess`，避免误接塔/炉。

禁止直接改现有 NBT 键语义或删除 `crystallizer_controller`。结晶器先作为釜式预配置兼容类存在。

## 9. 池式真实差距

当前池固定 3×3、1 环、8 桶容量，使用 `FilteringLogic` 以过滤机 0.25 倍速度直接产 cake。它没有面积吞吐、顶部清液、底泥容量、夹带或排泥；所以只是慢过滤器，不符合新池计划。

池式施工前必须先把过程从 `FilteringLogic` 分开：沉降只迁移 `Suspended→Sediment` 并产生清液可抽取条件；滤饼物品仍由过滤机产生。否则池和过滤机没有玩法分工。

## 10. 釜式真实差距

- `stirringCoefficient()` 固定 1.0，没有动能网络与搅拌部件。
- 没有气体分布器能力；气体进入 tank 后自动参与内核。
- 催化剂与压力尚未进入配方匹配。
- 批次状态只有 `IDLE/RUNNING/OUTPUT_FULL/NEEDS_HEAT`，没有投料/反应/排空节拍端口。
- 连续流没有比例消耗和停留时间执行器。
- 结晶器的终点/冷凝逻辑在子类中已经证明“釜式预配置”可行，但还不是组合件。

## 11. 外部耦合与迁移成本

大量仪表、试纸、分液件、渲染器和 GameTest 直接依赖 `ReactorControllerBlockEntity` 或 `VesselBlockEntity.getTank()`。因此立即把 tank 从根类删除会造成大范围无价值 churn。

建议先引入三个窄接口并让现有类实现：

```text
LiquidProcessAccess  -> tank、液面、相位抽取
ProcessReadings      -> 温度、压力、pH、浊度、状态
StructureAccess      -> assembled、geometry、master binding
```

新代码只依赖窄接口；旧调用逐步迁移。待消费者清空后，才把 tank 从共用根类物理移除。

## 12. 第一批可施工任务

### A1：只加接口，不改行为

- 新增 `StructureAccess`、`LiquidProcessAccess`、`ProcessReadings`。
- `VesselBlockEntity`/`ReactorControllerBlockEntity` 实现适配。
- 迁移一个仪表和一个分液件作为样板。
- 所有现有测试应零行为差异。

### A2：结构能力快照

- 新增 `ProcessCapability` 与不可变 `StructureCapabilities`。
- 现有釜装配后生成 `MIXED_VOLUME`、开口/密封和容量参数。
- 只显示/测试，不参与配方，避免一次大切换。

### A3：配方可选要求

- `ChemicalReactionRecipe` 解析/序列化可选 `requiredCapabilities/requiredParts/conditions`。
- 缺字段完全保持旧行为。
- `ReactionLogic` 通过接口检查，但首批数据不迁移。

### B1：第一个真实组合件——搅拌头

- 使用 Create kinetic BE/shaft 模式，把有效转速转成有上限的搅拌系数。
- 装配扫描生成 `AGITATED` 能力和参数。
- 用一条现有反应/结晶测试证明它改变结果，而非只作为门锁。

### B2：气体分布器

- 端口/内件决定气体是否进入液相；无分布器时只进入头部气相和压力模型。
- 迁移 SO₂ 吸收或索尔维碳化作为第一条能力约束配方。

### B3：批次端口

- 液位、计量和状态信号先落地；不先写中央程控器。
- 用现有 Create 红石搭成可重复批次。

连续流应在 A/B 完成后单独设计，不与地基重构并行。

## 13. 不建议的做法

- 不新增一个包含 tank/items 的 `AbstractChemicalMultiblock` 再让四类继承。
- 不让塔和炉继承现有 `VesselBlockEntity` 后覆盖一半方法。
- 不一次性改 NBT、继承树、配方格式、仪表和所有测试。
- 不先创建空的 tower/furnace controller 注册来证明架构。
- 不把池面积工程化与过滤机 cake 逻辑继续绑在一起。

## 14. 审计后的开工门槛

下一次代码修改应只实施 A1。A1 完成并全测试通过后，再决定是否继续 A2；这让最危险的结构/过程解耦以可回滚的小步进行，同时为塔、池、炉提供真正可依赖的公共接口。
