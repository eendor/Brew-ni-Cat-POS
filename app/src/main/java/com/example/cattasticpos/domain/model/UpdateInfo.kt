package com.example.cattasticpos.domain.model

/**
 * The latest published app build, as advertised by the cloud `app_release` row.
 * Surfaced to the user only when [versionCode] is newer than the running build.
 */
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val notes: String?,
    val mandatory: Boolean,
    /** Published SHA-256 of the APK (FR-118); null when the row has none, which is refused. */
    val sha256: String? = null
)
