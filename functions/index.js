const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue, Timestamp } = require("firebase-admin/firestore");
const crypto = require("node:crypto");

initializeApp();

// Matches the provisioned named Firestore database ID in app/src/main/res/values/firebase_applet_config.xml
const DATABASE_ID = "ai-studio-android-friendsm-e9b1a466-7cfb-4f1f-9f62-5f4440dae04f";
const db = getFirestore(DATABASE_ID);

function generateEightDigitUid() {
  return String(crypto.randomInt(10000000, 100000000));
}

function hashSecret(value) {
  return crypto.createHash("sha256").update(value).digest("hex");
}

/**
 * Atomically allocates a unique 8-digit Friends Meet UID in `userIds/{friendsMeetUid}`,
 * initializes `users/{uid}`, `privacySettings/{uid}`, `userSettings/{uid}`,
 * and dispatches a 4-digit Guardian Verification letter if user is under 13.
 */
exports.completeRegistration = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Google Sign-In is required.");
  }

  const uid = request.auth.uid;
  const { fullName, dateOfBirth, gender, phoneNumber, photoUrl, identityPublicKey } = request.data || {};
  if (!fullName || !dateOfBirth || !gender) {
    throw new HttpsError("invalid-argument", "fullName, dateOfBirth, and gender are required.");
  }

  const dob = new Date(dateOfBirth);
  if (Number.isNaN(dob.getTime())) {
    throw new HttpsError("invalid-argument", "dateOfBirth must be YYYY-MM-DD.");
  }

  const now = new Date();
  let age = now.getUTCFullYear() - dob.getUTCFullYear();
  const m = now.getUTCMonth() - dob.getUTCMonth();
  if (m < 0 || (m === 0 && now.getUTCDate() < dob.getUTCDate())) age--;
  const isUnder13 = age < 13;

  const result = await db.runTransaction(async (tx) => {
    const userRef = db.collection("users").doc(uid);
    const existingUser = await tx.get(userRef);
    if (existingUser.exists) {
      return { friendsMeetUid: existingUser.data().friendsMeetUid, isUnder13: existingUser.data().isUnder13 };
    }

    let allocatedUid = null;
    for (let attempt = 0; attempt < 10; attempt++) {
      const candidate = generateEightDigitUid();
      const idRef = db.collection("userIds").doc(candidate);
      const idSnap = await tx.get(idRef);
      if (!idSnap.exists) {
        allocatedUid = candidate;
        tx.set(idRef, {
          uid,
          friendsMeetUid: candidate,
          displayName: fullName,
          ...(photoUrl ? { photoUrl } : {}),
          online: true,
          accountStatus: isUnder13 ? "PENDING_GUARDIAN" : "ACTIVE",
          ...(identityPublicKey ? { identityPublicKey } : {}),
          createdAt: FieldValue.serverTimestamp(),
        });
        break;
      }
    }

    if (!allocatedUid) {
      throw new HttpsError("resource-exhausted", "Unable to allocate unique 8-digit UID.");
    }

    tx.set(userRef, {
      uid,
      friendsMeetUid: allocatedUid,
      fullName,
      dateOfBirth,
      gender,
      ...(phoneNumber ? { phoneNumber } : {}),
      email: request.auth.token.email || "",
      ...(photoUrl ? { photoUrl } : {}),
      online: true,
      lastSeenAt: FieldValue.serverTimestamp(),
      isUnder13,
      guardianVerified: !isUnder13,
      accountStatus: isUnder13 ? "PENDING_GUARDIAN" : "ACTIVE",
      ...(identityPublicKey ? { identityPublicKey } : {}),
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });

    if (isUnder13) {
      const code = String(crypto.randomInt(1000, 10000));
      const expiresAt = Timestamp.fromMillis(Date.now() + 30 * 60 * 1000);
      const letterRef = db.collection("letterbox").doc();
      tx.set(letterRef, {
        recipientUid: uid,
        category: "GUARDIAN_VERIFICATION",
        title: "Guardian Verification Required",
        body: `Your 4-digit Guardian Verification code is ${code}. It expires in 30 minutes (max 5 attempts).`,
        read: false,
        trashed: false,
        verificationCodeHash: hashSecret(code),
        verificationAttempts: 0,
        verificationExpiresAt: expiresAt,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
    }

    return { friendsMeetUid: allocatedUid, isUnder13 };
  });

  return result;
});

/**
 * Verifies the 4-digit Guardian Verification code (30-min expiration, max 5 attempts).
 */
exports.verifyGuardianCode = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentication required.");
  }
  const uid = request.auth.uid;
  const code = String(request.data?.code || "").trim();
  if (!/^[0-9]{4}$/.test(code)) {
    throw new HttpsError("invalid-argument", "A 4-digit numeric code is required.");
  }

  const snap = await db
    .collection("letterbox")
    .where("recipientUid", "==", uid)
    .where("category", "==", "GUARDIAN_VERIFICATION")
    .orderBy("createdAt", "desc")
    .limit(1)
    .get();

  if (snap.empty) {
    throw new HttpsError("not-found", "No guardian verification letter found.");
  }

  const letterDoc = snap.docs[0];
  const data = letterDoc.data();
  if (data.verificationAttempts >= 5) {
    throw new HttpsError("permission-denied", "Maximum 5 verification attempts exceeded.");
  }
  if (data.verificationExpiresAt.toMillis() < Date.now()) {
    throw new HttpsError("deadline-exceeded", "Verification code has expired (30-minute limit).");
  }

  if (hashSecret(code) !== data.verificationCodeHash) {
    await letterDoc.ref.update({
      verificationAttempts: FieldValue.increment(1),
      updatedAt: FieldValue.serverTimestamp(),
    });
    throw new HttpsError("permission-denied", "Incorrect verification code.");
  }

  await db.runTransaction(async (tx) => {
    tx.update(db.collection("users").doc(uid), {
      guardianVerified: true,
      accountStatus: "ACTIVE",
      updatedAt: FieldValue.serverTimestamp(),
    });
    const userSnap = await tx.get(db.collection("users").doc(uid));
    const fuid = userSnap.data()?.friendsMeetUid;
    if (fuid) {
      tx.update(db.collection("userIds").doc(fuid), { accountStatus: "ACTIVE" });
    }
  });

  return { verified: true };
});

/**
 * Generates a short-lived (5-minute), single-use QR pairing token for linked devices.
 * Permanent credentials or refresh tokens are never embedded in QR payloads.
 */
exports.createDeviceLinkToken = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentication required.");
  }
  const uid = request.auth.uid;
  const primaryDevicePublicKey = String(request.data?.primaryDevicePublicKey || "");
  if (!primaryDevicePublicKey) {
    throw new HttpsError("invalid-argument", "primaryDevicePublicKey is required.");
  }

  const activeDevicesSnap = await db
    .collection("linkedDevices")
    .where("ownerUid", "==", uid)
    .where("active", "==", true)
    .get();
  if (activeDevicesSnap.size >= 2) {
    throw new HttpsError("failed-precondition", "Maximum of 2 linked devices already reached.");
  }

  const tokenId = `tok_${crypto.randomBytes(16).toString("hex")}`;
  const expiresAt = Timestamp.fromMillis(Date.now() + 5 * 60 * 1000);
  await db.collection("deviceLinkTokens").doc(tokenId).set({
    ownerUid: uid,
    tokenId,
    primaryDevicePublicKey,
    used: false,
    expiresAt,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  });

  return { tokenId, expiresAtMillis: expiresAt.toMillis() };
});

/**
 * Atomically redeems a single-use QR pairing token and links up to 2 secondary devices.
 */
exports.redeemDeviceLinkToken = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentication required.");
  }
  const uid = request.auth.uid;
  const { tokenId, deviceId, deviceName, devicePlatform, devicePublicKey } = request.data || {};
  if (!tokenId || !deviceId || !deviceName || !devicePlatform || !devicePublicKey) {
    throw new HttpsError("invalid-argument", "All device pairing fields are required.");
  }

  await db.runTransaction(async (tx) => {
    const tokenRef = db.collection("deviceLinkTokens").doc(tokenId);
    const tokenSnap = await tx.get(tokenRef);
    if (!tokenSnap.exists || tokenSnap.data().ownerUid !== uid) {
      throw new HttpsError("not-found", "Invalid pairing token.");
    }
    const tokenData = tokenSnap.data();
    if (tokenData.used) {
      throw new HttpsError("failed-precondition", "Pairing token was already used.");
    }
    if (tokenData.expiresAt.toMillis() < Date.now()) {
      throw new HttpsError("deadline-exceeded", "Pairing token expired.");
    }

    const activeSnap = await tx.get(
      db.collection("linkedDevices").where("ownerUid", "==", uid).where("active", "==", true)
    );
    if (activeSnap.size >= 2) {
      throw new HttpsError("failed-precondition", "Maximum 2 linked devices allowed.");
    }

    tx.update(tokenRef, {
      used: true,
      redeemedByDeviceId: deviceId,
      updatedAt: FieldValue.serverTimestamp(),
    });

    tx.set(db.collection("linkedDevices").doc(deviceId), {
      ownerUid: uid,
      deviceName,
      devicePlatform,
      devicePublicKey,
      active: true,
      lastSyncedAt: FieldValue.serverTimestamp(),
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });

    tx.set(db.collection("letterbox").doc(), {
      recipientUid: uid,
      category: "SECURITY_ALERT",
      title: "New Linked Device Added",
      body: `${deviceName} (${devicePlatform}) was linked to your Friends Meet account.`,
      read: false,
      trashed: false,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
  });

  return { linked: true };
});
