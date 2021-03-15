package firesim.bridges

import midas.widgets._

import chisel3._
import chisel3.util._
import chisel3.experimental.{DataMirror, Direction}
import freechips.rocketchip.config.Parameters
import difuzzrtl.DifuzzRTLPortIO

class DifuzzRTLTargetIO extends Bundle {
  val clock = Input(Clock())
  val difuzzrtl_io = Flipped(new DifuzzRTLPortIO)
}

case class DifuzzRTLKey()

class DifuzzRTLBridge(implicit p: Parameters) extends BlackBox
    with Bridge[HostPortIO[DifuzzRTLTargetIO], DifuzzRTLBridgeModule] {

  val io = IO(new DifuzzRTLTargetIO)

  val bridgeIO = HostPort(io)

  val constructorArg = Some(DifuzzRTLKey())

  generateAnnotations()
}

object DifuzzRTLBridge {
  def apply(clock: Clock, difuzzrtl_io: DifuzzRTLPortIO)(implicit p: Parameters): DifuzzRTLBridge = {
    val ep = Module(new DifuzzRTLBridge)
    ep.io.difuzzrtl_io <> difuzzrtl_io
    ep.io.clock := clock
    ep
  }
}

class DifuzzRTLBridgeModule(key: DifuzzRTLKey)(implicit p: Parameters) extends BridgeModule[HostPortIO[DifuzzRTLTargetIO]]()(p) {
  lazy val module = new BridgeModuleImp(this) {
    val io = IO(new WidgetIO())
    val hPort = IO(HostPort(new DifuzzRTLTargetIO))

    // val txfifo = Module(new Queue(UInt(30.W), 128))

    val bridgeReset = dontTouch(Reg(UInt(1.W)))
    val covInit = Reg(UInt(1.W))
    val metaReset = Reg(UInt(1.W))
    val covSum = Reg(UInt(30.W))

    val target = hPort.hBits.difuzzrtl_io

    val fire = hPort.toHost.hValid &&
               hPort.fromHost.hReady

    hPort.toHost.hReady := fire
    hPort.fromHost.hValid := fire

    target.bridgeReset := bridgeReset
    target.covInit := covInit
    target.metaReset := metaReset
    covSum := target.covSum
    // txfifo.io.enq.bits := covSum
    // txfifo.io.enq.valid := 0.U

    // genROReg(txfifo.io.deq.bits, "out_bits")
    // genROReg(txfifo.io.deq.valid, "out_valid")

    genROReg(covSum, "out_covSum")
    genWORegInit(covInit, "in_covInit", false.B)
    genWORegInit(metaReset, "in_metaReset", false.B)
    genWORegInit(bridgeReset, "in_bridgeReset", false.B)

    genCRFile()
  }
}
