# Initial data model

The concrete D1 migration may evolve, but the current API/domain contract follows these entities.

## School

- id
- name
- timezone
- dailyActivityTargetMinutes
- createdAt

## ClassGroup

- id
- schoolId
- name

## StudentMembership

- id
- schoolId
- classGroupId
- participantId
- createdAt

`participantId` is app-facing and does not need to be a real student name.

## SchoolTimeWindow

Planned for the Health Connect slice:

- schoolId
- weekday/date override
- startTime
- endTime

Android will use this only to remove in-school intervals before uploading outside-school activity aggregates.

## Menu

- id
- schoolId
- date
- mealSlot

## Dish

- id
- menuId
- name
- standardServingGrams
- nutritionPerServingJson

The nutrition object currently supports energy, protein, fat, carbohydrate, fiber, sodium, sugar and saturated fat.

## MealConsumption

- id
- studentMembershipId
- dishId
- servingMultiplier
- consumedGrams
- consumedAt

Students can use quick serving fractions or type the actual consumed grams. When a standard serving exists, the server normalizes grams back to a serving multiplier for deterministic nutrition math.

## PETimetable

- id
- schoolId
- classGroupId
- weekday
- startTime
- endTime

## PESession

- id
- timetableId
- date
- actualActivityMinutes
- recordedByAdminId
- updatedAt

School PE comes from the administrator-maintained timetable plus the actual activity minutes recorded for that lesson.

## OutsideSchoolActivityDaily

- id
- studentMembershipId
- date
- exerciseMinutes
- steps
- activeEnergyKcal

Only the daily aggregate needed by the product is stored. Raw GPS tracks are not required.

## ManualActivitySession

- id
- studentMembershipId
- date
- activityType
- startTime optional
- durationMinutes
- intensity: light / moderate / vigorous
- estimatedActiveEnergyKcal optional
- createdAt

Manual activity is a student-provided fallback/correction source. When phone and manual duration both exist, the current demo uses the larger outside-school duration rather than naively summing them.

## StudentPreferences

- studentMembershipId
- dailyEnergyReferenceKcal optional
- updatedAt

The reference is private to the student's own experience. The daily view reports the difference between confirmed intake and this reference instead of presenting it as a weight-loss target.

## HomeMeal

- id
- studentMembershipId
- date
- mealSlot
- confirmedItemsJson
- createdAt

The original image is **not persisted** in D1/R2. The Android request is forwarded by the Worker through the configured Cloudflare Tunnel to AGY for structured analysis; only the student's confirmed structured result is stored.

## Administrator

The demo currently uses a lightweight role shell; production auth is intentionally left as a later hardening step.

## Derived daily summary

Do not store every summary field as mutable truth unless necessary.

Compute from:

- school meal consumption
- confirmed home meals
- PE sessions for the student's class
- Health Connect outside-school aggregate
- manual activity fallback/correction
- the student's optional energy reference

The summary exposes macro energy composition, fiber/sodium totals, activity intensity breakdown, activity-target progress and the energy-reference gap.
