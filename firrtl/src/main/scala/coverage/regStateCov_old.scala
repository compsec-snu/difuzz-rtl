// Instrumentation for register state coverage

package coverage

import java.io.{File, PrintWriter}

import firrtl.FirrtlProtos.Firrtl.BigInt
import firrtl._
import firrtl.ir._
import firrtl.Mappers._
import firrtl.PrimOps._

import scala.collection.immutable.Range
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

// Leger sweeps fir file, find regs and muxes
// It also discriminates control-flow related registers from data-flow related ones
class Ledger {
  private var moduleName: Option[String] = None
  private var module: Option[Module] = None
  var modules = mutable.Set[String]()
  private var moduleCtrlRegNum = mutable.Map[String, Int]()
  private var moduleCtrlRegBits = mutable.Map[String, Int]()
  private var moduleCtrlRegMap = mutable.Map[String, List[DefRegister]]()
  private var moduleRegMap = mutable.Map[String, List[DefRegister]]()
  private var moduleMuxCtrls = mutable.Map[String, List[Expression]]()
  private var moduleMuxNum = mutable.Map[String, Int]()
  private var moduleCovSize = mutable.Map[String, Int]()
  var totRegNum = 0
  var totRegBits = 0
  var totCtrlRegNum = 0
  var totCtrlRegBits = 0
  var totMuxNum = 0

  var moduleInst = mutable.Map[String, List[WDefInstance]]()

  private var isData: Int = 0

  def foundMux(muxCtrl: Expression): Unit = moduleName match {
    case None => sys.error("Module name not defined in Ledger!")
    case Some(name) =>
      moduleMuxCtrls(name) = moduleMuxCtrls.getOrElse(name, List[Expression]()) :+ muxCtrl
      moduleMuxNum(name) = moduleMuxNum.getOrElse(name, 0) + 1
      totMuxNum = totMuxNum + 1
    case _ => ()
  }

  def foundInst(defInst: WDefInstance): Unit = moduleName match {
    case None => sys.error("Module name not defined in Ledger!")
    case Some(name) =>
      moduleInst(name) = moduleInst.getOrElse(name, List[WDefInstance]()) :+ defInst
    case _ => ()
  }

  def foundReg(defReg: DefRegister): Unit = moduleName match {
    case None => sys.error("Module name not defined in Ledger!")
    case Some(name) =>
      val bits: Int = defReg.tpe match {
        case utpe: UIntType => utpe.width.asInstanceOf[IntWidth].width.toInt
        case stpe: SIntType => stpe.width.asInstanceOf[IntWidth].width.toInt
        case _ => throw new Exception(s"${defReg.name}, all widths must be explicitly defined in low firrtl")
      }
      moduleRegMap(name) = moduleRegMap.getOrElse(name, List[DefRegister]()) :+ defReg
      totRegNum = totRegNum + 1
      totRegBits = totRegBits + bits
      if (isControl(defReg)) {
        moduleCtrlRegMap(name) = moduleCtrlRegMap.getOrElse(name, List[DefRegister]()) :+ defReg
        moduleCtrlRegNum(name) = moduleCtrlRegNum.getOrElse(name, 0) + 1

        moduleCtrlRegBits(name) = moduleCtrlRegBits.getOrElse(name, 0) + bits
        totCtrlRegNum = totCtrlRegNum + 1
        totCtrlRegBits = totCtrlRegBits + bits
      }
    case _ => ()
  }

  def setModuleCovSize(name: String, size: Int): Unit = { moduleCovSize(name) = size }

  def getModuleCovSize(name: String): Int = moduleCovSize.getOrElse(name, 0)

  // Check whether the found register belong to control flow or data flow
  // We define  3 criteria to be data flow related register
  // 1) Should have multiple bits
  // 2) Should have direct flow of data (fan-in)
  // 3) Not related to branch (fan-out)
  def isControl(defReg: DefRegister): Boolean = {
    defReg.tpe match {
      case utpe: UIntType =>
        if (utpe.width.asInstanceOf[IntWidth].width == 1) {
          if (fanOutMux(defReg))
            return true
          else
            return false
        }
      case stpe: SIntType =>
        if (stpe.width.asInstanceOf[IntWidth].width == 1) {
          if (fanOutMux(defReg))
            return true
          else
            return false
        }
      case _ =>
        throw new Exception(s"${defReg.name}, all widths must be explicitly defined in low firrtl")
    }

    if (!fanInData(defReg)) {
      return true
    }

    if (fanOutMux(defReg)) {
      return true
    }

      false
  }

  // Recursively find all sources (Ports, Connection to instances, Literals),
  // and check whether comming from ports or connection to instances
  // Here we exclude reduced operation (xorr, orr, etc)
  // It should be optimized!!
  def fanInData(defReg: DefRegister): Boolean = {
    val buffer = findSource(defReg,
      module.getOrElse(throw new Exception("Reg found module must be internal")))

    buffer.nonEmpty
  }

  def fanOutMux(defReg: DefRegister): Boolean = {
    val name = moduleName.getOrElse(throw new Exception("Reg found module must be internal"))
    val muxCtrls = moduleMuxCtrls.getOrElse(name, List[Expression]())

    muxCtrls.exists(eqExpr(_, WRef(defReg)))
  }

  def findSource(sink: DefRegister, mod: Module): Seq[Expression] = {
    val body = mod.body
    val buffer = ArrayBuffer[Expression]()

    isData = 0
    recursiveFind(WRef(sink), buffer, body, ArrayBuffer[Expression]())

    buffer
  }

  // Recursively find all sources,
  // Some regs make circular data flow, which should be detected and stop finding
  // sink: want to find sources of sink
  // buf: returns the found sources
  def recursiveFind(sink: Expression, buf: ArrayBuffer[Expression],
                    stmt: Statement, loopCheck: ArrayBuffer[Expression]): Unit = {
    if (isData == 1)
      return

    val srcBuf = ArrayBuffer[Expression]()

    if (isLiteral(sink)) {
      return
    } else if (isDoPrim(sink)) {
      throw new Exception("DoPrim should have been factorized")
    } else if (isMemKind(sink)) {
      buf += sink
      isData = 1
      return
    } else if (isInstanceKind(sink)) {
      buf += sink
      isData = 1
      return
    } else if (isPortKind(sink)) {
      buf += sink
      isData = 1
      return
    } else if (sink.isInstanceOf[Mux]) {
      srcBuf += sink
    } else {
      stmt map findFlow(sink, srcBuf)
    }

    if (loopCheck.exists(eqExpr(_, sink))) {
      return
    } else {
      loopCheck += sink
    }

    if (srcBuf.isEmpty) {
      throw new Exception(s"No connection! $sink")
    }
    if (srcBuf.length > 1)
      throw new Exception("Low level firrtl should have only one connection per element")

    val srcExpr = srcBuf(0)

    srcExpr match {
      case UIntLiteral(_,_) | SIntLiteral(_,_) |
        WRef(_,_,_,_) | WSubField(_,_,_,_) | WSubIndex(_,_,_,_) | WSubAccess(_,_,_,_) =>
        recursiveFind(srcExpr, buf, stmt, loopCheck)
      case Mux(_, tval, fval, _) =>
        if (isDoPrim(tval) && !isReduceOp(tval.asInstanceOf[DoPrim])) {
          val args = findElems(tval.asInstanceOf[DoPrim].args)
          for (expr <- args) {
            recursiveFind(expr, buf, stmt, loopCheck)
          }
        } else {
          recursiveFind(tval, buf, stmt, loopCheck)
        }

        if (isDoPrim(fval) && !isReduceOp(fval.asInstanceOf[DoPrim])) {
          val args = findElems(fval.asInstanceOf[DoPrim].args)
          for (expr <- args) {
            recursiveFind(expr, buf, stmt, loopCheck)
          }
        } else {
          recursiveFind(fval, buf, stmt, loopCheck)
        }
      case ValidIf(_, value, _) =>
        if (isDoPrim(value) && !isReduceOp(value.asInstanceOf[DoPrim])) {
          val args = findElems(value.asInstanceOf[DoPrim].args)
          for (expr <- args) {
            recursiveFind(expr, buf, stmt, loopCheck)
          }
        } else {
          recursiveFind(value, buf, stmt, loopCheck)
        }
      case doPrim: DoPrim =>
        if (!isReduceOp(doPrim)) {
          val args = findElems(doPrim.args)
          for (expr <- args) {
            recursiveFind(expr, buf, stmt, loopCheck)
          }
        }
      case other =>
        throw new Exception(s"Connection src type: ${other.getClass.getName}")
    }
    loopCheck -= sink

  }

  // findFlow find first source connection to sink
  def findFlow(sink: Expression, buf: ArrayBuffer[Expression])(stmt: Statement): Statement = {

    val visited = stmt map findFlow(sink, buf)

    visited match {
      case con: Connect if eqExpr(con.loc, sink) =>
        if (sink.getClass.getSimpleName == "WSubField$") {
          print(s"findFlow sink: $sink\n")
        }
        buf += con.expr
      case defNode: DefNode if eqExpr(WRef(defNode), sink) =>
        buf += defNode.value
      case _ => ()
    }
    stmt
  }

  // check a equals b
  def eqExpr(a: Expression, b: Expression): Boolean = {
    if (a.getClass.getName != b.getClass.getName) {
      return false
    }

    a match {
      case aWRef: WRef =>
        val bWRef = b.asInstanceOf[WRef]
        (aWRef.serialize == bWRef.serialize)
      case aWSubField: WSubField =>
        val bWSubField = b.asInstanceOf[WSubField]
        (aWSubField.serialize == bWSubField.serialize)
      case aWSubIndex: WSubIndex =>
        val bWSubIndex = b.asInstanceOf[WSubIndex]
        (aWSubIndex.serialize == bWSubIndex.serialize)
      case aWSubAccess: WSubAccess =>
        val bWSubAccess = b.asInstanceOf[WSubAccess]
        (aWSubAccess.serialize == bWSubAccess.serialize)
      case _ =>
        false
    }
  }

  // Find all related expressions of DoPrim which is not Literal
  def findElems(args: Seq[Expression]): List[Expression] = {
    val list = args.toList
    list.flatMap(arg => arg match {
      case prim: DoPrim => findElems(prim.args)
      case x if (!(x.getClass.getSimpleName matches ".*Literal.*")) => List[Expression](x)
      case _ => List[Expression]()
    })
  }

  def isLiteral(expr: Expression): Boolean = {
    expr.getClass.getSimpleName matches ".*Literal.*"
  }

  def isDoPrim(expr: Expression): Boolean = {
    expr.getClass.getSimpleName == "DoPrim"
  }

  def isReduceOp(doprim: DoPrim): Boolean = {
    doprim.op match {
      case Lt | Leq | Gt | Geq | Eq | Neq => true
      case Andr | Orr | Xorr => true
      case _ => false
    }
  }

  def isMemKind(expr: Expression): Boolean = {
    expr.isInstanceOf[WSubField] &&
      expr.asInstanceOf[WSubField].expr.isInstanceOf[WSubField] &&
      expr.asInstanceOf[WSubField].expr.asInstanceOf[WSubField].expr.isInstanceOf[WRef] &&
      expr.asInstanceOf[WSubField].expr.asInstanceOf[WSubField].
        expr.asInstanceOf[WRef].kind.getClass.getSimpleName == "MemKind$"
  }

  def isInstanceKind(expr: Expression): Boolean = {
    expr.isInstanceOf[WSubField] &&
      expr.asInstanceOf[WSubField].expr.isInstanceOf[WRef] &&
      expr.asInstanceOf[WSubField].expr.asInstanceOf[WRef].
        kind.getClass.getSimpleName == "InstanceKind$"
  }

  def isPortKind(expr: Expression): Boolean = {
    expr.isInstanceOf[WRef] &&
      expr.asInstanceOf[WRef].kind.getClass.getSimpleName == "PortKind$"
  }

  def getModuleName: String = moduleName match {
    case None => Utils.error("Module name not defined in Ledger!")
    case Some(name) => name
  }

  def setModuleName(name: String): Unit = {
    modules += name
    moduleName = Some(name)
  }

  def setModule(mod: Option[Module]): Unit = {
    module = mod
  }

  def getModuleRegs(name: String): List[DefRegister] = {
    moduleRegMap.getOrElse(name, List[DefRegister]())
  }

  def getModuleCtrlRegs(name: String): List[DefRegister] = {
    moduleCtrlRegMap.getOrElse(name, List[DefRegister]())
  }

  def serialize: String = {
    modules map { name =>
      s"[$name regs] => ${moduleCtrlRegNum.getOrElse(name, 0)}, ${moduleCtrlRegBits.getOrElse(name, 0)}!\n" +
      s"[$name muxes] => ${moduleMuxNum.getOrElse(name, 0)}\n\n" +
      s"${
        moduleCtrlRegMap.getOrElse(name, List[DefRegister]()) map { defreg =>
          s"${defreg.name}, ${defreg.tpe}"
        } mkString "\n"
      }"
    } mkString "\n" + s"totRegNum: ${totRegNum}\ntotRegBits: ${totRegBits}\ntotCtrlRegNum: ${totCtrlRegNum}" +
        s"\ntotCtrlRegBits: ${totCtrlRegBits}\ntotMuxes: ${totMuxNum}"
  }
}

class StateCoverage extends Transform {
  val MAX_STATE_WIDTH = 20

  def inputForm: LowForm.type = LowForm

  def outputForm: LowForm.type = LowForm

  def execute(state: CircuitState): CircuitState = {
    val ledger = new Ledger()
    val circuit = state.circuit

    val findMux = false
    val findReg = true

    // First find all mux control signals
    // Then, find all regs and check whether the regs are control-flow related
    circuit map walkModule(ledger, findMux)
    circuit map walkModule(ledger, findReg)

    // Instrument register state coverage
    print(s"=================== Instrument Coverage Map ===================")
    val instrCircuit = circuit map instrCov(ledger, MAX_STATE_WIDTH)
    // Instrument metaReset
    print(s"==============================================================")
    print(s"==================== Instrument MetaReset ====================")
    val newCircuit = instrCircuit map instrReset(ledger)
    print(s"==============================================================")

    print(ledger.serialize)

    val hierWriter = new PrintWriter(new File("hierarchy.txt"))
    for (key <- ledger.modules) {
      val listInst = ledger.moduleInst.getOrElse(key, List[WDefInstance]())
      hierWriter.write(s"$key\t${listInst.length}\t${ledger.getModuleCovSize(key)}\n")
      listInst.map(i => hierWriter.write(s"\t${i.name}\t${i.module}\n"))
    }
    hierWriter.close()

    state.copy(newCircuit)
  }

  def walkModule(ledger: Ledger, findReg: Boolean)(m: DefModule): DefModule = {
    ledger.setModuleName(m.name)

    m match {
      case mod: Module =>
        ledger.setModule(Some(mod))
      case mod: ExtModule =>
        ledger.setModule(None)
    }
    m map walkStatement(ledger, findReg)
  }

  def walkStatement(ledger: Ledger, findReg: Boolean)(s: Statement): Statement = {
    if (!findReg) {
      s map walkExpression(ledger, findReg)
      s map walkStatement(ledger, findReg)

    } else {
      val visited = s map walkStatement(ledger, findReg)

      visited match {
        case defreg: DefRegister =>
          ledger.foundReg(defreg)
          s
        case definst: WDefInstance =>
          ledger.foundInst(definst)
          s
        case something => something
      }
    }
  }

  def walkExpression(ledger: Ledger, findReg: Boolean)(e: Expression): Expression = {
    val visited = e map walkExpression(ledger, findReg)

    visited match {
      case Mux(cond, tval, fval, tpe) =>
        ledger.foundMux(cond)
        e
      case notmux => notmux
    }
  }

  // Instrument register state coverage
  // Manages state_reg, prev_state_reg, cov, cov_sum
  // state_reg: random offset xors of registers in a module
  // prev_state_reg: cycle delayed reg of state_reg
  // cov: coverage map which updates state_reg ^ prev_state_reg index
  // cov_sum: sum of coverage map
  def instrCov(ledger: Ledger, max_state_width: Int = 20)(m: DefModule): DefModule = {

    val regs = ledger.getModuleCtrlRegs(m.name)

    val name_state = m.name + "_state"
    val name_cov = m.name + "_cov"
    val (total_bit_width, width_seq) = getBitWidth(regs)
    print(s"Instrument module, ${m.name}, total bit width: ${total_bit_width}\n")

    val (width_state, reg_offset) = total_bit_width match {
      case 0 => (0, Seq[(DefRegister, Int)]())
      case x if x <= max_state_width =>
        val offset = width_seq.foldLeft[Seq[Int]](Seq(0))(
          (seq, i) => seq :+ (seq.last + i)
        ).dropRight(1)
        (x, regs zip offset)
      case x if x > max_state_width =>
        val rand = Random
        val zip_reg_width = regs zip width_seq
        val filtered = zip_reg_width.filter{ case (_, i) => (i < max_state_width)}
        if (m.name == "DivSqrtRawFN_small") {
          print(s"filtered !!! ${filtered}\n")
        }
        if (filtered.length == 0) {
          (0, Seq[(DefRegister, Int)]())
        } else {
          val offsets = filtered.map{ case (reg, i) => (reg, rand.nextInt(max_state_width - i + 1))}
          (max_state_width, offsets)
        }
    }

    val size_cov = Math.pow(2, width_state).toInt

    val clockRef = (name: String) => WRef(name, ClockType, PortKind, SourceFlow)
    val initReg = (name: String, width: Int) =>
      WRef(name, UIntType(IntWidth(width)), RegKind, UnknownFlow)
    val defRegister = (name: String, info: String, clock_name: String, width: Int) =>
      DefRegister(FileInfo(StringLit(info)),
        name, UIntType(IntWidth(width)),
        clockRef(clock_name), UIntLiteral(0, IntWidth(1)), initReg(name, width))

    m match {
      case mod: Module  =>
        val block = mod.body.asInstanceOf[Block]
        val stmts = block.stmts

        val instances: Seq[WDefInstance] = ledger.moduleInst.getOrElse(mod.name, List[WDefInstance]()).toSeq
        val (clock_name, reset_name, has_clock_and_reset) = hasClockAndReset(mod)

        if (width_state != 0 && has_clock_and_reset && m.name != "Rob") {
          ledger.setModuleCovSize(m.name, if (size_cov == 1) 0 else size_cov)

          val stateReg = defRegister(name_state, s"Register tracking ${m.name} state", clock_name, width_state)
          val prevStateReg = defRegister(name_state + "_p", s"Cycle delayed ${name_state}", clock_name, width_state)

          val concatState = DefWire(FileInfo(StringLit("Concatenation of circuit states")),
            "state_concat", UIntType(IntWidth(width_state)))

          val (stateCov, covRef) = defMemory(name_cov, s"Coverage map for ${m.name}",
            size_cov, width_state)
          val covSum = defRegister(name_cov + "sum", s"Sum of coverage map", clock_name, max_state_width)
          val covSumPort = Port(NoInfo, "io_cov_sum", Output, UIntType(IntWidth(max_state_width)))

          val readSubField = WSubField(covRef, "read")
          val writeSubField = WSubField(covRef, "write")

          val stConnections = stateConnect(stateReg, prevStateReg, concatState, reg_offset, width_state)
          val rdConnections = readConnect(readSubField, concatState, covSum, clock_name, width_state)
          val wrConnections = writeConnect(writeSubField, concatState, clock_name, width_state)

          val ptConnections = portConnect(instances, covSumPort, covSum, max_state_width)

          val ports = mod.ports :+ covSumPort
          val newBlock = Block((stmts :+ stateReg :+ prevStateReg :+ concatState :+ stateCov :+ covSum)
            ++ stConnections ++ rdConnections ++ wrConnections ++ ptConnections)
          Module(mod.info, mod.name, ports, newBlock)
        } else {
          val covSum = DefWire(NoInfo, name_cov + "sum", UIntType(IntWidth(max_state_width)))
          val zero_cov = Connect(NoInfo, WRef(covSum), UIntLiteral(0, IntWidth(max_state_width)))
          val covSumPort = Port(NoInfo, "io_cov_sum", Output, UIntType(IntWidth(max_state_width)))
          val ptConnections = portConnect(instances, covSumPort, covSum, max_state_width)

          val ports = mod.ports :+ covSumPort
          val newBlock = Block((stmts :+ covSum :+ zero_cov) ++ ptConnections)
          Module(mod.info, mod.name, ports, newBlock)
        }
      case ext: ExtModule =>
        ext
      case other => other
    }
  }

  def hasClockAndReset(mod: Module): (String, String, Boolean) = {
    val ports = mod.ports
    val clock_name = ports.foldLeft[String]("None")((name, p) =>
      (if (p.name contains "clock") p.name
      else name))
    val reset_name = ports.foldLeft[String]("None")((name, p) =>
      (if (p.name contains "reset") p.name
      else name))

    val has_clock_and_reset = (clock_name != "None") && (reset_name != "None")

    (clock_name, reset_name, has_clock_and_reset)
  }

  def getBitWidth(regs: List[DefRegister]): (Int, Seq[Int]) = {
    val regWidth = (reg: DefRegister) => {
      val width = reg.tpe match {
        case UIntType(iw) => iw
        case SIntType(iw) => iw
        case _ => throw new Exception("Reg not UIntType or SIntType")
      }
      width match {
        case IntWidth(len) => len
        case _ => throw new Exception("Reg type width not IntWidth")
      }
    }

    val total_bit_width = regs.foldLeft[Int](0)((x, reg) => x + regWidth(reg).toInt)
    val seq_bit_width = regs.map(regWidth(_).toInt)
    (total_bit_width, seq_bit_width)
  }

  def defMemory(name: String, info: String, size: Int, width: Int): (DefMemory, WRef) = {
    val mem = DefMemory(FileInfo(StringLit(info)), name,
      UIntType(IntWidth(1)), size, 1, 0,
      ArrayBuffer("read"), ArrayBuffer("write"), ArrayBuffer())
    val ref = WRef(name, BundleType(ArrayBuffer(
      Field("read", Flip, BundleType(List(
        Field("addr", Default, UIntType(IntWidth(width))),
        Field("en", Default, UIntType(IntWidth(1))),
        Field("clk", Default, ClockType),
        Field("data", Flip, UIntType(IntWidth(1))))))
      ,
      Field("write", Flip, BundleType(List(
        Field("addr", Default, UIntType(IntWidth(width))),
        Field("mask", Default, UIntType(IntWidth(1))),
        Field("en", Default, UIntType(IntWidth(1))),
        Field("clk", Default, ClockType),
        Field("data", Default, UIntType(IntWidth(1)))
      )))))
      , MemKind, SourceFlow)

    (mem, ref)
  }

  def stateConnect(stateReg: DefRegister, prevStateReg: DefRegister, concatState: DefWire,
                   reg_offset: Seq[(DefRegister, Int)], width: Int): Seq[Statement] = {
    val xorListReverse = makeXor(reg_offset, width)
    val xorList = xorListReverse.reverse
    val xorRegs = xorListReverse(1)
    val stCons = ArrayBuffer[Statement]()

    val updateState = Connect(NoInfo,
      WRef(stateReg),
      Mux(WRef("reset", UIntType(IntWidth(1)), PortKind, SourceFlow),
        UIntLiteral(0, IntWidth(width)),
        WRef(xorRegs.asInstanceOf[DefWire])
      )
    )

    val updatePrevState = Connect(NoInfo,
      WRef(prevStateReg),
      Mux(WRef("reset", UIntType(IntWidth(1)), PortKind, SourceFlow),
        UIntLiteral(0, IntWidth(width)),
        WRef(stateReg)
      )
    )

    val connectState = Connect(NoInfo, WRef(concatState),
      DoPrim(Xor, Seq(WRef(stateReg), UIntLiteral(0, IntWidth(width))),
        Seq(), UIntType(IntWidth(width))))

    ((stCons :+ updateState :+ updatePrevState :+ connectState) ++ xorList).toSeq
  }

  // Recursively offset and xor all registers and connect
  def makeXor(reg_offset: Seq[(DefRegister, Int)], width: Int): Seq[Statement] = {
    val reg = reg_offset(0)._1
    val reg_width = (reg.tpe.asInstanceOf[UIntType].width.asInstanceOf[IntWidth].width).toInt
    val offset = reg_offset(0)._2
    val pad = width - offset - reg_width
    val new_reg_offset = reg_offset.drop(1)

    val shl_wire = DefWire(NoInfo, reg.name + "_shl", UIntType(IntWidth(offset + reg_width)))
    val pad_wire = DefWire(NoInfo, reg.name + "_pad", UIntType(IntWidth(width)))

    val shl_op = DoPrim(Shl, Seq(WRef(reg)), Seq(offset), UIntType(IntWidth(width)))
    val shl_con = Connect(NoInfo, WRef(shl_wire), shl_op)

    val pad_con = pad match {
      case 0 => Connect(NoInfo, WRef(pad_wire), WRef(shl_wire))
      case pad_size => Connect(NoInfo, WRef(pad_wire),
        DoPrim(Cat, Seq(UIntLiteral(0, IntWidth(pad_size)), WRef(shl_wire)), Seq(), UIntType(IntWidth(width))))
    }
    val xor_wire = DefWire(NoInfo, reg.name + "_xor", UIntType(IntWidth(width)))

    if (new_reg_offset.isEmpty) {
      val xor_con = Connect(NoInfo, WRef(xor_wire),
        DoPrim(Xor, Seq(WRef(pad_wire), UIntLiteral(0, IntWidth(width))), Seq(), UIntType(IntWidth(width))))
      ArrayBuffer[Statement](xor_con, xor_wire, pad_con, pad_wire, shl_con, shl_wire)
    } else {
      val intermediates = makeXor(new_reg_offset, width)
      val xor_con = Connect(NoInfo, WRef(xor_wire),
        DoPrim(Xor, Seq(WRef(pad_wire), WRef(intermediates(1).asInstanceOf[DefWire])),
          Seq(), UIntType(IntWidth(width))))
      ArrayBuffer[Statement](xor_con, xor_wire, pad_con, pad_wire, shl_con, shl_wire) ++ intermediates
    }
  }

  def readConnect(readSubField: WSubField, concatState: DefWire, covSum: DefRegister,
                  clock_name: String, width_state: Int): Seq[Statement] = {
    val rdCons = ArrayBuffer[Statement]()
    val readAddr = Connect(NoInfo,
      WSubField(readSubField, "addr", UIntType(IntWidth(width_state)), SinkFlow),
      WRef(concatState))

    val readEn = Connect(NoInfo,
      WSubField(readSubField, "en", UIntType(IntWidth(1)), SinkFlow),
      UIntLiteral(1, IntWidth(1)))

    val readClk = Connect(NoInfo,
      WSubField(readSubField, "clk", ClockType, SinkFlow),
      WRef(clock_name, ClockType, PortKind))

    val updateSum = Connect(NoInfo,
      WRef(covSum),
      Mux(WSubField(readSubField, "data", UIntType(IntWidth(1))),
        WRef(covSum),
        DoPrim(Add, Seq(WRef(covSum), UIntLiteral(1, IntWidth(1))), Seq(), UIntType(IntWidth(width_state)))
      ))

    (rdCons :+ readAddr :+ readEn :+ readClk :+ updateSum).toSeq
  }

  def writeConnect(writeSubField: WSubField, concatState: DefWire,
                   clock_name: String, width_state: Int): Seq[Statement] = {
    val wrCons = ArrayBuffer[Statement]()

    val writeAddr = Connect(NoInfo,
      WSubField(writeSubField, "addr", UIntType(IntWidth(width_state)), SinkFlow),
      WRef(concatState))

    val writeMask = Connect(NoInfo,
      WSubField(writeSubField, "mask", UIntType(IntWidth(1)), SinkFlow),
      UIntLiteral(1, IntWidth(1)))

    val writeEn = Connect(NoInfo,
      WSubField(writeSubField, "en", UIntType(IntWidth(1)), SinkFlow),
      UIntLiteral(1, IntWidth(1)))

    val writeClk = Connect(NoInfo,
      WSubField(writeSubField, "clk", ClockType, SinkFlow),
      WRef(clock_name, ClockType, PortKind))

    val updateCov = Connect(NoInfo,
      WSubField(writeSubField, "data", UIntType(IntWidth(1))),
      UIntLiteral(1, IntWidth(1)))

    (wrCons :+ writeAddr :+ writeMask :+ writeEn :+ writeClk :+ updateCov).toSeq
  }

  def portConnect(instances: Seq[WDefInstance], port: Port, covSum: Statement, max_state_width: Int): Seq[Statement] = {
    val portCons = ArrayBuffer[Statement]()
    val covSums = makeSum(instances, port, covSum, max_state_width)

    (portCons ++ covSums).toSeq
  }

  // Recursively add all the io_cov_sum results of internal instances
  def makeSum(instances: Seq[WDefInstance], port: Port, sum: Statement, max_state_width: Int): Seq[Statement] = {
    val sumRef = sum match {
      case defReg: DefRegister => WRef(defReg.asInstanceOf[DefRegister])
      case defWire: DefWire => WRef(defWire.asInstanceOf[DefWire])
      case _ => throw new Exception("Sum of coverages must be wire")
    }

    if (instances.isEmpty) {
      val portCon = Connect(NoInfo, WRef(port), sumRef)
      ArrayBuffer[Statement](portCon)
    } else {
      val instance = instances.head
      val new_instances = instances.drop(1)

      val inst_port_name = "io_cov_sum"

      val sum_wire = DefWire(NoInfo, instance.name + "_sum", UIntType(IntWidth(max_state_width)))

      val sum_con = Connect(NoInfo, WRef(sum_wire),
        DoPrim(Add, Seq(sumRef, WSubField(WRef(instance), inst_port_name)),
          Seq(), UIntType(IntWidth(max_state_width))))

      val intermediates = makeSum(new_instances, port, sum_wire, max_state_width)
      ArrayBuffer[Statement](sum_wire, sum_con) ++ intermediates

    }
  }

  // Intialize microarchitecture of DUT every iteration by using meta_reset
  // meta_reset initialize all registers to 0
  // Reset process goes as follow, meta_reset -> reset
  // Memories must be initialize manually (by zero writing instructions)
  def instrReset(ledger: Ledger)(m: DefModule): DefModule = {

    m match {
      case mod: Module =>
        val regs = ledger.getModuleRegs(mod.name)

        val metaResetPort = Port(NoInfo, "meta_reset", Input, UIntType(IntWidth(1)))
        val newModule = mod map instrResetStmt(regs, metaResetPort)

        val block = newModule.asInstanceOf[Module].body.asInstanceOf[Block]
        val stmts = block.stmts
        val instances: Seq[WDefInstance] = ledger.moduleInst.getOrElse(newModule.name, List[WDefInstance]()).toSeq

        val rstCons = ArrayBuffer[Statement]()
        val insResetPorts = ArrayBuffer[Port]()
        for (instance <- instances) {
          val insResetPort = Port(NoInfo, instance.name + "_halt", Input, UIntType(IntWidth(1)))

          val or_ins_reset = DoPrim(Or, Seq(WRef(metaResetPort), WRef(insResetPort)), Seq(), UIntType(IntWidth(1)))
          val rst_port_con = Connect(NoInfo, WSubField(WRef(instance), "meta_reset"), or_ins_reset)

          rstCons += rst_port_con
          insResetPorts += insResetPort
        }

        val ports = (newModule.ports :+ metaResetPort) ++ insResetPorts
        val newBlock = Block(stmts ++ rstCons)

        Module(newModule.info, newModule.name, ports, newBlock)
      case ext: ExtModule =>
        ext
      case other => other
    }
  }

  def instrResetStmt(regs: List[DefRegister], meta_reset: Port)(s: Statement): Statement = {
    val visited = s map instrResetStmt(regs, meta_reset)

    visited match {
      case Connect(info, loc, expr) if regs.exists(r => r.name == loc.serialize) =>
        val reg = regs.find(r => r.name == loc.serialize).get
        val width = reg.tpe match {
          case utpe: UIntType =>
            utpe.width.asInstanceOf[IntWidth].width
          case stpe: SIntType =>
            stpe.width.asInstanceOf[IntWidth].width
          case _ =>
            throw new Exception("Register type must be one of uint or sint")
        }
        Connect(info, loc, Mux(WRef(meta_reset), UIntLiteral(0, IntWidth(width)), expr))
      case other => other
    }
  }
}


