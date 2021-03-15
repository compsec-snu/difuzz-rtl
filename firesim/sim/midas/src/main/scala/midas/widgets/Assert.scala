// See LICENSE for license details.

package midas.widgets

import chisel3._
import chisel3.util._
import chisel3.experimental.{DataMirror, Direction}

import scala.collection.immutable.ListMap

import freechips.rocketchip.config.{Parameters}
import freechips.rocketchip.util.{DecoupledHelper}

class AssertBundle(val numAsserts: Int) extends Bundle {
  val asserts = Output(UInt(numAsserts.W))
}

class AssertBridgeModule(assertMessages: Seq[String])(implicit p: Parameters) extends BridgeModule[HostPortIO[UInt]]()(p) {
  lazy val module = new BridgeModuleImp(this) {
    val numAsserts = assertMessages.size
    val io = IO(new WidgetIO())
    val hPort = IO(HostPort(Input(UInt(numAsserts.W))))
    val resume = WireInit(false.B)
    val cycles = RegInit(0.U(64.W))
    val asserts = hPort.hBits
    val assertId = PriorityEncoder(asserts)
    val assertFire = asserts.orR

    val stallN = (!assertFire || resume)

    val tFireHelper = DecoupledHelper(hPort.toHost.hValid, stallN)
    val targetFire = tFireHelper.fire
    hPort.toHost.hReady := tFireHelper.fire(hPort.toHost.hValid)
    // We only sink tokens, so tie off the return channel
    hPort.fromHost.hValid := true.B
    when (targetFire) {
      cycles := cycles + 1.U
    }

    genROReg(assertId, "id")
    genROReg(assertFire && hPort.toHost.hValid, "fire")
    // FIXME: no hardcode
    genROReg(cycles(31, 0), "cycle_low")
    genROReg(cycles >> 32, "cycle_high")
    Pulsify(genWORegInit(resume, "resume", false.B), pulseLength = 1)
    genCRFile()

    override def genHeader(base: BigInt, sb: StringBuilder) {
      import CppGenerationUtils._
      val headerWidgetName = getWName.toUpperCase
      super.genHeader(base, sb)
      sb.append(genConstStatic(s"${headerWidgetName}_assert_count", UInt32(assertMessages.size)))
      sb.append(genArray(s"${headerWidgetName}_assert_messages", assertMessages.map(CStrLit)))
    }
  }
}
