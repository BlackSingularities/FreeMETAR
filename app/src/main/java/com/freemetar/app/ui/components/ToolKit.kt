package com.freemetar.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freemetar.app.ui.theme.Fm
import com.freemetar.app.ui.theme.Mono

/** Panel narzędzia lub ustawienia: tytuł z ikoną i treść na powierzchni karty. */
@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    dense: Boolean = false,   // mniejsze marginesy (np. ekran ustawień)
    content: @Composable ColumnScope.() -> Unit,
) {
    Panel(modifier) {
        Column(Modifier.padding(if (dense) 12.dp else 16.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(icon, null, tint = Fm.c.ink2, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium, color = Fm.c.ink, modifier = Modifier.weight(1f))
                    trailing()
                }
                Spacer(Modifier.size(if (dense) 6.dp else 12.dp))
            }
            content()
        }
    }
}

/** Pole wyniku obliczeń: etykieta, wartość krojem kokpitowym, dopisek. */
@Composable
fun StatTile(label: String, value: String, sub: String? = null, modifier: Modifier = Modifier, accent: Color? = null, icon: ImageVector? = null) {
    Column(modifier.clip(RoundedCornerShape(8.dp)).background(Fm.c.terrain).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = Fm.c.ink2, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = Fm.c.ink2, maxLines = 1)
        }
        Text(value, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = accent ?: Fm.c.ink)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = Fm.c.ink2)
    }
}
