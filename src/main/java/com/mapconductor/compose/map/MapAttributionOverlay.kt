package com.mapconductor.compose.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.Color
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.widget.TextView

@Composable
internal fun MapAttributionOverlay(attributions: List<String>) {
    val html = attributions.joinToString(" | ")
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                TextView(context).apply {
                    setBackgroundColor(Color.argb(217, 255, 255, 255))
                    setTextColor(Color.rgb(34, 34, 34))
                    textSize = 10f
                    gravity = Gravity.END
                    movementMethod = LinkMovementMethod.getInstance()
                    setPadding(5, 2, 5, 2)
                }
            },
            update = { view ->
                view.text = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
            },
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    // 下辺は地図にぴったり付ける。浮かせると「地図の外の帯」に見えてしまい、
                    // どの地図に対する出典なのかが伝わりにくい。3 プラットフォームとも同じ。
                    .padding(end = 4.dp)
                    .fillMaxWidth(0.95f)
                    .wrapContentHeight(),
        )
    }
}
