package com.metrolist.music.ui.utils

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import kotlinx.coroutines.launch

/**
 * A clickable modifier that debounces clicks to prevent rapid successive clicks.
 * Useful for preventing double-navigation when users rapidly tap buttons.
 *
 * @param debounceInterval The time in milliseconds to wait before allowing another click (default: 200ms)
 * @param enabled Whether the clickable is enabled (default: true)
 * @param onClickLabel Semantic label for accessibility
 * @param role Accessibility role
 * @param onClick The callback to invoke when clicked (debounced)
 */
fun Modifier.debouncedClickable(
    debounceInterval: Long = 200L,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: androidx.compose.ui.semantics.Role? = null,
    onClick: () -> Unit
): Modifier = composed {
    val coroutineScope = rememberCoroutineScope()
    var lastClickTime = remember { 0L }
    
    clickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        indication = ripple(),
        interactionSource = remember { MutableInteractionSource() }
    ) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime >= debounceInterval) {
            lastClickTime = currentTime
            coroutineScope.launch {
                onClick()
            }
        }
    }
}
