import fs from "node:fs";
import path from "node:path";

const root = path.resolve(import.meta.dirname, "..");
const platform = process.argv[2] ?? "all";

function read(relative) {
  return fs.readFileSync(path.join(root, relative), "utf8");
}

function exists(relative) {
  if (!fs.existsSync(path.join(root, relative))) {
    throw new Error(`missing required file: ${relative}`);
  }
}

function requireText(relative, needles) {
  const text = read(relative);
  for (const needle of needles) {
    if (!text.includes(needle)) {
      throw new Error(`${relative} is missing contract token: ${needle}`);
    }
  }
}

const commonRoutes = [
  "v1/today/menu",
  "v1/today/summary",
  "v1/school/day-windows",
  "v1/meals/consumption",
  "v1/activity/outside-school",
  "v1/activity/manual",
  "v1/preferences/energy-reference",
  "v1/home-meals/analyze",
  "v1/home-meals"
];

const syncRoutes = [
  "v1/sync/auth/challenge",
  "v1/sync/auth/register",
  "v1/sync/auth/login",
  "v1/sync/auth/recovery",
  "v1/sync/auth/reset-password",
  "v1/sync/auth/change-password",
  "v1/sync/keys",
  "v1/sync/keys/rotate",
  "v1/sync/push",
  "v1/sync/pull",
  "v1/sync/devices",
  "v1/sync/devices/:deviceId/revoke"
];

function verifyBackend() {
  requireText("services/api/src/index.ts", commonRoutes.map((r) => `/${r}`));
  requireText("packages/contracts/src/api.ts", commonRoutes.map((r) => `/${r}`));
  requireText("services/api/src/index.ts", syncRoutes.map((r) => `/${r}`));
  requireText("packages/contracts/src/api.ts", syncRoutes.map((r) => `/${r}`));
  requireText("apps/admin-web/src/api.ts", [
    "/v1/admin/stats/overview",
    "/v1/admin/classes",
    "/v1/admin/menus",
    "/v1/admin/pe/timetable",
    "/v1/admin/pe/sessions"
  ]);
}

function verifyAndroid() {
  exists("apps/android/app/src/main/java/uk/lunarlab/health2609/ui/StudentAppShell.kt");
  requireText(
    "apps/android/app/src/main/java/uk/lunarlab/health2609/core/network/HealthApi.kt",
    commonRoutes
  );
  requireText(
    "apps/android/app/src/main/java/uk/lunarlab/health2609/core/sync/SyncRepository.kt",
    syncRoutes.filter((route) => !route.includes(":deviceId") && route !== "v1/sync/keys")
  );
}

function verifyIos() {
  exists("apps/ios/Sources/Health2609/Views/StudentAppShell.swift");
  exists("apps/ios/Sources/Health2609/Health/HealthKitManager.swift");
  requireText("apps/ios/Sources/Health2609/Network/HealthApi.swift", commonRoutes);
}

function verifyArk(rootDir) {
  exists(`${rootDir}/entry/src/main/ets/view/AdaptiveStudentAppShell.ets`);
  exists(`${rootDir}/entry/src/main/ets/model/ApiModels.ets`);
  requireText(`${rootDir}/entry/src/main/ets/service/HealthApi.ets`, commonRoutes);
  requireText(`${rootDir}/entry/src/main/module.json5`, ["default", "tablet", "2in1"]);
}

verifyBackend();

if (platform === "all" || platform === "android") verifyAndroid();
if (platform === "all" || platform === "ios") verifyIos();
if (platform === "all" || platform === "harmony") verifyArk("apps/harmony");
if (platform === "all" || platform === "openharmony") verifyArk("apps/openharmony");

console.log(`multiplatform contract verification passed: ${platform}`);
