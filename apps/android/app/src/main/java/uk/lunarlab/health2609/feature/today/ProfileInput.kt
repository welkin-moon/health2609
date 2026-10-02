package uk.lunarlab.health2609.feature.today

import uk.lunarlab.health2609.core.storage.UserProfile

internal fun profileFromInputs(
    age: String,
    gender: String,
    height: String,
    weight: String
): UserProfile? {
    val ageValue = age.toIntOrNull()?.takeIf { it in 6..25 } ?: return null
    val heightValue = height.toDoubleOrNull()?.takeIf { it.isFinite() && it in 80.0..230.0 } ?: return null
    val weightValue = weight.toDoubleOrNull()?.takeIf { it.isFinite() && it in 20.0..200.0 } ?: return null
    if (gender !in setOf("male", "female", "neutral")) return null
    return UserProfile(ageValue, gender, heightValue, weightValue)
}
