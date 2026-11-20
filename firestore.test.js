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

  // Bob can look up Alice's 8-digit UID via get OR list the zero-PII directory for Old Chat
  await assertSucceeds(bobDb.collection("userIds").doc(ALICE_FUID).get());
  await assertSucceeds(bobDb.collection("userIds").get());
  await assertFails(testEnv.unauthenticatedContext().firestore().collection("userIds").get());
});

test("friends & friendTags: owner isolation, non-existent doc pre-check, and filtered list queries", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate(5000);
  const connId = `${ALICE_UID}_${BOB_UID}`;

  // Pre-creation get() on non-existent docs must succeed (not crash on resource == null)
  await assertSucceeds(aliceDb.collection("friends").doc(connId).get());
  await assertSucceeds(aliceDb.collection("chats").doc(CHAT_AB).get());
  await assertSucceeds(aliceDb.collection("friendTags").doc(connId).get());

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

test("oldChat: enforces initiator message limit until recipient sets limit or replies to unlock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("chats").doc(CHAT_AB).set({
      participantUids: [ALICE_UID, BOB_UID],
      disappearingDurationSeconds: 0,
      typingUids: [],
      e2eeSessionVersion: 1,
      chatMode: "OLD_CHAT",
      oldChatInitiatorUid: ALICE_UID,
      oldChatMessageLimit: 2,
      oldChatSentCount: 0,
      oldChatUnlocked: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice cannot self-unlock her own Old Chat
  await assertFails(
    aliceDb.collection("chats").doc(CHAT_AB).update({
      oldChatUnlocked: true,
      updatedAt: pastDate(1000),
    })
  );

  // Alice sends 2 messages up to limit
  for (let i = 1; i <= 2; i++) {
    await assertSucceeds(
      aliceDb.collection("chats").doc(CHAT_AB).collection("messages").doc(`om_${i}`).set({
        chatId: CHAT_AB,
        senderUid: ALICE_UID,
        recipientUid: BOB_UID,
        participantUids: [ALICE_UID, BOB_UID],
        messageType: "TEXT",
        ciphertext: "Y2lwaGVy",
        iv: "MTIzNDU2Nzg5MDEy",
        readByUids: [ALICE_UID],
        deleteForMeUids: [],
        deletedForEveryone: false,
        edited: false,
        createdAt: pastDate(2000 - i * 100),
        updatedAt: pastDate(2000 - i * 100),
      })
    );
    await assertSucceeds(
      aliceDb.collection("chats").doc(CHAT_AB).update({
        oldChatSentCount: i,
        updatedAt: pastDate(2000 - i * 100),
      })
    );
  }

  // 3rd message from Alice must fail while locked at limit 2
  await assertFails(
    aliceDb.collection("chats").doc(CHAT_AB).collection("messages").doc("om_3").set({
      chatId: CHAT_AB,
      senderUid: ALICE_UID,
      recipientUid: BOB_UID,
      participantUids: [ALICE_UID, BOB_UID],
      messageType: "TEXT",
      ciphertext: "Y2lwaGVy",
      iv: "MTIzNDU2Nzg5MDEy",
      readByUids: [ALICE_UID],
      deleteForMeUids: [],
      deletedForEveryone: false,
      edited: false,
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );

  // Bob can adjust limit before replying, or reply & unlock unlimited
  await assertSucceeds(
    bobDb.collection("chats").doc(CHAT_AB).collection("messages").doc("om_reply").set({
      chatId: CHAT_AB,
      senderUid: BOB_UID,
      recipientUid: ALICE_UID,
      participantUids: [ALICE_UID, BOB_UID],
      messageType: "TEXT",
      ciphertext: "cmVwbHk=",
      iv: "MTIzNDU2Nzg5MDEy",
      readByUids: [BOB_UID],
      deleteForMeUids: [],
      deletedForEveryone: false,
      edited: false,
      createdAt: pastDate(400),
      updatedAt: pastDate(400),
    })
  );
  await assertSucceeds(
    bobDb.collection("chats").doc(CHAT_AB).update({
      oldChatUnlocked: true,
      updatedAt: pastDate(400),
    })
  );

  // Once unlocked, Alice can send unlimited messages
  await assertSucceeds(
    aliceDb.collection("chats").doc(CHAT_AB).collection("messages").doc("om_3").set({
      chatId: CHAT_AB,
      senderUid: ALICE_UID,
      recipientUid: BOB_UID,
      participantUids: [ALICE_UID, BOB_UID],
      messageType: "TEXT",
      ciphertext: "Y2lwaGVy",
      iv: "MTIzNDU2Nzg5MDEy",
      readByUids: [ALICE_UID],
      deleteForMeUids: [],
      deletedForEveryone: false,
      edited: false,
      createdAt: pastDate(200),
      updatedAt: pastDate(200),
    })
  );
});

test("groups & groupMessages: participant membership isolation and QR join", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("groups").doc("grp_1").set({
      name: "Study Squad",
      description: "Final prep",
      creatorUid: ALICE_UID,
      adminUids: [ALICE_UID],
      participantUids: [ALICE_UID, BOB_UID],
      inviteCode: "SQUAD99",
      allowQrJoin: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    bobDb.collection("groups").doc("grp_1").collection("groupMessages").doc("gm_1").set({
      groupId: "grp_1",
      senderUid: BOB_UID,
      senderName: "Bob",
      text: "Hello group!",
      createdAt: pastDate(1000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("groups").doc("grp_1").collection("groupMessages").get()
  );
  await assertFails(
    charlieDb.collection("groups").doc("grp_1").collection("groupMessages").get()
  );

  // Charlie cannot alter admins or remove existing members while joining
  await assertFails(
    charlieDb.collection("groups").doc("grp_1").update({
      participantUids: [ALICE_UID, CHARLIE_UID],
      updatedAt: pastDate(900),
    })
  );
  await assertFails(
    charlieDb.collection("groups").doc("grp_1").update({
      adminUids: [ALICE_UID, CHARLIE_UID],
      participantUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      updatedAt: pastDate(900),
    })
  );

  // Charlie joins cleanly via Group QR scan (adds only himself)
  await assertSucceeds(
    charlieDb.collection("groups").doc("grp_1").update({
      participantUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      updatedAt: pastDate(800),
    })
  );

  // Once joined, Charlie can read messages and send a reply/forwarded message
  await assertSucceeds(
    charlieDb.collection("groups").doc("grp_1").collection("groupMessages").get()
  );
  await assertSucceeds(
    charlieDb.collection("groups").doc("grp_1").collection("groupMessages").doc("gm_2").set({
      groupId: "grp_1",
      senderUid: CHARLIE_UID,
      senderName: "Charlie",
      text: "Glad to join!",
      replyToMessageId: "gm_1",
      replyToSenderName: "Bob",
      replyToText: "Hello group!",
      forwardedCount: 0,
      createdAt: pastDate(700),
    })
  );
});
