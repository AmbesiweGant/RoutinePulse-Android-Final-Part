package com.routinepulse.app

import com.routinepulse.app.data.model.HabitDto
import com.routinepulse.app.data.model.HabitRequest
import com.routinepulse.app.data.remote.ApiError
import com.routinepulse.app.data.remote.ApiResult
import com.routinepulse.app.data.remote.ErrorKind
import com.routinepulse.app.data.repository.HabitRepository
import com.routinepulse.app.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

private class FakeHabitRepository(
    var habits: List<HabitDto>,
    var failNext: Boolean = false
) : HabitRepository {
    var lastDate: String? = null
    val deletedIds = mutableListOf<String>()

    private fun <T> maybeFail(value: T): ApiResult<T> =
        if (failNext) ApiResult.Failure(ApiError(ErrorKind.NETWORK)) else ApiResult.Success(value)

    override suspend fun list(date: String): ApiResult<List<HabitDto>> { lastDate = date; return maybeFail(habits) }
    override suspend fun get(id: String, date: String): ApiResult<HabitDto> = maybeFail(habits.first { it.id == id })
    override suspend fun create(request: HabitRequest, date: String): ApiResult<HabitDto> = maybeFail(habits.first())
    override suspend fun update(id: String, request: HabitRequest, date: String): ApiResult<HabitDto> = maybeFail(habits.first())
    override suspend fun delete(id: String): ApiResult<Unit> {
        if (!failNext) deletedIds += id
        return maybeFail(Unit)
    }
    override suspend fun setCompleted(id: String, date: String, completed: Boolean): ApiResult<HabitDto> {
        val h = habits.first { it.id == id }
        return maybeFail(h.copy(completedToday = completed, streakCount = if (completed) h.streakCount + 1 else h.streakCount - 1))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val today = LocalDate.of(2024, 1, 8)
    private val water = HabitDto(id = "1", title = "Drink water", streakCount = 4)
    private val read = HabitDto(id = "2", title = "Read 20 min", streakCount = 1, completedToday = true)

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun load_success_populates_habits_and_sends_todays_date() {
        val repo = FakeHabitRepository(listOf(water, read))
        val vm = HomeViewModel(repo) { today }
        vm.load()
        assertEquals(2, vm.state.value.habits.size)
        assertFalse(vm.state.value.loading)
        assertEquals("2024-01-08", repo.lastDate)
    }

    @Test fun load_failure_sets_an_error_and_stops_loading() {
        val repo = FakeHabitRepository(listOf(water), failNext = true)
        val vm = HomeViewModel(repo) { today }
        vm.load()
        assertFalse(vm.state.value.loading)
        assertEquals(ErrorKind.NETWORK, vm.state.value.error?.kind)
        assertTrue(vm.state.value.habits.isEmpty())
    }

    @Test fun toggle_replaces_the_habit_with_the_updated_streak() {
        val repo = FakeHabitRepository(listOf(water, read))
        val vm = HomeViewModel(repo) { today }
        vm.load()
        vm.toggle(vm.state.value.habits.first { it.id == "1" })
        val updated = vm.state.value.habits.first { it.id == "1" }
        assertTrue(updated.completedToday)
        assertEquals(5, updated.streakCount)
        assertTrue(vm.state.value.busyIds.isEmpty())
        // the other habit is untouched
        assertEquals(1, vm.state.value.habits.first { it.id == "2" }.streakCount)
    }

    @Test fun toggle_failure_keeps_the_list_and_reports_an_action_error() {
        val repo = FakeHabitRepository(listOf(water))
        val vm = HomeViewModel(repo) { today }
        vm.load()
        repo.failNext = true
        vm.toggle(vm.state.value.habits.first())
        assertFalse(vm.state.value.habits.first().completedToday)
        assertNotNull(vm.state.value.actionError)
        assertTrue(vm.state.value.busyIds.isEmpty())
    }

    @Test fun delete_removes_the_habit_from_the_list() {
        val repo = FakeHabitRepository(listOf(water, read))
        val vm = HomeViewModel(repo) { today }
        vm.load()
        vm.delete(water)
        assertEquals(listOf("2"), vm.state.value.habits.map { it.id })
        assertEquals(listOf("1"), repo.deletedIds)
    }
}
