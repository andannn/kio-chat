package me.andannn.kio.chat.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.absoluteValue

// 预定义一组协调且现代的头像背景色
private val AvatarColors = listOf(
    Color(0xFF5C6BC0), // Indigo
    Color(0xFF42A5F5), // Blue
    Color(0xFF26A69A), // Teal
    Color(0xFF66BB6A), // Green
    Color(0xFFFFA726), // Orange
    Color(0xFFFF7043), // Deep Orange
    Color(0xFFAB47BC), // Purple
    Color(0xFFEC407A), // Pink
    Color(0xFF78909C), // Blue Grey
)

/**
 * 默认圆形用户头像，展示昵称首字符
 */
@Composable
fun UserAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    key: Any? = null,
    backgroundColor: Color? = null,
    contentColor: Color = Color.White,
) {
    val initial = remember(name) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) {
            val codePoint = trimmed.codePointAt(0)
            String(Character.toChars(codePoint)).uppercase()
        } else {
            "?"
        }
    }

    val bg = backgroundColor ?: remember(key, name) {
        val hash = (key?.hashCode() ?: name.hashCode()).absoluteValue
        AvatarColors[hash % AvatarColors.size]
    }

    val fontSize = (size.value * 0.44f).sp

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = contentColor,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
