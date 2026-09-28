# Initial data model

The concrete D1 migration may evolve, but the first API/domain contract follows these entities.

## School

- id
- name
- joinCodeHash / join-code metadata
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

- schoolId
- weekday/date override
- startTime
- endTime

Used by Android to exclude school-time activity from Health Connect-derived outside-school totals.

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

## MealConsumption

- id
- studentMembershipId
- dishId
- servingMultiplier
- consumedAt

## PETimetable

- id
- schoolId
- classGroupId
- weekday/date
- startTime
- endTime

## PESession

- id
- timetableId
- date
- actualActivityMinutes
- recordedByAdminId
- updatedAt

## OutsideSchoolActivityDaily

- id
- studentMembershipId
- date
- exerciseMinutes
- steps
- activeEnergyKcal
- sourceMetadataJson

Only the needed daily aggregate is stored.

## HomeMeal

- id
- studentMembershipId
- date
- mealSlot
- confirmedItemsJson
- imageObjectKey nullable
- createdAt

## Administrator

- id
- schoolId
- credential fields
- role
- createdAt

## AuditEvent

- id
- schoolId
- administratorId
- action
- targetType
- targetId
- metadataJson
- createdAt

## Derived daily summary

Do not store every summary field as mutable truth unless necessary.

Compute from:

- school meal consumption
- confirmed home meals
- PE sessions for the student's class
- outside-school daily aggregate
