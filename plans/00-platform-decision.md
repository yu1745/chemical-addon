# 00 · 平台决策：Create Forge 化学附属（已定）

> 文档状态：**current**（平台决策未变）

> 本文件记录化学附属的平台决策与理由。**决策已于 2026-09 生态盘点后定稿：Create Forge 6.0.8 原生附属**，见 [00-ecosystem-recon.md](00-ecosystem-recon.md)。

## 1. 决策：Create Forge 6.0.8 + createaddition 1.3.3

| 项 | 值 |
|----|-----|
| 目标平台 | **Forge 1.20.1（Create 6.0.8，Forge 47.x）** |
| 依赖 | `create-1.20.1-6.0.8`（硬）、`createaddition-1.20.1-1.3.3`（硬）、Fabric 生态不再考虑 |
| 生产服 | forge1 **已原生安装**两者（mods 目录实测），部署零风险 |
| IC2 关系 | 降级为可选联动（跨加载器，不承诺）；ic2-fabric 计划书存放于其仓库 `plans/chemical-addon/` |

## 2. 为什么不是 Create Fabric / 独立 IC2 附属

| 备选 | 否决/降级理由 |
|------|--------------|
| 独立 IC2 附属（v1 计划） | 需自建罐/管/加热/物流/多方块全套骨架（~70 方块），等于重写 Create；且 forge1 无 IC2 原生流体生态优势 |
| Create Fabric（0.5.1-j / 6.x） | forge1 已原生跑 Create **Forge**；Fabric 版经 Connector 转译属额外风险；mekanism-fabric 移植骨架虽含物质抽象，但加载器不匹配 |
| Create Forge + 自研全部化学管线 | 已选，但化学管线**只做状态敏感部分**（见 D12；物质模型后演进为 D19 离子基底），标准态液体/配方/热/物流全部复用 Create |

## 3. 复用边界速览（详见生态盘点报告）

- **直接用**：FluidTank 多方块、管道/泵/阀、Basin+Mixer 配方管线（ProcessingRecipe）、HeatCondition 热级、BoilerHeater 注册表、动能扩展点、物品物流（IItemHandler/传送带）、红石控制、Contraption、Registrate、创想附加（电动马达/电网/电池仓/BaseElectricBlockEntity/液体烈焰人）。
- **自研**：釜体（多方块处理机）、气体管/泵/阀（Create 6.0.8 **无气体系统**）、储罐（mixture 多相存储）、压缩机、换热器、电解槽、过滤机、蒸馏塔板、冷却塔、净化塔、仪表（S02–S04）、催化剂托盘、节拍控制件（S11–S15，见 13-flow-modes）（约 35–39 方块）。
- **不做**：石油/柴油/精馏主线（TFMG 与 Diesel Generators 已占据），主攻无机化工。

## 4. 工程形态（开放）

- 倾向：**独立仓库**（Forge mod，Kotlin 可选），与 ic2-fabric 解耦；
- 计划书暂存本目录（`ic2-fabric/plans/chemical-addon/`），定稿后随仓库迁移；
- 化学内容（物质目录/反应目录）加载器无关，迁移零成本。
