package com.pocket48.app.data.danmaku

import com.pocket48.app.data.model.LrcLine

/**
 * LRC 弹幕/歌词解析器
 * 支持两种的时间格式:
 *  - [hh:mm:ss.fff]text   ← pocket48 直播弹幕 (3段)
 *  - [mm:ss.fff]text      ← 标准 LRC 歌词 (2段)
 *
 * 文本格式: "昵称\t内容" (制表符分隔), 解析后只保留"内容"部分
 */
object DanmakuParser {

    /** [hh:mm:ss.fff] 3段式 (pocket48) */
    private val HOURS_REGEX = Regex("""\[(\d{1,2}):(\d{2}):(\d{2})[.:](\d{1,3})](.*)""")

    /** [mm:ss.fff] 2段式 (标准 LRC) */
    private val MINUTES_REGEX = Regex("""\[(\d{1,2}):(\d{2})[.:](\d{1,3})](.*)""")

    fun parse(lrcText: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        for (line in lrcText.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // 优先匹配 3 段式 [hh:mm:ss.ms]
            var match = HOURS_REGEX.find(trimmed)
            if (match != null) {
                val h = match.groupValues[1].toLongOrNull() ?: continue
                val m = match.groupValues[2].toLongOrNull() ?: continue
                val s = match.groupValues[3].toLongOrNull() ?: continue
                val ms = normalizeMs(match.groupValues[4])
                val text = stripNickname(match.groupValues[5].trim())
                if (text.isNotEmpty()) {
                    lines.add(LrcLine(h * 3_600_000 + m * 60_000 + s * 1_000 + ms, text))
                }
                continue
            }

            // 回退到 2 段式 [mm:ss.ms]
            match = MINUTES_REGEX.find(trimmed)
            if (match != null) {
                val m = match.groupValues[1].toLongOrNull() ?: continue
                val s = match.groupValues[2].toLongOrNull() ?: continue
                val ms = normalizeMs(match.groupValues[3])
                val text = stripNickname(match.groupValues[4].trim())
                if (text.isNotEmpty()) {
                    lines.add(LrcLine(m * 60_000 + s * 1_000 + ms, text))
                }
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    /** 将毫秒字符串补全/截断为 3 位数字 */
    private fun normalizeMs(msStr: String): Long {
        return msStr.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
    }

    /**
     * 从 "昵称\t内容" 中提取纯内容, 无昵称的行原样返回
     * 格式示例: "未来我要与众不同\t刘鹏" → "未来我要与众不同"
     *           "hi多多" → "hi多多"
     */
    private fun stripNickname(text: String): String {
        val idx = text.indexOf('\t')
        if (idx < 0) return text
        return text.substring(idx + 1).trim()
    }
}
