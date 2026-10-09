<p align="center">
  <img src=".github/banner.svg" width="100%" alt="Reference Tools · DVB-I Services over 5G Systems: DVB-I over 5G Reference App">
</p>

<h1 align="center">DVB-I over 5G Reference App</h1>

<p align="center">
  A native Android DVB-I client that discovers a service list, presents its channels with their
  content guide, and plays them with Media3 ExoPlayer, per ETSI TS 103 770.
</p>

<p align="center">
  <img alt="Status: under development"
    src="https://img.shields.io/badge/Status-Under_Development-yellow">
  <a href="https://github.com/5G-MAG/rt-dvb-i-android-application/releases"><img alt="Version"
    src="https://img.shields.io/github/v/release/5G-MAG/rt-dvb-i-android-application?label=Version&sort=semver"></a>
  <a href="LICENSE"><img alt="License: 5G-MAG Public License v1.0"
    src="https://img.shields.io/badge/License-5G--MAG%20PL%20v1.0-blue"></a>
</p>

<p align="center">
  <a href="https://www.5g-mag.com/reference-tools/dvb-i">Project page</a> &nbsp;&middot;&nbsp;
  <a href="https://github.com/5G-MAG/rt-dvb-i-android-application/issues">Issues</a> &nbsp;&middot;&nbsp;
  <a href="https://www.5g-mag.com/contributing">Contributing</a>
</p>

---

## At a glance

|  |  |
|---|---|
| **Implements** | ETSI TS 103 770 V1.2.1 (2024-09), *Digital Video Broadcasting (DVB); Service Discovery and Programme Metadata for DVB-I*, client side |
| **Runs on** | Android 10 (API level 29) or newer |
| **Plays** | DASH, HLS and DVB-I Playlists over HTTP, via Media3 ExoPlayer 1.10.0; HTML applications and on-demand players in a WebView |
| **Part of** | [DVB-I Services over 5G Systems](https://www.5g-mag.com/reference-tools/dvb-i), alongside [rt-dvb-i-application](https://github.com/5G-MAG/rt-dvb-i-application) (the browser client), [rt-dvb-i-application-provider](https://github.com/5G-MAG/rt-dvb-i-application-provider) (the list and guide), [rt-dvb-i-service-list-registry](https://github.com/5G-MAG/rt-dvb-i-service-list-registry) (discovery), [rt-dvb-i-examples](https://github.com/5G-MAG/rt-dvb-i-examples) (runnable demos) and [rt-5gms-application](https://github.com/5G-MAG/rt-5gms-application) (the Exo DVB-I Player) |

## Introduction

A DVB-I client for Android: it loads a service list, from a URL or picked from a Service List
Registry, lists the channels with now/next and a programme guide, and plays the selected service.
It is used with the service list and content guide of `rt-dvb-i-application-provider` and the
registry of `rt-dvb-i-service-list-registry`; `rt-dvb-i-examples` runs them together.
On the phone, the app is branded *5G-MAGflix for DVB-I*.

## Specification

Built against **ETSI TS 103 770 V1.2.1 (2024-09)**.

What the specification defines, and what this repository implements and does not, is on the project
page: <https://www.5g-mag.com/reference-tools/dvb-i>

## Install dependencies

The build needs a JDK 17 or 21 (the Android Gradle Plugin 9.2.0 does not run on newer ones) and the Android
SDK with platform `android-37.0` and build-tools `37.0.0`. Everything else (Gradle 9.5.0, the plugins and the
libraries) is downloaded by the Gradle wrapper from Google's Maven repository and Maven Central. No 5G-MAG
library has to be published to Maven Local first.

These commands install the toolchain without root rights and without changing any shell profile. They are
the ones used to build this application on Linux x86_64.

```sh
# JDK 21 (Eclipse Temurin 21.0.12.1+1) under ~/opt
mkdir -p ~/opt && cd ~/opt
curl -LO https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz
echo "ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94  OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz" | sha256sum -c
tar -xzf OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz

# Android command-line tools (build 15859902) under ~/Android/Sdk
mkdir -p ~/Android/Sdk/cmdline-tools && cd ~/Android/Sdk/cmdline-tools
curl -LO https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip
echo "4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583  commandlinetools-linux-15859902_latest.zip" | sha256sum -c
unzip -q commandlinetools-linux-15859902_latest.zip && mv cmdline-tools latest

# Environment for this shell only
export JAVA_HOME=$HOME/opt/jdk-21.0.12.1+1
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH

# SDK licences (read them), then the packages the build uses
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --install "platforms;android-37.0" "build-tools;37.0.0" "platform-tools"
```

The checksums are the ones published on adoptium.net and developer.android.com/studio for these files.

## Downloading

```bash
cd ~
git clone https://github.com/5G-MAG/rt-dvb-i-android-application.git
```

## Building

With `JAVA_HOME` and `ANDROID_HOME` set as above:

```sh
cd rt-dvb-i-android-application/
./gradlew test             # JVM unit tests
./gradlew assembleDvbiDebug    # app/build/outputs/apk/dvbi/debug/app-dvbi-debug.apk
```

Each variant has its own task and APK; `./gradlew assembleDebug` builds all three:

| Variant | Task | APK | Application ID |
| --- | --- | --- | --- |
| `dvbi` | `assembleDvbiDebug` | `app/build/outputs/apk/dvbi/debug/app-dvbi-debug.apk` | `com.fivegmag.dvbiclient` |
| `dvbiMbms` | `assembleDvbiMbmsDebug` | `app/build/outputs/apk/dvbiMbms/debug/app-dvbiMbms-debug.apk` | `com.fivegmag.dvbiclient.mbms` |
| `dvbi5gms` | `assembleDvbi5gmsDebug` | `app/build/outputs/apk/dvbi5gms/debug/app-dvbi5gms-debug.apk` | `com.fivegmag.dvbiclient.fivegms` |

The application IDs differ, so the variants can be installed side by side.

## Installing

Over adb, with the phone connected by USB and USB debugging enabled:

```sh
adb devices
adb install -r app/build/outputs/apk/dvbi/debug/app-dvbi-debug.apk
```

Without adb, copy `app-dvbi-debug.apk` to the phone (for example by download or file transfer) and open it there,
allowing the installation of applications from that source when Android asks.

## Running

### The demo over Wi-Fi

The phone and the laptop are on the same Wi-Fi network (for the demo, 192.168.1.0/24, with the laptop at
192.168.1.202). On the laptop, start the DVB-I live demo of
[rt-dvb-i-examples](https://github.com/5G-MAG/rt-dvb-i-examples) (`scripts/dvbi-live-demo`) so that it serves
on its Wi-Fi address rather than on `localhost`:

```sh
cd rt-dvb-i-examples/scripts/dvbi-live-demo
DEMO_HOST=192.168.1.202 ./start-all.sh
# ...
DEMO_HOST=192.168.1.202 ./stop-all.sh
```

The demo's own README describes its options. The service list, logos, content guide and media URLs all come
from the service list, so nothing else is configured in the app.

On the phone, the defaults point at the laptop:

| Setting | Default |
| --- | --- |
| Service list URL | `http://192.168.1.202:4000/service-list.xml` |
| Service List Registry endpoint | `http://192.168.1.202:7000/query` |

The path `/query` is part of the registry setting. Other defaults can be built in with
`-PdvbiServiceListUrl=... -PdvbiRegistryUrl=...`, together with `-PdvbiCleartextHosts=...` for their host.

In **Settings**, *Query the registry and pick a list* sends the query (with `TargetCountry` when a country is
set) and offers the lists with the default marked; *Save* installs the chosen one. While the endpoints are
reached without TLS, the home screen shows a plain HTTP notice under the toolbar.

## Configuration

### Plain HTTP and the network security configuration

Android blocks plain HTTP unless the application's network security configuration allows it. The
build writes a configuration that permits cleartext only to the hosts named in the Gradle property
`dvbiCleartextHosts` (comma separated), and to no other host. The default is `192.168.1.202`, the
demo laptop's Wi-Fi address. For another address:

```sh
./gradlew assembleDebug -PdvbiCleartextHosts=192.168.0.10
```

The list is fixed when the APK is built, because Android reads the network security configuration from the
APK. HTTPS works towards any host.

## Development

This project follows the [Gitflow workflow](https://www.atlassian.com/git/tutorials/comparing-workflows/gitflow-workflow).
The `development` branch of this project serves as an integration branch for new features.

`./gradlew test` runs the JVM unit tests, which need no device. The activities have no automated
tests. CI runs `./gradlew test assembleDebug` from `.github/workflows/test.yml`.

### Layout

| Module | What it holds |
| --- | --- |
| `dvbi-core/` | An Android library with the DVB-I logic and no user interface. |
| `app/` | The application built on `dvbi-core`, with its three variants: `dvbi` (the default), `dvbiMbms` and `dvbi5gms`. |
| `adapter-mbms/` | The MBMS Client adapter for `dvbiMbms`. Not implemented yet. |
| `adapter-5gms/` | The 5GMSd Client adapter for `dvbi5gms`. Not implemented yet. |

Until the adapters are implemented, `dvbiMbms` and `dvbi5gms` behave as `dvbi`.

## Credits

This project continues the DVB-I client contributed by Dolby to 5G-MAG/rt-5gms-application, in
`fivegmag_ExoDvbi_player`, by Giuseppe Crisci and kkrau: a WebView front end for the DVB-I Reference
Client plus an ExoPlayer patch. That history is preserved here, and the directory is kept as it was
contributed. This app replaces the WebView front end with a native Android client.

The user interface is based on the design of 5G-MAGflix, the 5GMSd Aware Application in
5G-MAG/rt-5gms-application, by Daniel Silhavy (Fraunhofer FOKUS):
<https://github.com/5G-MAG/rt-5gms-application>. The files derived from it carry its author and
copyright beside this adaptation's.

- **Font:** Ubuntu (Ubuntu Font Licence 1.0), downloaded at run time from the Google Fonts provider of
  Google Play services; no font file is bundled.
- **Icons:** [Material Symbols](https://github.com/google/material-design-icons) (Google, Apache License 2.0).
- **From the 5G-MAG website:** the launcher icon (`device-tv`, a Tabler icon, MIT licence) and the white
  5G-MAG logo.

## Contributing

Contributions are welcome. How to raise an issue, fork the repository and open a pull request, and
the Contributor License Agreement required before code can be merged, are described at
<https://www.5g-mag.com/contributing>.

## License

Distributed under the 5G-MAG Public License v1.0. See [LICENSE](LICENSE).
