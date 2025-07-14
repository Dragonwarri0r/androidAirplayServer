# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- GitHub Actions CI/CD pipeline for automated building and releases
- Comprehensive changelog documentation
- Enhanced debugging and monitoring capabilities

### Changed
- **BREAKING**: Improved audio architecture with AAC ELD support
- **PERFORMANCE**: Optimized video player with reduced latency and improved error recovery
- Streamlined codebase by removing unused audio players (ALAC, GStreamer, ExoPlayer)
- Enhanced MediaCodec configuration with low-latency mode and larger buffer sizes
- Improved error handling and automatic decoder restart functionality

### Fixed
- **CRITICAL**: Resolved AAC ELD audio playback issues with proper codec configuration
- Fixed video stuttering through optimized queue management and non-blocking operations
- Resolved MediaCodec compatibility issues with modern Android versions
- Fixed memory leaks in audio/video player lifecycle management

### Removed
- Legacy ALAC audio player implementation
- GStreamer audio player (due to NDK compatibility issues)
- ExoPlayer audio implementation
- Excessive debug logging and audio data dumping functionality
- Deprecated MediaCodec APIs (getInputBuffers/getOutputBuffers)

### Technical Improvements
- **Audio**: Implemented dedicated AAC ELD decoder with proper codec_data configuration
- **Video**: Enhanced queue capacity from 500 to 1000 packets to reduce blocking
- **Performance**: Added non-blocking data processing in MediaCodec callbacks
- **Monitoring**: Integrated queue size monitoring and performance metrics
- **Error Recovery**: Added automatic decoder restart on codec errors
- **Resource Management**: Improved thread safety and resource cleanup

## [1.0.0] - 2024-01-XX

### Added
- Initial Android AirPlay server implementation
- Basic audio and video streaming support
- MediaCodec-based video decoding
- AudioTrack-based audio playback
- Surface rendering for video display

### Features
- AirPlay protocol support for iOS device mirroring
- H.264 video decoding and display
- PCM audio playback
- Network discovery and pairing
- Basic error handling and logging

---

## Version History Summary

- **v1.0.0**: Initial release with basic AirPlay functionality
- **v1.1.0**: Performance optimizations and AAC ELD audio support
- **v1.2.0**: Enhanced CI/CD and improved documentation (unreleased)

## Contributing

Please read our contributing guidelines before submitting changes. All notable changes should be documented in this changelog following the [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) format.

## Migration Guide

### From v1.0.0 to v1.1.0

**Audio Changes:**
- The project now exclusively uses `AacAudioPlayer` for audio playback
- ALAC and GStreamer audio players have been removed
- AAC ELD format is properly supported with enhanced codec configuration

**Video Improvements:**
- Video stuttering should be significantly reduced
- Better error recovery with automatic decoder restart
- Enhanced queue management for smoother playback

**API Changes:**
- No public API changes for end users
- Internal audio player APIs have been simplified and optimized 