package com.pyamsoft.tetherfi.trace

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentContainerView

@Composable
fun TraceConsoleEntry(
    modifier: Modifier = Modifier,
) {
  AndroidView(
      modifier = modifier,
      factory = { context ->
        FragmentContainerView(context).apply {
          id = android.R.id.custom
          val fragmentManager = (context as? androidx.activity.ComponentActivity)?.supportFragmentManager
          if (fragmentManager != null && childFragmentManager.fragments.isEmpty()) {
            childFragmentManager.beginTransaction()
                .replace(android.R.id.custom, TraceConsoleFragment())
                .commit()
          }
        }
      }
  )
}
