# AGENTS.md - chemical-addon（Create 化学附属）

本仓库是 **Create Forge 6.0.8 的无机化工附属**（Forge 1.20.1，Java 17），设计计划书在 `plans/`（主索引 `plans/README.md`，平台决策 `plans/00-platform-decision.md`，生态盘点 `plans/00-ecosystem-recon.md`）。

## 核心架构（改动前必读 plans/03-substance-model.md）

- **全量流体注册**：所有化学物种注册为 Forge Fluid（气体=负密度流体），运输/储存层复用 Create 管道/罐。
- **组合系统**：多组分溶液（氨盐水=水+NaCl+NH₃）用数据驱动组合表达，借鉴匠魂 JSON 修饰器架构（JSON 定义 + ResourceLocation id + 轻量条目）。
- **釜内流股**：只有进入反应釜（自研多方块）才使用流股表示（进度/中间态/温度/压力/催化）。
- 自研多方块模板（釜/塔/池）是后续所有容器结构的范本（plans/10-multiblock.md）。

## 构建

```bash
./gradlew build          # 编译 + 打包（JDK 17 已由 gradle.properties 的 org.gradle.java.home 钉定）
./gradlew runData        # 未来 datagen（配方/模型 provider 接入后使用）
```

- 依赖：Forge 47.4.0、Create 6.0.8-289（`:slim`，maven.createmod.net）、Registrate MC1.20-1.3.3、Ponder、Flywheel、JEI（compileOnly）。
- 上游参考：`/home/wangyu/server/develop/create-forge_1.20.1`（Create 本体源码）、`createaddition-forge_1.20.1`（addon 工程模板）、`TinkersConstructForge`（组合系统参考）、`create-tfmg-forge_1.20.1`（蒸馏塔/储罐结构参考）、`mc_source_1.20.1_neoforge`（vanilla 1.20.1 反编译源码，官方映射命名，与本工程 parchment 命名一致，查 MC 类直接 grep 这里）。

## 物种资源生成（重要）

**不要手写** 流体/固体注册代码、纹理、语言文件——单一数据源在 `tools/gen_species.py`（38 流体 + 18 固体），修改物种先改该脚本的数据表，再运行：

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

## 规范

- 反应配方未来接入 Create ProcessingRecipe 管线（自定义 RecipeType `chemical_reaction`），不手写分散配方入口。
- 生产服 forge1 已装 Create 6.0.8 + createaddition 1.3.3；本 mod 上线走 `deploy-waiting/forge1/` 部署流程（见 server/AGENTS.md），**未经要求不得重启服务器**。
- 提交前：`./gradlew build` 必须通过。
