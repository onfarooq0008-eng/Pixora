package com.pixorapdf

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Blue = Color(0xFF1769FF)
val DeepBlue = Color(0xFF0847D9)
val Sky = Color(0xFF0CA9FF)
val Green = Color(0xFF14B87F)
val Indigo = Color(0xFF4F5BEA)
val Red = Color(0xFFF04452)
val Purple = Color(0xFF7B4DFF)
val Orange = Color(0xFFFF8A1F)

private val LightScheme = lightColorScheme(
    primary = Blue, onPrimary = Color.White,
    background = Color(0xFFF5F8FF), onBackground = Color(0xFF10234D),
    surface = Color.White, onSurface = Color(0xFF10234D),
    onSurfaceVariant = Color(0xFF7583A3), outlineVariant = Color(0xFFE3EAF8),
    surfaceVariant = Color(0xFFE9EFFB)
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF4C8DFF), onPrimary = Color.White,
    background = Color(0xFF0D1322), onBackground = Color(0xFFEEF3FF),
    surface = Color(0xFF161E33), onSurface = Color(0xFFEEF3FF),
    onSurfaceVariant = Color(0xFF97A3C2), outlineVariant = Color(0xFF28324D),
    surfaceVariant = Color(0xFF1D2740)
)

@Composable
fun PixoraTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}

@Composable
fun Header(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(start = if (onBack != null) 6.dp else 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
        Text(
            title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = if (onBack != null) 4.dp else 0.dp)
        )
        actions()
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ChoiceChip(label: String, selected: Boolean, modifier: Modifier = Modifier, sub: String? = null, icon: ImageVector? = null, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val fg = if (selected) Color.White else cs.onSurface
    Column(
        modifier.clip(shape)
            .background(if (selected) cs.primary else cs.surface)
            .border(1.dp, if (selected) cs.primary else cs.outlineVariant, shape)
            .clickable(onClick = onClick).padding(vertical = 11.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, Modifier.size(16.dp), tint = fg); Spacer(Modifier.width(5.dp)) }
            Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        if (sub != null) Text(sub, color = fg.copy(alpha = .8f), fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
}

@Composable
fun ToolTile(title: String, icon: ImageVector, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(shape).background(cs.surface).border(1.dp, cs.outlineVariant, shape)
            .clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, lineHeight = 15.sp, minLines = 2, maxLines = 2)
    }
}

@Composable
fun PdfBadge(size: Int = 44) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)).background(Red.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.PictureAsPdf, null, tint = Red, modifier = Modifier.size((size * 0.58f).dp))
    }
}

@Composable
fun <T> ChoiceDialog(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { o ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(o); onDismiss() }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = o == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label(o))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun TextInputDialog(title: String, initial: String, suffix: String? = null, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                suffix = if (suffix != null) ({ Text(suffix) }) else null,
                shape = RoundedCornerShape(12.dp)
            )
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ConfirmDialog(title: String, message: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = Red) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun BusyDialog(text: String, progress: Float? = null) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text(text) },
        text = {
            if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
    )
}
