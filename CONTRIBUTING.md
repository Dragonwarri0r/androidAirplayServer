# Contributing to Android AirPlay Server

Thank you for your interest in contributing to the Android AirPlay Server project! This document provides guidelines for contributing to the project.

## 🚀 Getting Started

### Prerequisites
- Android Studio 2023.1+ (Hedgehog)
- JDK 17+
- Git
- Android SDK with API Level 24+

### Setting up the Development Environment
1. Fork the repository
2. Clone your fork:
   ```bash
   git clone https://github.com/yourusername/androidAirplayServer.git
   cd androidAirplayServer
   ```
3. Open the project in Android Studio
4. Wait for Gradle sync to complete
5. Build the project to ensure everything works:
   ```bash
   ./gradlew assembleDebug
   ```

## 📝 How to Contribute

### Reporting Issues
Before creating a new issue, please:
1. Check if the issue already exists
2. Use the provided issue templates
3. Include detailed information:
   - Device model and Android version
   - Steps to reproduce
   - Expected vs actual behavior
   - Relevant logs (use `adb logcat`)

### Submitting Pull Requests

1. **Create a feature branch**:
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes**:
   - Follow the coding standards (see below)
   - Add tests if applicable
   - Update documentation

3. **Commit your changes**:
   ```bash
   git commit -m "feat: add your feature description"
   ```
   
   Use conventional commit format:
   - `feat:` for new features
   - `fix:` for bug fixes
   - `docs:` for documentation changes
   - `refactor:` for code refactoring
   - `test:` for test additions
   - `chore:` for maintenance tasks

4. **Push to your fork**:
   ```bash
   git push origin feature/your-feature-name
   ```

5. **Create a Pull Request**:
   - Use the provided PR template
   - Link related issues
   - Provide clear description of changes

## 📋 Coding Standards

### Java/Kotlin Code Style
- Follow Android's official coding standards
- Use meaningful variable and method names
- Add comments for complex logic
- Keep methods focused and concise

### Architecture Guidelines
- **Video Player**: Modifications should maintain MediaCodec compatibility
- **Audio Player**: Ensure AAC ELD format support is preserved
- **Network**: Follow AirPlay protocol specifications
- **UI**: Maintain Android TV compatibility

### Logging
- Use appropriate log levels:
  - `Log.e()` for errors
  - `Log.w()` for warnings
  - `Log.i()` for important information
  - `Log.d()` for debug information
- Include meaningful context in log messages
- Use consistent TAG naming: `private static final String TAG = "ClassName"`

### Testing
- Write unit tests for new functionality
- Test on different Android versions if possible
- Test with various iOS devices and versions
- Verify both audio and video functionality

## 🔍 Code Review Process

### What We Look For
- **Functionality**: Does the code work as intended?
- **Performance**: Are there any performance regressions?
- **Compatibility**: Does it work on different Android versions?
- **Code Quality**: Is the code clean and maintainable?
- **Documentation**: Are changes properly documented?

### Review Timeline
- Initial review: 1-3 days
- Follow-up reviews: 1-2 days
- We may request changes or ask questions

## 🎯 Project Areas

### High Priority
- **Audio Processing**: Improvements to AAC ELD decoding
- **Video Optimization**: Reduce latency and improve quality
- **Error Handling**: Better recovery from network/codec errors
- **Performance**: Memory and CPU optimizations

### Medium Priority
- **UI Improvements**: Better user experience
- **Configuration**: User-configurable settings
- **Logging**: Enhanced debugging capabilities
- **Documentation**: Code comments and user guides

### Low Priority
- **Additional Formats**: Support for more audio/video formats
- **Features**: New AirPlay protocol features
- **Tools**: Development and debugging tools

## 📊 Testing Guidelines

### Manual Testing
1. **Basic Functionality**:
   - App starts without crashes
   - Device discovery works
   - Screen mirroring displays correctly
   - Audio plays without issues

2. **Edge Cases**:
   - Network disconnection/reconnection
   - App backgrounding/foregrounding
   - Multiple connection attempts
   - Different iOS versions

3. **Performance**:
   - No significant memory leaks
   - Smooth video playback
   - Low audio latency
   - Stable over extended use

### Automated Testing
- Run existing unit tests: `./gradlew testDebugUnitTest`
- Add tests for new functionality
- Ensure CI/CD pipeline passes

## 🐛 Debugging Tips

### Common Tools
- **ADB Logcat**: `adb logcat -s AacAudioPlayer VideoPlayer MainActivity`
- **Android Studio Profiler**: For memory and CPU analysis
- **Network Tools**: Wireshark for protocol analysis

### Debugging Audio Issues
- Check MediaCodec format support
- Verify AAC ELD codec configuration
- Monitor AudioTrack state
- Check audio data flow through queue

### Debugging Video Issues
- Monitor MediaCodec callbacks
- Check surface state
- Verify H.264 stream format
- Monitor packet queue sizes

## 📚 Resources

### Documentation
- [Android MediaCodec Guide](https://developer.android.com/guide/topics/media/mediacodec)
- [AirPlay Protocol Documentation](https://github.com/serezhka/airplay-lib)
- [Android Audio Development](https://developer.android.com/guide/topics/media/audio)

### Tools
- [Android Studio](https://developer.android.com/studio)
- [ADB](https://developer.android.com/studio/command-line/adb)
- [Wireshark](https://www.wireshark.org/)

## 🤝 Community

### Communication
- Use GitHub Issues for bug reports and feature requests
- Use Pull Request discussions for code-related questions
- Be respectful and constructive in all interactions

### Recognition
Contributors will be recognized in:
- CHANGELOG.md for significant contributions
- README.md acknowledgments
- Release notes for major features

## 📄 License

By contributing to this project, you agree that your contributions will be licensed under the MIT License.

---

Thank you for contributing to Android AirPlay Server! Your efforts help make this project better for everyone. 