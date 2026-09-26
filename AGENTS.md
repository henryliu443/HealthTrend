# HealthTrend — Master Development & Architecture Specification (v3.0)

> **Personal Health & Longitudinal Data Analytics App**  
> *Local-first, Pure Kotlin Math Engine, Jetpack Compose, Room Persistence*  
> 全量架构规划、数学算法设计蓝图、动态营养模型、自动营养获取服务及终极防错手册

---

## 目录

- [〇、现状盘点与架构基本法](#〇现状盘点与架构基本法)
  - [0.1 DevConfig-Gen 能否替代 Room？结论：绝不可替代](#01-devconfig-gen-能否替代-room结论绝不可替代)
  - [0.2 纯 Kotlin 引擎裁决：坚决拒绝移动端 Python Runtime](#02-纯-kotlin-引擎裁决坚决拒绝移动端-python-runtime)
  - [0.3 Kotlin / Java 互操作规范：纯 Kotlin 源码 + 零成本调用成熟 Java 科学计算库](#03-kotlin--java-互操作规范纯-kotlin-源码--零成本调用成熟-java-科学计算库)
- [一、核心思想升级（v3.0 重大演进）](#一核心思想升级v30-重大演进)
  - [1.1 营养学语义纠正：Reference Amount 不是用户输入规则](#11-营养学语义纠正reference-amount-不是用户输入规则)
  - [1.2 营养素解耦：拒绝在数据库中将微量元素写死成列](#12-营养素解耦拒绝在数据库中将微量元素写死成列)
  - [1.3 终极统一：Nutrition 是摄入域，进入分析层全是一维时序](#13-终极统一nutrition-是摄入域进入分析层全是一维时序)
  - [1.4 新增特性：自动获取参考营养值与卡路里服务 (Nutrition Lookup Service)](#14-新增特性自动获取参考营养值与卡路里服务-nutrition-lookup-service)
  - [1.5 缓存策略降级：正确性第一，性能调优延后 (Deferred Cache)](#15-缓存策略降级正确性第一性能调优延后-deferred-cache)
- [二、环境准备与 Android SDK 搭建指引（针对当前无 SDK 环境）](#二环境准备与-android-sdk-搭建指引针对当前无-sdk-环境)
  - [2.1 macOS SDK 安装路径与环境变量配置](#21-macos-sdk-安装路径与环境变量配置)
  - [2.2 SDK Platforms & Build-Tools 版本选型（minSdk 35 / compileSdk 36）](#22-sdk-platforms--build-tools-版本选型minsdk-35--compilesdk-36)
  - [2.3 本地 ARM64 AVD 模拟器极速搭建](#23-本地-arm64-avd-模拟器极速搭建)
- [三、模块分层与 Gradle 架构体系](#三模块分层与-gradle-架构体系)
  - [3.1 多模块边界定义 (Multi-Module Boundary)](#31-多模块边界定义-multi-module-boundary)
  - [3.2 libs.versions.toml 集中化依赖配置](#32-libsversionstoml-集中化依赖配置)
  - [3.3 Analytics Engine 绝对隔离原则（零 Android / 零业务依赖）](#33-analytics-engine-绝对隔离原则零-android--零业务依赖)
- [四、数据持久化与可扩展数据库设计（Room + SQLite）](#四数据持久化与可扩展数据库设计room--sqlite)
  - [4.1 核心指标表结构（MetricDefinition & MetricObservation）](#41-核心指标表结构metricdefinition--metricobservation)
  - [4.2 动态扩展的营养数据库设计（FoodItem, NutrientDefinition, FoodNutrientValue）](#42-动态扩展的营养数据库设计fooditem-nutrientdefinition-foodnutrientvalue)
  - [4.3 摄入日志与自动时序投影管道 (Intake Logging & Automatic Projection)](#43-摄入日志与自动时序投影管道-intake-logging--automatic-projection)
  - [4.4 时序索引与冷热查询优化](#44-时序索引与冷热查询优化)
  - [4.5 数据库 Migration 策略与 TypeConverters](#45-数据库-migration-策略与-typeconverters)
- [五、自动获取参考营养值与卡路里架构设计 (Nutrition Lookup Architecture)](#五自动获取参考营养值与卡路里架构设计-nutrition-lookup-architecture)
  - [5.1 插件化接口：NutritionLookupProvider](#51-插件化接口nutritionlookupprovider)
  - [5.2 三级获取策略：内置离线词典 → 公共数据库 API → AI/估算 Adapter](#52-三级获取策略内置离线词典--公共数据库-api--ai估算-adapter)
  - [5.3 离线优先与用户确认机制](#53-离线优先与用户确认机制)
- [六、Analytics Engine 算法数学蓝图与具体陷阱防御](#六analytics-engine-算法数学蓝图与具体陷阱防御)
  - [6.1 描述性统计（数值稳定性与 Sample/Population 陷阱）](#61-描述性统计数值稳定性与-samplepopulation-陷阱)
  - [6.2 累计与时间聚合（自然日历与时区对齐）](#62-累计与时间聚合自然日历与时区对齐)
  - [6.3 变化率、加速度与复合变化（CAGR / 算术 vs 几何）](#63-变化率加速度与复合变化cagr--算术-vs-几何)
  - [6.4 滑动窗口统计（基于时间天数而非点数的多指针滑动算法）](#64-滑动窗口统计基于时间天数而非点数的多指针滑动算法)
  - [6.5 指数平滑（不规则时间序列的时间衰减 EWMA）](#65-指数平滑不规则时间序列的时间衰减-ewma)
  - [6.6 趋势分析与局部趋势（带时间戳的 OLS 线性回归与 t-检验）](#66-趋势分析与局部趋势带时间戳的-ols-线性回归与-t-检验)
  - [6.7 异常检测综合算法（Z-Score, IQR, MAD, EWMA Residual）](#67-异常检测综合算法z-score-iqr-mad-ewma-residual)
  - [6.8 交叉相关、滞后分析与协方差矩阵（时间戳对齐与 Spearman 秩次）](#68-交叉相关滞后分析与协方差矩阵时间戳对齐与-spearman-秩次)
  - [6.9 周期性分析、趋势剔除与 FFT（窗函数与功率谱密度）](#69-周期性分析趋势剔除与-fft窗函数与功率谱密度)
  - [6.10 数据质量与采样密度分析](#610-数据质量与采样密度分析)
- [七、UI 架构与可视化实现（Compose + MVI + Vico）](#七ui-架构与可视化实现compose--mvi--vico)
  - [7.1 响应式单向数据流（MVI / UDF）与调度器隔离](#71-响应式单向数据流mvi--udf与调度器隔离)
  - [7.2 Vico 图表层复合渲染封装](#72-vico-图表层复合渲染封装)
  - [7.3 多指标归一化对比视图（Z-Score / Min-Max / Baseline 100）](#73-多指标归一化对比视图z-score--min-max--baseline-100)
  - [7.4 性能防卡顿：LTTB 降采样算法集成](#74-性能防卡顿lttb-降采样算法集成)
- [八、测试战略与 Python Golden-Master 参照校验体系](#八测试战略与-python-golden-master-参照校验体系)
  - [8.1 Python 生成基准向量脚本 (NumPy / SciPy Reference Generator)](#81-python-生成基准向量脚本-numpy--scipy-reference-generator)
  - [8.2 Kotlin JVM 测试套件与边界验证矩阵](#82-kotlin-jvm-测试套件与边界验证矩阵)
- [九、全周期项目开发里程碑与排期表](#九全周期项目开发里程碑与排期表)
- [十、全系统终极防错避坑手册 (The Non-Negotiable Rules)](#十全系统终极防错避坑手册-the-non-negotiable-rules)

---

## 〇、现状盘点与架构基本法

### 0.1 DevConfig-Gen 能否替代 Room？结论：绝不可替代

- **业务本质不同**：`DevConfig-Gen` 是面向文本/配置转换生成工具（JSON/YAML/Env 序列化），而 HealthTrend 是以本地持久化、长周期、不规则时间序列查询为主的个人数据库应用。
- **无查询引擎**：`DevConfig-Gen` 完全不具备 SQLite 的 B-Tree 索引、范围查询、ACID 事务、聚合查询与响应式数据流（Flow）机制。
- **结论**：`DevConfig-Gen` 仅作为独立项目参考其模块组织思想，**本项目绝对不采用其作为持久层，全面使用标准的 Room + SQLite**。

### 0.2 纯 Kotlin 引擎裁决：坚决拒绝移动端 Python Runtime

- **体积与冷启动**：Android 引入 Python Runtime（如 Chaquopy）会导致 APK 膨胀 50MB+，冷启动严重延迟 2~3 秒，且伴随双向 JNI 序列化性能开销与内存翻倍。
- **纯 Kotlin 完胜**：统计学与时间序列的核心算法在 JVM 运行时上以编译期强类型优化执行，纳秒级响应，零调试摩擦，内存零多余拷贝。

### 0.3 Kotlin / Java 互操作规范：纯 Kotlin 源码 + 零成本调用成熟 Java 科学计算库

- **源码 100% Kotlin**：项目全仓均为 `.kt`，无须写一行 Java。
- **生态零成本共享**：在需要复杂概率分布函数（如 Student's t 分布累计概率 $p$-value 检验）或线性代数计算时，通过 Gradle 依赖引入 `org.apache.commons:commons-math3`，在 Kotlin 中像原生标准库一样无感直接调用。

---

## 一、核心思想升级（v3.0 重大演进）

本章专门收录对原规格的重大纠偏与思想升华。

### 1.1 营养学语义纠正：Reference Amount 不是用户输入规则

> [!IMPORTANT]
> **“100 g”是营养数据库的参考基准量（Reference Amount），绝不能成为用户的输入规则或系统的硬编码常数。**

真实场景中：
- 包装食品可能标示：每份 30 g 含热量 120 kcal；
- 瓶装饮品可能标示：每 100 ml 含糖 9.5 g；
- 某些食材按个计算：1 个鸡蛋（约 50 g）含蛋白质 6.3 g。

**严谨的动态计算公式**：
$$\text{ActualNutrient} = \text{NutrientPerReferenceAmount} \times \frac{\text{ActualIntakeAmount}}{\text{ReferenceAmount}}$$

- 用户输入：米饭 `73 g`，数据库基准：`100 g` 对应 `2.6 g` 蛋白质 $\implies 2.6 \times \frac{73}{100} = 1.898 \text{ g}$。
- 用户输入：牛奶 `237 ml`，数据库基准：`100 ml` 对应 `3.4 g` 蛋白质 $\implies 3.4 \times \frac{237}{100} = 8.058 \text{ g}$。
- 用户输入：鸡蛋 `2 个`，数据库基准：`1 个` 对应 `70 kcal` $\implies 70 \times \frac{2}{1} = 140 \text{ kcal}$。

系统数据模型支持**任意浮点基准量**与**基准单位**（`g`, `ml`, `serving`, `piece`）。

---

### 1.2 营养素解耦：拒绝在数据库中将微量元素写死成列

传统的反模式是将卡路里、蛋白质、钙、铁、钠作为 `FoodItemEntity` 的固定列。
**致命弊端**：
1. 世界上存在数十种维生素、微量矿物质、脂肪酸（饱和/单不饱和/Omega-3/反式脂肪酸）、添加糖等。
2. 一旦写死为列，每支持一种新营养素都需要编写 SQLite `ALTER TABLE` 迁移脚本并升级 App 版本。
3. 大量食材缺失某些微量元素，导致表里出现大量冗余的 NULL。

**v3.0 规范：全面采用实体-属性解耦设计 (Entity-Attribute-Value Model / Normalized Schema)**：
- `NutrientDefinition`（营养素定义：id, name, unit, category）
- `FoodItem`（食物本体：id, name, defaultReferenceAmount, defaultReferenceUnit）
- `FoodNutrientValue`（食物营养对照：foodId, nutrientId, amountPerReference）

**收益**：未来新增“维生素 B12”、“叶酸”、“牛磺酸”，只需向 `nutrient_definitions` 表插入一条元数据记录，**数据库 Schema 零变动，代码零重构**！

---

### 1.3 终极统一：Nutrition 是摄入域，进入分析层全是一维时序

在顶层领域建模中，**彻底消除 Nutrition 与 Health Metric 的对立隔阂**：
- **摄入域 (Nutrition Ingestion Domain)**：负责食物字典、分量输入、单次进餐记录与按日汇总。
- **统一投影 (Projection to Time Series)**：日结汇总后的营养素指标，直接投影为统一的 `MetricObservation`（例如 `metric_id = "nutrient_protein"`, `value = 125.4`, `timestamp = dayStartEpoch`）。
- **分析引擎的纯粹性**：Analytics Engine 保持 **100% 领域无关 (Domain-Agnostic)**。数学引擎只认 `TimeSeries(timestamp, value)`，它不在乎这个数字代表的是“ALT 转氨酶”还是“摄入钠毫克数”，从而以一套数学内核支撑全生命周期健康分析。

---

### 1.4 新增特性：自动获取参考营养值与卡路里服务 (Nutrition Lookup Service)

用户记录时不可能每次手动查找食品成分表。系统设计一个**离线优先、插件化可扩展的自动营养解析与补全服务**。

```
                    ┌──────────────────────────────┐
                    │ 用户输入食物名称 (如: "燕麦") │
                    └──────────────┬───────────────┘
                                   │
                                   ▼
                ┌──────────────────────────────────────┐
                │        NutritionLookupService        │
                └──────────────────┬───────────────────┘
                                   │
         ┌─────────────────────────┼─────────────────────────┐
         │ (1) 命中本地历史/收藏   │ (2) 本地内置标准词典    │ (3) 联网公共数据源 API
         ▼                         ▼                         ▼
┌──────────────────┐      ┌──────────────────┐      ┌─────────────────────────┐
│ 用户自建 FoodItem │      │ 预置 Common Foods │      │ OpenFoodFacts / USDA    │
│ (SQLite local)   │      │ (~300常见食物)   │      │ FoodData Central / LLM  │
└──────────────────┘      └──────────────────┘      └─────────────────────────┘
         │                         │                         │
         └─────────────────────────┼─────────────────────────┘
                                   │
                                   ▼
                ┌──────────────────────────────────────┐
                │ 返回带 ReferenceAmount 的营养属性集合 │
                │ (用户界面预览确认 -> 自动填入重量)   │
                └──────────────────────────────────────┘
```

- **内置常见食物包**：预置大米、鸡胸肉、鸡蛋、西红柿、橄榄油等约 200~500 种常见食材的标准营养数据（离线可用）。
- **可插拔远程扩展 Provider**：提供标准接口，未来允许接入 OpenFoodFacts、USDA FoodData Central 或大模型语义估算服务。
- **免手动录入**：输入食物后自动带出默认基准分量（如 100g），用户只需调节实际克数（如 180g），秒级完成记录。

---

### 1.5 缓存策略降级：正确性第一，性能调优延后 (Deferred Cache)

- 原规划中的 `AnalysisCacheEntity`（SQLite 分析结果持久缓存）**暂时降级为 Phase 7 性能优化备选**。
- **理由**：健康数据即使记录 10 年，日频数据也仅 3,650 个点。纯 Kotlin 运算 3,650 个浮点数的均值、方差、线性回归或 FFT，在现代手机 CPU 上通常仅需 **1ms ~ 5ms**。
- 过早引入持久化磁盘缓存会导致：
  1. 缓存一致性维护成本极高（任何单点修改/删除都要处理复杂的逐级失效）。
  2. 极易引发由于缓存未刷新造成的“数据更新了但图表不反应”的诡异 Bug。
- **第一阶段缓存原则**：仅依靠内存中的 **Kotlin `StateFlow` 与 ViewModel 级 `remember` / `derivedStateOf`** 缓存，杜绝磁盘过度设计。

---

## 二、环境准备与 Android SDK 搭建指引（针对当前无 SDK 环境）

针对 macOS（Apple Silicon 架构）当前完全未安装 Android SDK 的现状，提供精准命令步骤：

### 2.1 macOS SDK 安装路径与环境变量配置

```bash
# 1. 确保安装 Java 17
brew install openjdk@17
sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk

# 2. 创建标准 SDK 目录
mkdir -p ~/Library/Android/sdk/cmdline-tools

# 3. 下载 Command-line Tools (可在官网下载后解压到此)
# 解压后目录必须重命名为 "latest"
# 最终路径必须为: ~/Library/Android/sdk/cmdline-tools/latest/bin/sdkmanager

# 4. 配置 ~/.zshrc 环境变量
cat << 'EOF' >> ~/.zshrc

# Android SDK Environment
export ANDROID_HOME=$HOME/Library/Android/sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/emulator
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
EOF

source ~/.zshrc
```

---

### 2.2 SDK Platforms & Build-Tools 版本选型（minSdk 35 / compileSdk 36）

```bash
# 同意所有证书协议
yes | sdkmanager --licenses

# 安装平台工具与 SDK 35/36
sdkmanager "platform-tools" \
           "platforms;android-35" \
           "platforms;android-36" \
           "build-tools;36.0.0"
```

---

### 2.3 本地 ARM64 AVD 模拟器极速搭建

```bash
# 下载系统镜像（Apple Silicon 使用 arm64-v8a）
sdkmanager "system-images;android-35;google_apis;arm64-v8a"

# 创建 AVD 模拟器
avdmanager create avd \
  -n "Pixel_8_API_35" \
  -k "system-images;android-35;google_apis;arm64-v8a" \
  -d "pixel_8"

# 验证启动（终端后台运行）
emulator -avd Pixel_8_API_35 -no-snapshot-load &
```

---

## 三、模块分层与 Gradle 架构体系

### 3.1 多模块边界定义 (Multi-Module Boundary)

```
:app                    (Android Application: Compose UI, ViewModels, DI Graph)
  │
  ├─► :core:data        (Android Library: Room Database, DAOs, Repository Impl)
  │     │
  │     └─► :core:domain
  │
  ├─► :core:analytics   (Pure Kotlin Library: 零 Android 依赖，纯数学与时序引擎)
  │     │
  │     └─► :core:domain
  │
  ├─► :core:domain      (Pure Kotlin: 领域模型、Repository 接口、LookupProvider 接口)
  │     │
  │     └─► :core:common
  │
  └─► :core:common      (Pure Kotlin: 时间工具、Result 包装、通用常量)
```

### 3.2 libs.versions.toml 集中化依赖配置

```toml
[versions]
kotlin = "2.1.20"
agp = "8.9.0"
ksp = "2.1.20-1.0.31"
core-ktx = "1.15.0"
lifecycle = "2.8.7"
navigation-compose = "2.8.8"
compose-bom = "2025.02.00"
room = "2.7.0-alpha13"
koin = "4.0.2"
vico = "2.0.0-beta.3"
commons-math3 = "3.6.1"
junit5 = "5.11.4"
kotest = "5.9.1"
turbine = "1.2.0"

[libraries]
kotlin-stdlib = { group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version.ref = "kotlin" }
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version = "1.10.1" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version = "1.10.1" }

# Compose BOM
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-navigation = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation-compose" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }

# Room
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# Scientific Computation
apache-commons-math3 = { group = "org.apache.commons", name = "commons-math3", version.ref = "commons-math3" }

# Charting
vico-compose = { group = "com.patrykandpatrick.vico", name = "compose", version.ref = "vico" }
vico-compose-m3 = { group = "com.patrykandpatrick.vico", name = "compose-m3", version.ref = "vico" }

# DI
koin-android = { group = "io.insert-koin", name = "koin-android", version.ref = "koin" }
koin-compose = { group = "io.insert-koin", name = "koin-androidx-compose", version.ref = "koin" }

# Test
junit-jupiter-api = { group = "org.junit.jupiter", name = "junit-jupiter-api", version.ref = "junit5" }
junit-jupiter-engine = { group = "org.junit.jupiter", name = "junit-jupiter-engine", version.ref = "junit5" }
kotest-assertions = { group = "io.kotest", name = "kotest-assertions-core", version.ref = "kotest" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

---

### 3.3 Analytics Engine 绝对隔离原则（零 Android / 零业务依赖）

`:core:analytics` 为纯 Kotlin 模块，仅接受纯数值与时间序列入参：
```kotlin
// 唯一输入载体
data class RawDataPoint(val timestampEpochMilli: Long, val value: Double)
data class TimeSeries(val seriesId: String, val points: List<RawDataPoint>, val unit: String)
```
**铁律**：计算引擎内部没有 `Food`、`UricAcid`、`Weight`、`Meal` 等任何业务名词，仅提供 `calculateMean`、`fitLinearRegression`、`detectAnomalies`、`computeFFT` 等纯数学接口。

---

## 四、数据持久化与可扩展数据库设计（Room + SQLite）

### 4.1 核心指标表结构（MetricDefinition & MetricObservation）

```kotlin
@Entity(
    tableName = "metric_definitions",
    indices = [
        Index(value = ["category"]),
        Index(value = ["name"], unique = true)
    ]
)
data class MetricDefinitionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String, // BODY, NUTRITION, ACTIVITY, LIFESTYLE, HEALTH, CUSTOM
    val unit: String,
    val dataType: String, // NUMERIC, BOOLEAN, DURATION
    val description: String,
    val expectedFrequency: String?,
    val minValue: Double?,
    val maxValue: Double?,
    val referenceRangeLow: Double?,
    val referenceRangeHigh: Double?,
    val isBuiltIn: Boolean,
    val displayOrder: Int
)

@Entity(
    tableName = "metric_observations",
    foreignKeys = [
        ForeignKey(
            entity = MetricDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["metric_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["metric_id", "timestamp"]), // 复合覆盖索引
        Index(value = ["timestamp"])
    ]
)
data class MetricObservationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "metric_id") val metricId: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long, // UTC Epoch Milliseconds
    @ColumnInfo(name = "value") val value: Double,
    @ColumnInfo(name = "unit") val unit: String,
    @ColumnInfo(name = "source") val source: String, // manual, nutrition_agg, health_connect
    @ColumnInfo(name = "metadata_json") val metadataJson: String?
)
```

---

### 4.2 动态扩展的营养数据库设计（FoodItem, NutrientDefinition, FoodNutrientValue）

彻底告别将微量元素写死为数据表列的设计：

```kotlin
// 1. 营养素元定义表 (如 Calories, Protein, Vitamin C, Zinc, etc.)
@Entity(tableName = "nutrient_definitions")
data class NutrientDefinitionEntity(
    @PrimaryKey val id: String, // 例如: "calories", "protein", "vitamin_c"
    val name: String,           // 显示名称: "能量", "蛋白质", "维生素 C"
    val unit: String,           // "kcal", "g", "mg", "ug"
    val category: String,       // MACRO, MINERAL, VITAMIN, LIPID
    val dailyRecommended: Double?, // 推荐摄入参考值 RDA
    val displayOrder: Int
)

// 2. 食物条目表 (具备灵活的基准参考量 Reference Amount)
@Entity(
    tableName = "food_items",
    indices = [Index(value = ["name"])]
)
data class FoodItemEntity(
    @PrimaryKey val id: String,
    val name: String,                      // "大米 (生)", "全脂牛奶", "鸡蛋"
    val brand: String? = null,
    val referenceAmount: Double = 100.0,   // 参考分量: 100, 1, 250
    val referenceUnit: String = "g",       // 基准单位: "g", "ml", "piece", "serving"
    val isCustom: Boolean = false          // 是否为用户自定义创建
)

// 3. 食物-营养成分对照表 (多对多关联)
@Entity(
    tableName = "food_nutrient_values",
    primaryKeys = ["food_id", "nutrient_id"],
    foreignKeys = [
        ForeignKey(
            entity = FoodItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["food_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = NutrientDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["nutrient_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["nutrient_id"])]
)
data class FoodNutrientValueEntity(
    @ColumnInfo(name = "food_id") val foodId: String,
    @ColumnInfo(name = "nutrient_id") val nutrientId: String,
    @ColumnInfo(name = "amount_per_reference") val amountPerReference: Double // 在基准分量下的含量
)
```

---

### 4.3 摄入日志与自动时序投影管道 (Intake Logging & Automatic Projection)

```kotlin
// 4. 用户进食打卡日志
@Entity(
    tableName = "meal_logs",
    foreignKeys = [
        ForeignKey(
            entity = FoodItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["food_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["timestamp"])]
)
data class MealLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "food_id") val foodId: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "actual_amount") val actualAmount: Double, // 用户吃的实际数量 (如 180.0)
    @ColumnInfo(name = "actual_unit") val actualUnit: String,     // "g", "ml", "piece"
    @ColumnInfo(name = "meal_type") val mealType: String          // BREAKFAST, LUNCH, DINNER, SNACK
)
```

#### 投影逻辑与数据流动：
每当用户新增或修改一条 `MealLogEntity`：
1. **换算营养值**：
   $$\text{ActualNutrient} = \text{amountPerReference} \times \frac{\text{actualAmount}}{\text{foodItem.referenceAmount}}$$
2. **汇总当日摄入**：按自然日（依据用户当前时区）对各 `nutrient_id` 进行 `SUM(ActualNutrient)`。
3. **自动同步为时序观测记录**：
   将计算得到的 `(nutrientId, dayStartTimestamp, totalValue)` 以 `source = "nutrition_agg"` 写入 `metric_observations`。
4. **触发分析刷新**：由于数据落入 `metric_observations`，所有观察该指标的时序图和分析逻辑自动接收最新 Flow 发射，完全闭环！

---

### 4.4 时序索引与冷热查询优化

针对查询性能的终极防御：
- 查询某指标特定时间区间的语句：
  ```sql
  SELECT * FROM metric_observations 
  WHERE metric_id = :metricId AND timestamp BETWEEN :startEpoch AND :endEpoch 
  ORDER BY timestamp ASC
  ```
- 依靠 `Index(value = ["metric_id", "timestamp"])` 建立的 B-Tree，SQLite 可以直接通过单次索引扫描（Index Seek + Scan）在 0.5 毫秒内取出区间内的数千条数据，无全表扫描开销。

---

### 4.5 数据库 Migration 策略与 TypeConverters

```kotlin
class CommonConverters {
    @TypeConverter
    fun fromEpoch(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun toEpoch(instant: Instant?): Long? = instant?.toEpochMilli()
}
```
- **配置 `exportSchema = true`**，每次修改必须提供 `Migration(M, N)` 单元测试。
- 绝不在 Release 包使用 `fallbackToDestructiveMigration()`。

---

## 五、自动获取参考营养值与卡路里架构设计 (Nutrition Lookup Architecture)

### 5.1 插件化接口：NutritionLookupProvider

```kotlin
// 在 :core:domain 模块中定义纯 Kotlin 契约
interface NutritionLookupProvider {
    val providerName: String
    val isOfflineCapable: Boolean

    suspend fun searchFoods(query: String): Result<List<FoodSearchResult>>
    suspend fun getNutrientProfile(foodRefId: String): Result<FoodNutrientProfile>
}

data class FoodSearchResult(
    val id: String,
    val name: String,
    val brand: String?,
    val defaultReferenceAmount: Double,
    val defaultReferenceUnit: String
)

data class FoodNutrientProfile(
    val foodName: String,
    val referenceAmount: Double,
    val referenceUnit: String,
    val nutrients: Map<String, Double> // nutrientId -> amountPerReference
)
```

---

### 5.2 三级获取策略：内置离线词典 → 公共数据库 API → AI/估算 Adapter

由 `NutritionLookupRepository` 负责编排：

1. **第一优先级：用户历史与收藏 (User Personal Foods)**  
   优先匹配用户之前吃过或手动纠正过的自定义食物项。
2. **第二优先级：本地内置离线营养字典 (Bundled Common Lexicon)**  
   随 App 打包一份轻量级 SQLite / 静态预置表（约 300 种标准中国/国际常见食材，如各种主食、肉类、常见蔬菜水果蛋奶）。
   - **零网络请求**、毫秒级响应、断网完美可用。
3. **第三优先级：网络公共数据库 / API 扩展（可选插件）**  
   预留标准 Adapter 适配公共开放接口（例如 OpenFoodFacts 国际条形码与商品库、USDA FoodData Central 公共营养库）。
4. **第四优先级：AI 语义解析估算（可选远期扩展）**  
   在未来配置 LLM Key 时，允许通过自然语言（“一碗红烧牛肉面加一个荷包蛋”）调用大模型进行分量与卡路里粗略估算。

---

### 5.3 离线优先与用户确认机制

- **确认机制**：所有外部获取到的参考营养值，在保存至数据库前，必须在 UI 上向用户展示：
  - “米饭（熟）：每 100g 含能量 116 kcal，蛋白质 2.6g，碳水 25.9g”
  - 用户可微调参考数值，点击确定后持久化到本地 `food_items` 与 `food_nutrient_values`。
- **一旦存入，永久离线可用**。

---

## 六、Analytics Engine 算法数学蓝图与具体陷阱防御

### 6.1 描述性统计（数值稳定性与 Sample/Population 陷阱）

- **稳健两趟均值与方差**：
  $$s^2 = \frac{1}{N-1} \sum_{i=1}^N (x_i - \bar{x})^2 \quad (\text{Sample})$$
  $$\sigma^2 = \frac{1}{N} \sum_{i=1}^N (x_i - \bar{x})^2 \quad (\text{Population})$$
- **陷阱防御**：
  - $N < 2$ 时 Sample Variance 返回 `Double.NaN`。
  - 浮点精度导致 $\sum (x - \bar{x})^2 < 0$ 时，开方前使用 `maxOf(0.0, variance)`。
  - 偏度 (Skewness) 与峰度 (Kurtosis) 采用无偏 Fisher-Pearson 样本修正，并在标准差为 0 时返回 `0.0`。

---

### 6.2 累计与时间聚合（自然日历与时区对齐）

- **时区对齐**：使用 `ZonedDateTime` 结合 `ZoneId.systemDefault()` 进行自然日历日切（`truncatedTo(ChronoUnit.DAYS)`），严禁使用绝对时间戳除以 86400。

---

### 6.3 变化率、加速度与复合变化（CAGR / 算术 vs 几何）

- **CAGR 年化复合变化**：
  $$\text{CAGR} = \left(\frac{x_{end}}{x_{start}}\right)^{\frac{365.25}{\Delta \text{days}}} - 1$$
- **正值约束**：任一 $x \le 0$ 时严禁计算 CAGR，返回 `null`，标记为 `ARITHMETIC_ONLY`。

---

### 6.4 滑动窗口统计（基于时间天数而非点数的多指针滑动算法）

- **双指针 $O(N)$ 算法**：
  滑动窗口右指针每次移动，左指针同步推进至满足 $t_{left} \ge t_{right} - \text{WindowMillis}$。窗口是连续物理时间段，而非离散样本点个数。

---

### 6.5 指数平滑（不规则时间序列的时间衰减 EWMA）

- **连续时间衰减模型**：
  $$\lambda = \frac{\ln 2}{t_{1/2}}, \quad \Delta t = t_k - t_{k-1}$$
  $$\alpha_k = 1 - e^{-\lambda \Delta t}, \quad S_k = \alpha_k x_k + (1 - \alpha_k) S_{k-1}$$

---

### 6.6 趋势分析与局部趋势（带时间戳的 OLS 线性回归与 t-检验）

- **时间跨度作为自变量 $x$**：$x_i = (t_i - t_0) / 86400000.0$。
- **显著性检验**：计算残差标准误 $s_e$ 与斜率标准误 $s_b$，调用 `commons-math3` 的 `TDistribution` 计算双尾 $p$-value。只有当 $p < 0.05$ 时才标定为 `INCREASING` 或 `DECREASING`。

---

### 6.7 异常检测综合算法（Z-Score, IQR, MAD, EWMA Residual）

- **稳健中位数绝对偏差 (MAD)**：
  $$\text{Robust Z-Score} = \frac{0.6745 \times (x_i - \tilde{x})}{\text{MAD}}$$
  不受偏态与极端离群值干扰。
- **严禁医学化**：UI 统一标定为“偏离历史基线 $X\sigma$”，严禁出现“疾病”、“异常危险”字眼。

---

### 6.8 交叉相关、滞后分析与协方差矩阵（时间戳对齐与 Spearman 秩次）

- **对齐网格**：将两个异构指标在同一自然日网格进行内连接（Inner Join），有效采样数 $N < 3$ 时停止相关性计算。
- **声明因果**：UI 醒目标注“统计相关不等于因果关联”。

---

### 6.9 周期性分析、趋势剔除与 FFT（窗函数与功率谱密度）

- **标准流程**：
  等步长重采样插值 $\to$ 线性去趋势 (Detrend) $\to$ 均值消除 (Zero-Mean) $\to$ 加 Hann 窗 $\to$ 补零至 $2^n \to$ Radix-2 FFT $\to$ 单边功率谱密度 (PSD)。
- **周期检出有效性约束**：观测总时长必须 $\ge 2 \times \text{Period}$，否则返回 `isReliable = false`。

---

### 6.10 数据质量与采样密度分析

- 计算采样间隔均值、方差、中位数、最长缺失时长及“星期打卡偏好”。

---

## 七、UI 架构与可视化实现（Compose + MVI + Vico）

### 7.1 响应式单向数据流（MVI / UDF）与调度器隔离

```kotlin
data class MetricDetailUiState(
    val isLoading: Boolean = true,
    val definition: MetricDefinition? = null,
    val timeRange: TimeRange = TimeRange.Last30Days,
    val rawPoints: List<DataPoint> = emptyList(),
    val analysisResult: AnalysisResult? = null
)
```
- **线程规约**：ViewModel 内部所有的 `AnalyticsEngine` 计算全部包装在 `withContext(Dispatchers.Default)` 中，与主线程绝对隔离。

---

### 7.2 Vico 图表层复合渲染封装

- 采用 Vico Compose-M3 组件，自底向上绘制：
  1. **背景范围带**：基线参考区间。
  2. **主折线**：原始打卡数据（Raw）。
  3. **平滑拟合线**：EWMA / Rolling 趋势。
  4. **线性回归虚线**：OLS 趋势线。
  5. **散点标注**：离群偏离点（Anomaly Highlights）。

---

### 7.3 多指标归一化对比视图（Z-Score / Min-Max / Baseline 100）

- 支持体重与生化指标多轴重叠比对，提供 Z-Score、Min-Max 及 Baseline 100 相对指数投影，绝不修改底层存储数据。

---

### 7.4 性能防卡顿：LTTB 降采样算法集成

- 针对超长周期数据，集成 **Largest-Triangle-Three-Buckets (LTTB)** 算法，将点数降采样至 300~500 点传给 Compose 绘制层，保障 120fps 流畅滑动。

---

## 八、测试战略与 Python Golden-Master 参照校验体系

### 8.1 Python 生成基准向量脚本 (NumPy / SciPy Reference Generator)

独立脱机脚本 `tools/generate_golden_vectors.py` 负责：
1. 产生包含常量、单点、极值、不规则日期间隔的测试序列。
2. 调用 `numpy`、`scipy.stats` 计算出真值标准结果。
3. 导出标准 JSON 基准文件。

### 8.2 Kotlin JVM 测试套件与边界验证矩阵

`:core:analytics` 中的测试加载 JSON 并执行断言：
```kotlin
@Test
fun `verify stats against numpy golden truth`() {
    val vector = loadVector("golden_test_vectors.json")
    val result = DescriptiveStats.calculate(vector.inputs)
    result.sampleVariance shouldBe (vector.expected.sampleVariance plusOrMinus 1e-6)
    result.skewness shouldBe (vector.expected.skewness plusOrMinus 1e-5)
}
```

---

## 九、全周期项目开发里程碑与排期表

```
 Phase 0 (1-2d)   Phase 1 (3-5d)     Phase 2 (6-12d)    Phase 3 (13-17d)   Phase 4 (18-22d)   Phase 5 (23-26d)
┌──────────────┐ ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│ SDK环境就绪  ├─► 数据库与实体 ├─► │ 数学分析引擎 ├──►│ Compose UI  ├──►│ 动态营养管道 ├──►│ 导出与打磨   │
│ 多模块脚手架 │ │ (动态营养库) │   │ (JVM测试齐备)│   │ (Vico复合图) │   │ (Lookup服务) │   │ (Ready for v1)│
└──────────────┘ └──────────────┘   └──────────────┘   └──────────────┘   └──────────────┘   └──────────────┘
```

---

## 十、全系统终极防错避坑手册 (The Non-Negotiable Rules)

1. **时间铁律**：存且仅存 UTC 纪元毫秒（`Long`），任何字符串日期只能在 UI 展示层由本地 `ZoneId` 格式化。
2. **基准量铁律**：`referenceAmount` 可以是任意值（100g, 1 serving, 250ml），计算必须使用动态比例除法，绝不能在代码中除以常数 100。
3. **因果与医学红线**：UI 严禁声称因果关系，严禁输出医疗诊断语言。
4. **原始数据只读原则**：Detrending、Normalization 只能生成纯内存临时副本，绝不回写原始表。
5. **计算调度原则**：任何多于 10 个数据点的分析计算必须移至 `Dispatchers.Default`。
