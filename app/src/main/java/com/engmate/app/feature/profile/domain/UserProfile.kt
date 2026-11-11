package com.engmate.app.feature.profile.domain

data class UserProfile(
    val uid: String,
    val email: String,
    val displayName: String,
    val photoUrl: String?,
    val englishLevel: String,
    val dailyGoalMinutes: Int,
    val createdAt: Long,
    val updatedAt: Long,
)
data class ProfileInput(val displayName: String, val photoUrl: String = "", val englishLevel: String = "A1", val dailyGoalMinutes: String = "15") {
    fun normalized() = copy(displayName = java.text.Normalizer.normalize(displayName.trim(), java.text.Normalizer.Form.NFC), photoUrl = photoUrl.trim(), dailyGoalMinutes = dailyGoalMinutes.trim())
}
enum class ProfileField { NAME, PHOTO, LEVEL, GOAL }
object ProfileValidator {
    val levels = listOf("A1", "A2", "B1", "B2", "C1", "C2")
    fun errors(input: ProfileInput): Map<ProfileField, String> = buildMap {
        val value = input.normalized()
        if (value.displayName.length !in 2..50 || !value.displayName.matches(Regex("[\\p{L}\\p{M} ]+")))
            put(ProfileField.NAME, "Họ tên từ 2–50 ký tự, chỉ gồm chữ và khoảng trắng.")
        if (value.photoUrl.isNotEmpty() && !validPhoto(value.photoUrl))
            put(ProfileField.PHOTO, "Nhập đường dẫn ảnh HTTPS hợp lệ hoặc để trống.")
        if (value.englishLevel !in levels) put(ProfileField.LEVEL, "Chọn trình độ hợp lệ.")
        if (value.dailyGoalMinutes.toIntOrNull() !in 5..120) put(ProfileField.GOAL, "Mục tiêu từ 5 đến 120 phút/ngày.")
    }
    private fun validPhoto(value: String): Boolean = runCatching {
        val uri = java.net.URI(value)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
}
class InvalidProfile(val errors: Map<ProfileField, String>) : IllegalArgumentException("Hồ sơ không hợp lệ")
class ProfileAlreadyExists : IllegalStateException("Hồ sơ đã tồn tại")
class ProfileMissing : IllegalStateException("Hồ sơ không còn tồn tại")
class SessionRequired : IllegalStateException("Bạn cần đăng nhập lại")
