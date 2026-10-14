package com.ares.mpesaledger.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The few Material icons not in material-icons-core, as 24dp vectors (Apache 2.0 paths). */
object AppIcons {
    val PieChart: ImageVector by lazy {
        icon("PieChart", "M11,2v20c-5.07,-0.5 -9,-4.79 -9,-10s3.93,-9.5 9,-10zM13.03,2v8.99H22c-0.47,-4.74 -4.24,-8.52 -8.97,-8.99zM13.03,13.01V22c4.74,-0.47 8.5,-4.25 8.97,-8.99h-8.97z")
    }
    val ContentCopy: ImageVector by lazy {
        icon("ContentCopy", "M16,1H4c-1.1,0 -2,0.9 -2,2v14h2V3h12V1zM19,5H8c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2zM19,21H8V7h11v14z")
    }
    val Sms: ImageVector by lazy {
        icon("Sms", "M20,2H4c-1.1,0 -1.99,0.9 -1.99,2L2,22l4,-4h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM9,11H7V9h2v2zM13,11h-2V9h2v2zM17,11h-2V9h2v2z")
    }
    val FileOpen: ImageVector by lazy {
        icon("FileOpen", "M14,2H6c-1.1,0 -1.99,0.9 -1.99,2L4,20c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2V8l-6,-6zM16,18H8v-2h8v2zM16,14H8v-2h8v2zM13,9V3.5L18.5,9H13z")
    }
    val Battery: ImageVector by lazy {
        icon("Battery", "M15.67,4H14V2h-4v2H8.33C7.6,4 7,4.6 7,5.33v15.33C7,21.4 7.6,22 8.33,22h7.33c0.74,0 1.34,-0.6 1.34,-1.33V5.33C17,4.6 16.4,4 15.67,4z")
    }

    private fun icon(name: String, pathData: String) = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black)).build()
}
