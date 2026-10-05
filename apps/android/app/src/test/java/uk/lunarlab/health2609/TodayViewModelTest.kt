package uk.lunarlab.health2609

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uk.lunarlab.health2609.feature.today.DishAmount
import uk.lunarlab.health2609.feature.today.HomeMealDraftItem
import uk.lunarlab.health2609.feature.today.TodayRepository
import uk.lunarlab.health2609.feature.today.TodayViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeApi: FakeHealthApi
    private lateinit var repository: TodayRepository
    private lateinit var viewModel: TodayViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeApi = FakeHealthApi()
        repository = TodayRepository(fakeApi)
        viewModel = TodayViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialLoad_populatesDishesAndDefaults() = runTest(testDispatcher) {
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertNotNull(state.menu)
        assertEquals(2, state.menu?.dishes?.size)
        assertEquals(2, state.amounts.size)
        assertEquals(DishAmount(0.0, 0.0), state.amounts["dish-1"])
    }

    @Test
    fun testSetPortion_updatesMultiplierAndCalculatedGrams() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.setPortion("dish-1", 0.5) // standardServingGrams is 150.0 -> 75.0g
        val state = viewModel.uiState.value
        val amount = state.amounts["dish-1"]
        assertNotNull(amount)
        assertEquals(0.5, amount?.servingMultiplier ?: 0.0, 0.001)
        assertEquals(75.0, amount?.consumedGrams ?: 0.0, 0.001)
    }

    @Test
    fun persistedLunchPortionIsRestoredOnFirstLoad() = runTest(testDispatcher) {
        fakeApi.menuToReturn = fakeApi.menuToReturn.copy(dishes = fakeApi.menuToReturn.dishes.map {
            if (it.id == "dish-1") it.copy(savedServingMultiplier = 0.75, savedConsumedGrams = 112.5) else it
        })
        advanceUntilIdle()
        assertEquals(DishAmount(0.75, 112.5), viewModel.uiState.value.amounts["dish-1"])
    }

    @Test
    fun repeatedTapBeforeDispatchSavesManualActivityOnlyOnce() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.saveManualActivity("跑步")
        viewModel.saveManualActivity("跑步")
        advanceUntilIdle()
        assertEquals(1, fakeApi.manualSaveCount)
        assertFalse(viewModel.uiState.value.savingActivity)
    }

    @Test
    fun testSetConsumedGrams_updatesMultiplierAndGrams() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.setConsumedGrams("dish-2", 60.0) // standardServingGrams is 120.0 -> 0.5 multiplier
        val state = viewModel.uiState.value
        val amount = state.amounts["dish-2"]
        assertNotNull(amount)
        assertEquals(0.5, amount?.servingMultiplier ?: 0.0, 0.001)
        assertEquals(60.0, amount?.consumedGrams ?: 0.0, 0.001)
    }

    @Test
    fun lunchSummaryCannotUnlockAnUnfinishedActivitySave() = runTest(testDispatcher) {
        advanceUntilIdle()
        fakeApi.summaryGate = CompletableDeferred()
        viewModel.saveMeal()
        runCurrent()
        fakeApi.manualSaveGate = CompletableDeferred()
        viewModel.saveManualActivity("跑步")
        runCurrent()
        assertTrue(viewModel.uiState.value.savingActivity)
        fakeApi.summaryGate!!.complete(Unit)
        runCurrent()
        assertTrue(viewModel.uiState.value.savingActivity)
        viewModel.saveManualActivity("跑步")
        assertEquals(1, fakeApi.manualSaveCount)
        fakeApi.manualSaveGate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.savingActivity)
    }

    @Test
    fun savedDinnerRestoresAndAddingFoodPreservesItsEarlierItems() = runTest(testDispatcher) {
        fakeApi.savedHomeMeals = uk.lunarlab.health2609.core.network.HomeMealsDto("2026-09-30", listOf(
            uk.lunarlab.health2609.core.network.SavedHomeMealDto("dinner", listOf(
                uk.lunarlab.health2609.core.network.ConfirmedHomeMealItemRequest("炒饭", 180.0,
                    uk.lunarlab.health2609.core.network.NutritionDto(energyKcal = 300.0))
            ))
        ))
        advanceUntilIdle()
        assertEquals("炒饭", viewModel.uiState.value.homeMealDraft.single().name)
        viewModel.addManualHomeMealItem("苹果", 100.0)
        viewModel.setHomeMealSlot("breakfast")
        assertTrue(viewModel.uiState.value.homeMealDraft.isEmpty())
        viewModel.setHomeMealSlot("dinner")
        assertEquals(2, viewModel.uiState.value.homeMealDraft.size)
        viewModel.saveHomeMeal()
        advanceUntilIdle()
        assertEquals(listOf("炒饭", "苹果"), fakeApi.lastSavedHomeMealRequest!!.items.map { it.name })
        assertEquals(300.0, fakeApi.lastSavedHomeMealRequest!!.items.first().nutrition!!.energyKcal!!, 0.001)
    }

    @Test
    fun recreatingScreenWithSameServerPreservesPhotosAndUnsavedFood() = runTest(testDispatcher) {
        viewModel.configureContext("test-server|test-school")
        advanceUntilIdle()
        viewModel.analyzeHomeMeal("fake-image".toByteArray(), "image/jpeg")
        advanceUntilIdle()
        viewModel.setHomeMealName(0, "已编辑的菜名")
        val before = viewModel.uiState.value
        viewModel.configureContext("test-server|test-school")
        advanceUntilIdle()
        assertEquals(before.stagedMealImages, viewModel.uiState.value.stagedMealImages)
        assertEquals(before.homeMealDraft, viewModel.uiState.value.homeMealDraft)
    }

    @Test
    fun testManualActivityControls() = runTest(testDispatcher) {
        viewModel.setManualActivityMinutes(45)
        assertEquals(45, viewModel.uiState.value.manualActivityMinutes)

        // Test clamping (5 to 300 minutes)
        viewModel.setManualActivityMinutes(2)
        assertEquals(5, viewModel.uiState.value.manualActivityMinutes)

        viewModel.setManualActivityMinutes(400)
        assertEquals(300, viewModel.uiState.value.manualActivityMinutes)

        viewModel.setManualActivityType("羽毛球")
        assertEquals("羽毛球", viewModel.uiState.value.manualActivityType)

        viewModel.setManualActivityIntensity("vigorous")
        assertEquals("vigorous", viewModel.uiState.value.manualActivityIntensity)

        // Invalid intensity rejected
        viewModel.setManualActivityIntensity("extreme_invalid")
        assertEquals("vigorous", viewModel.uiState.value.manualActivityIntensity)
    }

    @Test
    fun testEnergyReferenceInput_filtersNonDigitsAndClampsLength() {
        viewModel.setEnergyReferenceInput("2200kcal")
        assertEquals("2200", viewModel.uiState.value.energyReferenceInput)

        viewModel.setEnergyReferenceInput("123456")
        assertEquals("1234", viewModel.uiState.value.energyReferenceInput)
    }

    @Test
    fun testHomeMealDraftOperations() = runTest(testDispatcher) {
        viewModel.setHomeMealSlot("dinner")
        assertEquals("dinner", viewModel.uiState.value.homeMealSlot)

        // Analyze image mock
        viewModel.analyzeHomeMeal("fake-image".toByteArray(), "image/jpeg")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.analyzingHomeMeal)
        assertEquals(1, state.homeMealDraft.size)
        assertEquals("红烧牛肉", state.homeMealDraft[0].name)
        assertEquals(150.0, state.homeMealDraft[0].grams ?: 0.0, 0.001)

        // Edit item name & grams
        viewModel.setHomeMealName(0, "清炖牛肉")
        viewModel.setHomeMealGrams(0, 180.0)
        assertEquals("清炖牛肉", viewModel.uiState.value.homeMealDraft[0].name)
        assertEquals(180.0, viewModel.uiState.value.homeMealDraft[0].grams ?: 0.0, 0.001)

        // Save home meal
        viewModel.saveHomeMeal()
        advanceUntilIdle()

        assertNotNull(fakeApi.lastSavedHomeMealRequest)
        assertEquals("清炖牛肉", fakeApi.lastSavedHomeMealRequest?.items?.get(0)?.name)
        assertEquals(180.0, fakeApi.lastSavedHomeMealRequest?.items?.get(0)?.grams ?: 0.0, 0.001)
        assertEquals("清炖牛肉", viewModel.uiState.value.homeMealDraft.single().name)
    }

    @Test
    fun testSaveMeal_dispatchesToApi() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.setPortion("dish-1", 1.0)
        viewModel.saveMeal()
        advanceUntilIdle()

        assertNotNull(fakeApi.lastSavedMealRequest)
        assertEquals("lunch", fakeApi.lastSavedMealRequest?.mealSlot)
    }

    @Test
    fun testStagedMealImages_limitsToFiveAndAllowsRemoval() {
        val images = (1..7).map { i ->
            uk.lunarlab.health2609.feature.today.StagedMealImage(
                id = "img-$i",
                bytes = "image-bytes-$i".toByteArray(),
                mimeType = "image/jpeg",
                fileName = "photo-$i.jpg"
            )
        }

        viewModel.stageMealImages(images)
        assertEquals(5, viewModel.uiState.value.stagedMealImages.size)
        assertEquals("img-1", viewModel.uiState.value.stagedMealImages[0].id)
        assertEquals("img-5", viewModel.uiState.value.stagedMealImages[4].id)
        assertEquals("最多支持添加 5 张餐食图片", viewModel.uiState.value.message)

        viewModel.removeStagedMealImage("img-2")
        assertEquals(4, viewModel.uiState.value.stagedMealImages.size)
        assertFalse(viewModel.uiState.value.stagedMealImages.any { it.id == "img-2" })

        viewModel.clearStagedMealImages()
        assertTrue(viewModel.uiState.value.stagedMealImages.isEmpty())
    }

    @Test
    fun testAnalyzeStagedHomeMeals_multiImageSuccess() = runTest(testDispatcher) {
        val images = listOf(
            uk.lunarlab.health2609.feature.today.StagedMealImage(
                id = "overview",
                bytes = "img-overview".toByteArray(),
                mimeType = "image/jpeg"
            ),
            uk.lunarlab.health2609.feature.today.StagedMealImage(
                id = "closeup",
                bytes = "img-closeup".toByteArray(),
                mimeType = "image/jpeg"
            )
        )
        viewModel.stageMealImages(images)
        viewModel.analyzeStagedHomeMeals()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.analyzingHomeMeal)
        assertEquals(1, state.homeMealDraft.size)
        assertEquals("红烧牛肉", state.homeMealDraft[0].name)
        assertTrue(state.message?.contains("已汇总 2 张照片") == true)
    }

    @Test
    fun testAnalyzeStagedHomeMeals_failureReportsRealErrorWithoutFakeDraft() = runTest(testDispatcher) {
        fakeApi.shouldFailAnalyze = true
        fakeApi.analyzeException = RuntimeException("Cloudflare AI tunnel timeout")

        val image = uk.lunarlab.health2609.feature.today.StagedMealImage(
            id = "single",
            bytes = "test-bytes".toByteArray(),
            mimeType = "image/jpeg"
        )
        viewModel.stageMealImages(listOf(image))
        viewModel.analyzeStagedHomeMeals()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.analyzingHomeMeal)
        // CRITICAL REQUIREMENT: NO fake meal fallback!
        assertTrue(state.homeMealDraft.isEmpty())
        val expectedMessage = uk.lunarlab.health2609.core.network.ApiFactory.formatErrorMessage(fakeApi.analyzeException!!)
        assertTrue(state.homeMealNotes.any { it.contains("已保留") })
        assertEquals(listOf(image), state.stagedMealImages)
        assertEquals(expectedMessage, state.message)
    }

    @Test
    fun recognitionFailureKeepsEditedDishAndPhoto() = runTest(testDispatcher) {
        viewModel.analyzeHomeMeal("photo".toByteArray(), "image/jpeg")
        advanceUntilIdle()
        viewModel.setHomeMealName(0, "清炖牛肉")
        viewModel.setHomeMealGrams(0, 180.0)
        val draft = viewModel.uiState.value.homeMealDraft
        val photos = viewModel.uiState.value.stagedMealImages

        fakeApi.shouldFailAnalyze = true
        fakeApi.analyzeException = RuntimeException("timeout")
        viewModel.analyzeStagedHomeMeals()
        advanceUntilIdle()

        assertEquals(draft, viewModel.uiState.value.homeMealDraft)
        assertEquals(photos, viewModel.uiState.value.stagedMealImages)
        assertFalse(viewModel.uiState.value.analyzingHomeMeal)
    }

    @Test
    fun unnamedManualDishIsNotSilentlySavedOrDropped() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.addManualHomeMealItem()
        viewModel.saveHomeMeal()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.homeMealDraft.size)
        assertTrue(viewModel.uiState.value.homeMealDraft.single().name.isBlank())
        assertEquals(null, fakeApi.lastSavedHomeMealRequest)
        assertEquals("请填写每道菜的名称，再保存这餐。", viewModel.uiState.value.message)
    }
}
