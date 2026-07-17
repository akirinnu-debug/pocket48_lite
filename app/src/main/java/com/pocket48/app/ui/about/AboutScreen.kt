package com.pocket48.app.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 开源项目致谢 - 数据来源
 * (注: URL 来自 references 目录下的开源 README)
 */
private data class OpenSource(
    val name: String,
    val url: String,
    val desc: String,
)

private val OPEN_SOURCE_PROJECTS = listOf(
    OpenSource(
        name = "48tools",
        url = "https://github.com/duan602728596/48tools",
        desc = "桌面端口袋48工具箱 (Web 打包/下载/弹幕)，本项目弹幕池逻辑参考其 PlayerContent 实现",
    ),
    OpenSource(
        name = "SNH48G-API",
        url = "https://github.com/theprimone/SNH48G-API",
        desc = "口袋48 公开 API 文档，本项目无 token 接口与签名逻辑均参考此文档整理",
    ),
    OpenSource(
        name = "WebPocket48Assistant",
        url = "https://github.com/Lawaxi/WebPocket48Assistant",
        desc = "Web 端口袋48助手，UI 设计灵感来源",
    ),
    OpenSource(
        name = "Partner48",
        url = "https://github.com/Akimaylilll/Partner48",
        desc = "第三方口袋48 客户端，队伍/成员数据组织方式参考",
    ),
    OpenSource(
        name = "yaya_msg",
        url = "https://github.com/ (本地参考)",
        desc = "弹幕/消息解析参考实现",
    ),
)

/** 官方开源仓库 (用于安全来源校验) */
private const val OFFICIAL_REPO_URL = "https://github.com/akirinnu-debug/pocket48_lite"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen() {
    val uriHandler = LocalUriHandler.current
    Scaffold(
        topBar = { TopAppBar(title = { Text("关于") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // === 应用标识 ===
            AppHeader()
            Spacer(Modifier.height(16.dp))

            // === 安全提示 (置顶, 最显眼) ===
            SecurityWarningCard(
                onOpenRepo = { uriHandler.openUri(OFFICIAL_REPO_URL) },
            )
            Spacer(Modifier.height(12.dp))

            // === 技术说明 ===
            SectionCard(
                title = "技术说明",
                icon = { Icon(Icons.Default.Info, null, modifier = Modifier.size(18.dp)) },
            ) {
                Text(
                    "本项目所有技术均来源于已开源社区成果，" +
                            "对原始项目作者深表感谢。本项目仅作为个人学习与" +
                            "公开技术整合的实验性作品，不提供、不参与、不鼓励" +
                            "任何形式的商业化运营。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                )
            }
            Spacer(Modifier.height(12.dp))

            // === 开源致谢 ===
            SectionCard(
                title = "致谢 / 开源项目",
                icon = { Icon(Icons.Default.Code, null, modifier = Modifier.size(18.dp)) },
            ) {
                OPEN_SOURCE_PROJECTS.forEach { p ->
                    OpenSourceItem(p)
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(12.dp))

            // === 免责声明 ===
            SectionCard(
                title = "免责声明",
                icon = { Icon(Icons.Outlined.PrivacyTip, null, modifier = Modifier.size(18.dp)) },
            ) {
                BulletItem("本项目仅供个人学习与开源技术研究使用。")
                BulletItem("本项目所有数据来源于口袋48 公开 API，版权归" +
                        "上海丝芭文化传媒有限公司及对应内容创作者所有。")
                BulletItem("本项目未与官方建立任何合作关系，亦未获得任何" +
                        "官方授权、赞助或背书。")
                BulletItem("本项目作者不对使用本项目所产生的一切后果负责，" +
                        "使用者应自行承担相应法律责任。")
                BulletItem("如本项目无意中侵犯了您的合法权益，请联系作者" +
                        "下架处理，作者将在第一时间响应。")
            }
            Spacer(Modifier.height(12.dp))

            // === 版权声明 ===
            SectionCard(
                title = "版权声明",
                icon = { Icon(Icons.Default.Favorite, null, modifier = Modifier.size(18.dp)) },
            ) {
                Text(
                    "本项目源代码遵循开源协议发布。\n" +
                            "本项目所展示的成员照片、直播回放、弹幕等内容，\n" +
                            "其版权均归上海丝芭文化传媒集团有限公司及对应\n" +
                            "内容创作者所有，未经授权请勿用于商业用途。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                )
            }
            Spacer(Modifier.height(16.dp))

            // === 作者 ===
            AuthorFooter()
            Spacer(Modifier.height(20.dp))
        }
    }
}

/**
 * 安全提示卡片 - 警告恶意搬运改版 + 指引到官方 GitHub 仓库
 *
 * 置顶显示, 用 error 色调强调, 引导用户验证安装包来源
 */
@Composable
private fun SecurityWarningCard(onOpenRepo: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "安全提示 · 请认准官方来源",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "近期我们注意到网络上已出现未经授权的恶意搬运与改版。" +
                        "这些改版可能包含后门、广告插件、数据窃取等风险代码，" +
                        "我们无法保障其安全性。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Pocket48 Lite 一直为开源且免费，源代码完全公开透明，" +
                        "不含任何商业行为。为确保安全，请仅从以下官方 GitHub " +
                        "仓库获取安装包：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(8.dp))
            // 可点击的仓库链接
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    .clickable(onClick = onOpenRepo)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    OFFICIAL_REPO_URL,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun AppHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "P48",
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                "Pocket48 Lite",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "个人学习用实验性客户端",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(Modifier.width(6.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun OpenSourceItem(p: OpenSource) {
    Column {
        Text(
            text = p.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
        )
        Text(
            text = p.url,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
            fontSize = 10.sp,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = p.desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun BulletItem(text: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            "• ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun AuthorFooter() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Author",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                "48archive / Etizolam",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "本项目完全开源透明，不含任何商业行为",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 10.sp,
            )
        }
    }
}
