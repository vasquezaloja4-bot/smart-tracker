const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_vasquez";
const ADMIN_EMAIL = "vasquezaloja4@gmail.com";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

function adminCtx() {
  return testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  });
}

function aliceCtx() {
  return testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  });
}

function bobCtx() {
  return testEnv.authenticatedContext(BOB_UID, {
    email: "bob@example.com",
    email_verified: true,
  });
}

test("Unauthenticated user: denied across all collections", async () => {
  const db = testEnv.unauthenticatedContext().firestore();
  await assertFails(db.collection("users").doc(ALICE_UID).get());
  await assertFails(db.collection("trackingRecords").get());
  await assertFails(db.collection("trackingCategories").get());
  await assertFails(db.collection("activityLogs").get());
  await assertFails(db.collection("systemSettings").doc("global").get());
});

test("Users collection: RBAC anti-escalation, PII isolation, and bootstrapped admin", async () => {
  const now = new Date(Date.now() - 2000);
  const aliceDb = aliceCtx().firestore();
  const bobDb = bobCtx().firestore();
  const adminDb = adminCtx().firestore();

  // Regular user cannot self-register as ADMIN
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      role: "ADMIN",
      active: true,
      permissions: ["CREATE_RECORD"],
      notifyOnUpdate: true,
      notifyOverdue: true,
      themeMode: "SYSTEM",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Regular user can self-register as USER
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      role: "USER",
      active: true,
      permissions: ["CREATE_RECORD", "UPDATE_OWN_RECORD", "VIEW_REPORTS"],
      notifyOnUpdate: true,
      notifyOverdue: true,
      themeMode: "SYSTEM",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Ghost field rejected
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      email: "bob@example.com",
      role: "USER",
      active: true,
      permissions: ["CREATE_RECORD"],
      notifyOnUpdate: true,
      notifyOverdue: true,
      themeMode: "SYSTEM",
      isVerified: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Cross-user PII read & list denied for regular user
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());

  // Regular user cannot self-promote role on update
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "ADMIN",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Regular user can update own profile/preferences
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice M.",
      themeMode: "DARK",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Bootstrapped admin can register as ADMIN, list users, and update another user's role
  await assertSucceeds(
    adminDb.collection("users").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      displayName: "Admin",
      email: ADMIN_EMAIL,
      role: "ADMIN",
      active: true,
      permissions: ["ALL"],
      notifyOnUpdate: true,
      notifyOverdue: true,
      themeMode: "LIGHT",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(adminDb.collection("users").get());
  await assertSucceeds(
    adminDb.collection("users").doc(ALICE_UID).update({
      active: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Unverified admin email spoof rejected
  const spoofDb = testEnv.authenticatedContext("spoof_1", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();
  await assertFails(spoofDb.collection("users").get());
});

test("TrackingRecords, Updates, Attachments & Categories: CRUD, consistency, terminal lock, query alignment", async () => {
  const now = new Date(Date.now() - 5000);
  const aliceDb = aliceCtx().firestore();
  const bobDb = bobCtx().firestore();
  const adminDb = adminCtx().firestore();

  // Seed category via admin (shared)
  await assertSucceeds(
    adminDb.collection("trackingCategories").doc("cat_ops").set({
      userId: ADMIN_UID,
      name: "Operations",
      colorHex: "#0F294A",
      description: "Core operations",
      active: true,
      shared: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice cannot create record referencing non-existent category
  await assertFails(
    aliceDb.collection("trackingRecords").doc("rec_bad").set({
      trackingCode: "TRK-0001",
      userId: ALICE_UID,
      ownerName: "Alice",
      assigneeId: BOB_UID,
      assigneeName: "Bob",
      title: "Inspect Shipment",
      description: "Verify intake",
      categoryId: "nonexistent",
      categoryName: "Operations",
      priority: "HIGH",
      status: "PENDING",
      progressPercent: 0,
      startDateMillis: Date.now() - 10000,
      dueDateMillis: Date.now() + 86400000,
      sourceType: "MANUAL",
      notesCount: 0,
      attachmentsCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice creates valid record assigned to Bob
  await assertSucceeds(
    aliceDb.collection("trackingRecords").doc("rec_1").set({
      trackingCode: "TRK-0001",
      userId: ALICE_UID,
      ownerName: "Alice",
      assigneeId: BOB_UID,
      assigneeName: "Bob",
      title: "Inspect Shipment",
      description: "Verify intake",
      categoryId: "cat_ops",
      categoryName: "Operations",
      priority: "HIGH",
      status: "PENDING",
      progressPercent: 0,
      startDateMillis: Date.now() - 10000,
      dueDateMillis: Date.now() + 86400000,
      sourceType: "MANUAL",
      notesCount: 0,
      attachmentsCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Future timestamp rejected
  await assertFails(
    aliceDb.collection("trackingRecords").doc("rec_future").set({
      trackingCode: "TRK-0002",
      userId: ALICE_UID,
      ownerName: "Alice",
      assigneeId: ALICE_UID,
      assigneeName: "Alice",
      title: "Future Test",
      description: "Future",
      categoryId: "cat_ops",
      categoryName: "Operations",
      priority: "LOW",
      status: "PENDING",
      progressPercent: 0,
      startDateMillis: Date.now() - 10000,
      dueDateMillis: Date.now() + 86400000,
      sourceType: "MANUAL",
      notesCount: 0,
      attachmentsCount: 0,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Query alignment: unfiltered fails for Alice, filtered succeeds
  await assertFails(aliceDb.collection("trackingRecords").get());
  await assertSucceeds(aliceDb.collection("trackingRecords").where("userId", "==", ALICE_UID).get());
  await assertSucceeds(bobDb.collection("trackingRecords").where("assigneeId", "==", BOB_UID).get());
  await assertSucceeds(adminDb.collection("trackingRecords").get());

  // Bob (assignee) can update status/progress, CANNOT change title
  await assertFails(
    bobDb.collection("trackingRecords").doc("rec_1").update({
      title: "Hijacked Title",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    bobDb.collection("trackingRecords").doc("rec_1").update({
      status: "IN_PROGRESS",
      progressPercent: 50,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Bob adds an update log & attachment
  await assertSucceeds(
    bobDb.collection("trackingRecords").doc("rec_1").collection("updates").doc("upd_1").set({
      recordId: "rec_1",
      userId: BOB_UID,
      recordOwnerId: ALICE_UID,
      assigneeId: BOB_UID,
      authorName: "Bob",
      previousStatus: "PENDING",
      newStatus: "IN_PROGRESS",
      progressPercent: 50,
      note: "Started inspection",
      createdAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    bobDb.collection("trackingRecords").doc("rec_1").collection("attachments").doc("att_1").set({
      recordId: "rec_1",
      userId: BOB_UID,
      recordOwnerId: ALICE_UID,
      assigneeId: BOB_UID,
      fileName: "receipt.jpg",
      mimeType: "image/jpeg",
      sizeBytes: 1024,
      localUriString: "content://media/picker/1",
      uploadedByName: "Bob",
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Terminal state lock: Alice marks COMPLETED, then cannot mutate anymore, while Admin can
  await assertSucceeds(
    aliceDb.collection("trackingRecords").doc("rec_1").update({
      status: "COMPLETED",
      progressPercent: 100,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("trackingRecords").doc("rec_1").update({
      status: "IN_PROGRESS",
      progressPercent: 80,
      updatedAt: new Date(Date.now() - 200),
    })
  );
  await assertSucceeds(
    adminDb.collection("trackingRecords").doc("rec_1").update({
      status: "IN_PROGRESS",
      progressPercent: 90,
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Notifications, ActivityLogs, SystemSettings: isolation, immutability, admin-only config", async () => {
  const now = new Date(Date.now() - 2000);
  const aliceDb = aliceCtx().firestore();
  const bobDb = bobCtx().firestore();
  const adminDb = adminCtx().firestore();

  // Notifications
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("n_1").set({
      userId: ALICE_UID,
      recordId: "rec_1",
      trackingCode: "TRK-0001",
      title: "Record Updated",
      message: "Status moved to In Progress",
      type: "STATUS_UPDATE",
      read: false,
      createdAt: now,
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("notifications").get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("n_1").update({
      read: true,
    })
  );

  // ActivityLogs: immutable, user-scoped vs admin
  await assertSucceeds(
    aliceDb.collection("activityLogs").doc("log_1").set({
      userId: ALICE_UID,
      actorName: "Alice",
      actorEmail: "alice@example.com",
      action: "RECORD_CREATED",
      targetType: "RECORD",
      targetId: "rec_1",
      details: "Created TRK-0001",
      createdAt: now,
    })
  );
  await assertFails(aliceDb.collection("activityLogs").doc("log_1").update({ details: "tampered" }));
  await assertFails(aliceDb.collection("activityLogs").doc("log_1").delete());
  await assertFails(bobDb.collection("activityLogs").where("userId", "==", ALICE_UID).get());
  await assertSucceeds(aliceDb.collection("activityLogs").where("userId", "==", ALICE_UID).get());
  await assertSucceeds(adminDb.collection("activityLogs").get());

  // SystemSettings: admin write only, shared read
  await assertFails(
    aliceDb.collection("systemSettings").doc("global").set({
      shared: true,
      autoMarkOverdue: true,
      deadlineWarningHours: 24,
      defaultPriority: "MEDIUM",
      allowUserRegistration: true,
      externalTrackerStatus: "NOT_CONFIGURED",
      updatedBy: "Alice",
      updatedAt: now,
    })
  );
  await assertSucceeds(
    adminDb.collection("systemSettings").doc("global").set({
      shared: true,
      autoMarkOverdue: true,
      deadlineWarningHours: 24,
      defaultPriority: "MEDIUM",
      allowUserRegistration: true,
      externalTrackerStatus: "NOT_CONFIGURED",
      updatedBy: "Admin",
      updatedAt: now,
    })
  );
  await assertSucceeds(aliceDb.collection("systemSettings").where("shared", "==", true).get());
});
