package com.example.typechowriter

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// ============================================================
// 数据类
// ============================================================

data class PublishResponse(
    val success: Boolean?,
    val cid: Int?,
    val slug: String?,
    val category: Int?,
    val error: String?
)

data class PolishResponse(
    val success: Boolean?,
    val category: String?,
    val title: String?,
    val tags: String?,
    val content: String?,
    val error: String?
)

data class UploadResponse(
    val success: Boolean?,
    val url: String?,
    val key: String?,
    val name: String?,
    val size: Long?,
    val error: String?
)

data class UnsplashPhoto(
    val id: String?,
    val thumb: String?,
    val regular: String?,
    val small: String?,
    val width: Int?,
    val height: Int?,
    val alt: String?,
    val author: String?,
    val authorLink: String?
)

data class UnsplashSearchResponse(
    val success: Boolean?,
    val results: List<UnsplashPhoto>?,
    val query: String?,
    val error: String?
)

data class UnsplashCollection(
    val id: String?,
    val title: String?,
    val total_photos: Int?,
    val cover: String? = null
)

data class UnsplashCollectionsResponse(
    val success: Boolean?,
    val results: List<UnsplashCollection>?,
    val error: String?
)

// ↓↓↓ 分类 ↓↓↓
data class Category(
    val mid: Int?,
    val name: String?
)

data class CategoriesResponse(
    val success: Boolean?,
    val results: List<Category>?,
    val error: String?
)

// ============================================================
// Retrofit 接口
// ============================================================

interface TypechoApi {

    @FormUrlEncoded
    @POST("write-api.php?action=publish")
    suspend fun publish(
        @Header("X-API-Token") token: String,
        @Field("title") title: String,
        @Field("content") content: String,
        @Field("status") status: String,
        @Field("tags") tags: String = "",
        @Field("slug") slug: String = "",
        @Field("category") category: Int = 0
    ): PublishResponse

    @FormUrlEncoded
    @POST("write-api.php?action=polish")
    suspend fun polish(
        @Header("X-API-Token") token: String,
        @Field("content") content: String,
        @Field("style") style: String = ""
    ): PolishResponse

    @Multipart
    @POST("write-api.php?action=upload-image")
    suspend fun uploadImage(
        @Header("X-API-Token") token: String,
        @Part file: MultipartBody.Part
    ): UploadResponse

    @GET("write-api.php?action=unsplash-search")
    suspend fun unsplashSearch(
        @Header("X-API-Token") token: String,
        @Query("query") query: String,
        @Query("page") page: Int = 1
    ): UnsplashSearchResponse

    @GET("write-api.php?action=unsplash-collections")
    suspend fun unsplashCollections(
        @Header("X-API-Token") token: String
    ): UnsplashCollectionsResponse

    @GET("write-api.php?action=unsplash-collection-photos")
    suspend fun unsplashCollectionPhotos(
        @Header("X-API-Token") token: String,
        @Query("id") id: String,
        @Query("page") page: Int = 1
    ): UnsplashSearchResponse

    @GET("write-api.php?action=get-categories")
    suspend fun getCategories(
        @Header("X-API-Token") token: String
    ): CategoriesResponse
}

// ============================================================
// Retrofit 客户端
// ============================================================

object ApiClient {

    fun create(baseUrl: String): TypechoApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Android) TypechoWriter/1.0")
                    .build()
                chain.proceed(req)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TypechoApi::class.java)
    }
}

// ============================================================
// 上传工具
// ============================================================

suspend fun uploadImage(context: Context, uri: Uri): UploadResponse? {
    val baseUrl = Settings.baseUrl(context).first()
    val token = Settings.token(context).first()
    if (baseUrl.isBlank() || token.isBlank()) return null

    val inputStream = context.contentResolver.openInputStream(uri) ?: return null
    val bytes = inputStream.readBytes()
    inputStream.close()

    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
    val ext = when {
        mime.contains("png") -> "png"
        mime.contains("gif") -> "gif"
        mime.contains("webp") -> "webp"
        else -> "jpg"
    }

    val body = bytes.toRequestBody(mime.toMediaTypeOrNull())
    val part = MultipartBody.Part.createFormData(
        "file",
        "image_${System.currentTimeMillis()}.$ext",
        body
    )

    return try {
        ApiClient.create(baseUrl).uploadImage(token, part)
    } catch (e: Exception) {
        null
    }
}
