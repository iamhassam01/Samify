#!/bin/bash

# GitHub Release Creation Script for Samify v2.0.0
# This script creates a professional GitHub release following the established pattern from v1.5.0

set -e

VERSION="2.0.0"
TAG="v${VERSION}"
RELEASE_TITLE="Release v2.0.0 - Spotify Integration and Enhanced Music Experience"

# APK file paths
UNIVERSAL_APK="releases/Samify-v2.0.0-universal-signed.apk"
ARM64_APK="releases/Samify-v2.0.0-arm64-signed.apk"
ARMEABI_APK="releases/Samify-v2.0.0-armeabi-signed.apk"
X86_APK="releases/Samify-v2.0.0-x86-signed.apk"
X86_64_APK="releases/Samify-v2.0.0-x86_64-signed.apk"

# Verify APK files exist
echo "Verifying APK files..."
for apk in "$UNIVERSAL_APK" "$ARM64_APK" "$ARMEABI_APK" "$X86_APK" "$X86_64_APK"; do
    if [ ! -f "$apk" ]; then
        echo "ERROR: APK file not found: $apk"
        exit 1
    fi
    echo "✓ Found: $apk ($(du -h "$apk" | cut -f1))"
done

# Release notes following v1.5.0 pattern
RELEASE_NOTES="Release v2.0.0 - Spotify Integration and Enhanced Music Experience

**Features:**
🎵 **Full Spotify Integration**
- Native Spotify account authentication and login support
- Import playlists directly from your Spotify account
- Seamless synchronization of your Spotify music library
- Support for both private and collaborative playlists
- Advanced playlist import with intelligent track matching

🎨 **Enhanced User Interface**
- New Spotify integration screens with modern design
- Improved playlist management interface
- Better authentication flow with secure OAuth2 implementation
- Enhanced library management with Spotify content

🔐 **Security & Authentication**
- Secure Spotify OAuth2 authentication with PKCE
- Safe token management and refresh capabilities
- Privacy-focused authentication flow
- Secure API communication with Spotify services

**Technical Improvements:**
🛠 **Spotify SDK Integration**
- Added Spotify Android SDK (v2.1.0) for native authentication
- Implemented Retrofit2 for robust API communication
- Enhanced OkHttp client with logging and security features
- Modern Kotlin coroutines integration for async operations

🏗 **Architecture Enhancements**
- New Spotify service layer with dependency injection
- Enhanced repository pattern for music data management
- Improved error handling and state management
- Better separation of concerns with dedicated Spotify modules

⚡ **Performance Optimizations**
- Optimized playlist import with background processing
- Improved track matching algorithms
- Better memory management for large playlist imports
- Enhanced caching mechanisms for Spotify data

**Release Builds:**
📱 Universal APK for all devices (recommended for most users)
🏗 Architecture-specific APKs for optimal performance:
- ARM64 (arm64-v8a) - Modern 64-bit ARM devices
- ARMv7 (armeabi-v7a) - Older ARM devices
- x86 - Intel-based Android devices
- x86_64 - 64-bit Intel-based devices

**Version Information:**
- Version bump from 1.5.0 to 2.0.0
- Target SDK: 36 (Android 15+)
- Minimum SDK: 26 (Android 8.0+)
- Compiled with latest Kotlin and Gradle toolchain

**Installation Notes:**
⚠️ **Important:** This version requires Spotify account authentication for the new import features. Existing local functionality remains unchanged and doesn't require Spotify integration."

# Create the GitHub release
echo "Creating GitHub release $TAG..."

# Using gh CLI (requires authentication)
gh release create "$TAG" \
    --title "$RELEASE_TITLE" \
    --notes "$RELEASE_NOTES" \
    "$UNIVERSAL_APK" \
    "$ARM64_APK" \
    "$ARMEABI_APK" \
    "$X86_APK" \
    "$X86_64_APK" \
    --verify-tag

echo "✅ GitHub release $TAG created successfully!"
echo "📱 Uploaded APK files:"
echo "  - Universal APK: $UNIVERSAL_APK"
echo "  - ARM64 APK: $ARM64_APK"
echo "  - ARMv7 APK: $ARMEABI_APK"
echo "  - x86 APK: $X86_APK"
echo "  - x86_64 APK: $X86_64_APK"
echo ""
echo "🔗 Release URL: https://github.com/iamhassam01/Samify/releases/tag/$TAG"