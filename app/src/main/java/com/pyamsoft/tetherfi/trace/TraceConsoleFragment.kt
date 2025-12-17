package com.pyamsoft.tetherfi.trace

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pyamsoft.tetherfi.core.TraceLoggingManager
import com.pyamsoft.tetherfi.databinding.FragmentTraceConsoleBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

class TraceConsoleFragment : Fragment() {

  @Inject lateinit var traceLoggingManager: TraceLoggingManager

  private val adapter = TraceAdapter()
  private var binding: FragmentTraceConsoleBinding? = null

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    binding = FragmentTraceConsoleBinding.inflate(inflater, container, false)
    return binding?.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val b = binding ?: return
    b.recyclerTrace.layoutManager = LinearLayoutManager(requireContext())
    b.recyclerTrace.adapter = adapter

    // Collect trace stream
    lifecycleScope.launch {
      traceLoggingManager.traceStream.collectLatest { line ->
        adapter.addLine(line)
        b.recyclerTrace.scrollToPosition(adapter.itemCount - 1)
      }
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    binding = null
  }
}
