package autocomplete

import com.codahale.metrics.MetricRegistry

object GlobalReg {
  val reg = new MetricRegistry()
}
