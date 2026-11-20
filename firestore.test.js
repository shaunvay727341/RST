const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_101";
const BOB_UID = "bob_uid_202";
const CHARLIE_UID = "charlie_uid_303";
const ALICE_FUID = "48291037";
const BOB_FUID = "73910482";
const CHAT_AB = `${ALICE_UID}_${BOB_UID}`;

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 2000) {
  return new Date(Date.now() - msOffset);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("userIds").doc(ALICE_FUID).get());
  await assertFails(unauthDb.collection("friends").where("ownerUid", "==", ALICE_UID).get());
  await assertFails(unauthDb.collection("chats").where("participantUids", "array-contains", ALICE_UID).get());
  await assertFails(unauthDb.collection("letterbox").where("recipientUid", "==", ALICE_UID).get());
});

test("users & userIds: PII isolation, valid registration, ghost field rejection, and anti-scraping", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      friendsMeetUid: ALICE_FUID,
      fullName: "Alice Rahman",
      dateOfBirth: "1998-05-14",
      gender: "FEMALE",
      email: "alice@example.com",
      online: true,
      isUnder13: false,
      guardianVerified: true,
      accountStatus: "ACTIVE",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob cannot read Alice's private PII document
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());

  // Shadow update with ghost field must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      accountStatus: "SUSPENDED",
      updatedAt: pastDate(1000),
    })
  );

  // Future timestamp must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      bio: "Hello",
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Atomic 8-digit userIds registry entry (no PII)
  await assertSucceeds(
    aliceDb.collection("userIds").doc(ALICE_FUID).set({
      uid: ALICE_UID,
      friendsMeetUid: ALICE_FUID,
      displayName: "Alice Rahman",
      online: true,
      accountStatus: "ACTIVE",
      createdAt: now,
    })
  );

  // Bob can look up Alice's 8-digit UID directly via get, but cannot list/scrape userIds
  await assertSucceeds(bobDb.collection("userIds").doc(ALICE_FUID).get());
  await assertFails(bobDb.collection("userIds").get());
});

test("friends & friendTags: owner isolation and filtered list queries", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate(5000);
  const connId = `${ALICE_UID}_${BOB_UID}`;

  await assertSucceeds(
    aliceDb.collection("friends").doc(connId).set({
      ownerUid: ALICE_UID,
      friendUid: BOB_UID,
      friendMeetUid: BOB_FUID,
      friendName: "Bob Karim",
      friendOnline: true,
      blocked: false,
      addedVia: "QR_CODE",
      chatId: CHAT_AB,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Filtered query succeeds, unfiltered fails
  await assertSucceeds(aliceDb.collection("friends").where("ownerUid", "==", ALICE_UID).get());
  await assertFails(aliceDb.collection("friends").get());
  await assertFails(bobDb.collection("friends").doc(connId).get());

  // Single private friendTag per friend
  await assertSucceeds(
    aliceDb.collection("friendTags").doc(connId).set({
      ownerUid: ALICE_UID,
      friendUid: BOB_UID,
      category: "Office",
      label: "Design Lead",
      colorHex: "#0A8D87",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(bobDb.collection("friendTags").doc(connId).get());
});

test("chats & messages: 2-participant 1-to-1 isolation and non-participant denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("chats").doc(CHAT_AB).set({
      participantUids: [ALICE_UID, BOB_UID],
      disappearingDurationSeconds: 86400,
      typingUids: [],
      e2eeSessionVersion: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("chats").doc(CHAT_AB).collection("messages").doc("msg_1").set({
      chatId: CHAT_AB,
      senderUid: ALICE_UID,
      recipientUid: BOB_UID,
      participantUids: [ALICE_UID, BOB_UID],
      messageType: "TEXT",
      ciphertext: "ZW5jcnlwdGVkX2hlbGxv",
      iv: "MTIzNDU2Nzg5MDEy",
      readByUids: [ALICE_UID],
      deleteForMeUids: [],
      deletedForEveryone: false,
      edited: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob can list messages in the shared chat; Charlie cannot
  await assertSucceeds(
    bobDb
      .collection("chats")
      .doc(CHAT_AB)
      .collection("messages")
      .where("participantUids", "array-contains", BOB_UID)
      .get()
  );
  // Charlie cannot read Alice/Bob's message, run unfiltered list, or query Alice's messages
  await assertFails(
    charlieDb
      .collection("chats")
      .doc(CHAT_AB)
      .collection("messages")
      .doc("msg_1")
      .get()
  );
  await assertFails(
    charlieDb
      .collection("chats")
      .doc(CHAT_AB)
      .collection("messages")
      .get()
  );
  await assertFails(
    charlieDb
      .collection("chats")
      .doc(CHAT_AB)
      .collection("messages")
      .where("participantUids", "array-contains", ALICE_UID)
      .get()
  );
});

test("calls: signaling lifecycle and terminal state locking", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").set({
      callerUid: ALICE_UID,
      calleeUid: BOB_UID,
      participantUids: [ALICE_UID, BOB_UID],
      callType: "AUDIO",
      status: "RINGING",
      offerSdp: "v=0...",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    bobDb.collection("calls").doc("call_1").update({
      status: "ENDED",
      durationSeconds: 42,
      endedAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );

  // Terminal state lock: cannot reopen ENDED call
  await assertFails(
    aliceDb.collection("calls").doc("call_1").update({
      status: "ACCEPTED",
      updatedAt: pastDate(500),
    })
  );
});

test("letterbox: blocks client creation, allows owner read and trash actions only", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate(5000);

  // Ordinary users cannot create letters
  await assertFails(
    aliceDb.collection("letterbox").doc("letter_1").set({
      recipientUid: ALICE_UID,
      category: "SYSTEM_NOTICE",
      title: "Fake Notice",
      body: "Spoofed",
      read: false,
      trashed: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Seed system letter with admin context
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("letterbox").doc("letter_1").set({
      recipientUid: ALICE_UID,
      category: "SECURITY_ALERT",
      title: "New device sign-in",
      body: "Your account signed in on a new device.",
      read: false,
      trashed: false,
      createdAt: now,
      updatedAt: now,
    });
  });

  await assertSucceeds(aliceDb.collection("letterbox").where("recipientUid", "==", ALICE_UID).get());
  await assertFails(bobDb.collection("letterbox").doc("letter_1").get());

  // Recipient can mark read & move to trash, but cannot alter title/body
  await assertSucceeds(
    aliceDb.collection("letterbox").doc("letter_1").update({
      read: true,
      trashed: true,
      trashedAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    aliceDb.collection("letterbox").doc("letter_1").update({
      title: "Tampered",
      updatedAt: pastDate(500),
    })
  );
});

test("linkedDevices, privacySettings, notifications, backupMetadata, userSettings: strict ownership", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("linkedDevices").doc("dev_1").set({
      ownerUid: ALICE_UID,
      deviceName: "Pixel Tablet",
      devicePlatform: "Android",
      devicePublicKey: "pk_base64_123",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(bobDb.collection("linkedDevices").doc("dev_1").get());

  await assertSucceeds(
    aliceDb.collection("privacySettings").doc(ALICE_UID).set({
      uid: ALICE_UID,
      profileVisibility: "FRIENDS",
      photoVisibility: "EVERYONE",
      dobVisibility: "NOBODY",
      onlineStatusVisibility: "FRIENDS",
      lastSeenVisibility: "FRIENDS",
      bioVisibility: "EVERYONE",
      friendListVisibility: "FRIENDS",
      phoneVisibility: "NOBODY",
      emailVisibility: "NOBODY",
      updatedAt: now,
    })
  );
  await assertFails(bobDb.collection("privacySettings").doc(ALICE_UID).get());

  await assertSucceeds(
    aliceDb.collection("notifications").doc("notif_1").set({
      recipientUid: BOB_UID,
      senderUid: ALICE_UID,
      type: "REACTION",
      title: "New Reaction",
      body: "Alice reacted with ❤️",
      read: false,
      createdAt: now,
    })
  );
  await assertSucceeds(bobDb.collection("notifications").where("recipientUid", "==", BOB_UID).get());
  await assertFails(aliceDb.collection("notifications").doc("notif_1").get());

  await assertSucceeds(
    aliceDb.collection("backupMetadata").doc(ALICE_UID).set({
      ownerUid: ALICE_UID,
      storagePath: `backups/${ALICE_UID}/latest.enc`,
      keyChecksumSha256: "a".repeat(64),
      encryptionAlgorithm: "AES-256-GCM",
      messageCount: 120,
      sizeBytes: 40960,
      lastBackupAt: now,
      updatedAt: now,
    })
  );
  await assertFails(bobDb.collection("backupMetadata").doc(ALICE_UID).get());

  await assertSucceeds(
    aliceDb.collection("userSettings").doc(ALICE_UID).set({
      uid: ALICE_UID,
      themeMode: "DARK",
      quickMessages: ["On my way!", "Call me when free"],
      customTagCategories: ["Gym", "College"],
      translationAutoDetect: true,
      updatedAt: now,
    })
  );
  await assertFails(bobDb.collection("userSettings").doc(ALICE_UID).get());
});

test("deviceLinkTokens & message 15-min edit window: single-use redemption and time lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate(5000);
  const expiresAt = new Date(Date.now() + 300000);

  await assertSucceeds(
    aliceDb.collection("deviceLinkTokens").doc("tok_abc123").set({
      ownerUid: ALICE_UID,
      tokenId: "tok_abc123",
      primaryDevicePublicKey: "pk_primary_123",
      used: false,
      expiresAt,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Another user cannot read or redeem Alice's pairing token
  await assertFails(bobDb.collection("deviceLinkTokens").doc("tok_abc123").get());

  // Alice's linked device redeems token once
  await assertSucceeds(
    aliceDb.collection("deviceLinkTokens").doc("tok_abc123").update({
      used: true,
      redeemedByDeviceId: "dev_tablet_1",
      updatedAt: pastDate(1000),
    })
  );

  // Re-redeeming a used token must fail
  await assertFails(
    aliceDb.collection("deviceLinkTokens").doc("tok_abc123").update({
      used: true,
      redeemedByDeviceId: "dev_tablet_2",
      updatedAt: pastDate(500),
    })
  );

  // Seed an old message (>15 min) and verify edit fails
  const twentyMinAgo = new Date(Date.now() - 20 * 60 * 1000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("chats").doc(CHAT_AB).collection("messages").doc("old_msg").set({
      chatId: CHAT_AB,
      senderUid: ALICE_UID,
      recipientUid: BOB_UID,
      participantUids: [ALICE_UID, BOB_UID],
      messageType: "TEXT",
      ciphertext: "b2xkX2NpcGhlcg==",
      iv: "MTIzNDU2Nzg5MDEy",
      readByUids: [ALICE_UID],
      deleteForMeUids: [],
      deletedForEveryone: false,
      edited: false,
      createdAt: twentyMinAgo,
      updatedAt: twentyMinAgo,
    });
  });

  await assertFails(
    aliceDb.collection("chats").doc(CHAT_AB).collection("messages").doc("old_msg").update({
      ciphertext: "ZWRpdGVk",
      iv: "MTIzNDU2Nzg5MDEy",
      edited: true,
      editedAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );
});
