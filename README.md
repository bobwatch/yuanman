# 沅满记账

沅满记账是一款 Android 本地优先账本，提供快捷记账、账单明细、预算复盘、统计图表、CSV 导入导出、完整备份、局域网设备同步和桌面组件。

## 数据与网络说明

- 账单默认保存在本机 Room 数据库中。
- 如果设备开启 Android 系统备份，系统可能按其账号与设备策略备份应用私有数据；这由 Android 设置控制。
- 检查更新时会访问 GitHub Releases；下载安装包前会验证包名与签名，发布提供 SHA-256 文件时还会校验摘要。
- 设备同步仅在用户授权后通过同一局域网传输，并使用配对码派生密钥加密负载。
- “卸载后自动恢复”默认关闭。开启后数据库副本会写入公共 Documents/Yuanman；关闭时会移除该公共副本。
- 手动完整备份包含账单、分类、预算、偏好和快捷记账学习规则，并带 SHA-256 完整性校验。

## 本地构建

需要 JDK 17 和 Android SDK 34。

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Release 构建要求 `app/keystore/yuanman-release.jks` 及 `local.properties` 中的签名配置，缺失时构建会主动失败，避免生成无法覆盖安装的升级包。
`assembleRelease` 完成后会在 APK 旁生成 `app-release.apk.sha256`，发布时应与 APK 一起上传。

版本发布要求见 [docs/RELEASE_CHECKLIST.md](docs/RELEASE_CHECKLIST.md)，v0.0.4 范围见 [docs/V0.0.4_PLAN.md](docs/V0.0.4_PLAN.md)。
