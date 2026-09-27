# 待办

延后项，连同"为什么延后"和"什么条件下可以动"。这里没有一项是被设计决定卡住的——每一项都在等：**这台机器给不了的验证**、**只有产品所有者能做的决定**、或者**还没落地的阶段**。

大致按该动手的先后排序。

---

## 1. 联网食物库 provider（AGENTS.md §5.2，第三层）

**做什么。** 在 `LookupTier.REMOTE` 上实现一个 `NutritionLookupProvider`，对接公开食物数据库——OpenFoodFacts 做品牌/条码查询，或 USDA FoodData Central 的实时 API 补内置词库没有的条目。

**为什么延后。** 不是因为 App 是离线优先。**网络从来不是障碍**：第 2 项同样需要联网。障碍是**第三方实时调用在这台机器上无法验证**，而发布一个没人见过它返回正确结果的适配器，等于发布一条谁都不知道对不对的代码路径。AGENTS.md 的验证门禁不允许把这种工作标成"做完"。

**什么条件下可以动。** 插件点已经就位并有测试覆盖：

- `NutritionLookupProvider` + `LookupTier` + `FoodRef`（`:core:domain`）。
- `NutritionLookupRepositoryImpl` 负责层级排序、按名称去重、把 `profile()` 路由回所属 provider、并把用户已拥有的命中重标为"我的"——全部由 `NutritionLookupRepositoryTest` 覆盖。
- 注册就是 `AppModule` 里一个 `single<NutritionLookupProvider>(named(...))`。

所以要做的是：(a) 在一个可注入的 HTTP 传输端口上写适配器；(b) 用罐头响应测 JSON → `FoodNutrientProfile` 的映射；(c) 在一个允许的网络里对真实端点跑一次；(d) 加 `INTERNET` 权限。某一层失败本来就会被跳过，所以降级到内置词库是自动的。

## 2. AI / 自然语言估算 provider（AGENTS.md §5.2，第四层）

**做什么。** "一碗红烧牛肉面加一个荷包蛋" → 粗略的热量和营养素估算，落在 `LookupTier.ESTIMATE`。

**为什么延后。** 与第 1 项同样的验证问题，另外还要 key 和成本策略。它按设计就是优先级最低的一层：枚举已把它排在最后，所以它永远不可能顶掉实测值。

**什么条件下可以动。** 实现 `NutritionLookupProvider` 就够了，管道不用改。有两条要求现在就该写下、免得以后丢掉：必须 `isOfflineCapable = false`；它的输出必须原样走现有的确认单，因为 §5.3 那条"保存前给用户看"**对猜测比对数据库记录更重要**。

## 3. 数据导入器

**做什么。** 把导出的快照读回数据库（见 `DataExporter`）。

**为什么延后。** 是刻意延后，不是没时间。往已有历史里导入，正是本地优先 App 会**悄悄毁掉数据**的地方：id 冲突、部分成功的导入、来自不同 schema 版本的快照，以及导入到底是**合并**还是**替换**——每个都是没有安全默认值的设计决定。导出只读所以安全；导入不是，而且导出格式应该先被真实使用过，再冻结成恢复路径。

**什么条件下可以动。** 先定合并还是替换。manifest 里已经带了 `formatVersion` 和 `databaseVersion`，将来的导入器可以据此拒绝不认识的快照。

## 4. 扩充内置词库

205 条，全部从 SR Legacy 无歧义解析出来。空缺是结构性的、不是偶然的：SR Legacy 是美国成分表，所以没有中式预制菜（馒头、饺子、油条、豆浆、米粥），另有个别食材没有干净记录（莲藕、空心菜、荔枝）。

两个选项，按偏好排序：

1. 把 FNDDS（`FoodData_Central_survey_food_csv`）加为第二个数据源以覆盖预制食品。它和 SR Legacy 一样是每 100 g，且有 `fndds_ingredient` 映射，同一个生成器可以吸收它。
2. 在 SR Legacy 内部继续扩充策展清单——便宜，但覆盖良好的食材快用完了。

两条路的工作都发生在 `tools/food_lexicon_catalog.py`；生成器和测试已经是通用的。

## 5. 繁体中文

目前只发 `values-zh-rCN`，所以设备语言是 `zh-TW`/`zh-Hant` 时会回退到英文，而不是显示简体。加 `values-zh-rTW` 并在 `locales_config.xml` 里加第二个 `<locale>` 是机械工作，检查点在 `docs/TESTING.md` §4 第 9 步。

## 6. 真机验证

所有视觉部分和演示数据种入都只经过编译与预览验证——模拟器没跑过。`docs/TESTING.md` §4 是清单，每步都写了期望结果；§6 列出了仍未证实的具体内容。

## 7. Koin DSL 弃用

`AppModule` 用的是 `org.koin.androidx.viewmodel.dsl.viewModel`，Koin 4 已弃用，改用 `org.koin.core.module.dsl.*`。纯粹是改 import，但每次构建都会打四条警告。
