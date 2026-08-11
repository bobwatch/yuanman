# 参与贡献

感谢你对沅满 Yuanman 的关注！以下是参与方式。

## 如何构建

1. 安装 **Android Studio Hedgehog（2023.1.1）或更新版本**（自带 JDK 17）
2. `File → Open` 选择本仓库根目录，等待 Gradle Sync 完成
3. 命令行验证：`./gradlew assembleDebug`

技术栈：Kotlin + Jetpack Compose（Material 3），AGP 8.5.2 / Kotlin 1.9.24 / Gradle 8.7，
minSdk 24 / targetSdk 34。项目约定**零第三方依赖**（不含数据库、网络、图表库），
新增依赖前请先在 Issue 中讨论。

## 分支约定

- `main`：主干分支，始终保持可发布状态
- 功能分支：`feature/xxx`，修复分支：`fix/xxx`，从 `main` 切出，合并回 `main`

## Commit 规范

使用 [Conventional Commits](https://www.conventionalcommits.org/zh-hans/) 风格：

- `feat: 添加年度统计页`
- `fix: 修复导入时备注换行导致的 CSV 错列`
- `refactor: …` / `docs: …` / `chore: …`

## 提 Issue 和 PR

- 报 Bug 请使用「Bug 反馈」模板，写清机型、Android 版本和复现步骤
- 新功能建议先开 Issue 讨论，再动手写代码
- PR 请保持改动聚焦，一个 PR 只做一件事

## 行为准则

友善、尊重、就事论事。本项目遵循常识性的开源社区礼仪：
不人身攻击、不发布无关内容，对他人劳动保持尊重。
