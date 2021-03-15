package chipyard.unittest

import chisel3._
import freechips.rocketchip.config.Parameters

class TestHarness(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle { val success = Output(Bool()) })
  io.success := Module(new UnitTestSuite).io.finished
}
