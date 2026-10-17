package com.tteumsae.app.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Shared spacing scale. Use the role names for component padding. */
object TteumSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp

    val screenHorizontal = lg
    val cardPadding = md
    val fieldHorizontal = md
    val fieldVertical = sm
    val rowHorizontal = md
    val rowVertical = sm
    val sectionGap = lg
    val itemGap = sm
    val inlineGap = xs
    val badgeHorizontal = xs
    val badgeVertical = xxs
    val dialogPadding = lg
    val sheetHorizontal = lg
}

/** Four corner radii; circles and fully rounded handles retain their own geometry. */
object TteumRadius {
    val badge = 8.dp
    val compact = 12.dp
    val control = 16.dp
    val card = control
    val dialog = 24.dp
    val sheet = dialog
}

object TteumShapes {
    val badge = RoundedCornerShape(TteumRadius.badge)
    val compact = RoundedCornerShape(TteumRadius.compact)
    val control = RoundedCornerShape(TteumRadius.control)
    val card = RoundedCornerShape(TteumRadius.card)
    val dialog = RoundedCornerShape(TteumRadius.dialog)
    val sheet = RoundedCornerShape(topStart = TteumRadius.sheet, topEnd = TteumRadius.sheet)

    val material = Shapes(
        extraSmall = badge,
        small = compact,
        medium = card,
        large = dialog,
        extraLarge = dialog,
    )
}

/** Insets for the content inside a surface, independent of its outer margins. */
object TteumInsets {
    val button = PaddingValues(horizontal = TteumSpacing.md, vertical = TteumSpacing.sm)
    val card = PaddingValues(TteumSpacing.cardPadding)
    val field = PaddingValues(
        horizontal = TteumSpacing.fieldHorizontal,
        vertical = TteumSpacing.fieldVertical,
    )
    val row = PaddingValues(
        horizontal = TteumSpacing.rowHorizontal,
        vertical = TteumSpacing.rowVertical,
    )
    val dialog = PaddingValues(TteumSpacing.dialogPadding)
    val badge = PaddingValues(
        horizontal = TteumSpacing.badgeHorizontal,
        vertical = TteumSpacing.badgeVertical,
    )
}

object TteumSizing {
    val touchTarget = 48.dp
    val buttonHeight = 56.dp
    val inputMinHeight = 64.dp
    // Two columns still fit a 360dp screen with 24dp side margins and a 12dp gap.
    val placeCardMinWidth = 144.dp
}
