# Elarvic Admin App

Separate Android admin application for **Elarvic V1** user/account management.

## Planned capabilities
- Firebase Google authentication for administrators
- Admin allow-list using the `admins/{uid}` Firestore collection
- User creation, editing, activation/deactivation and deletion
- Expiry-date management
- Active/expired user visibility
- Centralized Firestore-backed user records

## Firebase setup
1. Use the same Firebase project as the Elarvic User App.
2. Add Android app ID `com.elarvic.admin`.
3. Enable Google Authentication.
4. Add authorized administrator UID documents under `admins/{firebaseUid}`.
5. Download `google-services.json` into `app/`.
6. Configure `firebase_web_client_id` in `strings.xml`.
7. Deploy `firestore.rules` before production use.

`google-services.json` and signing files are intentionally ignored by Git.

## Security model
Admin access is determined by the Firestore `admins` collection, not by a client-side boolean. Production rules must remain deployed so a normal user cannot write user records.
