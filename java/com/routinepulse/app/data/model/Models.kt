package com.routinepulse.app.data.model

// ---- Request bodies sent to the REST API ----
data class LoginRequest(val email: String, val password: String)
data class RegisterRequest(val email: String, val password: String, val displayName: String)
data class HabitRequest(
    val title: String,
    val frequency: String,
    val customDays: List<Int>,
    val reminderTime: String?,
    val priority: String,
    val clientUpdatedAt: Long? = null // sent when syncing an offline edit, so the server can apply last-write-wins
)
data class CompleteRequest(val date: String)
data class NoteRequest(
    val subject: String,
    val title: String,
    val content: String,
    val tags: List<String>,
    val clientUpdatedAt: Long? = null
)
data class SubmitRequest(val answers: List<Int>)
data class GoogleLoginRequest(val googleIdToken: String)
data class DeviceRequest(val token: String, val timezone: String, val notificationsEnabled: Boolean)
data class DeviceUnregisterRequest(val token: String)

// ---- Response bodies. Every field has a default so Gson never leaves a surprise null. ----
data class ProfileDto(val email: String? = null, val displayName: String? = null, val ssoProvider: String? = null)
data class AuthResponse(val userId: String? = null, val authToken: String? = null, val profile: ProfileDto? = null)

data class HabitDto(
    val id: String = "",
    val title: String = "",
    val frequency: String = "daily",
    val customDays: List<Int>? = null,
    val reminderTime: String? = null,
    val priority: String = "medium",
    val streakCount: Int = 0,
    val completedToday: Boolean = false,
    val scheduledToday: Boolean = true,
    val recentCompletions: List<String>? = null,
    val completionDates: List<String>? = null, // every completed date we know about (offline streaks + streak board)
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val conflict: Boolean = false // true when the server copy was newer and won (last-write-wins)
)
data class CompleteResponse(val habit: HabitDto? = null, val streakCount: Int = 0, val syncStatus: Boolean = false)

data class NoteDto(
    val id: String = "",
    val subject: String = "",
    val title: String = "",
    val content: String = "",
    val tags: List<String>? = null,
    val updatedAt: String? = null,
    val conflict: Boolean = false
)

data class QuestionDto(val text: String = "", val options: List<String> = emptyList())
data class QuizDto(
    val id: String = "",
    val noteId: String? = null,
    val noteTitle: String? = null,
    val source: String? = null,
    val questions: List<QuestionDto>? = null,
    val questionCount: Int = 0,
    val lastScore: Int? = null,
    val attemptCount: Int = 0,
    val generatedAt: String? = null
)
data class FeedbackDto(val correct: Boolean = false, val correctAnswer: String? = null)
data class SubmitResponse(
    val score: Int = 0,
    val total: Int = 0,
    val correctAnswers: List<Int>? = null,
    val feedback: List<FeedbackDto>? = null,
    val message: String? = null
)

data class HealthDto(val status: String? = null, val db: String? = null)
data class TestPushResponse(val ok: Boolean = false, val devices: Int = 0, val sent: Int = 0, val failed: Int = 0)
data class ErrorBody(val error: String? = null)

// ---- Enums with the exact strings the API uses ("wire" values) ----
enum class Frequency(val wire: String) {
    DAILY("daily"), WEEKDAYS("weekdays"), CUSTOM("custom");

    companion object {
        fun fromWire(v: String?): Frequency = entries.firstOrNull { it.wire == v } ?: DAILY
    }
}

enum class Priority(val wire: String) {
    LOW("low"), MEDIUM("medium"), HIGH("high");

    companion object {
        fun fromWire(v: String?): Priority = entries.firstOrNull { it.wire == v } ?: MEDIUM
    }
}
