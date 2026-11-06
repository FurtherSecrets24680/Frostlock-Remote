package com.frostlock.remote.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun <T> CompactDropdown(
    title: String,
    selected: T,
    options: List<T>,
    labelFor: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    description: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth().semantics { contentDescription = "$title selection" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
            subtitle?.let {
                Text(it, fontSize = 12.sp, lineHeight = 17.sp, color = colors.onSurfaceVariant)
            }
        }
        Box {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.primary.copy(alpha = 0.09f),
                modifier = Modifier
                    .widthIn(min = 104.dp, max = 156.dp)
                    .clickable { expanded = true },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedContent(
                        targetState = labelFor(selected),
                        transitionSpec = {
                            (fadeIn(tween(140)) + slideInVertically(tween(160)) { it / 5 }) togetherWith
                                fadeOut(tween(100))
                        },
                        label = "$title selection label",
                    ) { label ->
                        Text(
                            label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = colors.onSurface.copy(alpha = 0.48f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.widthIn(min = 168.dp, max = 280.dp),
                containerColor = colors.surface,
                tonalElevation = 0.dp,
                shadowElevation = 6.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant),
                shape = RoundedCornerShape(14.dp),
            ) {
                options.forEach { option ->
                    val isSelected = option == selected
                    DropdownMenuItem(
                        text = {
                            Text(
                                labelFor(option),
                                color = if (isSelected) colors.primary else colors.onSurface,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        trailingIcon = {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = colors.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        },
                    )
                }
            }
        }
    }
    description?.let {
        Text(
            it,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp).alpha(0.92f),
        )
    }
}