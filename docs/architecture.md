# Architecture

One DVB-I client, built in three roles. The DVB-I logic is written and audited once, in `dvbi-core`.
Each 5G role adds only the connection to a client-side component that another 5G-MAG project
already maintains.

## Modules

| Module | Depends on | What it holds |
| --- | --- | --- |
| `dvbi-core` | nothing but the Android SDK | The DVB-I logic, no user interface: service list, LCN, instance selection, HTTP rules of clause 4.3, content guide, registry discovery, the `mbms://` URL check, the MBMS Client interface and `NoMbmsClient` |
| `app` | `dvbi-core`; one adapter per variant | The DVB-I client, "5G-MAGflix for DVB-I": home screen, player (Media3 ExoPlayer), schedule and programme guide, More Episodes and Box Sets, application frame, settings, About |
| `adapter-mbms` | `dvbi-core` | The MBMS Client of rt-mbms-mw-android behind the MBMS Client interface. Not implemented |
| `adapter-5gms` | `dvbi-core` | The 5GMSd Client through the 5G-MAG 5GMS client libraries. Not implemented |

## Roles

The `app` module has one flavor dimension, `role`, with three variants:

| Variant | Role of this app | Client side | Status |
| --- | --- | --- | --- |
| `dvbi` (default) | DVB-I client | none | Built: what the client does today |
| `dvbiMbms` | DVB-I client acting as MBMS-Aware Application | MBMS Client: the MwService of [rt-mbms-mw-android](https://github.com/5G-MAG/rt-mbms-mw-android), through `adapter-mbms` | Builds; behaves as `dvbi` (see Open) |
| `dvbi5gms` | DVB-I client acting as 5GMSd-Aware Application | 5GMSd Client: the 5G-MAG 5GMS client libraries ([rt-5gms-media-stream-handler](https://github.com/5G-MAG/rt-5gms-media-stream-handler) with the [Media Session Handler](https://github.com/5G-MAG/rt-5gms-media-session-handler), [rt-5gms-common-android-library](https://github.com/5G-MAG/rt-5gms-common-android-library)), through `adapter-5gms` | Builds; behaves as `dvbi` (see Open) |

Each variant's `app/src/<variant>/` holds a `Role` object that supplies the MBMS Client the app
installs at start. Only the `dvbiMbms` variant links `adapter-mbms`, and only `dvbi5gms` links
`adapter-5gms`.

For 5G Broadcast, the DVB-I client itself takes the MBMS-Aware Application role:

ETSI TS 103 770 V1.2.1 clause 9.3.3: "When a DVB-I service instance with an mbms:// locator is selected by the user, the DVB-I client (acting as an MBMS-Aware Application) shall invoke the MBMS Client to initiate reception of the corresponding MBMS User Service."

For 5G Media Streaming, the TR places DVB-I in the aware-application role:

ETSI TR 103 972 V1.1.1 clause 4.3.2.1: "it is expected that DVB-I (in the role of 5GMS-Aware Application) defines service announcement."

## Principles

- One DVB-I client, audited once. A 5G role does not get its own copy of the DVB-I logic.
- Other projects keep their client-side components. The MBMS Client stays in rt-mbms-mw-android, and
  the 5GMSd Client in the 5GMS repositories.
- Adapters depend only on `dvbi-core` and on the published interfaces of those components.
- No code is copied between repositories.
- Another front end can depend on `dvbi-core` directly, for example a 5GMSd-Aware Application in
  [rt-5gms-application](https://github.com/5G-MAG/rt-5gms-application).

## Open

- **MwService has no binding interface.** In rt-mbms-mw-android, main 03a4a63 and development
  cf85791, `MwService.onBind()` is `TODO("Not yet implemented")` (`MwService.kt:195`). The service is
  started through the intent action `nakolosmw.intent.action.START`. No interface exists for
  `adapter-mbms` to call, so it answers as `NoMbmsClient` does. How the middleware will expose the
  TS 26.347 V18.1.0 clause 6.3 Media Streaming Service API to another app is not established.
- **5GMS Service Access Information has no DVB-I element.** The `dvbi5gms` variant has nothing to
  start a 5GMS session from:
  - ETSI TR 103 972 V1.1.1 clause 6.3.4, gap 1: "DVB-I service instance metadata needs to be extended
    to include baseline 5GMS Service Access Information parameters."
  - ETSI TR 103 972 V1.1.1 clause 6.3.4, gap 2: "the DVB-I Playlist entry element needs to be extended
    to include baseline 5GMS Service Access Information parameters."

  ETSI TS 103 770 V1.2.1 defines no such element, so nothing is invented for it.
- **External dependencies are not declared yet.** Neither adapter depends on its external library,
  because a clean build may not be able to reach those artifacts. Each adapter's README says what it
  will need and from where.
