# Elarvic Firebase setup

Both Elarvic apps use the **same Firebase project** and the **same Firestore ruleset**.

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

The downloaded admin `google-services.json` supplies Firebase's generated `default_web_client_id` used by the Google sign-in flow.

## 5. Create Firestore

Firebase Console → Firestore Database → Create database.

Use production/locked mode.

### Important: Firestore has one active ruleset

Do **not** deploy the User App rules and Admin App rules separately. Firestore uses one ruleset for the whole Firebase project. The `firestore.rules` files in both repositories are now synchronized and contain the combined authorization model. Deploy this same combined ruleset once from either repository.

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
role: "admin"
```

The Admin app accepts `role: "admin"` as the persistent administrator role. Existing records without `role` are temporarily tolerated by the client for migration, but production records should contain `role: "admin"` because the Firestore rules require it for admin privileges.

## 7. Deploy Firestore rules

Publish the root `firestore.rules` from either Elarvic repository into Firebase Console → Firestore Database → Rules.

The shared rules enforce:

- admins can read/list/manage issued keys
- only active admins with `role == "admin"` can create/revoke/manage keys
- normal users can only validate a supplied exact key with a `get`
- normal users cannot list all keys
- user key access requires `active == true` and `expiresAt > request.time`
- admins can manage `users/{uid}` records

Never use `allow read, write: if true` in production.

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

## 9. User flow

```text
User enters ELARVIC key
        ↓
Firebase anonymous authentication
        ↓
Firestore exact-key validation
        ↓
Valid + active + not expired
        ↓
WhatsApp channel gate (once)
        ↓
Elarvic dashboard
```

WhatsApp channel:

`https://whatsapp.com/channel/0029VbDUColKQuJI4D5IVA2L`

## 10. Admin flow

```text
Google Sign-In
      ↓
Firebase Auth UID
      ↓
admins/{UID}
role = admin + active = true
      ↓
Admin panel
      ↓
Generate 3/6/15-day key
      ↓
ELARVIC_XXXXXXXXXXXX
```

The Firebase Auth session persists across app restarts, but the Admin App re-checks the Firestore admin record so a revoked/deactivated role cannot remain trusted only because of local state.

## 11. Build checklist

Before testing either APK:

- [ ] Correct `google-services.json` in each `app/` folder
- [ ] Admin Google provider enabled
- [ ] User Anonymous provider enabled
- [ ] Admin SHA-1/SHA-256 added
- [ ] Firestore created
- [ ] `admins/{UID}` exists with `active: true` and `role: "admin"`
- [ ] Shared `firestore.rules` published once
- [ ] No Firebase secrets committed to Git
