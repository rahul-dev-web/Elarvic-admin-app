# Elarvic Firebase setup

Both Elarvic apps use the **same Firebase project**.

## 1. Create/select Firebase project

Open Firebase Console and create/select one project for Elarvic.

## 2. Add both Android apps

Add these two Android apps:

- User: `com.elarvic.user`
- Admin: `com.elarvic.admin`

For each app, download its own `google-services.json` and place it at:

```text
Elarvic-user-app/app/google-services.json
Elarvic-admin-app/app/google-services.json
```

Do not commit these files to GitHub; both repositories ignore them.

## 3. Authentication

Go to Firebase Console → Authentication → Sign-in method.

### Admin app

Enable **Google** provider.

### User app

Enable **Anonymous** provider. The user app does not use Google login. Anonymous auth is only used to make a Firebase-authenticated request while validating the access key.

## 4. Google sign-in prerequisites

In Firebase Project settings → Your apps → Admin app, add the SHA-1 and SHA-256 certificate fingerprints for the debug/release signing keys you will use.

The downloaded admin `google-services.json` must contain the OAuth web client used by Google sign-in. The app reads Firebase's generated `default_web_client_id` resource.

## 5. Create Firestore

Firebase Console → Firestore Database → Create database.

Use production/locked mode, then deploy the rules from `firestore.rules`. Do not use `allow read, write: if true` in production.

## 6. Create the first admin

After the admin app is connected to Firebase:

1. Run the Admin app.
2. Sign in with the Google account you want to make an administrator.
3. Copy that account's Firebase Auth UID from Firebase Console → Authentication → Users.
4. In Firestore create:

```text
admins/{UID}
```

with:

```text
active: true
```

The Admin app will then allow that Google account into the panel.

## 7. Deploy Firestore rules

Use the `firestore.rules` file in this repository for the admin/key-management side. The user repository contains the matching restricted rules for key validation.

If using Firebase CLI, initialize the project and deploy the Firestore rules from the appropriate repository. If you prefer the console, paste the repository's rules into Firestore → Rules and publish.

## 8. Key structure

The admin app creates documents under:

```text
keys/{ELARVIC_RANDOM_KEY}
```

Example fields:

```text
active: true
createdAt: Timestamp
createdBy: admin UID
durationDays: 3 | 6 | 15
expiresAt: Timestamp
```

The user app can only perform a single-document `get` for a supplied key and only receives access when the key is active and `expiresAt > request.time`.

## 9. User flow

```text
User enters ELARVIC key
        ↓
Firebase anonymous authentication
        ↓
Firestore key validation
        ↓
Valid + active + not expired
        ↓
WhatsApp channel gate (once)
        ↓
Elarvic dashboard
```

WhatsApp channel:

`https://whatsapp.com/channel/0029VbDUColKQuJI4D5IVA2L`

## 10. Build checklist

Before testing either APK:

- [ ] Correct `google-services.json` in each `app/` folder
- [ ] Admin Google provider enabled
- [ ] User Anonymous provider enabled
- [ ] Admin SHA-1/SHA-256 added
- [ ] Firestore created
- [ ] Admin UID document created
- [ ] Admin `firestore.rules` published
- [ ] User `firestore.rules` published
- [ ] No Firebase secrets committed to Git
