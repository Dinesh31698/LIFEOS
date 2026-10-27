package com.lifeos.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// ============================================================================
// LIFEOS Sprint 1 Design System: Component Shapes
// ============================================================================

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

// Specific component shapes matching UI wireframe
val PillShape = RoundedCornerShape(50)
val CardShape = RoundedCornerShape(18.dp)
val DialogShape = RoundedCornerShape(20.dp)
val ChipShape = RoundedCornerShape(12.dp)
val ButtonShape = RoundedCornerShape(14.dp)
