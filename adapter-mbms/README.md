# adapter-mbms

The MBMS Client of the 5G-MAG MBMS Middleware for Android,
[rt-mbms-mw-android](https://github.com/5G-MAG/rt-mbms-mw-android), behind dvbi-core's
`IMbmsStreamingClient` (3GPP TS 26.347 V18.1.0 clause 6.3 method names). It is linked into the
`dvbiMbms` variant of `app`, where the DVB-I client acts as MBMS-Aware Application (ETSI TS 103 770
V1.2.1 clause 9.3.3).

**Not implemented.** `MwServiceMbmsClient` compiles and delegates every call to `NoMbmsClient`:
registration fails, so 5G Broadcast instances are not played and another instance of the service
plays. MwService offers no interface to bind to yet. In rt-mbms-mw-android main 03a4a63 and
development cf85791, `MwService.onBind()` is `TODO("Not yet implemented")` (`MwService.kt:195`).

**Dependency it will need:** the middleware's client-side interface, from rt-mbms-mw-android. How to
depend on it is to be established: the rt-mbms-mw-android README describes building and installing
the middleware APK, not a library or interface to depend on. Nothing is declared in `build.gradle`
until it exists.

Depends on: `dvbi-core` only.
