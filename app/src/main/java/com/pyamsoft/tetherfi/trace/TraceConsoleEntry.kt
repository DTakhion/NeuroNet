package com.pyamsoft.tetherfi.trace

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentActivity


@Composable
fun TraceConsoleEntry(
    modifier: Modifier = Modifier,
) {
  AndroidView(
      modifier = modifier,
      factory = { context ->
        // Create a container view for the Fragment
        val container = FragmentContainerView(context).apply {
          // Use a generated id to avoid collisions
          id = View.generateViewId()
        }

        // Safely obtain the activity's FragmentManager and add the fragment once
        val activity = context as? FragmentActivity //intento de arreglo para despliegue (27/12/2025)
        val fragmentManager = activity?.supportFragmentManager
        if (fragmentManager != null) {
          // Only add the fragment if this container doesn't already have one
          val existing = fragmentManager.findFragmentById(container.id)
          if (existing == null) {
            fragmentManager.beginTransaction()
                .replace(container.id, TraceConsoleFragment())
                .commitNowAllowingStateLoss()
          }
        }

        container
      }
  )
}
