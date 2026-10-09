package uk.lunarlab.health2609

import org.junit.Test
import org.junit.Assert.*
import com.google.gson.JsonParser
import uk.lunarlab.health2609.core.network.*

class RequestSerializationTest {
    @Test fun clearingReferenceSendsExplicitNullButActivityKeepsOptionalFieldsAbsent() {
        val gson = ApiFactory.requestGson()
        val clear = JsonParser.parseString(gson.toJson(EnergyReferenceRequest(null))).asJsonObject
        assertTrue(clear.has("dailyEnergyReferenceKcal"))
        assertTrue(clear.get("dailyEnergyReferenceKcal").isJsonNull)
        val activity = JsonParser.parseString(gson.toJson(OutsideSchoolActivityRequest("2026-10-05", 30))).asJsonObject
        assertFalse(activity.has("steps"))
        assertFalse(activity.has("activeEnergyKcal"))
    }
}
