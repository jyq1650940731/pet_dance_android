package app.petdance.domain

fun normalizePhone(raw: String): String? {
    var digits = raw.trim().replace(" ", "").replace("-", "")
    if (digits.startsWith("+86")) digits = digits.removePrefix("+86")
    if (digits.startsWith("86") && digits.length == 13) digits = digits.drop(2)
    return digits.takeIf { PHONE.matches(it) }
}

fun isMainlandPhone(raw: String): Boolean = normalizePhone(raw) != null

private val PHONE = Regex("^1[3-9]\\d{9}$")

fun formatDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "%d:%02d".format(safe / 60, safe % 60)
}

fun categoryLabel(category: String): String = when (category) {
    "dance" -> "跳舞"
    "comedy" -> "搞怪"
    "daily" -> "日常"
    "recommend" -> "推荐"
    else -> category
}

fun petLabel(pet: String?): String = when (pet) {
    "dog" -> "狗"
    "cat" -> "猫"
    "other" -> "其他"
    else -> "未填"
}

/**
 * 狗、猫把对应 pet_scope 排到前面，any 居中，不匹配的靠后。
 * 未选或「其他」保持服务端排序。
 */
fun petRank(pet: String?, scope: String): Int {
    if (pet.isNullOrBlank() || pet == "other") return 1
    if (scope == pet) return 0
    if (scope == "any") return 1
    return 2
}

enum class StepMark { Done, Current, Pending }

fun generationStep(status: String, index: Int): StepMark {
    val current = when (status) {
        "queued" -> 1
        "moderating", "generating" -> 2
        "muxing" -> 3
        "succeeded" -> 4
        else -> 1
    }
    return when {
        index < current -> StepMark.Done
        index == current -> StepMark.Current
        else -> StepMark.Pending
    }
}

val GENERATION_STEPS = listOf("上传完成", "排队中", "生成中", "合成中")

fun worksLabel(status: String, playable: Boolean, errorMessage: String?): String = when {
    status == "failed" -> "失败"
    status == "succeeded" && playable -> "可播放"
    status == "succeeded" -> errorMessage ?: "不可播放"
    else -> when (status) {
        "queued" -> "排队"
        "moderating", "generating" -> "生成中"
        "muxing" -> "合成中"
        else -> status
    }
}

fun isActiveStatus(status: String): Boolean =
    status == "queued" || status == "moderating" || status == "generating" || status == "muxing"
