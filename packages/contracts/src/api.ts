import {
  adminPeSessionSchema,
  adminPeTimetableSchema,
  adminSchoolDayWindowSchema,
  adminUpsertMenuSchema,
  confirmedHomeMealSchema,
  energyReferenceSchema,
  manualActivitySchema,
  outsideActivitySchema,
  recordMealSchema,
  schoolActivityOverrideSchema
} from "./index";

export const API_CONTRACT_VERSION = 1 as const;

export const studentApiContract = {
  schools: { method: "GET", path: "/v1/student/schools" },
  schoolDayWindows: { method: "GET", path: "/v1/school/day-windows" },
  schoolActivity: { method: "GET", path: "/v1/today/school-activity" },
  setSchoolActivitySource: {
    method: "PUT",
    path: "/v1/activity/school-source",
    body: schoolActivityOverrideSchema
  },
  todayMenu: { method: "GET", path: "/v1/today/menu" },
  todaySummary: { method: "GET", path: "/v1/today/summary" },
  saveMeal: {
    method: "POST",
    path: "/v1/meals/consumption",
    body: recordMealSchema
  },
  saveOutsideActivity: {
    method: "POST",
    path: "/v1/activity/outside-school",
    body: outsideActivitySchema
  },
  saveManualActivity: {
    method: "POST",
    path: "/v1/activity/manual",
    body: manualActivitySchema
  },
  saveEnergyReference: {
    method: "PUT",
    path: "/v1/preferences/energy-reference",
    body: energyReferenceSchema
  },
  analyzeHomeMeal: { method: "POST", path: "/v1/home-meals/analyze" },
  saveHomeMeal: {
    method: "POST",
    path: "/v1/home-meals",
    body: confirmedHomeMealSchema
  }
} as const;

export const adminApiContract = {
  classes: { method: "GET", path: "/v1/admin/classes" },
  schoolDayWindows: { method: "GET", path: "/v1/admin/school/day-windows" },
  saveSchoolDayWindow: {
    method: "PUT",
    path: "/v1/admin/school/day-windows",
    body: adminSchoolDayWindowSchema
  },
  menus: { method: "GET", path: "/v1/admin/menus" },
  saveMenu: {
    method: "PUT",
    path: "/v1/admin/menus",
    body: adminUpsertMenuSchema
  },
  peTimetable: { method: "GET", path: "/v1/admin/pe/timetable" },
  savePeTimetable: {
    method: "PUT",
    path: "/v1/admin/pe/timetable",
    body: adminPeTimetableSchema
  },
  peSessions: { method: "GET", path: "/v1/admin/pe/sessions" },
  savePeSession: {
    method: "PUT",
    path: "/v1/admin/pe/session",
    body: adminPeSessionSchema
  },
  overview: { method: "GET", path: "/v1/admin/stats/overview" }
} as const;

export const syncApiContract = {
  register: { method: "POST", path: "/v1/sync/auth/register" },
  login: { method: "POST", path: "/v1/sync/auth/login" },
  push: { method: "POST", path: "/v1/sync/push" },
  pull: { method: "GET", path: "/v1/sync/pull" },
  devices: { method: "GET", path: "/v1/sync/devices" }
} as const;

export type ApiContract =
  | (typeof studentApiContract)[keyof typeof studentApiContract]
  | (typeof adminApiContract)[keyof typeof adminApiContract]
  | (typeof syncApiContract)[keyof typeof syncApiContract];
