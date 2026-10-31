# Source and build-tool notices

Value Forge Companion's Java client and local touch UI are distributed under
GPL-3.0-only. They communicate with Value Forge's documented LAN API. The native
scanner is not bundled in this Android app.

- Value Forge is derived from Memory Deck by Camzie99 (Cameron Redmore),
  HeyItsWaters and contributors, and includes scanmem by its credited authors.
  Their source, original notices, and licenses are preserved in the plugin repo:
  https://github.com/timekillr/value-forge-decky/blob/main/THIRD_PARTY.md
- Android APIs and WebView are provided by the device operating system.
- The Gradle wrapper/build tool is Apache-2.0; its generated wrapper scripts
  retain their copyright/license headers. https://github.com/gradle/gradle
- Android Gradle plugin is a build tool, not app runtime code.
- jsdom 26.1.0 (MIT) and JUnit 4.13.2 (EPL-1.0) are development/test dependencies;
  neither is included in the APK. Their licenses accompany their distributions.
- JSON-java 20240303 is used only for JVM transport tests; Android supplies the
  runtime JSON APIs. Its license accompanies its distribution.

No fonts, analytics services, remotely loaded scripts, or third-party UI runtime
libraries are included in the app assets.
