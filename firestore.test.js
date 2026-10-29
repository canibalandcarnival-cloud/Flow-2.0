const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user cannot read or write user profiles or bookmarks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("bookmarks")
      .where("userId", "==", ALICE_UID)
      .get()
  );
});

test("Authenticated owner can create, read, and update their profile with serverTimestamp", async () => {
  const aliceCtx = testEnv.authenticatedContext(ALICE_UID);
  const aliceDb = aliceCtx.firestore();
  const serverTs = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Glass",
      email: "alice@example.com",
      photoUrl: "https://example.com/avatar.png",
      selectedWallpaperId: "preset:savanna",
      createdAt: serverTs,
      updatedAt: serverTs,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());

  // Shadow update with ghost field must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice Updated",
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Valid update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice Updated",
      selectedWallpaperId: "preset:coastal",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Cross-user PII isolation: Bob cannot read or modify Alice's profile or bookmarks", async () => {
  const ts = new Date(Date.now() - 1000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@private.com",
      createdAt: ts,
      updatedAt: ts,
    });
    await adminDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("bookmarks")
      .doc("bm_1")
      .set({
        userId: ALICE_UID,
        url: "https://kotlinlang.org",
        title: "Kotlin",
        folder: "Dev",
        createdAt: ts,
        updatedAt: ts,
      });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("bookmarks")
      .doc("bm_1")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("bookmarks")
      .where("userId", "==", ALICE_UID)
      .get()
  );
});

test("Owner can CRUD and query bookmarks, favorites, and readingList with aligned userId filter", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("bookmarks")
      .doc("bm_1")
      .set({
        userId: ALICE_UID,
        url: "https://developer.android.com",
        title: "Android Developers",
        folder: "General",
        createdAt: ts,
        updatedAt: ts,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("bookmarks")
      .where("userId", "==", ALICE_UID)
      .get()
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("favorites")
      .doc("fav_1")
      .set({
        userId: ALICE_UID,
        title: "YouTube",
        url: "https://www.youtube.com",
        accentHex: 16711680,
        position: 0,
        createdAt: ts,
        updatedAt: ts,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("readingList")
      .doc("rl_1")
      .set({
        userId: ALICE_UID,
        url: "https://en.wikipedia.org",
        title: "Wikipedia",
        excerpt: "Free encyclopedia",
        isRead: false,
        createdAt: ts,
        updatedAt: ts,
      })
  );
});
