package com.pyamsoft.tetherfi.trace

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pyamsoft.tetherfi.R
import com.pyamsoft.tetherfi.core.TraceLoggingManager
import kotlinx.android.synthetic.main.fragment_trace_console.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

class TraceConsoleFragment : Fragment() {

  @Inject lateinit var traceLoggingManager: TraceLoggingManager

  private val adapter = TraceAdapter()

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    return inflater.inflate(R.layout.fragment_trace_console, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    recycler_trace.layoutManager = LinearLayoutManager(requireContext())
    recycler_trace.adapter = adapter

    // Collect trace stream
    lifecycleScope.launch {
      traceLoggingManager.traceStream.collectLatest { line ->
        adapter.addLine(line)
        recycler_trace.scrollToPosition(adapter.itemCount - 1)
      }
    }
  }
}
