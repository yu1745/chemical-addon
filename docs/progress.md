# 开发进度

> 最后更新：2026-08（M0–M2 完成，7/7 GameTest 通过）
> 里程碑定义见 `plans/11-content-scope.md`；设计计划书主索引 `plans/README.md`。

## 状态总览

| 里程碑 | 内容 | 状态 |
|--------|------|------|
| M0 | 工程接入 + 61 物种 + 组合系统骨架 | ✅ 完成 |
| M1 | 釜体模板 + 反应引擎 + 首条产线（硫磺→稀硫酸） | ✅ 完成 |
| M2 | 过滤机 + 沉淀池 + 釜高度参数化 | ✅ 完成 |
| M2.5 | 釜可玩性改造：世界内交互基调落地（护目镜 HUD/诊断/槽位 GUI/成型反馈） | ✅ 完成 |
| M3+ | 电解/索尔维/高压/零排放 | ⏳ 未开始 |

**自动化测试**：`./gradlew runGameTestServer` → **9/9 通过**。

## 已完成明细

### M0 · 地基
- Forge 47.4.0 / Create 6.0.8-289 / parchment 2023.06.26 / Java 17 / CreateRegistrate 工程
- 38 种流体（13 气体=负密度流体 + 24 液体溶液 + 导热油）+ 18 种固体 item + 创造标签
- 组合系统骨架：`data/chemicaladdon/chemistry/species/*.json` 数据包加载（匠魂修饰器式），盐水/氨盐水组合示例
- `tools/gen_species.py` 单一数据源 → 注册代码/纹理/语言文件

### M1 · 釜体模板与反应引擎
- **釜体模板**：化工砖 + 控制器多方块，3×3 底 × 高 3–6（容量随内层数 16 桶/层），成型校验（4 面尝试、内部空心、拆砖失效）
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

## 待办 / 下一步

1. **客户端实机验证**（用户）：护目镜 HUD 显示、釜内物品渲染（开口釜）、成型失败提示、开口/闭口切换、quickPlay 自动进档
2. **贴面仪表**：S02 温度计（Gauge 模式：贴面连接釜、温度/热级/红石阈值输出）——釜状态世界内化的下一步
3. **M3**：电解槽（FE）、吸收塔（塔式实例）、换热器、压缩机——氯碱 + 硫酸厂
4. **M4 旗舰**：索尔维制碱闭环（吸收塔氨盐水 → 碳化 → 煅烧 → 氨回收）
5. **基础设施**：流体桶（S08）、GUI 美化、datagen 接入（配方/模型 provider）、Jade 集成（流体显示/温度/进度 tooltip）、JEI 配方展示
6. **已知限制**：沉淀池/过滤机无 GUI；方块纹理为程序生成色块；砖无连接纹理（多变体方案待做）；底面尺寸固定 3×3（参数化待做）；压力/相态/催化未实现（计划 M3+）

## 常用命令

```bash
./gradlew build              # 构建
./gradlew runGameTestServer  # 7/7 自动化测试
./run-server.sh              # 服务端冒烟（自动关闭）
./gradlew runClient          # 客户端（自动进 "New World"，-PquickPlayWorld= 覆盖）
python3 tools/gen_species.py # 改物种后重新生成资源/注册代码
```
