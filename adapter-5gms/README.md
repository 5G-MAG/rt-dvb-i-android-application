# adapter-5gms

The 5GMSd Client, reached through the 5G-MAG 5GMS client libraries. It is linked into the `dvbi5gms`
variant of `app`, where the DVB-I client acts as 5GMSd-Aware Application.

**Not implemented.** `FiveGmsClient` compiles and holds only a status string; the variant behaves as
the plain DVB-I client.

5GMS Service Access Information has no DVB or 3GPP element in a DVB-I service list or playlist
yet. ETSI TR 103 972 V1.1.1 records both as "Identified gaps in existing DVB/3GPP/ETSI
specifications" (the title of its clause 6.3.4):

- ETSI TR 103 972 V1.1.1 clause 6.3.4, gap 1: "DVB-I service instance metadata needs to be extended
  to include baseline 5GMS Service Access Information parameters."
- ETSI TR 103 972 V1.1.1 clause 6.3.4, gap 2: "the DVB-I Playlist entry element needs to be extended
  to include baseline 5GMS Service Access Information parameters."

ETSI TS 103 770 V1.2.1 defines no such element, so nothing is invented for it here.

**Dependencies it will need:** those the 5GMSd-Aware Application of
[rt-5gms-application](https://github.com/5G-MAG/rt-5gms-application) uses
(`fivegmag_5GMSdAwareApplication/app/build.gradle` at release `rt-5gms-application-v1.3.0`, the same
lines on its `main` and `development` branches):

```groovy
implementation 'com.fivegmag:a5gmscommonlibrary:1.3.0'
implementation 'com.fivegmag:a5gmsmediastreamhandler:1.3.0'
```

They come from
[rt-5gms-common-android-library](https://github.com/5G-MAG/rt-5gms-common-android-library) (release
`rt-5gms-common-android-library-v1.3.0`) and
[rt-5gms-media-stream-handler](https://github.com/5G-MAG/rt-5gms-media-stream-handler) (release
`rt-5gms-media-stream-handler-v1.3.0`). Each one's
publishing block names GitHub Packages (`https://maven.pkg.github.com/5g-mag/<repository>`) with
credentials from `GITHUB_ACTOR` and `GITHUB_TOKEN`. The 5GMSd-Aware Application resolves them from
`mavenLocal()` after `./gradlew publishToMavenLocal` in each library. Neither route is reachable
from a clean build without that set-up, so nothing is declared in `build.gradle` yet.

Depends on: `dvbi-core` only.
