package com.routinepulse.app.data.remote

import com.routinepulse.app.data.model.*
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Retrofit definition of the RoutinePulse REST API (see backend/src/routes). */
interface ApiService {
    @GET("health") suspend fun health(): HealthDto

    @POST("api/auth/register") suspend fun register(@Body body: RegisterRequest): AuthResponse
    @POST("api/auth/login") suspend fun login(@Body body: LoginRequest): AuthResponse
    @POST("api/auth/login") suspend fun loginWithGoogle(@Body body: GoogleLoginRequest): AuthResponse

    @DELETE("api/auth/me") suspend fun deleteAccount()

    // Push notifications
    @POST("api/devices") suspend fun registerDevice(@Body body: DeviceRequest)
    @POST("api/devices/unregister") suspend fun unregisterDevice(@Body body: DeviceUnregisterRequest)
    @POST("api/devices/test") suspend fun testPush(): TestPushResponse

    @GET("api/habits") suspend fun getHabits(@Query("date") date: String): List<HabitDto>
    @GET("api/habits/{id}") suspend fun getHabit(@Path("id") id: String, @Query("date") date: String): HabitDto
    @POST("api/habits") suspend fun createHabit(@Body body: HabitRequest, @Query("date") date: String): HabitDto
    @PUT("api/habits/{id}") suspend fun updateHabit(
        @Path("id") id: String,
        @Body body: HabitRequest,
        @Query("date") date: String
    ): HabitDto
    @DELETE("api/habits/{id}") suspend fun deleteHabit(@Path("id") id: String)
    @POST("api/habits/{id}/complete") suspend fun completeHabit(
        @Path("id") id: String,
        @Body body: CompleteRequest
    ): CompleteResponse
    @DELETE("api/habits/{id}/complete") suspend fun uncompleteHabit(
        @Path("id") id: String,
        @Query("date") date: String
    ): CompleteResponse

    @GET("api/notes") suspend fun getNotes(): List<NoteDto>
    @GET("api/notes/{id}") suspend fun getNote(@Path("id") id: String): NoteDto
    @POST("api/notes") suspend fun createNote(@Body body: NoteRequest): NoteDto
    @PUT("api/notes/{id}") suspend fun updateNote(@Path("id") id: String, @Body body: NoteRequest): NoteDto
    @DELETE("api/notes/{id}") suspend fun deleteNote(@Path("id") id: String)
    @POST("api/notes/{id}/generate-quiz") suspend fun generateQuiz(@Path("id") id: String): QuizDto

    @GET("api/quizzes") suspend fun getQuizzes(): List<QuizDto>
    @GET("api/quizzes/{id}") suspend fun getQuiz(@Path("id") id: String): QuizDto
    @POST("api/quizzes/{id}/submit") suspend fun submitQuiz(
        @Path("id") id: String,
        @Body body: SubmitRequest
    ): SubmitResponse
}
