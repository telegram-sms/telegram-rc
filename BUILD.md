# Telegram RC Compilation Guide

This guide provides detailed instructions on how to compile the Telegram RC (Remote Control) application from source code.

## Prerequisites

Before compiling the application, ensure you have the following installed:

1. **Java Development Kit (JDK) 17** - Required for building Android applications with the latest SDK
2. **Android Studio** - Recommended IDE for Android development (optional but helpful)
3. **Git** - For cloning the repository
4. **Gradle** - Will be automatically downloaded via Gradle Wrapper (no need to install separately)

## Getting the Source Code

Clone the repository using Git:

```bash
git clone https://github.com/telegram-sms/telegram-rc.git
cd telegram-rc
```

Alternatively, you can download the source code archive from the releases page and extract it.

## Initializing Submodules

If you have already cloned the repository but forgot to initialize the submodules, you can initialize them with:

```bash
git submodule update --init --recursive
```

## Project Structure Overview

The project follows a standard Android project structure:
- `app/` - Main application module
- `app/src/main/java/` - Kotlin source code
- `app/src/main/res/` - Resources (layouts, drawables, strings, etc.)
- `app/build.gradle` - Module-level build configuration
- `build.gradle` - Project-level build configuration
- `gradle/` - Gradle Wrapper files

## Dependencies

The application depends on several third-party libraries including:
- OkHttp for network communication
- Gson for JSON serialization
- MMKV for key-value storage
- Shizuku for system-level API access
- Room for database operations
- Various AndroidX libraries

These dependencies are automatically resolved during the build process.

## Building the Application

### Using Gradle Wrapper (Recommended)

The project includes a Gradle Wrapper, which ensures everyone uses the same Gradle version.

#### On Linux/macOS:
```bash
./gradlew assembleRelease
```

#### On Windows:
```cmd
gradlew.bat assembleRelease
```

### Using Android Studio

1. Open Android Studio
2. Select "Open an existing Android Studio project"
3. Navigate to the project directory and select it
4. Wait for Gradle sync to complete
5. From the menu, select **Build > Generate Signed Bundle/APK**
6. Choose APK and follow the wizard

### Build Variants

The project supports two build variants:
- `debug` - For development and testing
- `release` - For production deployment (minified and obfuscated)

To build a specific variant:
```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

## Signing Configuration

The release build requires signing keys. The project is configured to use environment variables for signing:

- `KEYSTORE_PASS` - Keystore password
- `ALIAS_NAME` - Key alias
- `ALIAS_PASS` - Key password

You can either:
1. Set these environment variables before building
2. Pass them as project properties:
   ```bash
   ./gradlew assembleRelease -PKEYSTORE_PASS=your_keystore_password -PALIAS_NAME=your_alias -PALIAS_PASS=your_alias_password
   ```

By default, the project expects a keystore file named `keys.jks` in the `app/` directory.

## Version Configuration

Application version information is controlled via environment variables:
- `VERSION_CODE` - Integer version code (defaults to 1 if not set)
- `VERSION_NAME` - String version name (defaults to "debug-do_not_leak" if not set)

Example with custom version:
```bash
VERSION_CODE=10 VERSION_NAME="1.0.0" ./gradlew assembleRelease
```

## Supported Architectures

The application is configured to build only for `arm64-v8a` architecture to reduce APK size. If you need to support other architectures, modify the `abiFilters` in [app/build.gradle](app/build.gradle).

## Output Location

After successful compilation, the APK files will be located at:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

## Troubleshooting

### Common Issues

1. **Gradle sync failed**: Ensure you have a stable internet connection as the project downloads dependencies during the first build.

2. **Java version issues**: Make sure you're using JDK 17. You can specify the JAVA_HOME environment variable pointing to your JDK 17 installation.

3. **Missing dependencies**: If some dependencies fail to download, check your internet connection and try using a VPN if you're in a region where certain services are blocked.

4. **Compilation errors**: Clean and rebuild the project:
   ```bash
   ./gradlew clean
   ./gradlew assembleRelease
   ```

### Cleaning the Project

To clean build artifacts:
```bash
./gradlew clean
```

## Additional Information

- Minimum supported Android version: 8.0 (API level 26)
- Target SDK version: 36
- The application extensively uses Shizuku for system-level operations without requiring root access
- Precompiled versions only support 64-bit devices

For more information about the project, refer to the [README.md](README.md) file.
