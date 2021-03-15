package coverage

import firrtl.PrimOps.Or
import firrtl._
import firrtl.ir._

import scala.collection.mutable.ListBuffer

class InstrBridgeReset(m: DefModule) {
  assert(m.name == "FPGATop")

  private val mod = m.asInstanceOf[Module]
  private val instStmts = ListBuffer[Statement]()
  mod.body foreachStmt find(s => s match {
    case inst: DefInstance => true
    case winst: WDefInstance => true
    case _ => false
  })(instStmts)
  private val insts = instStmts.map(_.asInstanceOf[WDefInstance])
  private val reset = mod.ports.filter(_.name.contains("reset")).head

  def printLog() = {
    print("=============================================\n")
    print(s"${m.name}\n")
    print("---------------------------------------------\n")
    insts.foreach(i => print(s"${i.name}\n"))
    print("=============================================\n")
  }

  def instrument(): DefModule = {
    val stmts = mod.body.asInstanceOf[Block].stmts

    val difuzzRTLBridgeInst = insts.
      filter(i => i.module == "DifuzzRTLBridgeModule").
      head
    val simMasterInst = insts.filter(i => i.module == "SimulationMaster")
    val peekPokeInst = insts.filter(i => i.module == "PeekPokeBridgeModule")
    val serialInst = insts.filter(i => i.module == "SerialBridgeModule")
    val FASEDMemoryTimingModel = insts.filter(i => i.module == "FASEDMemoryTimingModel")
    val blockDevInst = insts.filter(i => i.module == "BlockDevBridgeModule")
    val UARTInst = insts.filter(i => i.module == "UARTBridgeModule")
    val TracerVInst = insts.filter(i => i.module == "TracerVBridgeModule")
    val clockModule = insts.filter(i => i.module == "ClockBridgeModule")
    val loadMemWidget = insts.filter(i => i.module == "LoadMemWidget")

    val bridge_reset = DefWire(NoInfo, "bridge_reset", UIntType(IntWidth(1)))
    val convertReset = (insts: Seq[WDefInstance], bReset: DefWire) => (stmt: Statement) =>
      stmt match {
        case con: Connect if (con.expr == WRef(reset.name, UIntType(IntWidth(1)), PortKind, SourceFlow) &&
          insts.map(_.name).exists(i => con.loc.serialize.contains(i))) =>
          Connect(con.info, con.loc,
            DoPrim(Or, Seq(WRef(reset), WRef(bReset)), Seq(), UIntType(IntWidth(1))))
        case other => other
      }
    val newStmts = mod.body mapStmt convert(convertReset(simMasterInst ++ peekPokeInst ++ loadMemWidget, bridge_reset))

    val conBridgeReset = Connect(NoInfo, WRef(bridge_reset),
      WSubField(WRef(difuzzRTLBridgeInst), "hPort_hBits_difuzzrtl_io_bridgeReset", UIntType(IntWidth(1)))
    )

    val newBlock = Block(Seq(newStmts, bridge_reset, conBridgeReset))
    Module(mod.info, mod.name, mod.ports, newBlock)
  }

  def find(func: Statement => Boolean)(nodes: ListBuffer[Statement])(stmt: Statement): Unit = stmt match {
    case s if (func(s)) => nodes.append(s)
    case other =>
      other foreachStmt find(func)(nodes)
  }

  def convert(func: Statement => Statement)(stmt: Statement): Statement = {
    func(stmt) mapStmt convert(func)
  }
}
