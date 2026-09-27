# HealthTrend 测试指南

三层，从便宜到贵：自动化测试 → Android Studio 预览 → 真机。绝大多数错误前两层就能抓到，所以按顺序往下走，别一上来就上手机。

环境准备（JDK、SDK、镜像）见 `development-setup.md`，这里假定已经做完。

---

## 1. 构建

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

./gradlew build            # lint + release APK + 全部单测
./gradlew installDebug     # 装到已连接设备
```

**debug** 和 **release** 包**开局都是空的**——应用不会自己往数据库里写任何虚构读数。要看图表，在仪表盘空状态点「加载演示数据」：那是 debug 才有的按钮，会种入约三个月的**虚构**读数（确定性生成，所以今天和明天截图只差结束的那一天）。真实用户看到的是空状态，所以**空状态要在 release 里测**。

---

## 2. 自动化测试

| 套件 | 覆盖内容 | 命令 |
|---|---|---|
| `:core:analytics`（64） | 描述性统计、EWMA、滑动窗口、OLS 趋势及其 p 值、异常检测、归一化、降采样——全部对照 NumPy/SciPy 基准向量 | `./gradlew :core:analytics:test` |
| `:core:data`（29） | 投影管道、查词分层、schema 迁移、导出 | `./gradlew :core:data:testDebugUnitTest` |

失败输出在 `core/*/build/test-results/`，HTML 报告在 `core/*/build/reports/tests/`。基准向量由 `tools/generate_golden_vectors.py` 生成并提交，analytics 测试加载那个 JSON。

有两个套件值得单独知道，因为它们断言的是**规则**而不是**行为**：

- `NutritionLookupRepositoryTest`——断言层级顺序；用户已经拥有的食物绝不会被当成新食物提供；保存时先注册营养素元数据**再**写值行（反过来的顺序会被 `RESTRICT` 外键拒绝）。
- `MetricDefinitionMigrationTest`——用**已提交的 `1.json`** 建出 v1 数据库，跑迁移，再用 Room 打开，由 Room 校验整个结果 schema。改动实体后，就是它告诉你迁移没跟上。

---

## 3. 预览——不需要设备

九个预览，全部用真实数据、经过真实分析引擎和真实营养素目录渲染。在 Android Studio 里打开文件点左侧 gutter 图标，或用 Preview 工具窗口。

| 文件 | 预览 | 要看什么 |
|---|---|---|
| `dashboard/DashboardScreen.kt` | `DashboardPreview` | 分类分区顺序、每个指标的最新值 |
| | `DashboardEmptyPreview` | 空状态是否读得通、演示按钮在不在 |
| `detail/MetricDetailScreen.kt` | `MetricDetailPreview` | 图表各层：原始折线、EWMA 拟合、OLS 点线、偏离点、参考带；**「关注方向」那一行**；卡片标题栏里的趋势徽标 |
| | `MetricDetailEmptyPreview` | 无图表、无统计、不崩 |
| `compare/CompareScreen.kt` | `ComparePreview` | 三条归一化曲线 + 图例；底部"无法归一化"的错误行 |
| | `CompareMinMaxPreview` | 深色主题、"至少选 2 个"状态 |
| `nutrition/NutritionScreen.kt` | `NutritionPreview` | 带来源徽标的搜索结果、已选食物、当日合计的 `数值 / 参考` |
| | `NutritionConfirmPreview` | **查词确认单**——尺寸是否合身、来源是否标明、每个数值是否可改 |
| | `NutritionEmptyPreview` | 深色主题、当天无记录 |

预览按默认语言（英文）渲染。想在不接设备的情况下看中文，临时给某个 `@Preview` 加 `locale = "zh-rCN"`。

---

## 4. 真机清单

构建全程刻意没开模拟器，所以下面每一条都只经过编译和预览验证。这是需要眼睛的部分。

1. **首次启动。** 装 debug 包打开。预期**什么都没有**：仪表盘显示空状态，只有 + 按钮和（debug 才有的）「加载演示数据」按钮。这一步是在确认应用不会自己编数据。**如果你装过更早的版本**，先清除应用数据（设置 → 应用 → HealthTrend → 存储 → 清除数据），否则旧的演示数据还在。
2. **种入演示数据（debug）。** 点「加载演示数据」。预期出现约 11 个指标分布在五个分类（Body / Health / Lifestyle / Activity / Nutrition），每个都有最新值。Nutrition 分区应有约 19 行——演示日记里含有的营养素数量。
3. **指标详情。** 点 *Body weight*。预期是 90 天图表，带参考带、原始线、平滑线、点状趋势线和两个偏离点；下面是描述性统计、趋势卡片、数据质量卡片。
4. **方向型指标。** 打开 *Total cholesterol*。趋势徽标应显示 **Falling**，而「关注方向」那一行显示 **Higher values**。**不应该有任何红绿**——下降对上限型指标是好事，但 App 不该替你下这个结论。
5. **对比。** 选三到四个指标，轮流切三种归一化方式。无法归一化的指标（Z-score 下试 *Uric acid*）应出现为一行错误，而不是悄悄消失。
6. **中英双语搜索。** 在食物日记里搜 `米饭`，再搜 `rice`。两者都应找到 *Cooked white rice*，且它的徽标显示 **我的食物库**（演示数据已收录它）——如果显示「内置词库」，说明它被当成新食物提供了。
7. **收录新食物。** 搜 `蓝莓` 或 `blueberry`（演示日记里没有）。点它：确认单应打开、一屏放得下、标出它来自哪条 USDA 记录、且每个数值可改。确认后记 100 g。
8. **投影。** 记完之后，当日合计应立即更新，仪表盘的 Nutrition 分区应出现新的营养素行。这就是 AGENTS.md §4.3——合计是从 `metric_observations` 读回来的，不是从日记累加的。
9. **导出。** 仪表盘 → *Export* → 存到 Downloads。解压：应有八个文件。用表格软件打开 `food_items.csv`，确认名称不乱码（文件带 UTF-8 BOM 就是为这个）。打开 `README.txt`——它应解释基准量的换算算法。
10. **语言。** 仪表盘 → *Language*：应打开**系统的**应用语言页，列出 English 和中文。切到中文再切回来。界面文案应变语言，**但存储的名称（食物名、指标名）不应变**——这是刻意的，见 `docs/TODO.md`。
11. **字体。** 三星设备上拉丁文字应为 SamsungOne；其他设备是 Roboto。两者都正确——App 是按名字申请设备字体族并回退。
12. **深色模式与旋转。** 切换深色模式：图表配色必须仍然可辨。旋转日记页和详情页。
13. **空状态（release）。** 装 release 包（或清除应用数据）打开：没有指标，空状态里**只有**手动录入入口，没有演示按钮（那个按钮是 debug 专属）。

---

## 5. 两个生成物的重新生成

二者都已提交；只在确实想改它们时才重生成，并且预期会出现可评审的 diff。

```bash
# NumPy/SciPy 基准向量（需要 tools/.venv，见 development-setup.md 第 5 节）
tools/.venv/bin/python tools/generate_golden_vectors.py

# 205 条食物词库，来自 USDA FoodData Central（需联网一次，约 6 MB）
python3 tools/generate_food_lexicon.py            # 空跑：只解析和报告
python3 tools/generate_food_lexicon.py --emit     # 写出 Kotlin
```

词库生成器在任何一条策展食物解析有歧义时都会拒绝输出，所以那里报错意味着要修数据，而不是可以跳过的步骤。

---

## 6. 已知空缺

- 完全没有设备端（instrumented）测试。
- 演示数据种入**从未真正跑过**：它能编译，它搜索的食物名也由 `:core:data` 的测试锁定，但种入路径本身未经验证。它现在是手动触发的，所以至少不会影响启动。
- 所有视觉部分——Vico 渲染、胶囊徽标、紧凑弹窗——都没在屏幕上验证过。
- `docs/TODO.md` 记录了延后项及其原因。
