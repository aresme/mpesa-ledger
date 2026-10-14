package com.ares.mpesaledger.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ares.mpesaledger.R
import com.ares.mpesaledger.ui.theme.AresSurfaceHigh

/** The launcher mark on a rounded tile, for About and the welcome screen. */
@Composable
fun BrandMark(size: Int = 88) {
    Box(Modifier.size(size.dp).background(AresSurfaceHigh, RoundedCornerShape((size * 0.28f).dp)), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size((size * 1.7f).dp))
    }
}
