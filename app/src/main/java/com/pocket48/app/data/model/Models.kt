package com.pocket48.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ===== API 响应模型 =====

@Serializable
data class ApiResponse<T>(
    @SerialName("status") val status: Int = 0,
    @SerialName("success") val success: Boolean = false,
    @SerialName("message") val message: String? = null,
    @SerialName("content") val content: T? = null,
)

// ===== 直播模型 =====

@Serializable
data class LiveListContent(
    @SerialName("liveList") val liveList: List<LiveListItem> = emptyList(),
    @SerialName("next") val next: String = "0",
)

@Serializable
data class LiveListItem(
    @SerialName("liveId") val liveId: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("subTitle") val subTitle: String? = null,
    @SerialName("coverPath") val coverPath: String = "",
    @SerialName("ctime") val ctime: Long = 0,
    @SerialName("startTime") val startTime: Long = 0,
    @SerialName("createdBy") val createdBy: Long = 0,
    @SerialName("createdName") val createdName: String = "",
    @SerialName("record") val record: Boolean = false,
    @SerialName("liveType") val liveType: Int = 0,
    @SerialName("onlineNum") val onlineNum: Int = 0,
    @SerialName("coverUrl") val coverUrl: String? = null,
    @SerialName("userInfo") val userInfo: LiveUserInfo? = null,
) {
    /** 兼容 ctime 和 startTime，取非零值 */
    val displayTime: Long get() = if (ctime > 0) ctime else startTime
}

@Serializable
data class LiveUserInfo(
    @SerialName("userId") val userId: Long = 0,
    @SerialName("nickname") val nickname: String = "",
    @SerialName("starName") val starName: String = "",
    @SerialName("teamName") val teamName: String = "",
)

@Serializable
data class LiveDetail(
    @SerialName("liveId") val liveId: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("coverPath") val coverPath: String = "",
    @SerialName("m3u8Url") val m3u8Url: String = "",
    @SerialName("lrcUrl") val lrcUrl: String? = null,
    @SerialName("playStreamPath") val playStreamPath: String = "",
    @SerialName("msgFilePath") val msgFilePath: String? = null,
    @SerialName("playDuration") val playDuration: Long = 0,
    @SerialName("ctime") val ctime: Long = 0,
    @SerialName("startTime") val startTime: Long = 0,
    @SerialName("endTime") val endTime: Long = 0,
    @SerialName("onlineNum") val onlineNum: Int = 0,
    @SerialName("createdBy") val createdBy: Long = 0,
    @SerialName("createdName") val createdName: String = "",
    @SerialName("user") val user: LiveDetailUser? = null,
) {
    /** 播放地址：优先 m3u8Url，fallback playStreamPath */
    val playUrl: String get() = m3u8Url.ifBlank { playStreamPath }
    /** 弹幕地址：优先 lrcUrl，fallback msgFilePath */
    val danmakuUrl: String? get() = lrcUrl ?: msgFilePath
}

@Serializable
data class LiveDetailUser(
    @SerialName("userId") val userId: Long = 0,
    @SerialName("userName") val userName: String = "",
    @SerialName("userAvatar") val userAvatar: String = "",
)

// ===== 本地成员模型 (来自 assets/members.json) =====

/** 成员状态 */
enum class MemberStatus(
    val label: String,
    val color: Long, // ARGB hex
) {
    ACTIVE("在籍", 0xFF4CAF50),       // 绿色
    REST("暂休", 0xFFFFC107),         // 黄色
    WITHDRAWN("退团", 0xFF9E9E9E),    // 灰色
    GRADUATED("毕业", 0xFF2196F3),    // 蓝色
}

/** 应标记为暂休的团体 (status=1 但实际为暂休) */
private val REST_GROUPS = setOf(
    "IDFT", "IDFT/团魂", "团魂", "RNTH", "燃烧吧团魂",
    "海外练习生", "新星闪耀计划", "丝芭影视",
)

/** 已解散团体 (全部成员为暂休) */
private val DISBANDED_GROUPS = setOf("SHY48")

/** 毕业/荣誉队伍名 (status=1 但应标记为毕业) */
private val GRADUATED_TEAMS = setOf("荣誉毕业生", "明星殿堂")

/**
 * 队伍颜色配置 - 与参考项目保持一致
 * 数据库中队伍名称为全大写格式 (TEAM HII, TEAM NII 等)
 */
object TeamColors {
    private val map = mapOf(
        // SNH48
        "TEAM SII" to 0xFF90CCEA,
        "TEAM NII" to 0xFFAD85BA,
        "TEAM HII" to 0xFFF39800,
        "TEAM X" to 0xFFB1D61B,
        "TEAM XII" to 0xFF00BE6E,
        "TEAM SIII" to 0xFFE70095,
        "TEAM HIII" to 0xFF4F008C,
        "TEAM FT" to 0xFF20B2AA,
        "SHY48" to 0xFFE0009C,
        // GNZ48
        "TEAM G" to 0xFFAAC913,
        "TEAM NIII" to 0xFFFFD700,
        "TEAM Z" to 0xFFEA627C,
        // BEJ48
        "TEAM B" to 0xFFFF2471,
        "TEAM E" to 0xFF0CC8C3,
        "TEAM J" to 0xFF006AB7,
        // CKG48
        "TEAM C" to 0xFFFEBA07,
        "TEAM K" to 0xFFFF5043,
        "IDFT" to 0xFF7B68EE,
        // CGT48
        "TEAM CII" to 0xFFE60000,
        "TEAM GII" to 0xFF00559B,
        // 特殊
        "预备生" to 0xFFA7B0BA,
        "荣誉毕业生" to 0xFFECC56A,
        "明星殿堂" to 0xFFC0A062,
        "联合" to 0xFF8B5CF6,
        "未入列" to 0xFFA7B0BA,
    )

    fun of(teamName: String): Long? = map[teamName]?.let { it }
        ?: map[teamName.uppercase()]?.let { it }
}

/** 获取成员的展示状态 */
fun MemberInfo.effectiveStatus(): MemberStatus = when {
    teamName in GRADUATED_TEAMS -> MemberStatus.GRADUATED
    groupName in DISBANDED_GROUPS -> MemberStatus.REST
    groupName in REST_GROUPS || teamName in REST_GROUPS -> MemberStatus.REST
    status == 1 -> MemberStatus.ACTIVE
    status == 2 -> MemberStatus.REST
    status == 3 -> MemberStatus.WITHDRAWN
    else -> MemberStatus.WITHDRAWN
}

/** 队伍筛选选项 */
data class GroupFilter(
    val groupName: String,
    val teams: List<String>,
)

@Serializable
data class MemberInfo(
    @SerialName("user_id") val userId: Long = 0,
    @SerialName("real_name") val realName: String = "",
    @SerialName("nickname") val nickname: String = "",
    @SerialName("pinyin") val pinyin: String = "",
    @SerialName("abbr") val abbr: String = "",
    @SerialName("avatar") val avatar: String = "",
    @SerialName("group_name") val groupName: String = "",
    @SerialName("team_name") val teamName: String = "",
    @SerialName("status") val status: Int = 0,
) : java.io.Serializable {
    /** 展示名称：优先真实姓名，fallback 到昵称 */
    val displayName: String get() = realName.ifBlank { nickname }
    /** 展示状态 */
    val displayStatus: MemberStatus get() = effectiveStatus()
}

// ===== LRC 弹幕模型 =====

data class LrcLine(
    val timeMs: Long,
    val text: String,
)

/** 将 48.cn 相对路径转为完整 URL */
fun sourceUrl(path: String): String {
    if (path.isBlank()) return ""
    return if (path.startsWith("http")) path else "https://source.48.cn/$path"
}

/** 格式化时间戳为中文相对时间 */
fun formatRelativeTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000}分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000}小时前"
        diff < 604_800_000 -> "${diff / 86_400_000}天前"
        else -> {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
            sdf.format(java.util.Date(timestamp))
        }
    }
}
