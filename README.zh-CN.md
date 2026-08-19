# 沅满 Yuanman

<p align="center"><img src="docs/logo.png" width="120" alt="沅满 logo"></p>

> 沅满 —— 记账 · 打卡 · 心情，一个 App 管好三件事。

**[English README](README.md)** | 免费开源，无服务器、无账号，数据不出局域网。

[![CI](https://github.com/bobwatch/yuanman/actions/workflows/ci.yml/badge.svg)](https://github.com/bobwatch/yuanman/actions/workflows/ci.yml)
[![Release](https://github.com/bobwatch/yuanman/actions/workflows/release.yml/badge.svg)](https://github.com/bobwatch/yuanman/actions/workflows/release.yml)
[![GitHub release](https://img.shields.io/github/v/release/bobwatch/yuanman)](https://github.com/bobwatch/yuanman/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![minSdk 24](https://img.shields.io/badge/Android-7.0%2B-blue)](https://github.com/bobwatch/yuanman)

## 特性

**记账**

- 两步记账：内置九宫格键盘输金额，点分类即保存
- 键盘支持连加求和，不弹系统键盘
- 支出/收入切换、emoji 分类、备注、Material 3 DatePicker 补记任意日期
- 点击记录即可编辑，左滑删除、Snackbar 一键撤销，长按「再记一笔」快速复制
- 周期账单：每周/每月/每年到期自动补记
- 月度预算执行进度、今日支出、连续记账天数、上月小结卡
- 备注/分类实时搜索，自定义分类，最近使用的分类排在最前
- 攒钱目标：圆环进度动画、达成日期预测、里程碑庆祝与 100% 撒花
- 桌面 Widget：今日/本月支出一目了然，「+」直达记账

**打卡**

- 培养习惯：一键打卡，自动统计连续天数，可撤销当天打卡
- 戒掉习惯：超大坚持天数自动累计，「我破了…」二次确认后清零重计
- 8 个常用预设一键填充（健身/学习/阅读/早起/戒饮料/戒酒/戒烟/戒熬夜）

**心情**

- 每天点一下 emoji 即完成记录（😄🙂😐😔😠），可附一句话
- 本月分布环形图、生气天数大数字、连续非生气天数
- 按数据动态生成的鼓励文案；本月心情色点网格
- 心情×消费交叉分析：生气日的平均支出 vs 平常日

**勋章**

- 12 枚勋章覆盖记账/打卡/心情，解锁撒花庆祝，勋章墙记录获得日期

**图表** — 分类占比环形图、近 6 个月趋势柱状图、本月每日走势面积图，全部 Compose Canvas 自绘，零第三方图表库。

**同步与隐私**

- 家庭同步：同一 Wi-Fi 下设备自动发现（NSD），TCP 双向合并账本
- 6 位配对码组成家庭组，握手只比对 SHA-256 摘要，不明文传输
- 无服务器、无注册登录、无统计埋点
- 数据以 JSON 文件保存在应用私有目录，原子写入 + 自动备份
- 备份可导出为 JSON / CSV，支持合并导入与覆盖导入
- 界面中英文自动跟随系统语言
- v0.0.2 打磨：表单输入状态进程重建不丢（rememberSaveable）、未保存内容丢弃二次确认、
  概览卡可点击进统计、返回键优先关闭搜索、Snackbar 不再遮挡 FAB、取出超额校验、
  图表文字按屏幕密度适配、明暗主题对比度优化

## 截图

> 占位：欢迎补充实际运行截图

| 记账 | 打卡 | 心情 | 我的 |
| --- | --- | --- | --- |
| ![记账](docs/screenshots/home.png) | ![打卡](docs/screenshots/habits.png) | ![心情](docs/screenshots/mood.png) | ![我的](docs/screenshots/mine.png) |

## 下载安装

- 前往 [Releases](https://github.com/bobwatch/yuanman/releases) 下载最新 APK
- 系统要求：Android 7.0（API 24）及以上
- 安装后长按桌面图标可使用「记一笔」快捷方式

## 技术架构

- Kotlin + Jetpack Compose（Material 3，Compose BOM 2024.06.00）+ Navigation Compose
- 单 Activity + 底部四 Tab（记账/打卡/心情/我的）+ 子页面路由
- 零第三方依赖：`org.json` 序列化、Canvas 自绘图表、RemoteViews 桌面 Widget、
  NsdManager + ServerSocket 局域网同步
- AGP 8.5.2 / Kotlin 1.9.24 / Gradle 8.7，minSdk 24 / targetSdk 34，Java 17
- 国际化：`values`（英文默认）+ `values-zh-rCN`（中文）

**目录结构**

```
app/src/main/java/com/moneyhistory/app/
├── MainActivity.kt            # 单 Activity，底部 Tab 导航与生命周期
├── MainViewModel.kt           # 全部页面状态与业务操作（含勋章引擎调度）
├── Transaction.kt(Store)      # 流水模型与 JSON 存储（v2 墓碑/合并/原子写入）
├── RecurringStore.kt          # 周期账单存储与到期结算
├── SavingsStore.kt            # 攒钱目标（里程碑/达成预测）
├── HabitsStore.kt             # 打卡习惯（连续天数/戒断坚持）
├── MoodStore.kt               # 心情记录（按自然日）
├── Badges.kt                  # 勋章定义与判定引擎
├── Categories.kt(Store)       # 预设分类 + 自定义分类持久化
├── SettingsStore.kt           # 主题/预算/勋章解锁/最近分类（SharedPreferences）
├── DateUtils.kt               # 日期工具（Calendar/SimpleDateFormat）
├── MoneyUtils.kt              # 分↔元格式化与解析
├── sync/FamilySyncManager.kt  # NSD 发现 + TCP 合并同步
├── widget/                    # 桌面 Widget（RemoteViews）
└── ui/                        # Compose 页面与组件（含 Charts.kt 自绘图表）
```

**存储结构**（`filesDir/transactions.json`，v2）

```json
{
  "version": 2,
  "transactions": [
    {
      "id": "3f2b2f5e-8c1d-4e6a-9a1b-2c3d4e5f6a7b",
      "type": "expense",
      "amount": 2550,
      "category": "🍜 餐饮",
      "note": "午饭",
      "timestamp": 1754827821000,
      "updatedAt": 1754827821000,
      "deleted": false
    }
  ]
}
```

`amount` 单位为分（Long）；`deleted` 为删除墓碑（UI 不显示，同步时传播删除）。
兼容 v1 备份导入，加载后自动迁移为 v2。周期账单、攒钱目标、习惯、心情、
自定义分类分别存于 `recurring.json` / `goals.json` / `habits.json` / `mood.json` /
`categories.json`。

**家庭同步协议**

服务类型 `_yuanman._tcp.`（NSD 注册 + 发现），TCP 按行交换 JSON：
双方互发 hello（deviceId + 配对码 SHA-256 前 8 位十六进制）→ 校验一致后
互发全量 `transactions`（含墓碑）→ 各自按「同 id 取 updatedAt 大者」合并落盘。
应用退后台即停止全部网络活动。

## 隐私声明

本应用不连接任何服务器，不收集任何数据。权限清单及用途：

| 权限 | 用途 |
| --- | --- |
| `INTERNET` | 家庭同步的局域网 TCP 通信 |
| `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE` | 判断局域网环境 |
| `CHANGE_WIFI_MULTICAST_STATE` | 接收 NSD 多播发现报文 |
| `NEARBY_WIFI_DEVICES`（neverForLocation） | Android 13+ 附近 Wi-Fi 设备发现 |
| `VIBRATE` | 记账/存入成功的轻震动反馈 |

## 构建

Android Studio Hedgehog（2023.1.1）或更新版本，打开仓库根目录即可。

命令行（需 JDK 17 + Android SDK）：

```bash
./gradlew assembleDebug      # 本地调试包
./gradlew assembleRelease    # 发布包（需配置签名环境变量，见 CI 配置）
```

## Roadmap

- [ ] 分类预算与超支提醒
- [ ] 年度统计与更多图表维度
- [ ] 打卡提醒通知
- [ ] 心情年历（全年色点图）
- [ ] 家庭同步增量协议与冲突提示

## 贡献

欢迎 Issue 与 PR，请先阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。
Commit message 一律使用英文；UI 文案必须走字符串资源
（`values` 为英文默认，`values-zh-rCN` 为中文），禁止硬编码。

- [提交 Bug](https://github.com/bobwatch/yuanman/issues/new?template=bug_report.md)
- [功能建议](https://github.com/bobwatch/yuanman/issues/new?template=feature_request.md)

## License

[MIT](LICENSE) © 2026 bobwatch
