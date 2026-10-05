package com.popuchoco.puricaremini

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AppIcons {
    val Bluetooth: ImageVector by lazy {
        ImageVector.Builder("Bluetooth", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(17.71f, 7.71f)
                lineTo(12f, 2f)
                lineTo(11f, 2f)
                lineTo(11f, 9.59f)
                lineTo(6.41f, 5f)
                lineTo(5f, 6.41f)
                lineTo(10.59f, 12f)
                lineTo(5f, 17.59f)
                lineTo(6.41f, 19f)
                lineTo(11f, 14.41f)
                lineTo(11f, 22f)
                lineTo(12f, 22f)
                lineTo(17.71f, 16.29f)
                lineTo(13.41f, 12f)
                close()
                moveTo(13f, 5.83f)
                lineTo(14.88f, 7.71f)
                lineTo(13f, 9.59f)
                close()
                moveTo(14.88f, 16.29f)
                lineTo(13f, 18.17f)
                lineTo(13f, 14.41f)
                close()
            }
        }.build()
    }

    val Power: ImageVector by lazy {
        ImageVector.Builder("Power", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(11f, 3f)
                lineTo(13f, 3f)
                lineTo(13f, 12f)
                lineTo(11f, 12f)
                close()
                moveTo(16.56f, 5.44f)
                lineTo(15.11f, 6.89f)
                curveTo(16.84f, 7.94f, 18f, 9.83f, 18f, 12f)
                curveTo(18f, 15.31f, 15.31f, 18f, 12f, 18f)
                curveTo(8.69f, 18f, 6f, 15.31f, 6f, 12f)
                curveTo(6f, 9.83f, 7.16f, 7.94f, 8.88f, 6.88f)
                lineTo(7.44f, 5.44f)
                curveTo(5.36f, 6.88f, 4f, 9.28f, 4f, 12f)
                curveTo(4f, 16.42f, 7.58f, 20f, 12f, 20f)
                curveTo(16.42f, 20f, 20f, 16.42f, 20f, 12f)
                curveTo(20f, 9.28f, 18.64f, 6.88f, 16.56f, 5.44f)
                close()
            }
        }.build()
    }

    val Air: ImageVector by lazy {
        ImageVector.Builder("Air", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 7f); lineTo(15f, 7f); lineTo(15f, 9f); lineTo(3f, 9f); close()
                moveTo(3f, 11f); lineTo(21f, 11f); lineTo(21f, 13f); lineTo(3f, 13f); close()
                moveTo(3f, 15f); lineTo(17f, 15f); lineTo(17f, 17f); lineTo(3f, 17f); close()
            }
        }.build()
    }
}
