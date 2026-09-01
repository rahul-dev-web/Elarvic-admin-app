# Elarvic Admin App

Separate Android admin application for **Elarvic V1**.

## Admin flow

1. Administrator signs in with Google using Firebase Authentication.
2. The signed-in Firebase UID must exist in `admins/{uid}` and have `active: true`.
3. Admin selects an access duration: **3 days**, **6 days**, or **15 days**.
4. The app generates a high-entropy key in the form `ELARVIC_<random>`.
5. The key is stored in Firestore with its creation time, expiry time, duration, active status, and creator UID.
6. Admin can copy the key and send it to the user.
7. Admin can view issued keys and revoke an active key.

## Firebase setup

1. Use the same Firebase project as the Elarvic User App.
2. Add Android app ID `com.elarvic.admin`.
3. Enable Google Authentication.
4. Add the first authorized administrator manually in Firestore:

`admins/{firebaseUid}`

```text
active: true
```

5. Download `google-services.json` into `app/`.
6. Put the Firebase Web client ID in `app/src/main/res/values/strings.xml`.
7. Deploy `firestore.rules` before production use.

## Key data

`keys/{ELARVIC_xxxxxxxxxxxx}`

```text
active: true
createdAt: timestamp
durationDays: 3 | 6 | 15
expiresAt: timestamp
createdBy: admin uid
```

## Security model

Only an authorized admin UID can create, update, revoke, or list keys. The user app uses Firebase Anonymous Authentication only for the key-validation read and does not receive admin credentials.

`google-services.json`, signing files, local properties, and environment secrets must not be committed.
