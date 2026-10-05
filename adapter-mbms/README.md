# adapter-mbms

The MBMS Client adapter for the 5G-MAG MBMS Middleware for Android,
[rt-mbms-mw-android](https://github.com/5G-MAG/rt-mbms-mw-android), linked into the `dvbiMbms`
variant of `app`. Not implemented yet: `MwServiceMbmsClient` compiles and delegates every call to
`NoMbmsClient`, and the variant behaves as the plain DVB-I client.

Depends on: `dvbi-core` only.
