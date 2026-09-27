# 开发环境搭建

在全新的 macOS（Apple Silicon）机器上构建与测试 HealthTrend 的可复现步骤。

## 1. 工具链

| 组件 | 版本 | 备注 |
|---|---|---|
| JDK | **17** | `brew install openjdk@17` |
| Gradle | **8.11.1** | 走 wrapper（`./gradlew`），无需系统安装 |
| Kotlin | 2.1.20 | 固定于 `gradle/libs.versions.toml` |
| AGP | 8.9.0 | 固定于 `gradle/libs.versions.toml` |
| Android SDK | `~/Library/Android/sdk` | |
| compileSdk / targetSdk / minSdk | 36 / 36 / 35 | |

### Shell 环境（`~/.zshrc`）

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
```

### Gradle 用的 JDK

两套互不相干的机制，刻意分开：

**仓库内** —— `gradle/gradle-daemon-jvm.properties` 固定 Gradle **守护进程**的工具链：

```properties
toolchainVersion=17
```

真正修好 Android Studio 的就是这个文件。Studio 从不传 `-Dorg.gradle.java.home`，而它自带的 JBR 是 Java 25，Gradle 8.11.1 会拒绝并报 *"incompatible with the Gradle JVM version 25"*。**Gradle 的守护进程 JVM 判据优先于 `org.gradle.java.home` 和启动器 JVM**，而 Studio 自己的 `IncompatibleGradleJvmAndGradleIssueChecker` 读的正是这个文件——所以判据文件修的是 IDE，不只是命令行。它只有一个版本号，因此提交进仓库。

**仓库外**（`~/.gradle/gradle.properties`）—— 只放本机相关路径：

```properties
org.gradle.java.home=/opt/homebrew/opt/openjdk@17
# Gradle 的工具链自动探测找不到 Homebrew 的 openjdk@17 formula：
org.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@17
```

手改 `.idea/gradle.xml` **无效**：Studio 2026.1 的迁移同步监听器会把 `gradleJvm` 改回 `jbr-25`。

## 2. Android SDK 组件

```bash
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "platforms;android-36" \
           "build-tools;36.0.0" "emulator" \
           "system-images;android-35;google_apis;arm64-v8a"

avdmanager create avd -n Pixel_8_API_35 \
  -k "system-images;android-35;google_apis;arm64-v8a" -d pixel_8
```

## 3. Robolectric 离线运行时（每台机器装一次）

`:core:data` 的单测通过 Robolectric 让 Room 跑在真实 SQLite 上。
**Robolectric 会自己去 Maven Central 下载 Android 运行时**（不走 Gradle），在这台机器所在网络上不可靠。把 jar 预置进本地 Maven 仓库，测试任务即可完全离线：

```bash
REL=org/robolectric/android-all-instrumented/15-robolectric-12650502-i7
JAR=android-all-instrumented-15-robolectric-12650502-i7.jar
DEST="$HOME/.m2/repository/$REL"
mkdir -p "$DEST"
curl -L --retry 3 -o "$DEST/$JAR" "https://maven.aliyun.com/repository/central/$REL/$JAR"
shasum -a 1 "$DEST/$JAR" | awk '{print $1}' > "$DEST/$JAR.sha1"
```

该产物版本由 Robolectric 4.14.1 针对 `@Config(sdk = [35])` 决定。如果 `libs.versions.toml` 里的 Robolectric 版本变了，从一次失败的 `testDebugUnitTest` 输出里读新坐标（报错信息会提到 `android-all-instrumented:<version>`）。

## 4. 构建与测试

```bash
./gradlew build                       # 全量
./gradlew :core:common:test :core:domain:test   # 纯 JVM 单测
./gradlew :core:analytics:test                  # 数学引擎 + golden-master 基准
./gradlew :core:data:testDebugUnitTest          # Room/Robolectric 集成测试
./gradlew :app:assembleDebug                    # APK -> app/build/outputs/apk/debug/
```

## 5. Golden-master 基准向量（AGENTS.md §8.1 / §8.2）

`:core:analytics` 的数值结果对照 NumPy/SciPy 验证，而不是对照手算常数。基准文件由一个独立脚本生成：

```bash
python3 -m venv tools/.venv
tools/.venv/bin/python -m pip install -i https://mirrors.aliyun.com/pypi/simple/ numpy scipy
tools/.venv/bin/python tools/generate_golden_vectors.py
```

它会重写 `core/analytics/src/test/resources/golden/golden_test_vectors.json`，由 `GoldenMasterTest` 加载并断言。输入也存在基准文件里，所以两边不会各存一份数据。**任何算法改动都必须同步改生成脚本并重新生成基准**，否则测试断言的就是过期的"真值"。

## 6. Maven 镜像

`settings.gradle.kts` 把阿里云镜像排在 `google()` / `mavenCentral()` 之前，因为直连 `repo1.maven.org` 的 HTTPS 在这台机器所在网络上会被 reset。上游仓库仍保留作为兜底，所以换到能直连 Maven Central 的网络也能构建。

Robolectric 完全不使用 Gradle 仓库——见第 3 节。

## 7. UI（Phase 3）

四个 Compose 目的地，在 `ui/navigation/HealthTrendNavHost.kt` 中接线：

| 路由 | 界面 | ViewModel |
|---|---|---|
| `dashboard` | `DashboardScreen` | `DashboardViewModel` |
| `detail/{metricId}` | `MetricDetailScreen` | `MetricDetailViewModel` |
| `compare` | `CompareScreen` | `CompareViewModel` |
| `nutrition` | `NutritionScreen` | `NutritionViewModel` |

每个界面拆成一层薄薄的 `*Screen`（Koin 注入 ViewModel）和一个无状态的 `*Content`，`@Preview` 渲染的是后者，因此 IDE 预览不需要 Koin 图。预览里的假数据是把合成序列喂给**真实的** `:core:analytics` 引擎算出来的，这样预览不会和 ViewModel 的真实产出脱节。

**评审数据。**`src/debug` 提供一个 `DemoDataInstaller`，种入约三个月确定性数据；`src/release` 提供的是空实现。仪表盘的"加载演示数据"按钮和首次启动的自动种入走同一个接口，所以 release 包完全不引用种入器。自 Phase 4 起，种入的食物是**通过真实查词管道**取来的（搜索 → 取档案 → 确认保存），因此 debug 包里每个营养数字都能追回词库所记录的 USDA 条目——而且每次全新安装都会跑一遍查词路径。

## 8. 食物词库（AGENTS.md §5.2）

内置离线词典是**生成的，不是手写的**。凭记忆敲的营养数字正是健康类 App 绝不能发的东西，所以其中每个数字都能追回一条公开记录：

```bash
# 需要联网一次（约 6 MB，缓存在 tools/.cache/）。
python3 tools/generate_food_lexicon.py           # 解析并报告，不写任何文件
python3 tools/generate_food_lexicon.py --emit    # 写出生成的 Kotlin
python3 tools/generate_food_lexicon.py --refresh # 先重新下载数据集
```

- **数据源**：USDA FoodData Central 的 *SR Legacy*（2018 年 4 月），公有领域。它是美国成分表，所以**食材**覆盖很好（米饭、鸡胸肉、豆腐、西兰花、三文鱼、鸡蛋、全脂牛奶……），**预制菜**完全没有——那正是未来联网/品牌层要补的。
- **人类策展的一半**：`tools/food_lexicon_catalog.py` 决定收录哪些食物、中文叫什么。**机器的一半**：`tools/generate_food_lexicon.py` 把每条解析到唯一的 FDC 记录，只要有任何一条模式有歧义就拒绝输出——所以刷新数据集不会悄悄换掉某个食物。
- **产物**：`core/data/src/main/kotlin/…/lexicon/BundledFoodLexiconData.kt`，生成、提交，是构建唯一需要的东西。日常构建和测试完全不碰网络。
- 天然按个计数的食物（鸡蛋、苹果、香蕉、橙子）按**每个**输出，克重取自 FDC 自己的 household measure。这是刻意的：AGENTS.md §1.1 那条"基准量绝不被假定为 100"，于是由**随包发布的数据**在扛，而不是只有测试在扛。

构建过程中踩到、现在脚本已防住的两个坑：

1. SR Legacy 里有**两行都叫 `Energy`**——一行 kcal（FDC 1008），一行 kJ（1062）。只按名字取数会拿到千焦那行，把**所有热量数字放大 4.184 倍**。营养素现在按 *(名称, 单位)* 选取。
2. Kotlin 文件里所有顶层属性的初始化代码会编译进**同一个 `<clinit>`**，205 个食物 × 约 22 个营养素撑爆了 JVM 每方法 64 KB 上限（`MethodTooLargeException`）。所以生成文件按分类输出成多个 `object`，各自独立初始化。

`NutrientCatalog`（`:core:domain`）是手写的另一半：22 个营养素的规范名称（英文，作为存储值）、单位和参考摄入量。新增一个营养素依然是一行数据、零迁移（AGENTS.md §1.2），而 `NutritionLookupRepositoryTest` 双向断言目录与词库一致——没有食物引用目录未描述的营养素，也没有目录条目是死元数据。

## 9. 与 AGENTS.md 的已核实偏差

| # | AGENTS.md | 实际做法 | 原因 |
|---|---|---|---|
| 1 | §2.1 用 `sudo ln -sfn ...` 注册 JDK 17 | `JAVA_HOME` + `org.gradle.java.home` 指向 Homebrew 的 `openjdk@17` | `sudo` 需要密码，非交互自动化跑不了。效果等价，不碰系统目录。 |
| 2 | §4.3 `meal_logs` 只按 `timestamp` 建索引 | 另加 `food_id` | Room 要求外键子列必须有索引，其警告也明确建议如此。只影响性能，无语义变化。 |
| 3 | §3.2 固定 AGP 8.9.0，§2.2 要求 compileSdk 36 | `gradle.properties` 里 `android.suppressUnsupportedCompileSdk=36` | AGP 8.9.0 只测到 compileSdk 35。抑制该检查可保留固定版本同时满足要求的 compileSdk。升级 AGP 后应复查。 |
| 4 | §2.1 通过 `JAVA_HOME` / JDK 软链设置 Gradle JVM | 提交 `gradle/gradle-daemon-jvm.properties`（`toolchainVersion=17`）+ 不提交的 `~/.gradle/gradle.properties` | Gradle 8.11.1 跑不了 Android Studio 自带的 JBR 25，而守护进程 JVM 判据是 Studio 唯一认的机制。见第 1 节。 |
| 5 | §4.4 "用索引保证查询性能" | 食物搜索在内存里过滤词库，而不是用 SQL `LIKE` | 内置词库只有几百行，一次读取同时服务搜索框和日记的食物名查找。词库涨到几千行以上时应复查。 |
| 6 | §4.2 `FoodItemEntity` 没有 `maxValue` 式的上界 | `NutrientDefinition.dailyRecommended` 可空，缺失时渲染为 `—` | 词库并不总能给出推荐值；编一个数字比显示破折号更糟。 |
| 7 | §5.2 "约 300 种标准中国/国际常见食材" | 205 条策展食物 | SR Legacy 是美国成分表；词库里的每一条都是记录能被无歧义解析出来的。用"名字对不上实物"的记录凑数比列表短更糟。 |
| 8 | §5.2 第三层"联网公共数据库 / API 扩展（可选插件）" | 插件点已交付，并由测试里的桩 provider 验证过，但没有 provider 发真实请求 | USDA 数据已通过**预烘焙进词库**离线覆盖，对离线优先的 App 来说严格优于运行时调用。实时适配器（OpenFoodFacts、品牌/条码查询）在无法验证的网络条件下，宁可延后也不带着未验证的代码发布。 |
| 9 | §5.2 "轻量级 SQLite / 静态预置表" | 编译进包的 Kotlin 表 | 免掉了没有设备就无法验证的运行时文件/asset 查找，并让 JVM 测试能直接检查随包发布的数据。 |
| 10 | §5.1 `getNutrientProfile(foodRefId: String)` | 引用带 provider 作用域（`bundled:rice_cooked`） | 两层可能都认识一个叫 `rice_cooked` 的食物；一旦存在第二个 provider，裸 id 就有歧义。两者作为一个值传递，避免被拆开。 |
| 11 | §5.2 把"第三优先级…第四优先级"写成并列描述 | 层级枚举化（`PERSONAL`、`BUNDLED`、`REMOTE`、`ESTIMATE`）并按序走 | 把优先级变成枚举上的显式顺序，新增一个 provider 就只是注册进 Koin，不用改管道。 |

## 10. 网络说明

直连 `repo1.maven.org`（Maven Central）的 HTTPS 在这台机器所在网络上经常被 reset，所以第 6 节配了镜像。Robolectric 完全不用 Gradle 仓库，故见第 3 节。
