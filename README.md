# Elarvic Admin App

Separate Android admin application for **Elarvic V1**. It uses the same Firebase project as the User App.

## Completed flow

1. Administrator signs in with Google.
2. Firebase Auth identifies the administrator.
3. Firestore `admins/{uid}` is checked for `active: true`.
4. Admin chooses a key lifetime: **3, 6 or 15 days**.
5. App generates an `ELARVIC_XXXXXXXXXXXX` access key.
6. Admin copies the key and sends it to the user.
7. Admin can search issued keys and revoke active keys.
8. Dashboard shows total, active and expired keys.

## Security model

Admin access is determined by the Firestore `admins` collection, not by a client-side boolean. The Firestore rules only allow authorized admins to create/read/update/delete key documents. The first admin UID must be seeded manually in Firestore.

## Branding

The admin app uses the Elarvic black/silver visual system and the supplied Elarvic mark.

## Firebase setup

See [`FIREBASE_SETUP.md`](FIREBASE_SETUP.md) for the complete Firebase configuration, SHA fingerprints, Google provider, Anonymous Auth, Firestore collections and rules setup.

`google-services.json` and signing files are intentionally ignored by Git.
