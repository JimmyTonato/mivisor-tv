package com.mivisor.tv.ui.design

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Iconos vectoriales (los de la v18, blancos): se tiñen al dibujarlos. */
object MvIcons {
    val Home = R.drawable.ic_home
    val Calendar = R.drawable.ic_calendar
    val Tv = R.drawable.ic_tv
    val Search = R.drawable.ic_search
    val Star = R.drawable.ic_star
    val Settings = R.drawable.ic_settings
    val Play = R.drawable.ic_play
    val Live = R.drawable.ic_live
    val Add = R.drawable.ic_add
    val Delete = R.drawable.ic_delete
    val Refresh = R.drawable.ic_refresh
    val Shield = R.drawable.ic_shield
    val Info = R.drawable.ic_info
    val Phone = R.drawable.ic_phone
    val Layers = R.drawable.ic_layers
    val History = R.drawable.ic_history
    val Close = R.drawable.ic_close
    val Bolt = R.drawable.ic_bolt
    val Edit = R.drawable.ic_edit
    val List = R.drawable.ic_list
    val Grid = R.drawable.ic_grid
    val ChevronRight = R.drawable.ic_chevron_right
    val Clock = R.drawable.ic_clock
    val Trophy = R.drawable.ic_trophy
    val Link = R.drawable.ic_link
}

@Composable
fun MvIcon(@DrawableRes id: Int, modifier: Modifier = Modifier, size: Dp = 20.dp, tint: Color = MvColors.Text) {
    Image(
        painter = painterResource(id),
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint)
    )
}
