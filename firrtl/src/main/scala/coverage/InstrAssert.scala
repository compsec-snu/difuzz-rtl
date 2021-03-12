package coverage

import firrtl.PrimOps.Or
import firrtl.ir._
import firrtl._

import scala.collection.mutable.ListBuffer

class InstrAssert(mod: DefModule, mInfo: moduleInfo) {
  private val mName = mod.name
  private val insts = mInfo.insts

  def instrument(): DefModule = {
    mod match {
      case m: Module => {
        val block = m.body
        val stmts = block.asInstanceOf[Block].stmts
        val (clockName, resetName, hasClockReset) = hasClockAndReset(m)

        val assertPort = Port(NoInfo, "metaAssert", Output, UIntType(IntWidth(1)))

        val stops = ListBuffer[Stop]()
        findStop(stops)(block)

        val stopEns = stops.zipWithIndex.map(tup => DefNode(NoInfo, s"stopEn${tup._2}", tup._1.en))
        val instAsserts = insts.map(inst =>
          (inst, DefWire(NoInfo, s"${inst.name}_metaAssert_wire", UIntType(IntWidth(1)))))
        val instAssertCons = instAsserts.map(tup =>
          Connect(NoInfo, WRef(tup._2), WSubField(WRef(tup._1), "metaAssert"))
        )

        val (topOr, orStmts) = makeOr(stopEns.map(en => WRef(en)) ++ instAsserts.map(tup => WRef(tup._2)), 0)

        val conStmts = if (hasClockReset && topOr != None) {
          val assertReg = DefRegister(NoInfo, s"${mName}_metaAssert", UIntType(IntWidth(1)),
            WRef(clockName, ClockType, PortKind, SourceFlow),
            UIntLiteral(0, IntWidth(1)),
            WRef(s"${mName}_metaAssert", UIntType(IntWidth(1)), RegKind, UnknownFlow))
          mInfo.assertReg = Some(assertReg)
          val or = DoPrim(Or, Seq(WRef(assertReg), topOr.get), Seq(), UIntType(IntWidth(1)))
          val assertRegCon = Connect(NoInfo, WRef(assertReg), or)
          val portCon = Connect(NoInfo, WRef(assertPort), WRef(assertReg))
          Seq[Statement](assertReg, assertRegCon, portCon)
        } else if (topOr != None) {
          val portCon = Connect(NoInfo, WRef(assertPort), topOr.get)
          Seq[Statement](portCon)
        } else {
          val portCon = Connect(NoInfo, WRef(assertPort), UIntLiteral(0))
          Seq[Statement](portCon)
        }

        val ports = (mod.ports :+ assertPort)
        val newStmts = stmts ++ stopEns ++ instAsserts.map(tup => tup._2) ++ instAssertCons ++ orStmts ++ conStmts
        val newBlock = Block(newStmts)
        Module(mod.info, mName, ports, newBlock)
      }
      case ext: ExtModule => ext
      case other => other
    }
  }

  def makeOr(stopEns: Seq[WRef], id: Int): (Option[WRef], Seq[Statement]) = {
    stopEns.length match {
      case 0 => (None, Seq[Statement]())
      case 1 => {
        (Some(stopEns.head), Seq[Statement]())
      }
      case 2 => {
        val or_wire = DefWire(NoInfo, mName + s"_or${id}", UIntType(IntWidth(1)))
        val or_op = DoPrim(Or, stopEns, Seq(), UIntType(IntWidth(1)))
        val or_connect = Connect(NoInfo, WRef(or_wire), or_op)
        (Some(WRef(or_wire)), Seq[Statement](or_wire, or_connect))
      }
      case _ => {
        val (or1, stmts1) = makeOr(stopEns.splitAt(stopEns.length / 2)._1, 2 * id + 1)
        val (or2, stmts2) = makeOr(stopEns.splitAt(stopEns.length / 2)._2, 2 * id + 2)
        val or_wire = DefWire(NoInfo, mName + s"_or${id}", UIntType(IntWidth(1)))
        val or_op = DoPrim(Or, Seq(or1.get, or2.get), Seq(), UIntType(IntWidth(1)))
        val or_connect = Connect(NoInfo, WRef(or_wire), or_op)
        (Some(WRef(or_wire)), stmts1 ++ stmts2 :+ or_wire :+ or_connect)
      }
    }
  }

  def findStop(stops: ListBuffer[Stop])(stmt: Statement): Unit = stmt match {
    case stop: Stop =>
      stops.append(stop)
    case s: Statement =>
      s foreachStmt findStop(stops)
  }

  def hasClockAndReset(mod: Module): (String, String, Boolean) = {
    val ports = mod.ports
    val (clockName, resetName) = ports.foldLeft[(String, String)](("None", "None"))(
      (tuple, p) => {
        if (p.name == "clock") (p.name, tuple._2)
        else if (p.name contains "reset") (tuple._1, p.name)
        else tuple
      })
    val hasClockAndReset = (clockName != "None") && (resetName != "None")

    (clockName, resetName, hasClockAndReset)
  }
}
