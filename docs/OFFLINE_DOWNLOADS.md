# Premium Offline Chapter Downloads

This backend issues seven-day, device-bound offline licenses and creates encrypted `CVPK1` chapter packages for Android readers. It raises the cost of casual extraction; it is not absolute DRM. A rooted device, a repackaged client, runtime instrumentation, or screen capture outside platform protections can still expose rendered content.

## Required Railway configuration

Offline downloads remain disabled and fail closed until a dedicated Ed25519 signing key pair is configured.

```text
OFFLINE_DOWNLOAD_ENABLED=true
OFFLINE_LICENSE_PRIVATE_KEY_BASE64=<PKCS8 DER encoded as Base64>
OFFLINE_LICENSE_PUBLIC_KEY_BASE64=<X509 DER encoded as Base64>
OFFLINE_LICENSE_SIGNING_KEY_ID=offline-ed25519-v1
OFFLINE_LICENSE_ISSUER=comiverse-api
OFFLINE_LICENSE_AUDIENCE=comiverse-android
OFFLINE_LICENSE_DURATION=168h
OFFLINE_DEVICE_CHALLENGE_TTL=5m
OFFLINE_MAX_DEVICES_PER_USER=3
OFFLINE_MAX_CHALLENGES_PER_HOUR=10
OFFLINE_MAX_PACKAGES_PER_HOUR=5
OFFLINE_MAX_LICENSES_PER_HOUR=20
OFFLINE_MAX_CHAPTER_PAGES=200
OFFLINE_MAX_PAGE_BYTES=12582912
OFFLINE_MAX_PACKAGE_BYTES=157286400
OFFLINE_SOURCE_CONNECT_TIMEOUT=8s
OFFLINE_SOURCE_REQUEST_TIMEOUT=20s
OFFLINE_ALLOWED_IMAGE_HOSTS=res.cloudinary.com
```

Generate a dedicated key pair outside the repository. Do not reuse the login JWT key and never commit the private key.

```powershell
openssl genpkey -algorithm ED25519 -out offline-license-private.pem
openssl pkey -in offline-license-private.pem -outform DER -out offline-license-private.pk8
openssl pkey -in offline-license-private.pem -pubout -outform DER -out offline-license-public.x509
[Convert]::ToBase64String([IO.File]::ReadAllBytes("offline-license-private.pk8"))
[Convert]::ToBase64String([IO.File]::ReadAllBytes("offline-license-public.x509"))
```

Pin the matching X.509 public key and expected key ID in the signed mobile build. The current client pins one key, so rotate only through a staged app release that first adds key-ring support, or wait for all licenses issued by the previous key to expire before removing it.

## API flow

All endpoints require an authenticated `READER` account. Package creation and renewal additionally require an `ACTIVE` or `TRIALING` Premium subscription whose current period has not ended.

1. `POST /api/downloads/devices/challenges`

   ```json
   {
     "deviceId": "stable-installation-identifier",
     "deviceName": "Pixel 8",
     "devicePublicKey": "BASE64_X509_RSA_PUBLIC_KEY"
   }
   ```

   The RSA key must be 2048 to 4096 bits with exponent 65537. The response contains a Base64URL challenge. Sign the decoded challenge bytes using `RSA-PSS`, SHA-256, MGF1-SHA256, and a 32-byte salt.

2. `POST /api/downloads/devices`

   ```json
   {
     "challengeId": "UUID",
     "signature": "BASE64URL_RSA_PSS_SIGNATURE"
   }
   ```

   Proof of possession prevents accidental or silent key replacement. It does not prove that the key is hardware-backed Android Keystore material. Full Android key attestation is a separate hardening step.

3. `POST /api/downloads/chapters/{chapterId}`

   ```json
   { "deviceKeyId": "UUID_FROM_ENROLLMENT" }
   ```

   The response is `application/vnd.comiverse.cvpack`. License, server time, hashes, and the device-wrapped content key are returned in `X-Comiverse-*` headers. The content key uses `RSA-OAEP-SHA256-MGF1SHA1`, which is interoperable with Android Keystore on API 24 through 34. The private RSA key must remain non-exportable.

4. `POST /api/downloads/packages/{packageId}/licenses`

   ```json
   { "deviceKeyId": "UUID_FROM_ENROLLMENT" }
   ```

   This renews only the signed seven-day license and reuses the existing encrypted package and wrapped key. Renewal fails if Premium ended, the device/package was revoked, the chapter is unpublished, or its source descriptor changed.

5. `GET /api/downloads/devices` lists enrolled devices. `DELETE /api/downloads/devices/{deviceKeyId}` revokes the device, its packages, and online renewal records.

## Package and license verification

`CVPK1` contains five ASCII magic bytes, a four-byte big-endian manifest length, a UTF-8 JSON manifest, then concatenated encrypted page frames. Manifest offsets are relative to the encrypted payload section. Every page uses a fresh 12-byte nonce and AES-256-GCM with a 128-bit tag. Decrypt only one page into RAM and never persist plaintext pages.

The exact page AAD is:

```text
CVPK1|packageId|userId|chapterId|deviceKeySha256|contentRevision|pageNumber|pageSha256
```

The compact JWS is signed with Ed25519 and binds the user, chapter, comic, package, device key ID, hashed device identifier, public-key fingerprint, strong content revision, manifest/package/wrapped-key hashes, package size, algorithms, format, issue time, server time, and expiry. The app must verify the signature, issuer, audience, key ID, all package/device bindings, `nbf`, and `exp` before unwrapping the key.

For offline clock handling, persist the last trusted server time with monotonic elapsed time. If the wall clock moves backwards unexpectedly, the monotonic baseline resets, the app is restored, or license validation is ambiguous, require online renewal.

## Remaining security boundary

Current chapter records contain direct Cloudinary `secure_url` values. Those URLs can be copied by an authorized online client and shared independently of this offline package system. Closing that existing extraction path requires private/authenticated Cloudinary delivery, short-lived signed URLs, or an authenticated throttled image proxy. Do not describe this feature as preventing all copying until online image delivery is hardened.

The database currently relies on Hibernate schema updates. Before production rollout, create and review an explicit migration for `offline_devices`, `offline_device_challenges`, `offline_packages`, and `offline_licenses`, then disable automatic production schema mutation.
