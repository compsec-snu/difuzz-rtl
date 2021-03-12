// Instrumentation for metaReset

package coverage

import firrtl.PrimOps.Or
import firrtl._
import firrtl.ir._

import scala.collection.mutable.ListBuffer

class InstrReset(mod: DefModule, mInfo: moduleInfo) {
  private val mName = mod.name
  private val insts = mInfo.insts
  private val regs = if (mInfo.assertReg != None) (mInfo.regs.toSeq :+ mInfo.assertReg.get).toSet else mInfo.regs
  private val regNames = regs.map(_.name)

  def printLog(): Unit = {
    print("=============================================\n")
    print(s"${mName}\n")
    print("---------------------------------------------\n")
    insts.foreach(inst => print(s"- [${inst.module}]: ${inst.name}\n"))
    print("=============================================\n")
  }

  def instrument(): DefModule = {
    mod match {
      case mod: Module => {
        val stmts = mod.body.asInstanceOf[Block].stmts
        val metaResetPort = Port(NoInfo, "metaReset", Input, UIntType(IntWidth(1)))
        val newStmts = stmts.map(addMetaReset(metaResetPort))

        val resetCons = ListBuffer[Statement]()
        val instResetPorts = ListBuffer[Port]()
        for (inst <- insts) {
          val instResetPort = Port(NoInfo, inst.name + "_halt", Input, UIntType(IntWidth(1)))

          val instReset = DoPrim(Or, Seq(WRef(metaResetPort), WRef(instResetPort)), Seq(), UIntType(IntWidth(1)))
          val instResetCons = Connect(NoInfo, WSubField(WRef(inst), "metaReset"), instReset)

          resetCons.append(instResetCons)
          instResetPorts.append(instResetPort)
        }

        val ports = (mod.ports :+ metaResetPort) ++ instResetPorts
        val newBlock = Block(newStmts ++ resetCons)

        Module(mod.info, mName, ports, newBlock)
      }
      case ext: ExtModule => ext
      case other => other
    }
  }

  def addMetaReset(metaReset: Port)(s: Statement): Statement = {
    s match {
      // All components are connected exactly once
      case Connect(info, loc, expr) if regs.exists(r => r.name == loc.serialize) =>
        val width = regs.find(r => r.name == loc.serialize).getOrElse(
          throw new Exception(s"${loc.serialize} is not in registers")
        ).tpe match {
          case utpe: UIntType => utpe.width.asInstanceOf[IntWidth].width
          case stpe: SIntType => stpe.width.asInstanceOf[IntWidth].width
          case _ =>
            throw new Exception("Register type must be one of UInt/SInt")
        }
        Connect(info, loc, Mux(WRef(metaReset), UIntLiteral(0, IntWidth(width)), expr))
      case other =>
        other.mapStmt(addMetaReset(metaReset))
    }
  }
}
