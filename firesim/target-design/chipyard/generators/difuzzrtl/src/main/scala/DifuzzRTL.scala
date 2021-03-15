package difuzzrtl

import chisel3._
import chisel3.experimental.IO
import freechips.rocketchip.config.{Config, Field, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp}
import freechips.rocketchip.subsystem.BaseSubsystem

case class DifuzzRTLConfig()
case object DifuzzRTLKey extends Field[Option[DifuzzRTLConfig]](None)

class DifuzzRTLPortIO extends Bundle {
  val bridgeReset = Input(UInt(1.W))
  val covInit = Input(UInt(1.W))
  val metaReset = Input(UInt(1.W))
  val covSum = Output(UInt(30.W))
}

trait CanHaveDifuzzRTL { this: BaseSubsystem =>
  implicit val p: Parameters
}

trait CanHaveDifuzzRTLImp extends LazyModuleImp {
  val outer: CanHaveDifuzzRTL
  val difuzzrtl_io = p(DifuzzRTLKey).map { k =>
    dontTouch(IO(new DifuzzRTLPortIO))
  }
}

// DOC include start: WithDifuzzRTL
class WithDifuzzRTL extends Config((site, here, up) => {
  case DifuzzRTLKey => Some(DifuzzRTLConfig())
})
// DOC include end: WithDifuzzRTL
