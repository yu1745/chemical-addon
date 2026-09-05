# 第三方素材与代码归属

## Mozilla Rhino — MPL 2.0

PLC 的受限 JavaScript 模式内嵌 Mozilla Rhino 1.7.15
（<https://github.com/mozilla/rhino>，Mozilla Public License 2.0）。本工程通过
Gradle jar-in-jar 分发原始库，不修改 Rhino 源码；运行时使用安全标准对象、
`ClassShutter` 和指令观察器，只向玩家脚本暴露 PLC I/O 上下文。

## PHREEQC（USGS）— 公有领域

规则引擎 v2 的**质量作用/饱和指数语义**与平衡常数数值出处：
[PHREEQC](https://www.usgs.gov/software/phreeqc-version-3)（David Parkhurst &
Tony Appelo，美国地质调查局，美国政府公有领域软件，可自由使用与再分发）。

- **借鉴范围**：ion-association 水相模型的概念（speciation / 饱和指数
  SI = log(Q/K) / 沉淀析出至 Q = K 留饱和母液）、平衡条目的数据组织方式
  （每相/每物种自带一条溶解反应 + log_k）。**未复制任何代码**（PHREEQC 是
  C/C++、molality 单位体系，与本工程的「无量纲配方单元」约定不兼容）。
- **数值摘录**：物种 JSON `equilibria` 条目中的 log_k 取自 PHREEQC 附带
  数据库（phreeqc.dat / minteq 系）及其常见地球化学文献值的量级，
  按 plans/03 §8 的量纲约定（`c = 分子式单元/水 mB`，全局旋钮
  `LOG_K_OFFSET`）重新标定——数值非逐位照抄，仅保留相对量级与排序。
- 明确**不采用**其活度系数模型（Debye-Hückel/WATEQ/Pitzer）与 pe/Eh
  平衡红氧（否决理由见 plans/03 §8.1）。

## Tinkers Construct（匠魂）与 Mantle — MIT License

本模组的化学玻璃（`chemicaladdon:chemical_glass`）的连接纹理（connected textures）
实现与贴图来自以下项目，遵循其 MIT 许可证（版权所有 © 2022 SlimeKnights，
<https://github.com/SlimeKnights/TinkersConstruct/blob/1.20.1/LICENSE>）：

- **连接纹理模型机制**：vendor 自 Mantle（1.20 分支，
  <https://github.com/SlimeKnights/Mantle>）的
  `slimeknights.mantle.client.model.connected.*` 与配套工具类，位于本工程的
  `src/main/java/com/yu1745/chemicaladdon/client/connected/`。裁剪说明：
  去掉了染色/tint 支持（ColoredBlockModel）与依赖 access transformer 的代码
  （改用公开 API 等价实现），每个文件头部均保留原始出处注释。
- **贴图**：`assets/chemicaladdon/textures/block/clear_glass.png` 与
  `assets/chemicaladdon/textures/block/clear_glass/*.png`（15 张边角连接变体）
  逐字节复制自 Tinkers Construct 1.20.1 资源包的
  `assets/tconstruct/textures/block/clear_glass.png` 与
  `assets/tconstruct/textures/block/clear_glass/`。
- **方块行为参照**：`ClearStainedGlassBlock` / `ClearGlassPaneBlock`
  （Tinkers Construct）——`connected_*` 方块状态属性镜像模型加载器的邻居扫描，
  相邻同类玻璃之间不剔除面，使连接纹理可见。
