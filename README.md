# Android AirPlay Server

[![Android CI](https://github.com/yourusername/androidAirplayServer/workflows/Android%20CI/badge.svg)](https://github.com/yourusername/androidAirplayServer/actions)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/yourusername/androidAirplayServer)](https://github.com/yourusername/androidAirplayServer/releases)

一个基于 Android 原生开发的 AirPlay 投屏服务器应用，专为雷鸟 X2 等 Android TV 设备优化。支持 iOS 设备的无线屏幕镜像和音频传输。
部分代码由大模型配合编写和修正。

## ✨ 功能特性

### 📱 屏幕镜像
- **高质量视频流**: 支持 H.264 解码，流畅的屏幕镜像体验
- **低延迟**: 优化的 MediaCodec 配置，减少视频延迟
- **自适应分辨率**: 自动适配不同设备的屏幕尺寸
- **错误恢复**: 自动检测和恢复视频解码错误

### 🔊 音频传输
- **AAC ELD 支持**: 完整支持 AirPlay 的 AAC ELD 音频格式
- **实时音频**: 低延迟音频播放，同步视频内容
- **多格式兼容**: 支持多种 AirPlay 音频格式
- **智能解码**: 自动音频格式检测和优化

### 🚀 性能优化
- **多线程架构**: 分离的音频/视频处理线程，提升性能
- **内存管理**: 优化的缓冲区管理，减少内存占用
- **队列优化**: 增强的数据队列，减少丢帧和卡顿
- **资源清理**: 完善的生命周期管理

## 🛠️ 技术架构

### 核心组件
- **AirPlay 协议**: 基于 [serezhka/airplay-lib](https://github.com/serezhka/airplay-lib)
- **视频解码**: MediaCodec + Surface 渲染
- **音频播放**: MediaCodec + AudioTrack
- **网络发现**: Bonjour/mDNS 服务发现

### 系统要求
- **Android**: 7.0+ (API Level 24+)
- **架构**: ARM64, ARMv7
- **内存**: 最少 1GB RAM
- **网络**: WiFi 连接（与 iOS 设备同网段）

## 📦 安装和使用

### 方式一：下载 APK
1. 从 [Releases](https://github.com/yourusername/androidAirplayServer/releases) 下载最新版本的 APK
2. 在 Android TV 上启用"未知来源应用安装"
3. 使用 U 盘或 ADB 安装 APK：
   ```bash
   adb install app-release.apk
   ```

### 方式二：从源码构建
1. **克隆项目**：
   ```bash
   git clone https://github.com/yourusername/androidAirplayServer.git
   cd androidAirplayServer
   ```

2. **构建 APK**：
   ```bash
   ./gradlew assembleDebug
   # 或构建发布版本
   ./gradlew assembleRelease
   ```

3. **安装到设备**：
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

### 使用步骤
1. 📱 确保 Android TV 和 iOS 设备连接在同一个 WiFi 网络
2. 🚀 在 Android TV 上启动 AirPlay Server 应用
3. 📺 应用会自动开始监听 AirPlay 连接（显示设备名称：VisionPro）
4. 🍎 在 iOS 设备上：
   - 打开控制中心（下拉或上滑）
   - 点击"屏幕镜像"
   - 选择"VisionPro"设备
   - 开始投屏

## 🔧 开发和贡献

### 开发环境
- **Android Studio**: 2023.1+ (Hedgehog)
- **JDK**: 17+
- **Gradle**: 8.7
- **Android Gradle Plugin**: 8.5.2

### GitHub Actions CI/CD
项目配置了完整的 CI/CD 流水线：

- **自动构建**: 每次 push 和 PR 都会触发构建
- **多版本打包**: 同时生成 Debug 和 Release 版本
- **自动发布**: 创建 Git tag 时自动发布到 GitHub Releases
- **测试运行**: 自动运行单元测试和集成测试

### 贡献指南
1. Fork 本仓库
2. 创建功能分支: `git checkout -b feature/amazing-feature`
3. 提交更改: `git commit -m 'Add amazing feature'`
4. 推送分支: `git push origin feature/amazing-feature`
5. 创建 Pull Request

详细的贡献指南请参考 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 📋 版本历史

查看 [CHANGELOG.md](CHANGELOG.md) 了解详细的版本更新历史。

### 最新更新 (v1.1.0)
- ✅ **修复音频播放**: 完整支持 AAC ELD 音频格式
- 🚀 **性能优化**: 显著减少视频卡顿和延迟
- 🔧 **代码优化**: 移除冗余代码，提升稳定性
- 📊 **监控增强**: 添加性能监控和调试功能

## 🐛 故障排除

### 常见问题

**Q: 无法发现设备？**
- 确保两设备在同一 WiFi 网络
- 检查防火墙设置
- 重启应用和网络连接

**Q: 有画面但没有声音？**
- 检查 Android TV 音量设置
- 确认音频格式支持（AAC ELD）
- 查看应用日志了解详细错误

**Q: 视频卡顿严重？**
- 检查网络连接质量
- 降低 iOS 设备的视频质量设置
- 重启应用清理缓存

### 调试模式
启用详细日志进行问题诊断：
```bash
adb logcat -s AacAudioPlayer VideoPlayer MainActivity
```

## 🙏 致谢

本项目基于以下开源项目：
- [serezhka/airplay-lib](https://github.com/serezhka/airplay-lib) - AirPlay 协议实现
- Android MediaCodec Framework - 音视频处理
- Android Support Libraries - UI 和工具库

特别感谢所有贡献者和社区的支持！

## 📄 许可证

本项目采用 MIT 许可证。详情请参考 [LICENSE](LICENSE) 文件。

## 🔗 相关链接

- [项目主页](https://github.com/yourusername/androidAirplayServer)
- [问题反馈](https://github.com/yourusername/androidAirplayServer/issues)
- [发布版本](https://github.com/yourusername/androidAirplayServer/releases)
- [开发文档](https://github.com/yourusername/androidAirplayServer/wiki)

---

如果这个项目对你有帮助，请考虑给个 ⭐ Star！