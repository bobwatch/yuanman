# AGENTS.md

「沅满 Yuanman」Android App —— 记账 · 打卡 · 心情，纯本地存储，无服务器。

## 构建

- 本仓库通过 GitHub Actions 构建：`.github/workflows/ci.yml`（push/PR 触发 `assembleDebug`）、`.github/workflows/release.yml`（tag `v*` 触发签名打包 + 发布 Release）
- 本地：Android Studio Hedgehog+ 或 `./gradlew assembleRelease`（需 JDK 17；release 签名读取环境变量 KEYSTORE_FILE/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD，无环境变量时不签名）
- 本机无 Android SDK 时禁止声称"已验证可编译"——以 CI 结果为准

## 技术栈硬约束

- Kotlin + Jetpack Compose + Material 3，Compose BOM 2024.06.00（material3 1.2.1 / foundation 1.6.8 / navigation-compose 2.7.7 / lifecycle 2.7.0）
- **不引入新第三方依赖**：JSON 用内置 org.json，图表/键盘/动效全部 Canvas 自绘，Widget 用框架 RemoteViews
- **不用数据库**：所有数据为 filesDir 下 JSON 文件（transactions/recurring/goals/habits/mood/categories），存储层一律原子写入（tmp + rename，transactions 另有 .bak）
- minSdk 24：**禁止 java.time**（避免 desugaring），日期用 Calendar/SimpleDateFormat（见 DateUtils.kt）

## 易错 API（重写文件时必须核对）

- material3 1.2.x 的 `SwipeToDismissBox` 内容参数名是 **`dismissContent`**，且它不在参数列表末尾——必须全部用命名参数调用，不能用尾随 lambda
- 金额小数键盘类型是 **`KeyboardType.Decimal`**（`NumberDecimal` 不存在）
- 实验 API 必须 @OptIn：ModalBottomSheet / DatePicker / FilterChip / TopAppBar / SwipeToDismissBox（ExperimentalMaterial3Api），combinedClickable / animateItemPlacement / HorizontalPager（ExperimentalFoundationApi），FlowRow（ExperimentalLayoutApi）
- `AnimatedContent` 在 animation 1.6 已稳定，保留 @OptIn(ExperimentalAnimationApi::class) 无副作用

## 约定

- **品牌**：应用名「沅满 Yuanman」；主题色「沅满蓝」#2AABEE；仓库 bobwatch/yuanman；不使用任何其他产品的品牌字眼
- applicationId 为 `com.yuanman`（2026-08 从 `com.moneyhistory.app` 迁移而来，代码包名与 applicationId 一致）；保持不再变更（Android 应用身份标识，再改名会导致老用户无法覆盖升级）
- **Commit message 一律英文**
- **UI 文案必须走字符串资源**：`res/values/strings.xml` 为英文（默认），`res/values-zh-rCN/strings.xml` 为中文，两文件 key 必须完全一致；Compose 用 `stringResource(R.string.xxx)`，非 Compose 层用 `context.getString(...)`；禁止硬编码 UI 文案
- 存入 JSON 的用户可见数据（分类名、习惯名、备注等）保持字符串原样，不做迁移
- 金额一律 Long（分），显示用 MoneyUtils
- 版本规则：versionName 从 0.0.1 起递增；versionCode 单调递增（发布签名不变，否则用户无法覆盖升级）
