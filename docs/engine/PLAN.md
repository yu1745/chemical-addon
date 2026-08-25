# 综合开发计划：热力学数据 + 氧化还原（chem-engine）

> 📍 **本文档是 chem-engine（化学内核）的计划书**，与 mod 侧计划（`plans/`，主索引 plans/README.md）分离维护；引擎完成态明细见同目录 development_status.md / known_limitations.md。
> 🏠 **正本**：chem-engine 已 vendor 并入本仓库（commit c988ea9，原 chem-engine 仓库封存），本文即唯一正本。
> 📌 **结构注记（2026-08）**：Tracks A–F/E 已完成或封存（历史记录保留于下文）；**当前主线是 Track G（IPhreeqc 内核嵌入）**；mod 侧接线待办见文末「下一步：mod 侧」。工业原料蓝图（原 Track C 总表）的 mod 侧唯一定义已迁 `plans/14-process-facility-map.md`（§0 归并方法/§2 工艺线）。

> 原 HANDOVER.md 的交接要点已并入本文，不再单独维护。

## 项目定位（一句话）

Minecraft `chemical-addon` mod 的化学内核库：**IPhreeqc（CC0）原生求解器 + JNA 绑定 + Java 游戏语义门面**
（quanta 整数网格、电解/熔融计量推进、mod 物种映射）。mod 本身只做渲染/MC 交互壳。

---

## Track G（现行主线）：IPhreeqc 内核嵌入 + JNA 绑定

> 决策（2026-08-17）：自研 Solver 封存于 tag `self-engine-final`（103 commits，155 测试，known_limitations §7–§10 未收敛）。
> §7/§8/§10 的 e-/pe 病态、§9 相竞争、复合收敛失败，本质都是"PHREEQC 系引擎 40 年数值积累"的问题；
> 移植/嵌入现成内核取代自研。选型依据见下，数据资产（tools/*.dat、species/*.json、场景断言）全部继续服役。

### G0 技术选型（已定）

- **内核**：iphreeqc（github.com/usgs-coupled/iphreeqc @ master，CC0/public domain），
  `cmake -DBUILD_SHARED_LIBS=ON` 出动态库；本机 MinGW gcc 14.2 编译一次，之后只在升级时重编
- **绑定**：JNA 5.16——MC 1.20.1 = Java 17，Panama/FFM 不可用；API 面 ~10 个纯字符串函数，
  事件驱动调用频率下 JNA 开销不可见；Java 侧以 `ChemKernel` 薄接口留 FFM 后门
- **数据库**：sit.dat（ThermoChimie 12a，取自官方 phreeqc3 仓库 database/）——唯一同时具备
  `Cl2(aq)`（`= -2e- + 2Cl-`）与 `S(+4)/SO3-2`、`O2(g)/O2` 的库；HOCl/OCl- 全官方库皆无，
  以文献常数自补（E°(HOCl/Cl⁻)=1.482 V、pKa(HOCl)=7.53），即 `ChemistryAddenda`
- **分发**：单 fat jar，`native/<plat>/iphreeqc.{dll,so,dylib}` 运行时解压 System.load；
  6 平台 CI（win/linux/macos × amd64/arm64，GitHub Actions 原生 runner，公开仓库免费）
- **兼容层沿用**：电解→`REACTION` 受迫进度+平衡联立（同 D1b 语义）、熔融/非水相计量模块独立、
  quanta 整数投影层移植到新内核输出上

### G1 里程碑

1. **G1a ✅**（2026-08-17）：native 加载 + JNA 绑定 + sit.dat(+addendum) 装载 + 纯水 speciation 全通；
   win-x86_64 用本地 /c/mingw64 构建（静态运行时，单文件自包含），其余 5 平台二进制来自 Docker 交叉矩阵
   （`native/<plat>/`，gitignore；含上游 fwrap !_WIN64 修复）
2. **G1b ✅ 三场景验收**（对齐旧引擎点名场景，全部通过）：
   - FeCl₂ 通 Cl₂：元素总量输入 + `REACTION Cl2` → **Fe(2)=6.000 / Fe(3)=4.000 mmol**（电荷平衡精确锁定化学计量，旧 §8 场景）
   - 亚硫酸通 O₂：`S 20 + pe -4` 初始还原态 + `EQUILIBRIUM_PHASES O2(g)` → **S(6)=20.000 mmol**（气液界面 + 跨价态氧化）
   - 次氯酸钠漂白液：介稳价态池 `Cl(1) 50 + Cl(-1) 100 + Na 150, pH charge` → **OCl⁻=49.87 mmol**（验证自补物种）
3. **G1c ✅**（2026-08-17）化学容器门面：
   - `ChemState`（builder：mol 总量 + pH 策略 + pe/temp/waterKg ↔ SOLUTION 脚本；`fromDump` 解析 SOLUTION_RAW）；
   - `IPhreeqc.equilibrate(state, watch...)`（SELECTED_OUTPUT + high_precision，12+ 位有效数字）；
   - `archive()`/`runRestored()`：DUMP SOLUTION_RAW 全精度零漂移存档/恢复（恢复不重算；读回需 USE+触发器）；
   - `Quanta`：1 quanta = 1e-7 g（旧引擎兼容），mB↔mol 确定取整，端到端装配-平衡-守恒投影测试全绿。
   footgun 记录：pH charge 需起始值（`pH 7 charge`）；DUMP -solution 不接受 all；REACTION 剂量 nmol 非法单位静默按 mol；punch 默认精度截断整数守恒（需 -high_precision）
4. **Track C 迁移第一批 ✅**（2026-08-17，`IndustrialAcceptanceTest` 4 场景全绿）：
   - 盐酸+烧碱中和：等摩尔 pH=6.998（旧引擎最经典）；
   - 索尔维碳化：饱和盐水 4.4 molal + 氨过量 + CO₂(g) 0.8（≈6 atm）+ 15°C → Nahcolite 沉淀 3.45 mol（78% 钠转化，真实水平），钠守恒);
     稀盐水/常压 CO₂ 不沉（实测确认：浓度与分压是沉淀驱动力，旧蓝图参数过稀）
   - 氯碱电解：REACTION 元素级受迫进度 `Cl -2 H 2 O 2`（H₂/Cl₂ 隐式逸出）→ Cl 1.000→0.500 线性、OH⁻ 精确互补、Na⁺ 旁观、pH→13.5（D1b 语义一步到位）；
   - 石灰石酸浸：Cl 100 mmol + pH charge = 精确 100 mmol HCl，方解石计量溶解 Ca=57.2 mmol（闭合体系 HCO₃⁻ 主导时边际 ~1 H⁺/CaCO₃），C==Ca 守恒。
   教训：定值 pH 的 NaOH 进料电荷失衡（pH 12 ≠ 50 mmol OH⁻，需 pH charge 涌现）；Pyrolusite 不在 sit.dat（MnO₂+HCl 场景待补数据）
5. **G1c 后续**（待做）：策展表 schema（伪元素注册 + KINETICS 白名单 + k 值 → resources 数据文件）与 Track C 剩余蓝图迁移；DUMP↔NBT 序列化接 mod 层
6. **G1d ✅**（2026-08-17，收官）：
   - `fatJar` 任务：自包含 uber-jar（JNA + gson + 6 平台原生库 + sit.dat，10.5MB），冒烟验证纯 classpath 解压加载成功；
   - native/ 二进制入仓（私有仓库 vendored，21MB，6 平台各一 canonical 名；win-x86_64 为本地 MinGW 静态构建，其余为 mc Docker 交叉矩阵产物）；
   - **CI 取消**（用户决策 2026-08-17）：测试本地跑，分发走 fat jar，将来需要时再议。

**G1 全部里程碑完成**（G1a 加载/绑定 → G1b 三场景+伪元素 → G1c 门面/存档/quanta → Track C 批 1 → G1d 打包）。

7. **G2 前奏 ✅ 策展表**（2026-08-17）：`resources/curation/chemistry.json` + `Curation` 加载器——
   手工内容的边界收敛为一张数据表（伪元素注册 + 动力学白名单 + k 值），生成
   addendum/RATES/KINETICS 三块文本；`Database` 重构为 sit.dat + 策展 addendum + Cl(+1) 参考。
   `CurationTest`：JSON 伪元素全链路可用；策展淬灭**逐位复现** m1 手写基线；
   酸门控（pH 12 不动 / pH 1 全灭 + Cl 总量 +50）；重复 keyword 块合法性已 CLI 实证。
8. **Track C 批 2 ✅**（2026-08-17）：复分解沉淀（BaSO4/Barite 49.97 mmol、留液 2.9e-5 M）；
   SO2 吸收（亨利 ≈1 mol + pH<1；**介稳教训**：全平衡把 2/3 S(+4) 氧化成 S(6)——真实亚硫酸介稳，
   建模需 Sul 伪池 + Sul(g) 伪气相，策展表 TODO）。

9. **策展表扩容 ✅**（2026-08-17）：
   - **SO₂ 介稳动力学吸收**（SulAbsorb）：`r = kLa*(H*P_SO2 − TOT(Sul))` 自限于亨利溶解度，
     PARM 机制接游戏侧供压（P→0 逆向解吸再生，循环验证）；实测 t=1000s Sul=1.240（=H×P），
     **S(6)=0.0000**（介稳成立——平衡参考同条件下 2/3 被氧化）；
   - **Pyrolusite 相补丁**（sit.dat 缺，常数取自官方 phreeqc.dat：log_k 41.38）：
     实验室制氯 MnO₂+浓盐酸 → Cl₂ 1:1 计量（逸出 0.041+溶解 0.059 = 0.0997 ≈ Mn 0.0996），
     Mn 以氯配合物留液（游离 Mn²⁺ <1e-5，浓盐酸真实化学）；
   - Curation API：phases 节（策展平衡相）+ PARM(n) 参数机制（JSON parms + 调用侧覆盖）
     + **kineticsBlock 反应包含过滤**（游戏侧选择本容器活跃过程；隔离误伤教训：
     未过滤时 SulAbsorb 默认 P=1 会吸硫酸化溶液，连带 AcidActivatesBleach 毁掉碱性漂白液测试）。

11. **六锅定向收集 + SiProbe 仪器 ✅**（2026-08-17）：每锅一假设，引擎级发现归档：
   - **SiProbe**（SI 审计器）：候选相扫过饱和且未声明相（Fe 缺策展实戗：Hematite(cr) SI=10.4 /
     Goethite 4.6 / Ferrihydrite(cr) 3.6）；实现依赖触发器 trick（USE 无触发器不 punch、
     -saturation_indices 在 i_soln 静默丢行）。
   - **锅1 敌意浓汤**：406ms 存活，Gypsum 0.785+Ferrihydrite 0.290 沉淀，账闭合——无引擎问题。
   - **锅2 熬干汤**：趋干时 rc=1 显式抛错（非静默）——好行为。
   - **锅3 中断存档汤**：**假设部分证伪**——SOLUTION_RAW 的 -totals 保留 Sul 池完整，
     恢复后淬灭从存档浓度继续（Hyp 0.0426→0.0402，Sul 0.00015），语义正确；
     KINETICS 积分器内部状态（-m 剩量）是否丢失待 mod 侧验证（capacity 1000 下无影响）。
   - **锅4 氧化还原汤**：Fe/Mn/N/S 多电对收敛，pe 2.60 涌现；N 全走 NH4+（真实 NO3- 介稳盲区，
     需 Nit 伪元素或接受——归策展内容缺口）。
   - **锅5 热汤**：150°C 无警告收敛，Ca 溶解度随 T 变化单调；-analytic 高温外推化学方向待专家审
     （记录为已知限制）。
   - **锅6 卤水极限**：6.5 molal 无警告收敛（pH 6.80）——SIT 域外**静默**外推但行为温和。
   - **REACTION 语法第 4 坑**：`Fe 2` 是计量系数（投 2×剂量）不是价态；
   - **punch 行为五铁律**（P6/P7/PE 探针链实证）：①SELECTED_OUTPUT 与被 punch 计算同模拟
     （END 分隔后定义不追溯 i_soln）；②已定义的对后续模拟生效；③string buffer 跨 RunString
     累积（IPhreeqc.java 已修：取最后表头段）；④-saturation_indices/-ionic_strength 在
     i_soln 静默丢行；⑤USE 无触发器不 punch。

10. **策展防线 A+B + 一锅汤压力测试 ✅**（2026-08-17，side-conversation 推演触发）：
   - **A（kind 二分）**：反应分 bulk（液内，默认全量发射）/interface（储库交换，须显式 opt-in）
     两类——interface 默认在场等于接了个虚拟大气，会污染任何未设防场景。防的是机制而非文档。
   - **B（多反应协同）**：SO₂ 涓流入漂白液，SulAbsorb+Quench 两速率方程联立自寻稳态
     （Sul* = kLa·H·P/(kLa+k·Hyp) ≈ 0.0198 < H·P），Hyp 单调耗，负对照（无 Quench）积累到 H·P。
   - **度量教训（cp1 实验）**：punch -totals 是 molality，策展 formula 会耗水
     （SO₂+H₂O→H₂SO₃，1.25 mol 吸收吃掉 ~4% 水）→ mol/kgw 全体虚涨（曾误读为"Na 增加"）；
     守恒断言必须乘 mass_H2O 换算成摩尔。另：Hyp 少而 SO₂ 浓时 pH 崩溃会激活
     AcidActivatesBleach 主导烧池（t=100s 烧光）——真实化学，场景断言时要意识到。
   - **一锅汤压力测试**（MultiIonSoupTest）：5 阳离子+5 池+介稳池同场，淬灭动力学+五相竞争
     （Calcite 19.96 / Barite 5.00 / Gypsum **零**：Ca 被 Calcite 锁死——旧引擎 §9 翻车区
     免费做对）+电荷平衡 pH 9.45 涌现，全部质量账 2e-4 内闭合；Fe 总量不动 =
     白名单盲区的活档案。

12. **策展缺口补全 ✅**（2026-08-17）：三大缺口闭合（chemistry.json 现 4 池 + 6 反应）：
   - **Nitra/Nitri 硝酸/亚硝酸介稳池**（锅4 盲区）：pe −4 下池纹丝不动（真实 N 对照全部被还原）；
     FerrousReducesNitrate（Nitra→Nitri 1:1，Fe(+2) 平衡浓度作速率门，20→7.8 mM @10ks，旧引擎同名场景复现）；
   - **HypOxidisesFerrous**（一锅汤盲区）：漂白液遇 Fe²⁺ 污染被消耗，Cl 1:1 释放，pH 下降；
   - **ChlorineAbsorbs**（电解链闭合）：Cl₂ 入碱制漂白液（Hyp=Cl 1:1 增长，OH⁻ 门控自停）。
   - **引擎边界四条源码级定谳**（V 探针矩阵 + kinetics.cpp/parse.cpp 源码）：
     ①-formula 价态 token 结构性不可能（get_elts_in_species 只解析化学式；k1 当年的"成功"是零速率
     跳过解析的假象——TOT 指向不存在池时速率恒 0，coef==0 直接 continue）；
     ②KINETICS 销毁溶液中不存在的池 → 无限 negative-mole 恢复循环（挂死非报错）——
     销毁池的反应必须 TOT 门控（加载器防线测试锁定）；
     ③伪元素名不得是前缀+数字（Nit2 解析成 Nit×2 → 用 Nitra/Nitri）；
     ④中性 master 不是挂死原因（带电 Dichl- 同挂）——挂的是池缺席本身；interface 反应的气源
     不入液账（Cl₂ 吸收=创建 Hyp+Cl，不存在液内 Dichl 销毁）。

下一步：mod 侧（DUMP↔NBT、FluidStack↔quanta 事件、容器 tick 驱动 KINETICS 步长与 PARM 供压）。

### G1b 补充实验（2026-08-17，介稳化学的自动架构，已实测验证）

**背景**：价态池方案对还原性杂质有盲区（实验 D：+10 mmol S(4) 淬灭剂，Cl(1) 池纹丝不动，超报）；
且发现**活 bug**：价态池只在初始解计算有效，任何批式反应步（REACTION/USE/KINETICS/EQUILIBRIUM_PHASES）
都会把同元素全部价态池坍缩到统一 pe（输出标记 "Adjusted to redox equilibrium"；q0 实验：1 µmol Na 无关 REACTION 即烧穿）。
当前 3 个测试未踩雷仅因没有同时用池输入+反应步。

**正解（m0/m1 实验验证）：伪元素 + 动力学白名单**
1. 介稳价态注册为独立伪元素（AMM.DAT 的 Amm 先例）：`Hyp  Hyp-  0  Hyp  51.452`，酸碱对 `Hyp- + H+ = HypH, log_k 7.53`；
   Sul 同理。**物种名不得含真实元素 token**（HypOCl- 会按 Hyp-O-Cl 解析，复杂物种向每个组成元素的物料账本缴税，实验中直接搬空了 Cl 池）
2. 跨池耦合用 KINETICS 纯元素 token：`-formula Hyp -1 Sul -1 Cl 1 S 1 O 4` + RATES 二级速率律 `r = k·TOT("Hyp")·TOT("Sul")`；
   实测 t=1/10/100/1000 s：Sul 9.95→9.51→6.19→0.15 mmol，Hyp→40.1，Cl→109.9，S→9.85——化学计量精确 + 真实时间刻度盘；
   m0 对照证明伪元素穿过 REACTION 步不塌缩（Cl=100.000/Hyp=50.000/Sul=10.000 分毫不动）
3. 手工内容的边界 = 一次性策展表：(伪元素:酸碱形态) + (白名单反应:化学计量+k)。与 sit.dat 同性质的数据，非逐场景脚本；
   k 同时是游戏节奏旋钮（游戏时间驱动化学）

**死路清单（全部实验实锤，勿重试）**
- PHASES 相方程无法表达跨元素账本转移（相组成不能为负，配平不可能）
- REACTION 不接受价态 token（"Unbalanced parentheses"）
- KINETICS -formula 价态 token 强制该元素 redox 平衡（毁池，k1 实验）
- 价态 token 在 -formula 仅当 RATES 中有 TOT("X(n)") 预注册时才可解析（k1/k2 同公式解析结果不同之谜）
- 全平衡热力学下 HOCl 必然氧化水（E° 1.482>1.23 V，bl 实验 12.5 mmol O₂）——介稳只能靠伪元素，无热力学解
4. **G1d ✅** 见上方 G1 里程碑第 6 条

### G1 已沉淀的关键语义（后续开发的铁律）

- **价态分池**：`S(4)`/`Fe(2)`/`Cl(1)` 等价态写法是互不转化的独立守恒池；要氧化还原发生必须
  以**元素总量**（`S`/`Fe`/`Cl`）输入，价态分配由 pe 平衡涌现（电荷平衡锁定化学计量）。
  介稳物种（HOCl/OCl⁻）用价态池定义（真实漂白液能存在纯靠动力学，平衡热力学下 HOCl 会氧化水成 O₂）。
- **数据库书写**：被定义物种 = 等号右侧首 token（parse_eq 的 swap）；`log_k` 按书写方向计；
  新 master 需价态方程而非恒等式。REACTION 语法：反应物列表与剂量分两行（`Cl2 1` 换行 `2 mmol in 1 step`）。
- **API 陷阱**：`LoadDatabaseString → UnLoadDatabase` 会清空 `SelectedOutputStringOn`，
  string 开关必须在数据库装载之后打开。
- **MinGW 本地构建的错误路径更稳健**：交叉（zig）DLL 在 tidy_model 错误路径会 SEGV；
  数据库合法时两者行为一致。win-x86_64 坚持用本地构建，其他平台遇到错误路径崩溃时优先怀疑工具链。

### G2 原则

- **相白名单纪律**（§9 教训）：mod 用数据库不放老化结晶相（hematite/tenorite 等），
  相竞争由数据配置控制，不加引擎机制
- 旧 `species/*.json` 仍是 mod 侧物种清单权威；场景测试断言从旧套件迁移，known_limitations 每节一条验收
- 移植纪律：如果将来做纯 Java 移植，用 IPhreeqc 当差分 oracle（同输入对到 1e-10），不许"边理解边重写"

## 目标

1. 用 PHREEQC 数据补齐权威 `delta_h` / `molarMass`
2. 实现 PHREEQC 式氧化还原：
   - `e-` 伪物种
   - `pe` 主变量
   - 半反应 + logK
   - 接入现有 Newton / Jacobian

两条轨道共用同一套热力学数据基础。

---

## Track A：热力学数据严谨化

### A0 数据源确认

- 选定 `phreeqc.dat` / `llnl.dat` / `minteq.v4.dat` 三库（均来自官方 PHREEQC 仓库 `database/`，位于 `tools/`）
- `phreeqc.dat` 为通用参考库，覆盖有限：无 Ag、无 Mg/Ca/Na 相关固相，Fe(OH)₃/Zn(OH)₂ 相缺 ΔH
- `llnl.dat`（热力学自洽计算库）：补 Magnesite/Brucite/Portlandite/Nahcolite/Fe(OH)₃/Zn(OH)₂(β,ε)/Malachite/Chlorargyrite/Zn(NH₃)₄²⁺/Cu(NH₃)₂·₃²⁺
- `minteq.v4.dat`：补 Cu(OH)₂、Ag₂CO₃、Ag(NH₃)₂⁺（NIST 来源）等 llnl 缺失项
- 所有库均无的物种（FeSCN²⁺、SO₃/NO₂ 气体吸收、Cu(NH₃)₄²⁺ 第 4 级、[Al(OH)₄]⁻）→ NIST/JANAF 生成焓 Hess 兜底，标记为估算
- 确认单位、标准态、反应方向：PHREEQC 多为酸溶形式（固相+nH⁺=离子+mH₂O），我们为直接溶解（固相=离子），需 Hess 换算：ΔH_我们 = ΔH_PHREEQC + n×55.8 kJ/mol（水自电离热）；llnl 与 minteq 对同一反应可能互为反方向，须按反应字符串对齐
- 建立当前反应 ↔ PHREEQC 反应映射表（含换算方式：直用 / 酸溶+n×自电离）

### A1 Schema 扩展

- `Species` 增加 `molarMass`
- 统一 `delta_h` 语义
- 短期保留 `heat_kj`，长期合并

### A2 转换脚本

- 写工具解析 PHREEQC 数据库
- 输出：
  - `reaction`
  - `log_k`
  - `delta_h`
  - `molarMass`

### A3 数据合入与审查

- 合入 `species/*.json`
- 人工抽查关键反应
- 标记 PHREEQC 没有覆盖的工业反应

### A4 代码改造

- `energyJ` / `heatRiseC` 改用真实 `molarMass`
- `delta_h` 同时服务 Van't Hoff 和热计算
- 最终删除 `heat_kj`

### A5 测试

- 中和热
- 矿物溶解/沉淀热
- 气体水合热
- 温度依赖回归

---

## Track B：氧化还原

### B0 设计

- 引入 `e-` 伪物种
- 引入 `pe = -log[e-]` 主变量
- 确定半反应数据格式：
  ```json
  {
    "reaction": "Fe+3 + e- = Fe+2",
    "log_k": 13.0,
    "delta_h": ...
  }
  ```

### B1 数据模型扩展

- `Equilibrium` 支持 `e-`
- `SystemModel` 识别 redox 半反应
- 把 `pe` 纳入 component / secondary 体系

### B2 Solver 扩展

- 增加 `pe` 未知量
- 增加 redox 残差
- 扩展解析 Jacobian
- 处理电子/电荷耦合
- 无 redox 时保持原路径

### B3 最小闭环

- 先跑通：
  ```text
  Fe3+ + e- = Fe2+
  ```
- 验证：
  - 不同 Fe³⁺/Fe²⁺ 比例
  - pe 变化
  - 与 pH 耦合

### B4 数据扩展

- 常见电对：
  - NO₃⁻ / NO₂⁻ / NO / NH₄⁺
  - SO₄²⁻ / H₂S
  - O₂ / H₂O
  - Cu²⁺ / Cu⁺
  - Mn / Cr / Cl

### B5 场景测试

- 与沉淀耦合
- 与气体耦合
- 与温度/热效应耦合
- 工业流程：
  - 硝酸
  - 湿法冶金
  - 废水处理

---

## Track C：常见化工原料生产流程蓝图（新）

> 目的：把插件（chemical-addon）全部化工原料 + 常见工业原料的**生产化学式**固化成代码蓝图
> （`src/test/java/com/yu1745/chemengine/industrial/`，每个原料一个类，类内步骤不去重、保留完整流程），
> 作为后续逐条实现为引擎场景测试/数据扩展的索引。原料表以本仓库
> `src/test/resources/species/*.json`（mod 数据副本）为插件侧权威清单。

### C0 原料总表（插件 50 种化学物种 + 常见工业原料）

图例：✅=现有数据/引擎可直接模拟（水相/气体吸收/沉淀/溶解度曲线）；⚠️=需补数据或引擎扩展
（气固催化、煅烧、电解、熔融）；❌=超出当前引擎形态（冶金、高温、有机合成）。

| # | 原料 | 插件 id / 状态 | 主要生产路线（步骤化学式） | 引擎 |
|---|------|---------------|--------------------------|------|
| 1 | 盐酸 HCl | `hydrochloric_acid`(aq)+`hydrogen_chloride`(g) ✅ | H₂+Cl₂→2HCl(g) 合成；NaCl+H₂SO₄(浓)→NaHSO₄+HCl↑（两步合并）；HCl(g)+H₂O→盐酸 | ⚠️ 缺 H₂/Cl₂ 气态平衡数据 |
| 2 | 硫酸 H₂SO₄ | `sulfuric_acid` ✅ | 接触法：S+O₂→SO₂；4FeS₂+11O₂→2Fe₂O₃+8SO₂；2SO₂+O₂⇌2SO₃(V₂O₅)；SO₃+H₂O→H₂SO₄（已实现） | ⚠️ SO₂ 催化氧化步缺数据 |
| 3 | 硝酸 HNO₃ | `nitric_acid` ✅ | 氨氧化：4NH₃+5O₂→4NO+6H₂O；2NO+O₂→2NO₂；3NO₂+H₂O→2HNO₃+NO（已实现） | ⚠️ 前两步缺 NH₃ 燃烧/NO 氧化数据 |
| 4 | 烧碱 NaOH | `caustic_soda_solution` ✅ | 氯碱电解：2NaCl+2H₂O→2NaOH+H₂↑+Cl₂↑；苛化：Na₂CO₃+Ca(OH)₂→2NaOH+CaCO₃↓（已实现苛化） | ⚠️ 电解需引擎扩展 |
| 5 | 纯碱 Na₂CO₃ | `soda_ash_solution` ✅ | 索尔维/侯氏：NH₃+CO₂+H₂O→NH₄HCO₃；NaCl+NH₄HCO₃→NaHCO₃↓+NH₄Cl；2NaHCO₃→△Na₂CO₃+CO₂↑+H₂O；母液回收 2NH₄Cl+Ca(OH)₂→2NH₃↑+CaCl₂+2H₂O | ⚠️ 碳化已实现（Solvay step1），煅烧步缺 Na₂CO₃(s) |
| 6 | 小苏打 NaHCO₃ | `sodium_bicarbonate`(+slurry) ✅ | NaCl+NH₃+CO₂+H₂O→NaHCO₃↓+NH₄Cl；Na₂CO₃+CO₂+H₂O→2NaHCO₃（饱和碱液碳化） | ✅ 已实现 |
| 7 | 熟石灰 Ca(OH)₂ | `slaked_lime`+`milk_of_lime` ✅ | CaCO₃→高温CaO+CO₂↑；CaO+H₂O→Ca(OH)₂（放热消化） | ⚠️ 消化步可表达；煅烧需扩展 |
| 8 | 生石灰 CaO | 缺 species ❌ | CaCO₃→高温CaO+CO₂↑（石灰窑） | ❌ 缺 CaO 数据 |
| 9 | 氨水 NH₃·H₂O | `ammonia_water`+`ammonia`(g) ✅ | NH₃(g)+H₂O⇌NH₃·H₂O⇌NH₄⁺+OH⁻（已实现） | ✅ |
| 10 | 合成氨 NH₃ | `ammonia` ✅ | 哈伯：N₂+3H₂⇌2NH₃（高温高压 Fe 催化）；实验室：Ca(OH)₂+2NH₄Cl→△CaCl₂+2NH₃↑+2H₂O（已实现 step5） | ⚠️ 哈伯缺 N₂/H₂ 数据；实验室法 ✅ |
| 11 | 氯化铵 NH₄Cl | `ammonium_chloride_solution` ✅ | 侯氏母液副产；NH₃+HCl→NH₄Cl；冷却结晶（已实现） | ✅ |
| 12 | 硫酸铵 (NH₄)₂SO₄ | `ammonium_sulfate_solution` ✅ | 2NH₃+H₂SO₄→(NH₄)₂SO₄（硫酸吸收氨） | ✅ 可表达 |
| 13 | 硝酸铵 NH₄NO₃ | `ammonium_nitrate_solution` ✅ | NH₃+HNO₃→NH₄NO₃（中和放热） | ✅ 可表达 |
| 14 | 碳酸氢铵 NH₄HCO₃ | 缺 species ⚠️ | NH₃+CO₂+H₂O→NH₄HCO₃（氨水碳化） | ⚠️ 缺物种数据 |
| 15 | 精制食盐 NaCl | `brine`+`rock_salt` ✅ | 粗盐溶解→BaCl₂ 除 SO₄²⁻（BaSO₄↓）→Na₂CO₃ 除 Ca²⁺/Ba²⁺→NaOH 除 Mg²⁺→HCl 回调→蒸发结晶（已实现粗盐精炼） | ✅ |
| 16 | 硝酸钾 KNO₃ | `potassium_nitrate_solution` ✅ | NaNO₃+KCl⇌KNO₃+NaCl（复分解、溶解度差分离）；KOH+HNO₃→KNO₃+H₂O | ✅ 可表达 |
| 17 | 氯化钙 CaCl₂ | `calcium_chloride_solution` ✅ | CaCO₃+2HCl→CaCl₂+CO₂↑+H₂O；索尔维废液：2NH₄Cl+Ca(OH)₂→2NH₃↑+CaCl₂+2H₂O | ✅ 可表达 |
| 18 | 氯化镁 MgCl₂ | `magnesium_chloride_solution` ✅ | 海水提镁：Mg(OH)₂+2HCl→MgCl₂+2H₂O；MgCO₃+2HCl→MgCl₂+CO₂↑+H₂O | ✅ 可表达 |
| 19 | 硫酸铜/胆矾 CuSO₄·5H₂O | `copper_sulfate`+solution ✅ | Cu+2H₂SO₄(浓)→△CuSO₄+SO₂↑+2H₂O；CuO+H₂SO₄→CuSO₄+H₂O；Cu₂(OH)₂CO₃+2H₂SO₄→2CuSO₄+CO₂↑+3H₂O；结晶 CuSO₄+5H₂O⇌CuSO₄·5H₂O | ✅ 可表达 |
| 20 | 硫酸亚铁/绿矾 FeSO₄·7H₂O | `ferrous_sulfate_solution` ✅ | Fe+H₂SO₄(稀)→FeSO₄+H₂↑；Fe+CuSO₄→FeSO₄+Cu（湿法/废液回收） | ⚠️ 缺 Fe(s)/Cu(s) 金属物种 |
| 21 | 明矾 KAl(SO₄)₂·12H₂O | `potassium_alum_solution` ✅ | 2Al(OH)₃+3H₂SO₄→Al₂(SO₄)₃+6H₂O；Al₂(SO₄)₃+K₂SO₄+24H₂O→2KAl(SO₄)₂·12H₂O | ✅ 可表达 |
| 22 | 硫氰酸钾 KSCN | `potassium_thiocyanate_solution` ✅ | NH₄SCN+KOH→△KSCN+NH₃↑+H₂O；KCN+S→熔融KSCN（剧毒） | ⚠️ 缺 NH₄SCN/KCN 数据 |
| 23 | 硝酸银 AgNO₃ | `silver_nitrate_solution` ✅ | Ag+2HNO₃(浓)→AgNO₃+NO₂↑+H₂O；3Ag+4HNO₃(稀)→3AgNO₃+NO↑+2H₂O | ⚠️ 缺 Ag(s) |
| 24 | 次氯酸钠 NaClO | 缺 species ⚠️ | Cl₂+2NaOH→NaCl+NaClO+H₂O（冷）；3Cl₂+6NaOH→△5NaCl+NaClO₃+3H₂O（热→氯酸盐） | ⚠️ 缺物种+Cl₂ 数据 |
| 25 | 漂白粉 Ca(ClO)₂ | 缺 species ⚠️ | 2Cl₂+2Ca(OH)₂→Ca(ClO)₂+CaCl₂+2H₂O | ⚠️ 同上 |
| 26 | 氢气 H₂ | 缺 species ⚠️ | 电解水 2H₂O→2H₂↑+O₂↑；氯碱副产；Zn+2HCl→ZnCl₂+H₂↑；水煤气 C+H₂O→高温CO+H₂ | ⚠️ 需电解/气体数据 |
| 27 | 氧气 O₂ | 缺 species（测试用 `oxygen`）⚠️ | 电解水副产；2H₂O₂→MnO₂ 2H₂O+O₂↑；2KClO₃→△2KCl+3O₂↑；空气液化分馏 | ⚠️ 需 H₂O₂/KClO₃ 数据 |
| 28 | 氯气 Cl₂ | 缺 species ⚠️ | 电解食盐水阳极；MnO₂+4HCl(浓)→△MnCl₂+Cl₂↑+2H₂O | ⚠️ 需电解/数据 |
| 29 | 二氧化碳 CO₂ | `carbon_dioxide` ✅ | CaCO₃→高温CaO+CO₂↑；CaCO₃+2HCl→CaCl₂+CO₂↑+H₂O（已实现除垢） | ✅（酸溶路线）；⚠️ 煅烧 |
| 30 | 二氧化硫 SO₂ | 缺 species ⚠️ | S+O₂→点燃SO₂；4FeS₂+11O₂→高温2Fe₂O₃+8SO₂；Na₂SO₃+2HCl→SO₂↑+2NaCl+H₂O | ⚠️ 缺数据 |
| 31 | 三氧化硫 SO₃ | `sulfur_trioxide` ✅ | 2SO₂+O₂⇌2SO₃（V₂O₅/450°C，已实现吸收） | ⚠️ 催化氧化步 |
| 32 | 一氧化氮 NO | `nitric_oxide` ✅ | 4NH₃+5O₂→催化4NO+6H₂O；N₂+O₂→放电2NO；3Cu+8HNO₃(稀)→3Cu(NO₃)₂+2NO↑+4H₂O | ⚠️ 缺数据/金属 |
| 33 | 二氧化氮 NO₂ | `nitrogen_dioxide` ✅ | 2NO+O₂→2NO₂；Cu+4HNO₃(浓)→Cu(NO₃)₂+2NO₂↑+2H₂O；2NO₂⇌N₂O₄（低温） | ⚠️ 缺数据 |
| 34 | 乙炔 C₂H₂ | 缺 species ❌ | CaC₂+2H₂O→Ca(OH)₂+C₂H₂↑（电石水解） | ❌ 缺有机物种 |
| 35 | 电石 CaC₂ | 缺 species ❌ | CaO+3C→电炉2200°C CaC₂+CO↑ | ❌ 高温电炉 |
| 36 | 铁 Fe（炼铁） | 缺 species ❌ | 高炉：C+O₂→CO₂；CO₂+C→高温2CO；Fe₂O₃+3CO→高温2Fe+3CO₂ | ❌ 冶金 |
| 37 | 铝 Al | 缺 species ❌ | 拜耳：Al₂O₃+2NaOH→2NaAlO₂+H₂O；NaAlO₂+CO₂+2H₂O→Al(OH)₃↓+NaHCO₃；煅烧；电解 2Al₂O₃→冰晶石 4Al+3O₂↑ | ⚠️ 拜耳水相部分可表达 |
| 38 | 铜 Cu | 缺 species ❌ | 湿法：Fe+CuSO₄→FeSO₄+Cu；火法：2Cu₂S+3O₂→高温2Cu₂O+2SO₂；Cu₂S+2Cu₂O→高温6Cu+SO₂ | ⚠️ 湿法置换可表达 |
| 39 | 氢氧化铁 Fe(OH)₃ | `iron_hydroxide` ✅ | FeCl₃+3NaOH→Fe(OH)₃↓+3NaCl；Fe₂(SO₄)₃+6NaOH→2Fe(OH)₃+3Na₂SO₄（已实现 Ksp 竞争） | ✅ |
| 40 | 氢氧化铜 Cu(OH)₂ | `copper_hydroxide` ✅ | CuSO₄+2NaOH→Cu(OH)₂↓+Na₂SO₄（已实现） | ✅ |
| 41 | 氢氧化锌 Zn(OH)₂ | `zinc_hydroxide` ✅ | ZnSO₄+2NaOH→Zn(OH)₂↓+Na₂SO₄；两性 Zn(OH)₂+2NaOH→Na₂ZnO₂+2H₂O | ✅（两性溶解可加数据） |
| 42 | 氢氧化铝 Al(OH)₃ | `aluminium_hydroxide` ✅ | AlCl₃+3NaOH→Al(OH)₃↓+3NaCl；两性 Al(OH)₃+OH⁻→[Al(OH)₄]⁻（已实现） | ✅ |
| 43 | 氢氧化镁 Mg(OH)₂ | `magnesium_hydroxide` ✅ | MgCl₂+Ca(OH)₂→Mg(OH)₂↓+CaCl₂；MgCl₂+2NaOH→Mg(OH)₂↓+2NaCl（已实现） | ✅ |
| 44 | 碳酸钡 BaCO₃ | `barium_carbonate` ✅ | BaCl₂+Na₂CO₃→BaCO₃↓+2NaCl（已实现） | ✅ |
| 45 | 硫酸钡 BaSO₄ | `barium_sulfate` ✅ | BaCl₂+Na₂SO₄→BaSO₄↓+2NaCl（已实现，粗盐除 SO₄） | ✅ |
| 46 | 氯化银 AgCl | `silver_chloride` ✅ | AgNO₃+NaCl→AgCl↓+NaNO₃（已实现） | ✅ |
| 47 | 碳酸银 Ag₂CO₃ | `silver_carbonate` ✅ | 2AgNO₃+Na₂CO₃→Ag₂CO₃↓+2NaNO₃（已实现） | ✅ |
| 48 | 碱式碳酸铜 Cu₂(OH)₂CO₃ | `copper_carbonate` ✅ | 2CuSO₄+2Na₂CO₃+H₂O→Cu₂(OH)₂CO₃↓+2Na₂SO₄+CO₂↑（已实现 malachite） | ✅ |
| 49 | 石膏 CaSO₄·2H₂O | `gypsum`+slurry ✅ | Ca(OH)₂+H₂SO₄→CaSO₄+2H₂O；CaCO₃+H₂SO₄→CaSO₄+CO₂↑+H₂O；脱硫氧化 2CaSO₃+O₂→2CaSO₄ | ✅ 可表达 |
| 50 | 亚硫酸钙 CaSO₃ | `calcium_sulfite_slurry` ✅ | Ca(OH)₂+SO₂→CaSO₃↓+H₂O（烟气脱硫） | ⚠️ 缺 CaSO₃(s) Ksp |
| 51 | 三氯化铁 FeCl₃ | `ferric_chloride_solution` ✅ | 2FeCl₂+Cl₂→2FeCl₃；Fe₂O₃+6HCl→2FeCl₃+3H₂O | ⚠️ 缺 Cl₂/Fe₂O₃ 数据 |
| 52 | 尿素 CO(NH₂)₂ | 缺 species ❌ | 2NH₃+CO₂→高温高压 NH₂COONH₄→CO(NH₂)₂+H₂O | ❌ 有机高压 |
| 53 | 过磷酸钙 | 缺 species ❌ | Ca₃(PO₄)₂+2H₂SO₄→Ca(H₂PO₄)₂+2CaSO₄（磷肥） | ❌ 缺磷物种 |
| 54 | 高锰酸钾 KMnO₄ | 缺 species ❌ | 2MnO₂+4KOH+O₂→熔融 2K₂MnO₄+2H₂O；3K₂MnO₄+2H₂SO₄→2KMnO₄+MnO₂↓+2K₂SO₄+2H₂O | ❌ 缺锰物种 |
| 55 | 氯化钾 KCl | `potassium_chloride_solution` ✅ | 光卤石/钾石盐提纯（溶解-结晶） | ✅ 溶解度曲线类 |
| 56 | 硫酸锌 ZnSO₄ | `zinc_sulfate_solution` ✅ | Zn+H₂SO₄(稀)→ZnSO₄+H₂↑；ZnO+H₂SO₄→ZnSO₄+H₂O | ⚠️ 缺 Zn(s)/ZnO |

### C1 代码蓝图（已建）

- 包 `com.yu1745.chemengine.industrial`（test 源码树）：**每原料一个类**（如 `SodaAshProcess`，
  共 **57 个类**，覆盖上表全部 56 项原料 + 石灰石原料类），
  类内列出该原料生产流程的完整步骤链；步骤在类间**不去重**（流程完整性优先）；
- 每步 = `ProcessStep(引擎可解析反应式 | null, 条件/传统式, 引擎可表达性标记)`；
  **电解、火法/煅烧（及尿素/过磷酸钙/高锰酸钾等有机特种）步骤的引擎反应式一律留空（null），
  note 以 `【留空·待实现】` 前缀标注**——占位注释优先，之后逐个实现；
- `IndustrialProcesses` 聚合清单 + `IndustrialProcessBlueprintTest` 校验（2 用例，80/80 全绿）：
  步骤非空、引擎式可被 `Equilibrium.parse` 解析（语法防线）、插件全部物种 id（除 thermal_oil）
  被至少一个原料类覆盖（覆盖防线）；由 `tools/gen_process_blueprints.py` 生成，改数据走生成器。

### C2 推进顺序

1. ✅ C1 蓝图类（57 个原料类，本计划落地）
2. ✅ ⚠️→✅ 转化完成：`IndustrialScenariosTest`（15 个场景，96/96 全绿）
   + 补数据：`gypsum` Ksp（phreeqc -4.58 直用）、`sulfur_dioxide`/`calcium_sulfite`/`chlorine`
   （NIST/文献，estimated）、`zinc_hydroxide` 两性锌酸盐（phreeqc Zn(OH)4-2 组合）、
   `hydrogen_peroxide`/`oxygen`（NBS Hess）；
   已覆盖：硫酸铵、硝酸铵、氯化镁（海水提镁）、硫酸铜（孔雀石酸溶）、明矾（铝酸溶）、
   硝酸钾（复分解+冷却结晶）、拜耳分解析出、石膏 Ksp、烟气脱硫 CaSO3、锌两性、氯气歧化制次氯酸钠、
   漂白粉母液、硫氰酸钾（NH4SCN 交换）、H2O2 分解制氧、亚硫酸盐+酸制 SO2（实验室）
3. 已验证不可行项（保持留空）：金属+酸/电解（Zn/Fe/Cu 溶解成自由电子的 Ksp 模型与电子总量
   守恒冲突，见 known_limitations §7）、KClO3 固相热分解、MnO2 浓酸制氯（需"浓"介质）、
   Cu(s) 置换（双金属 Ksp 相除给出电位差而非电位和）、Cl2 氧化 Fe2+（电子中性输入下
   e- 中间体模型数值失败，§8）、CuO/Fe2O3 酸溶（结晶氧化物相颠覆氢氧化物竞争，
   亚稳相动力学缺失，§9）
4. ❌ 项（电解/煅烧/冶金/有机，已以 `【留空·待实现】` 注释占位）：评估引擎扩展（固相分解反应、
   气固平衡、电解槽），超出范围的记为 mod 侧手工配方
5. ✅ C1/C2 主体完成：46/57 原料至少一条引擎可表达路线并有场景测试；3 个 ⚠️（氯气/一氧化氮/铜）
   与 8 个 ❌（生石灰/氢气/乙炔/电石/铁/尿素/过磷酸钙/高锰酸钾）明确原因后留空待实现

---

## Track D：所有留空项的解决方案（工艺模块扩展）

> C1/C2 把"引擎可表达"的原料都转成了场景测试；余下留空项分为三类统一解决：
> **① 非平衡净反应**（电解/金属腐蚀/Cl₂ 氧化/浓酸/氧化物溶解）→ 净反应推进器（D1）；
> **② 纯水相补数据**（磷/锰/氯酸盐）→ D2；**③ 真非水相形态** → 独立模块（D3）。
> 结论：经逐项核对，**所有 41 个留空步骤均有解决方案**。

### D0 核心洞察

电解、金属+酸/置换、Cl₂+Fe²⁺ 氧化、浓酸法（芒硝法）、氧化物酸溶——本质是**同一类：非平衡净反应**。
净反应（如 `Cl₂+2Fe²⁺→2Fe³⁺+2Cl⁻`、`2Cl⁻+2H₂O→Cl₂+H₂+2OH⁻`、`Fe₂O₃+6H⁺→2Fe³⁺+3H₂O`）**均不含自由电子**，
因此写成"净反应推进"就**绕开了 e⁻ 组分病态**（known_limitations §7/§8）与氧化物相竞争（§9）——
不需要修 e⁻ 平衡模型，也不需要把氧化物加进平衡相装配。

### D1 金属固相平衡条目（正确处理复合反应，引擎扩展，核心）

> ⚠️ 架构修正：纯"净反应推进器"（先推进再平衡）在**复合反应**下会错——金属置换与其他水相
> 平衡共享离子与 pH、互相影响（络合抑制置换、pH 耦合、多金属顺序竞争、产物二次反应）。
> 正确做法是把金属净反应建成**含金属固相的平衡条目**，并入求解器与溶液平衡**联立**。
> 净反应（`Fe(s)+Cu2+=Fe2++Cu(s)`、`Zn(s)+2H+=Zn2++H2(g)`）**不含自由电子**，故不触发
> known_limitations §7/§8 的 e- 病态；问题仅在于引擎当前拒绝"两个固体项"。

- **D1a（主路径）**：引擎扩展支持含金属固相的净反应平衡条目
  - `Equilibrium` 允许两个固体项（各一侧）；`SystemModel`/`Solver` 处理"固相+离子=固相+离子"
    及"固相+离子=离子+气体"的质量作用行，金属固相量（present/solidAmt）进 Newton 联立。
  - 覆盖：金属置换/腐蚀（Fe+CuSO4、Zn+2H+、Ag+HNO3、Cu+浓硫酸）、氧化物酸溶（Fe2O3+6H+，
    绕开 §9 相竞争——金属固相不参与氢氧化物相装配）、Cl2+2Fe2+（若写成净反应可不含 e-）。
  - 金属固相之间的顺序竞争由各条 logK（= nE0/0.05916）自动排序。
- **D1b（电解，已实现 ✅）**：受迫非平衡，不能当平衡条目。建模为"固定进度源/汇 + 平衡联立"
  （推进一步 → Solver 平衡），新增 `Electrolysis`（解析引擎物种净反应，`advance(state, units)`
  消耗反应物/生产产物，随后 `Engine.electrolyze` = `solveOpen` 联立平衡并让 H2/Cl2/O2 气体逸出）。
  新增 `hydrogen` 气态物种（gasSolubility 0.001）。`ElectrolysisTest`：电解水（2H2O→2H2+O2 逸出）、
  氯碱（2Cl- + 2H2O → 2OH- + H2 + Cl2，Na+ 旁观；批式混合下 Cl2 遇 OH- 歧化为次氯酸盐漂白，
  氯守恒/钠守恒/电荷 0 精确）。放电优先级由电解反应化学计量 + 后续平衡排序。
- **D1c（降级，可选）**：纯净反应推进器仅作 mod 侧"无耦合快速黑盒"，不承诺复合正确。
- 验收：`MetalDisplacementTest`（Fe+CuSO4 且与络合/pH 耦合时仍正确）、`ElectrolysisTest`（氯碱/电解水）。

### D2 纯水相补数据（最高优先，纯数据+场景）
| 项 | 缺 | 做法 | 状态 |
|---|---|---|---|
| 过磷酸钙 | 磷体系（PO4-3、磷灰石、H2PO4-） | 补 pKa2/pKa3 + 磷灰石 Ksp（phreeqc/llnl Whitlockite 换算）→ 酸溶+石膏沉淀场景 | ✅ 已完成（superphosphateFromPhosphateRockAndSulfuricAcid） |
| 高锰酸钾（歧化步） | 锰 redox 体系 | llnl MnO4-2/MnO4- 均以 Mn+2/O2 为多基准，歧化非干净净反应常数；且熔融氧化步独立 | ❌ 判不适合当前平衡模型，留空（勿硬造热力学） |
| 次氯酸钠热歧化 | ClO3- | llnl ClO3- 以 Cl-/O2 为基准，热/冷产物选择性本质是动力学而非平衡 | ❌ 判不适合当前平衡模型，留空 |

### D1a 详细设计（含金属固相平衡条目）

> 目标：金属置换/腐蚀进**平衡求解器**与溶液联立，正确处理复合反应（络合抑制、pH 耦合、
> 多金属顺序竞争、产物二次反应）。金属净反应（`Fe(s)+Cu2+=Fe2++Cu(s)`）不含自由电子，
> 故不触发 e- 病态（§7/§8）；障碍仅在于引擎不支持"两个固体项"。

**数据形态**（species JSON，金属固相 + 净反应）：
```json
{ "reaction": "chemicaladdon:iron_metal(s) + Cu+2 = Fe+2 + chemicaladdon:copper_metal(s)",
  "log_k": 26.5 }   // = n*E0/0.05916, E0(Fe/Cu) 0.784 V, n=2
{ "reaction": "chemicaladdon:zinc_metal(s) + 2 H+1 = Zn+2 + chemicaladdon:hydrogen",
  "log_k": 25.7 }   // 单固相 + 气体产物
```

**引擎改动**（4 处，风险集中在 Solver 矿物核心，需全套回归）：
1. `Equilibrium.parse`：允许"左一固相 + 右一固相"（双固相置换），仍拒绝同侧多固相。
2. `SystemModel`：识别双固相条目 → 新类型 `Displacement`（reactantSolid、productSolid、
   离子 coeff、logK）。coeff 过组分 = 产物固相溶解向量 − 反应物固相溶解向量（两固体活度 1）。
   单固相+气体条目走同路径（气体作产物次级，不含 e-）。
3. `Solver`（**注意：金属固相是"反应物池"而非"溶解池"**——与 Ksp 矿物不同，输入 Fe(s) 量不能
   用 coeff×量进 totals（coeff 是置换化学计量，含负离子项）；需跟踪金属储量 + 置换进度 x）：
   - 新增置换反应槽：present 布尔 + 进度 x（x 唯一约束：Fe(s) 剩余 = 初始Fe - x、Cu(s) 生成 = x、
     Fe2+ 生成 x、Cu2+ 消耗 x）；初始金属储量来自输入 suspended。
   - `residuals`：置换质量作用行 `log[Fe2+] - log[Cu2+] = logK·ln10`（coeff 混合符号，代码已支持）。
   - `analyticJacobian`：置换进度列 + 各离子行耦合。
   - `phaseAssemble`：置换 SI 判定（`[Fe2+]/[Cu2+]` vs K）→ 添加/移除；受金属储量 ≥ 0 限制。
   - 投影整数化：置换进度 → 两固相整数量（Fe 剩余、Cu 生成）写回 suspended。
4. 复合正确性：置换行与溶液平衡同一 Newton 联立收敛（络合抑制/pH 耦合/顺序竞争自动正确）。

**当前进度**：
- ✅ `Equilibrium.parse` 双固体支持（允许左一右一）
- ✅ `SystemModel.Mineral.productSolidKey` + 双固相分类/构造（coeff=产物离子-反应物离子）
- ✅ **D1a 完整实现（commit 66f815d）**：金属固相作为置换进度 x 进入求解器，不并入溶液 totals：
  - `Mineral.coeff` 专作**质量作用系数**（[Fe2+]/[Cu2+]=K 需 +1/-1）
  - residuals/Jacobian 物料行取 **-coeff**（Fe2+ 随 x 增长、Cu2+ 随 x 消耗）；tAq 扣固体用 **+coeff**
  - `projectExact`/`projectKinetic` 置换进度 clamp（≤反应物金属池）+ 双固体写回
    （reactantLeft=initReact−x、product=initProd+x）
  - 修复整数投影 rounding-up 循环对置换矿物符号错误（进度取整 +1 应增产物离子）
  - 修复进度 clamp 仅限反应物金属池、未限被消耗离子可用量导致的负余量崩溃
    （Fe(s)+水、无 Cu2+ 时 x 会冲到池上限 → Cu2+ 负；新增 `displacementIonCap`
    使 x ≤ 池 且 ≤ Σ 消耗离子可用量；覆盖 force-present seed 与两条投影路径）
  - `MetalDisplacementTest` 6 用例（Fe+CuSO4 完全/限量/过量 + 无反应物金属/仅产物金属/
    无氧化剂金属）：铁铜守恒精确至量子级，电荷 0
- ✅ 全套 **100 用例**全绿，现有测试零回归

**复合络合（已修复，commit 待定）**：Fe+CuSO4+NH3 强络合收敛问题根因是 **Newton 种子符号错误**——
位移矿物的物料平衡是 `n[c] = t[c] + coeff[c]·x`（金属池释放/吸收离子），但两条 Newton 种子路径
（BALANCED 与普通）都按 Ksp 的 `t − coeff·x` 计算，导致 Fe2+ 种子错成 1e-9（应为 x）、Cu2+ 种子
错成超大，Newton 被导向错误流域卡在 x=0。修复：种子对位移矿物取 `t + coeff·x`（符号翻转），
全矿物通用。修复后 Fe+CuSO4+NH3 正确收敛到完全置换（净反应 K = 10^26.5/10^12.6 = 10^13.9 仍强有利，
络合只是缓冲游离 Cu2+，不阻止净氧化还原），铁铜守恒精确、电荷 0。

**验证**：`MetalDisplacementTest`（已实现，3 用例全绿）
- Fe+CuSO4 纯体系：Cu2+→0、Fe2+ 生成、Cu(s) 沉淀、电荷 0（完全/限量/过量三种计量，铁铜守恒精确至量子级）
- ✅ Fe+CuSO4+NH3：络合抑制复合平衡——净反应仍完全置换（K=10^13.9），铁铜守恒、电荷 0
- ✅ Fe+CuSO4+酸：pH 耦合（同一 Newton 联立，酸不阻止置换，铁铜守恒、电荷 0）
- ✅ **气体产物位移**（金属+酸）：`Zn(s)+2H+ = Zn+2 + H2(g)`——Mineral 增 `productGasKey`，
  分类识别"金属固相 + 气体产物"为位移（`isMetalPlusGasDisplacement`，限 `_metal` 避免酸-碳酸盐误判），
  coeff 排除气体项（活度 1），写回产物气体为分子。绕开 §7 的自由电子病态。
  `MetalDisplacementTest.zincDissolvesInAcid_releasingHydrogen`（Zn+2=100、H+ 耗尽、H2=100、电荷 0）。
  单一金属的金属+酸（Zn）可用。
- ✅ **L3 共享金属池多重置换（已实现）**：同一金属被多个位移反应共用（Fe+Cu 与 Fe+acid，或
  Zn 同时置换 Cu 与 Fe）时，投影按 logK 降序贪心分配金属池（Zn+Cu 37.2 优先于 Zn+Fe 10.8；
  Fe+Cu 26.5 优先于 Fe+acid 14.9），每矿物受消耗离子可用量 + 池预算约束；写回聚合共享池消耗，
  且金属若为某位移的反应物则不作为另一位移的既有产物。守恒精确至量子级（金属恰耗尽边界
  处 1–2 量子整数投影精度伪影）。`MetalDisplacementTest.zincDisplacesCopperPreferentiallyOverIron`
  顺序竞争、`ironDisplacesCopper_inAcid_phCoupled`、`ironDisplacesCopperFromSulfate` 等。
- ⏳ Zn + (Fe2+ + Cu2+)：顺序竞争（✅ 已实现，见 L3）

**验收**：上述场景断言通过 + 现有 97 测试零回归（✅ 当前 100 全绿）。

---

### D3 独立非水相模块（与水相引擎解耦，mod 侧/后续）

> **机制（已实现 ✅）**：非水相高温/固相步用**纯计量净反应**（`Electrolysis.advance`，与 D1b 电解
> 同一受迫进度机制）——外部驱动（燃烧/煅烧）只需化学计量，**不虚构平衡常数**（保数据完整性）；
> 产物气体/固相按化学计量产出，下游水相捕集（SO2→酸、HCl→酸、CO2 吸收）是后续水相步由平衡引擎处理。
> `Equilibrium.parseReactionSides`：允许一侧多个固相（电石炉 CaO+3C→CaC2+CO 等），供净反应解析。
> 新增惰性物种（无平衡，仅 phase+molarMass）：sulfur(S)、carbon(C)、quicklime(CaO)、
> sodium_carbonate(Na2CO3)、aluminium_oxide(Al2O3)、nitrogen(N2)、carbon_monoxide(CO)、
> ferric_oxide(Fe2O3)、pyrite(FeS2)、calcium_carbide(CaC2)、acetylene(C2H2)、urea。
> `ThermalProcessTest` 26 用例：煅烧（石灰窑/纯碱/氢氧化铝）、燃烧（硫/碳/氨氧化/NO/固氮/FeS2/SO2/
> 合成盐酸）、高炉三步、水煤气、铜绿、电石/乙炔/尿素、铜火法两步、熔盐电解铝、芒硝法、
> KCN+S，以及**氧化物/金属酸溶净反应**（Fe2O3/ZnO/CuO/MnO2+酸、Ag+硝酸、拜耳法碱溶、KClO3 分解）——
> 均纯计量净反应（绕开 §9 氧化物相竞争与 §7 自由电子），化学计量精确。

| 模块 | 覆盖留空项 | 状态 |
|---|---|---|
| 燃烧/催化氧化 | S+O₂、C+O₂、H₂+Cl₂、NH₃ 氧化、2NO+O₂、N₂+O₂、FeS₂ 焙烧、2SO₂+O₂ | ✅ 已实现（纯计量净反应）；水煤气 C+H₂O 已实现 |
| 煅烧/固相热分解 | 2NaHCO₃→△、CaCO₃→△、2Al(OH)₃→△ | ✅ 已实现 |
| 高温冶金/电炉 | 高炉炼铁（三步）、铜火法（2Cu₂S+3O₂、Cu₂S+2Cu₂O）、电石电炉 | ✅ 高炉三步 + 铜火法两步已实现；新增 cuprous_sulfide/cuprous_oxide |
| 熔盐电解（铝） | 2Al₂O₃ 冰晶石熔融电解 | ✅ 已实现（2Al₂O₃→4Al+3O₂，纯计量净反应）；新增 aluminium_metal |
| 有机高压 | 尿素、乙炔、电石 | ✅ 已实现（尿素 2NH₃+CO₂、电石 CaO+3C、乙炔 CaC₂+2H₂O，纯计量净反应）；新增 calcium_carbide/acetylene/urea 物种 |
| 熔融 | KCN+S→KSCN（已实现）；KMnO₄ 熔融氧化判不适合平衡模型 | ✅ KCN+S→KSCN 已实现（新增 potassium_cyanide/potassium_thiocyanate）；KMnO₄ 留空（判不适合） |
| 物理分离 | 空气液化分馏 | ⏳ 纯物理，无化学反应（N₂/O₂ 物种已具备） |
| 浓酸模块 | 芒硝法 NaCl+H₂SO₄(浓)、NaNO₃+H₂SO₄(浓) | ✅ 已实现（芒硝法 NaCl+H₂SO₄→NaHSO₄+HCl、NaNO₃+H₂SO₄→NaHSO₄+HNO₃，纯计量净反应）；新增 sodium_bisulfate/sodium_nitrate |
| 环境规则 | 铜自然锈蚀生成孔雀石 | ✅ 已实现（2Cu+O₂+CO₂+H₂O→Cu₂(OH)₂CO₃，纯计量净反应） |

**Track D 最终状态**：除下列 3 项（均判不适合当前平衡模型/非化学反应）外，全部留空项已实现
（引擎平衡条目或 D3 纯计量净反应，含哈伯法 N₂+3H₂→2NH₃ 净反应表达，真实部分转化属工艺细节）。
- **FeCl₂+Cl₂→2FeCl₃**：✅ e- 模型缺陷已根治——`Solver.activeComponents` 无条件激活 e- 组分 +
  `projectExact` 对 e- 亚量子负余量钳零，治愈"电子中性氧化还原"整类问题（known_limitations §8）。
  工业配方如需落地，另需补 FeCl₂ species 与 Cl₂ 的 e- 电对条目（现 DB 的 chlorine 走歧化路径）；
  测试 `RedoxSolverTest.ferrousChlorideOxidisedByChlorine` 已按 e- 电对锁定求解。
- **空气液化分馏**：纯物理分离，无化学反应式。
- **KMnO₄ 熔融氧化**：判不适合当前平衡模型（动力学/熔融主导）。
- **NaClO 热歧化**：热/冷产物选择性本质是动力学而非平衡，判不适合。

### D4 推进顺序

1. ✅ D2 数据补全（磷/锰/氯酸盐）——纯数据，最快，各配一个场景测试
2. → D1a 引擎扩展：含金属固相平衡条目（`Equilibrium` 双固体 + `SystemModel`/`Solver` 金属相
   质量作用行），先做金属置换（Fe+CuSO4）+ 氧化物酸溶（Fe2O3+6H+）——正确处理复合反应
3. → D1b 电解源/汇（氯碱、电解水，logK 放电排序 + 超电压）
4. → D3 独立模块逐个落地（燃烧/煅烧/冶金/熔融/有机/物理/浓酸/熔盐电解）——mod 侧或独立求解，与 Solver 解耦

---


## 依赖关系

```text
Track A（热力学数据）
   ↓
提供权威 delta_h / molarMass
   ↓
Track B（氧化还原）也需要这些数据
```

## 推进顺序

```text
1. Track A0-A2：先建立数据管道
2. Track B0-B3： redox 最小闭环（可并行推进）
3. Track A3-A5：全量数据合入 + 热测试
4. Track B4-B5： redox 数据扩展 + 场景测试
5. 合并 heat_kj → delta_h
6. 文档与交接（HANDOVER 要点并入本文档）
7. ✅ 多库合入：llnl/minteq 缺口换算合入 + NIST 兜底 + 测试分批重校准（已完成，65/65 全绿）
```

## 当前实现状态

- Track A1：✅ molarMass schema 已实现
- Track A3/A4：✅ `heat_kj` 已全部移除，所有反应统一使用非零 `delta_h`，并已用 PHREEQC 权威数据替换部分近似值
- Track A5：✅ 已增加 NH₃ 温度依赖测试
- Track A2：✅ 导入工具支持三库（phreeqc/llnl/minteq）混合格式解析与单位换算；`apply_phreeqc_delta.py` 完成直用/Hess 换算/NIST 兜底合入（含 `delta_h_source`/`delta_h_derivation` 来源标注）
- Track A0（多库）：✅ 三库已纳入；16 项权威 `delta_h` 已合入（4 直用 + 8 Hess + 4 NIST），测试重校准后 65/65 全绿；FeSCN²⁺ / Cu(NH₃)₄²⁺ 无权威数据，标记 `estimated`
- Track B1：✅ `e-` 解析与组分识别
- Track B2/B3：✅ 双电对 redox 最小闭环已跑通（Fe/Cu，正反方向）；Track B4 已加入三电对 Fe/Cu/Ce 测试
- Track B5：✅ 已加入湿法冶金 FeCl₃/CuCl、酸性硝酸盐还原、O₂ 好氧氧化场景测试
- 已知限制：固定 `pe` 外部电子库会破坏电荷平衡，不作为主路径
- 单测审计：✅ 77→80 全绿（详见 `docs/unit_test_audit.md`）：修正了 3 处与物理现实相悖的断言（氨水温度方向、硝酸盐/O₂ 电对数据）、补全 2 处缺失的守恒断言、为物理审计加了 `Solver.auditChecksRun` 防空转计数器、移除氨水水解死数据条目（含 `SystemModel.droppedEquilibria` 防再犯）；经**独立子代理干净记忆重审**（结论全部独立复现 + 1 处纠错：hclDescale 非刀刃，溶解即时是 feasibility clamp 结构性副作用，见 `docs/known_limitations.md` §6），并处理其新发现：Ksp 数值盲区（`slakedLimeSaturationAnchorsKsp` 锚定 + DataIntegrityTest 数值带宽检查）、F1 方向哨兵测试（`ammoniaHydrolysisDirectionIsKnownLimitationSnapshot`）、刀刃断言加厚、KNO3 带宽放宽。第二轮重审（排除 Track C）追加：修正文档事实错误（有效水解焓 −52.2 而非 −3.6、slakedLime ±0.24）、KNO3/NH4Cl 上界加厚 + NH4 载体守恒、O2/硝酸盐电子守恒、neutralisation 物理锚、DataIntegrity log_k 波段 ±150（存在性保护）
- Track E（小主基重构）：✅ 已完成（48 主基 + 49 派生次级，零弃删；HSO4 解离/碳酸盐水解/Fe3+Cu+ 净氧化还原/铝两性等真实平衡由 ΔG_f° 涌现；ChaosProbe 19 种子零违规；known_limitations §10 记录电子边界）
- 测试套件：SolvayProcess / AmmoniumChloride / IndustrialSynthesis / IndustrialRedox / Coverage / Thermo / Kinetics / Degas / Malachite / RedoxSolver / Jacobian / DataIntegrity / PhysicsAudit / Invariants / IndustrialProcessBlueprint 等 81 个，全绿

## 里程碑完成状态

```text
M1: ✅ PHREEQC 映射表 + molarMass schema
M2: ✅ redox 最小闭环 Fe3+/Fe2+（双电对/多电对）跑通
M3: ✅ 全量 delta_h / molarMass 合入，65/65 全绿
M4: ✅ redox 多电对 + 工业场景（Fe/Cu/Ce、NO₃⁻、O₂、湿法冶金）
M5: ✅ 删除 heat_kj，统一 delta_h
M6: ✅ 文档完成（开发指导统一并入 PLAN.md / tools）
M7: ✅ 多库权威数据合入（llnl/minteq/NIST），剩余估算项显式标注
M8: ✅ HANDOVER.md 并入 PLAN.md，单一指导文档
M9: ✅ NO₂ 强非线性收敛修复（化学计量预推进 seed，20°C 恢复正确收敛）
```

后续优化（非阻塞）：

- 补齐 FeSCN²⁺ / Cu(NH₃)₄²⁺ 第 4 级的文献焓数据（当前标记 `estimated`）
- **Kw 温度依赖（✅ 已修复，Track F1）**：`SpeciesDatabase.allEquilibria()` 的隐式自电离条目 `H+1 + OH-1 = water` 现在携带 ΔH = −55.91 kJ/mol（按书写方向：H+ + OH- → H2O 形成；取自 phreeqc.dat "H2O = OH- + H+" +55.9066 的反向）。修复不只是加 ΔH：`SystemModel` 的 leaf-elimination 代数原先**只把 logK 折算到组分空间，ΔH 却原样透传**，导致消除方向与书写方向相反/复合的条目范霍夫符号错（如 OH- = −H+ 存 −55.91 实应为 +55.91、HCO3- 水解条目的 41.01 实应为质子化 −14.9、Mg(OH)₂ 溶解 0.47 实应为酸溶 −111.3）。现新增 `exprDeltaH`：**ΔH 与 logK 走同一套消除代数**（均为反应空间的线性量），每个次生生/矿物拿到组分空间有效焓（`deltaHVan`）用于 van't Hoff；书写方向焓（`deltaH`）仍用于能量核算，Kw 次生生从能量路径剔除（其热量由 NEUTRALISATION_J_PER_PAIR 拢合，避免重复计费）。效果：氨水解有效焓 = +55.91 − 52.22 = **+3.69 kJ/mol**（放热 → 吸热，与作者 +3.69/真实 +3.3 一致）；Kw_diss 随温度升高（pKw ≈14.9@0°C、13.3@50°C）；碳酸盐方向不再依赖哪条等价条目存活。数据 JSON **无需重写**（exprDeltaH 在引擎内完成 PLAN 原设的"质子化/直接形式重推导"）。测试重校准：`ammoniaHydrolysisDirectionIsKnownLimitationSnapshot` 哨兵翻转升级为正向断言 `ammoniaHydrolysisIsEndothermicSoHotFavoursAmmonium`、Solvay step1 NaHCO3 阈值 120→100 MB（20°C 下新物理值 110.5 MB）。155/155 全绿。
- 审计残差度量：`Solver` 的 H 行（t[H]=0 时不缩放）让 phaseAssemble 中间加相迭代打印巨大的假残差（如 malachite 的 187663，实际质量平衡行 1e-15）；建议 H 行按水体积缩放或仅打印最终解
- 相集合联合搜索：把 `phaseAssemble` 的"保守回退 + 失败相跳过"替换为真正的联合相平衡搜索（枚举/单调切换），多矿物竞争当前仍可能落入次优子相集
- 继续扩充真实电对与工业场景（注意：redox 合成 logK Fe/Cu/Ce = 13/6/15 vs 真实 13.0/2.6/29.1，排序正确但 Ce 改真实值会使"not all Ce4+ consumed"断言失效，需同步重写断言）
- 模组集成：`chemical-addon` 引用本库、数据同步、`Result.rateLimited` / `energyJ` / `gasVented` 接线核对

## 已知限制

详见 `docs/known_limitations.md`。

## 风险

- PHREEQC 数据映射工作量大
- redox 加入后 Jacobian 条件数可能变差
- `delta_h` 用于 Van't Hoff 后可能改变现有温度测试
- 工业煅烧/电解等反应 PHREEQC 没有，需要补充来源

---

# 交接要点（原 HANDOVER.md，已并入）

## 关键设计决策与陷阱（接手前必读）

1. **主物种基自动推导**：`SystemModel` 用 leaf elimination 把游离离子 pin 成主物种，H⁺ 是电荷锚、OH⁻ 是自电离次生；两性条目（`solid+OH=[Al(OH)4]`）与同固体 Ksp 合并。
2. **水是不守恒溶剂**：组分守恒里没有 H₂O；电荷由"反应守恒电荷 + 输入中性"推出，H 组分用质量平衡（等价于电荷平衡）。
3. **整数投影**：固相先取整，再重解水相，每个自由组分精确吸收舍入误差 → 电荷/组分守恒在整数域严格成立。`projectExact`（快路径）和 `projectKinetic`（限速路径）都依赖这一点，别改动其"组分 = t − Σ二次×floor"结构。
4. **限速条目**：`budget = rate × water × 2^((T-25)/25) × stirring × |Q/K−1|`（尺度相对，20 tick = 1 s）。`projectKinetic` 用"全平衡物种向量沿慢条目化学计量回拉 + 冻结重解"。
5. **自电离抑制**：细网格下水会自发出现 H⁺=OH⁻≈1000 量子；`suppressAutoionisation` 成对抹掉（保堆叠身份）。**别把它删了，否则纯水测试会炸。**
6. **TOL 的张力**：投影要求连续解残差 < 1e-6 绝对（否则 `Math.rint` 检查失败）；但细网格下难溶矿的平衡残差下限是 `n_trace/t`。`TOL_F=1e-5` 是当前折中，将来若把投影改成"容忍亚量子残差"可以再收紧。
7. **limestone 带 `rate: 0.0001`**（每单位水/tick），测试里大结垢是渐进、小清理瞬时。mod 集成时要把同一字段补进 `chemical-addon` 的 `limestone.json`。
8. **mod 侧数据同步**：库的 `src/test/resources/species/*.json` 是 mod 数据的副本；两处都要改。
9. **可行 secondary 依赖传播（`activeComponents`）**：零总量组分只有在某个 secondary 的正系数反应物全部 active 时，才会因该 secondary 的负系数而被激活。这是通用规则，修复了 NO₂ 数据导致 Solvay 第 1 步 CO₃²⁻ 负余量、以及高 OH⁻ 下苛化法 CaCO₃ 不沉淀的问题。
10. **相装配回退**：`phaseAssemble` 每次 add/remove 固相前快照，联合 Newton 发散（阈值 `residualNorm > 0.02`）时回退到上一个收敛子相集并跳过失败相，而不是放弃。
11. **强非线性反应的收敛**：NO₂ 吸收（Q 的 5 次方）这类产物侧主导的反应，从纯反应物初值出发会让 secondary 爆炸、Newton 卡死。`Solver.newtonSolve` 的**化学计量预推进 seed**（secondary 预推进到限制反应物的一半）解决了这个问题；以后遇到收敛卡死，优先检查初值是否产物侧可行，而不是调 TOL。
12. **物理一致性审计**：`-Dchemengine.audit=true` 时求解器校验每个投影后状态的电荷中性、非限速 secondary 的质量作用律（Q=K）与矿物 SI，违规打印 `[audit] PHYSICS VIOLATION` 并收集到 `Solver.auditViolations`；参与物种 < 1 MB 的量子受限状态自动跳过（亚量子平衡舍入到 0/1 量子是格点伪影）。**回归防线：`PhysicsAuditTest`**——历史上出过"守恒全对但热力学是垃圾"的状态（NO₂ 曾输出 H⁺ = 输入 335 倍），只有守恒断言发现不了。新场景上线前用 `java -Dchemengine.audit=true -cp ... TestMain` 过一遍。注意审计是**数量级级守卫**（Q/K 容差 100×、SI 容差 10×），干净 ≠ 浓度级精确。

## Track E：B 引擎重构 —— 小主基 + 其余作次级（自洽数据 → 可用化学模型）

> 背景：`InorganicIonCatalog.basis()` 把全部 96 个离子都注册成**基组分**（独立守恒量）。
> 结果：共轭酸碱对（HSO₄⁻/SO₄²⁻、HCO₃⁻/CO₃²⁻、NH₄⁺/NH₃、H₂PO₄⁻/HPO₄²⁻/PO₄³⁻）、氧化还原电对
> （Fe³⁺/Fe²⁺ 等）、配合平衡 **都不互相转化** —— 求解结果是“守恒、自洽但无真实化学”（随机探针
> 里 HSO₄⁻ 永不变成 SO₄²⁻ + H⁺）。`audit_selfconsistency.py` 的 21/21 关系闭合只证明 **ΔG_f° 数据自洽**，
> 不等于模型**执行**了这些平衡。

### E1 核心设计（定稿）

**小主基**：每元素一个"主离子"作为基组分（~45 个），加上两个特殊基：`H+1`（电荷锚）、`e-`（伪离子，ΔG_f° = 0，NBS 电子参考态）。**其余全部离子推为次级物种**，由自洽 ΔG_f° 经 `fromFreeEnergy` 的 `species()` 路径推导形成常数 logK。

- **主基清单**（每元素主氧化态；括号内为覆盖元素）：
  - 单原子阳离子：`Li+1`(Li) `Na+1`(Na) `K+1`(K) `Rb+1`(Rb) `Cs+1`(Cs) `Ag+1`(Ag) `Au+1`(Au) `Be+2`(Be) `Mg+2`(Mg) `Ca+2`(Ca) `Sr+2`(Sr) `Ba+2`(Ba) `Zn+2`(Zn) `Cd+2`(Cd) `Hg+2`(Hg) `Pb+2`(Pb) `Sn+2`(Sn) `Cu+2`(Cu) `Fe+2`(Fe) `Co+2`(Co) `Ni+2`(Ni) `Mn+2`(Mn) `Cr+3`(Cr) `Al+3`(Al) `Bi+3`(Bi) `Tl+3`(Tl) `As+3`(As) `Sb+3`(Sb) `Ti+4`(Ti) `Zr+4`(Zr) `Pt+2`(Pt)
  - 含氧酸根/氧阳离子（金属元素主形态）：`MoO4-2`(Mo) `WO4-2`(W) `VO3-1`(V) `UO2+2`(U) `SeO4-2`(Se) `SiO3-2`(Si) `BO3-3`(B)
  - 卤素：`F-1` `Cl-1` `Br-1` `I-1`
  - 非金属含氧酸根（元素主形态）：`SO4-2`(S) `CO3-2`(C) `NO3-1`(N) `PO4-3`(P)
- **次级推导示例**（元素+电荷平衡；O 经水系数、化合价经 e-）：
  - 质子化：`HSO4-1 = SO4-2 + H+`；`HCO3-1 = CO3-2 + H+`；`HSO3-1 = SO3-2 + H+`（SO3-2 = SO4-2 + 2e- + 2H+ − H2O）；`HPO4-2`/`H2PO4-1`；`NH4+1 = NO3-1 + 8e- + 9H+ − 3H2O`（N 的强还原电对）
  - 氧化还原：`Fe+3 = Fe+2 − e-`（logK 由 ΔG 差自动得 13.0，与 legacy nE°/0.05916 一致）；`Cu+1 = Cu+2 + e-`；`MnO4-1`/`CrO4-2`/`S-2` 同理（= 主氧化态 + n·e- + 酸式 O 平衡）
  - 配合：`Cu(NH3)4+2 = Cu+2 + 4 NH3`（NH3 = NO3- + 8e- + 6H+ − 3H2O，charge 0）；`Ag(NH3)2+1`；`Fe(CN)6-4`（CN- = CO3-2 + NO3- + 10e- + 12H+ − 6H2O）
  - 两性：`Al(OH)4-1 = Al+3 + 4H2O − 4H+`（酸式书写，O 经 water 系数）
- **e- 作为基组分的语义**：非主氧化态离子携带隐含电子储备量（输入 Fe+3 → 组件总量 e- −30 mB）；求解器已有"无条件激活 e- 组分 + 亚量子余量钳零"机制（legacy §8 根治，同一套 Solver 直接适用）。ΔG_f°(e-) = 0、ΔG_f°(H+) = 0 同为 NBS 参考态，redox logK = nE°/0.05916 自动复现。
- **OH-1 不进入主基**：维持 Kw 自电离次级（{H+1: −1}，logK −14；ΔH 由 Track F1 修复，±55.91 分量随符号方向），水为无限溶剂不守恒。
- **验证约束**：全部 96 离子 + 氢氧化物固相必须可表达（`droppedEquilibria` 为空）；`SystemModel.balance()` 的组合搜索需把 **e-**（无元素）与 H+ 一并纳入候选（当前候选过滤要求含目标非 H/O 元素，会排除 e-）。

### E2 步骤（✅ 全部落地）

1. ✅ `InorganicIonCatalog` 拆分：`masters()`（48 主基 = 46 元素主离子 + H+1 + e-）与
   `secondaries()`（49 个派生离子）；`basis()` 保留全基兼容工具脚本，`database()` =
   masters + secondaries。全部 49 次级**零弃删**表达（`droppedEquilibria` 为空）。
2. ✅ `SystemModel.balance()` 重构：**e- 作为可枚举参数 ε ∈ [−64..64]** 折入电荷行
   （覆盖目录最大还原跨度 |ε|=60：6 个氰基配体）；允许负水系数（还原反应放出水，
   如 S-2 = SO4-2 + 8e- + 8H+ − 4H2O）；无 e- 基的小库（FreeEnergyModelTest 自有库）
   自动退回 ε=0 单路径。`Solver.projectExact` 的可行性修复改为**不动点循环**（单遍贪心
   修复会因一个组分的修复破坏后续组分余量，如 B4O7 顺带消耗 4 BO3-3）。
3. ✅ 测试迁移：`HydroxideExpressibilityTest` 改用 `database()`（Fe(OH)3 断言
   coeff[Fe+2]=+1、coeff[e-]=−1、coeff[H+]=−3 —— 酸溶 + 电子池形态）；`ChaosProbe`
   迁移到主基+次级模型（随机态从主基生成、元素+电子守恒）并计数失败、以退出码门控。
4. ✅ 新增 `FreeEnergyChemistryTest`（5 用例）：HSO4- 部分解离（定量复现 Ka2=10^-1.997）、
   CO3-2 水解/HCO3- 生成、Fe3+ + Cu+ 净氧化还原近乎完全（K=10^10.4）、Fe(OH)3 化学计量
   沉淀（30 mB）、铝两性（化学计量碱 → Al(OH)3 沉淀 99.6 mB；过量碱 → 完全溶解为
   [Al(OH)4]-）——**真实平衡全部由 ΔG_f° 涌现**。
5. ✅ tools：parse 结构未动（basis() 保留），`audit_selfconsistency.py` 保持 21/21。
6. ✅ 探针跨 19 种子（20240817/1/2/3/42/777/13579/24680/111111/987654/5/6/7/8/9/10/
   100/1000/999983）全部 0 抛异常 / 0 KKT 失败 / 0 净电荷违规；状态输出呈现真实 speciation
   （如 seed 42：Al+PO4+As 体系同时出现 H2PO4-/HPO4-2 质子化、AsO4-3 氧化（e- 进入池）、
   Al(OH)4- 络合）。

### E3 验收（✅ 达成）

- ✅ 酸碱/氧化还原/配合关系**在求解中实际执行**：HSO4- 输入必然部分解离（27 mB 化为
   SO4-2+H+，Ka2 精确复现）；Fe3+/Cu+ 电子对按 ΔG 达成平衡并近乎完全反应；Al 两性配合
   随碱量在 [Al(OH)4]- 与 Al(OH)3(s) 间锐利切换。CO3 水解、磷酸逐级质子化同步涌现。
- ✅ 随机探针（19 种子）出现真实 speciation 且守恒/KKT/电荷零违规；此前 96 全基模型
   "0 异常" 记录的原因恰是**没有化学可解**（全部独立守恒量），现存记录是**有化学且稳健**。
- ✅ `FreeEnergyModelTest`（自有小库）不回归；`HydroxideExpressibilityTest`/`ChaosProbe`
   已按新模型适配并全绿；全套 157 用例绿色。
- ⏳ 已知边界（`known_limitations.md` §10）：纯氧化剂/纯还原剂输入（如纯 Fe3+、纯 NH4+）
   的 n[e-]=0 边界 Newton 不可达——与 legacy §8 同类，输入需电子配平或由沉淀/络合提供
   电子汇源；NH3/NH4+、Cu(NH3)4 的常数已推导验证（logK(NH4+)=119.03 ↔ E°0.88 V），
   求解落地列入未来工作（外部 pe 库 / 边界投影）。


---

## 构建 / 测试

```bash
./gradlew test    # 或见 README 的纯 java + TestMain 路径（无网络时用 ~/.gradle 缓存 jar）
```

（本机无 Gradle 环境时，README 里有一句 `javac`/`java` 手跑说明；实际验证用的是 Windows JDK17 + 缓存 jar + `com.yu1745.chemengine.runner.TestMain`。）
