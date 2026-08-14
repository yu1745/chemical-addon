# 第三方素材与代码归属

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
