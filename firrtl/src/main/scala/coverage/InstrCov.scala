// Modified version,
// Instrumentation for control coverage

// Controls: Elements which are wired into mux select signal
// Sources of mux: 1) Module input, 2) Register, 3) Instantiated module

// Instrumentations steps
// 1) Build graph of muxes, registers, instantiated modules
// 2) Finds all sources of muxes
// 3) Instrument only satisfying elements

package coverage

import firrtl._
import firrtl.ir._
import firrtl.PrimOps._

import scala.collection.mutable.ArrayBuffer
import scala.collection.{Map, Set, mutable}
import scala.util.Random

class InstrCov(mod: DefModule, mInfo: moduleInfo, extModules: Seq[String], val maxStateSize: Int = 20, val covSumSize: Int=30) {
  private val mName = mod.name

  private val ctrlSrcs = mInfo.ctrlSrcs
  private val muxSrcs = mInfo.muxSrcs
  private val insts = mInfo.insts.filter(x => !extModules.contains(x.module))
  private val vecRegs: Set[Tuple3[Int, String, Set[String]]] = mInfo.vecRegs
  private val dirInRegs: Set[DefRegister] = mInfo.dirInRegs.filter(regWidth(_) > 3)
  // Names of all vector registers (Set[String])
  private val vecRegNames = vecRegs.flatMap(tuple => {
    val idx = tuple._1
    val prefix = tuple._2
    val bodySet = tuple._3
    (0 until idx).toList.flatMap(x => bodySet.map(x.toString + _)).map(prefix + _)
  }).toSet

  private val regStateName = mName + "_state"
  private val covMapName = mName + "_cov"
  private val covSumName = mName + "_covSum"

  private val ctrlRegs = ctrlSrcs("DefRegister").map(
    _.node.asInstanceOf[DefRegister]).filter(
    !dirInRegs.contains(_))
  private val largeRegs = ctrlRegs.filter(regWidth(_) >= maxStateSize)
  private val smallRegs = ctrlRegs.filter(regWidth(_) < maxStateSize)
  private val scalaRegs = smallRegs.filter(x => !vecRegNames.contains(x.name))

  // vectorRegisters can also have large width over maxStateSize => filter out
  private val vectorRegs = smallRegs.filter(x => vecRegNames.contains(x.name))

  private var ctrlSigs = Seq[Expression]()
  for (reg <- largeRegs) {
    ctrlSigs = ctrlSigs ++ muxSrcs.filter(tuple => tuple._2.contains(reg.name)).map(tuple => tuple._1.cond)
  }

  private var coveredMuxSrcs = Seq[Expression]()
  for (reg <- smallRegs) {
    coveredMuxSrcs = coveredMuxSrcs ++ muxSrcs.filter(tuple => tuple._2.contains(reg.name)).map(tuple => tuple._1.cond)
  }

  private var optRegs = Seq[DefRegister]()
  for (reg <- scalaRegs) {
    val width = regWidth(reg).toInt
    var sinkMuxes = muxSrcs.filter(tuple => tuple._2.contains(reg.name)).map(tuple => tuple._1.cond).toSet

    if (sinkMuxes.size.toInt >= width) {
      optRegs = optRegs :+ reg
    } else {
      ctrlSigs = ctrlSigs ++ sinkMuxes.toSeq
    }
  }
  ctrlSigs = ctrlSigs.toSet.toSeq
  coveredMuxSrcs = coveredMuxSrcs.toSet.toSeq

  private val unCoveredCtrlSigs = ctrlSigs.filter(m => !coveredMuxSrcs.contains(m))

  // Map of (prefix, maxIdx) -> Set(first elements of vector registers)
  private val firstVecRegs: Map[(String, Int), Set[DefRegister]] = vecRegs.map(tuple => {
    val firstRegs = tuple._3.flatMap(body => {
      vectorRegs.find(_.name == (tuple._2 + "0" + body))
    })
    ((tuple._2, tuple._1), firstRegs)
  }).toMap
  val numOptRegs = (optRegs ++ firstVecRegs.values.toSet.flatten).size

  // Want to set same field in all the vector elements to the same location
  val (totBitWidth, regStateSize, ctrlOffsets) =
    getRegState((optRegs ++ firstVecRegs.values.toSet.flatten).toSeq, unCoveredCtrlSigs)

  private val scalaOffsets = ctrlOffsets.filter(tuple => tuple._1.getClass.getSimpleName match {
    case "DefRegister" => !firstVecRegs.values.toSet.flatten.contains(tuple._1.asInstanceOf[DefRegister])
    case _ => true
  })

  // Set of (first elements of vector registers, offset) (Set[(FirrtlNode, Int)])
  private val firstVecOffsets = (ctrlOffsets.toSet -- scalaOffsets.toSet)

  // Map of (prefix, maxIdx) -> Set[(body name, offset)]
  private val vecRegsOffsets: Map[(String, Int), Set[(String, Int)]] = firstVecRegs.map(tuple => {
    val prefix = tuple._1._1
    val set = tuple._2
    val newSet = set.map(x => {
      val regOffset = firstVecOffsets.find(tup => tup._1 == x).getOrElse(
        throw new Exception("firstVecOffsets should be in firstVecRegs\n")
      )
      val name = regOffset._1.asInstanceOf[DefRegister].name
      val offset = regOffset._2
      (name.substring((prefix + "0").length, name.length), offset)
    })
    (tuple._1, newSet)
  })

  // Set of (register name, offset)
  private val vecNameOffsets = vecRegsOffsets.flatMap(tuple => {
    val idx = tuple._1._2
    val prefix = tuple._1._1
    val bodyOffsets = tuple._2
    (0 until idx).toList.flatMap(x => {
      bodyOffsets.map(tup => (x.toString + tup._1, tup._2))
    }).map(tup => (prefix + tup._1, tup._2))
  })

  // Set of (DefRegister, offset)
  private val vectorOffsets = vecNameOffsets.map(tuple => {
    val reg = vectorRegs.find(_.name == tuple._1).getOrElse(
      throw new Exception("vectorOffsets should be in vectorRegs\n")
    )
    (reg, tuple._2)
  })


  private val allOffsets = (scalaOffsets ++ vectorOffsets)

  val covMapSize = if (regStateSize == 0) 0 else Math.pow(2, regStateSize).toInt

  def printLog(): Unit = {
    // val bitOptRegs = optRegs.foldLeft(0)((b, r) => b + regWidth(r).toInt)
    // val bitVecRegs = firstVecRegs.values.toSet.flatten.foldLeft(0)((b, r) => b + regWidth(r).toInt)
    // val bitDirInRegs = dirInRegs.foldLeft(0)((b, r) => b + regWidth(r).toInt)
    // val bitCtrlSigs = ctrlSigs.length
    // val bitUncoveredCtrlSigs = unCoveredCtrlSigs.length
    // val bitLargeRegs = largeRegs.foldLeft(0)((b, r) => b + regWidth(r).toInt)
    // val bitMuxes = muxSrcs.map(_._1.cond.serialize).toSet.size

    print("=============================================\n")
    print(s"${mName}\n")
    print("---------------------------------------------\n")
    print(s"regStateSize: ${regStateSize}, totBitWidth: ${totBitWidth}, numRegs: ${ctrlRegs.size}\n")
    // print(s"[Offsets]\n" + ctrlOffsets.map(tuple => tuple._1 match {
    //   case reg: DefRegister => s"${reg.name}: ${tuple._2}"
    //   case expr => s"${expr.serialize}: ${tuple._2}"
    // }).mkString("\n") + "\n")
    print(s"numOptRegs: ${numOptRegs}\n")

    // print("vectorOffsets\n")
    // vectorOffsets.foreach(tuple => print(s"[${tuple._1.name}] -- [${tuple._2}]\n"))
    // print("\n")

    // print(s"[Widths]\n" +
    //   s"optRegs: ${bitOptRegs}, largeRegs: ${bitLargeRegs}, vecRegs: ${bitVecRegs}\n" +
    //   s"ctrlSigs: ${bitCtrlSigs}, uncoveredCtrlSigs: ${bitUncoveredCtrlSigs}\n" +
    //   s"dirInRegs: ${bitDirInRegs}\n" +
    //   s"totMuxes: ${bitMuxes}\n"
    // )
    print("=============================================\n")
  }

  def instrument(): DefModule = {

    val clockRef = (name: String) => WRef(name, ClockType, PortKind, SourceFlow)
    val initReg = (name: String, width: Int) =>
      WRef(name, UIntType(IntWidth(width)), RegKind, UnknownFlow)
    val defRegister = (name: String, info: String, clock_name: String, width: Int) =>
      DefRegister(FileInfo(StringLit(info)),
        name, UIntType(IntWidth(width)),
        clockRef(clock_name), UIntLiteral(0, IntWidth(1)), initReg(name, width))

    mod match {
      case mod: Module => {
        val stmts = mod.body.asInstanceOf[Block].stmts
        val (clockName, resetName, hasCNR) = hasClockAndReset(mod)

        if (regStateSize != 0 && hasCNR) {

          val regState = defRegister(regStateName, s"Register tracking ${mName} state",
            clockName, regStateSize)
          val (covMap, covRef) = defMemory(covMapName, s"Coverage map for ${mName}",
            covMapSize, regStateSize)
          val covSum = defRegister(covSumName, s"Sum of coverage map", clockName, covSumSize)

          val covSumPort = Port(NoInfo, "io_covSum", Output, UIntType(IntWidth(covSumSize)))

          val readSubField = WSubField(covRef, "read")
          val writeSubField = WSubField(covRef, "write")

          val stConnections = stateConnect(regState, allOffsets, regStateSize)
          val rdConnections = readConnect(readSubField, regState, covSum, clockName, regStateSize)
          val wrConnections = writeConnect(writeSubField, regState, clockName, regStateSize)

          val ptConnections = portConnect(insts.toSeq, covSumPort, covSum)

          val ports = mod.ports :+ covSumPort
          val newBlock = Block((stmts :+ regState :+ covMap :+ covSum)
            ++ stConnections ++ rdConnections ++ wrConnections ++ptConnections)

          Module(mod.info, mName, ports, newBlock)
        } else {
          val covSum = DefWire(NoInfo, covSumName, UIntType(IntWidth(covSumSize)))
          val zeroCov = Connect(NoInfo, WRef(covSum), UIntLiteral(0, IntWidth(covSumSize)))
          val covSumPort = Port(NoInfo, "io_covSum", Output, UIntType(IntWidth(covSumSize)))
          val ptConnections = portConnect(insts.toSeq, covSumPort, covSum)

          val ports = mod.ports :+ covSumPort
          val newBlock = Block((stmts :+ covSum :+ zeroCov) ++ ptConnections)
          Module(mod.info, mName, ports, newBlock)
        }
      }
      case ext: ExtModule => ext
      case other => other
    }
  }

  def readConnect(readSubField: WSubField, regState: DefRegister, covSum: DefRegister,
                  clockName: String, regStateSize: Int): Seq[Statement] = {
    val rdCons = Seq[Statement]()
    val readAddr = Connect(NoInfo,
      WSubField(readSubField, "addr", UIntType(IntWidth(regStateSize)), SinkFlow),
      WRef(regState))

    val readEn = Connect(NoInfo,
      WSubField(readSubField, "en", UIntType(IntWidth(1)), SinkFlow),
      UIntLiteral(1, IntWidth(1)))

    val readClk = Connect(NoInfo,
      WSubField(readSubField, "clk", ClockType, SinkFlow),
      WRef(clockName, ClockType, PortKind))

    val updateSum = Connect(NoInfo,
      WRef(covSum),
      Mux(WSubField(readSubField, "data", UIntType(IntWidth(1))),
        WRef(covSum),
        DoPrim(Add, Seq(WRef(covSum), UIntLiteral(1, IntWidth(1))), Seq(), UIntType(IntWidth(covSumSize)))
      ))

    (rdCons :+ readAddr :+ readEn :+ readClk :+ updateSum)
  }

  def writeConnect(writeSubField: WSubField, regState: DefRegister,
                   clockName: String, regStateSize: Int): Seq[Statement] = {
    val wrCons = Seq[Statement]()
    val writeAddr = Connect(NoInfo,
      WSubField(writeSubField, "addr", UIntType(IntWidth(regStateSize)), SinkFlow),
      WRef(regState))

    val writeMask = Connect(NoInfo,
      WSubField(writeSubField, "mask", UIntType(IntWidth(1)), SinkFlow),
      UIntLiteral(1, IntWidth(1)))

    val writeEn = Connect(NoInfo,
      WSubField(writeSubField, "en", UIntType(IntWidth(1)), SinkFlow),
      UIntLiteral(1, IntWidth(1)))

    val writeClk = Connect(NoInfo,
      WSubField(writeSubField, "clk", ClockType, SinkFlow),
      WRef(clockName, ClockType, PortKind))

    val updateCov = Connect(NoInfo,
      WSubField(writeSubField, "data", UIntType(IntWidth(1))),
      UIntLiteral(1, IntWidth(1)))

    (wrCons :+ writeAddr :+ writeMask :+ writeEn :+ writeClk :+ updateCov)
  }

  def portConnect(insts: Seq[WDefInstance], port: Port, covSum: Statement): Seq[Statement] = {
    val portCons = Seq[Statement]()
    val covSums = makeSum(insts, port, covSum)

    (portCons ++ covSums)
  }

  def makeSum(insts: Seq[WDefInstance], port: Port, sum: Statement): Seq[Statement] = {
    val sumRef = sum match {
      case defReg: DefRegister => WRef(defReg.asInstanceOf[DefRegister])
      case defWire: DefWire => WRef(defWire.asInstanceOf[DefWire])
      case _ => throw new Exception("Sum of coverages must be wire")
    }

    if (insts.isEmpty) {
      val portCon = Connect(NoInfo, WRef(port), sumRef)
      Seq[Statement](portCon)
    } else {
      val inst = insts.head
      val new_insts = insts.drop(1)

      val instPortName = "io_covSum"
      val sumWire = DefWire(NoInfo, inst.name + "_sum", UIntType(IntWidth(covSumSize)))

      val sumCon = Connect(NoInfo, WRef(sumWire),
        DoPrim(Add, Seq(sumRef, WSubField(WRef(inst), instPortName)),
          Seq(), UIntType(IntWidth(covSumSize))))

      Seq(sumWire, sumCon) ++ makeSum(new_insts, port, sumWire)
    }
  }

  def stateConnect(regState: DefRegister, ctrlOffsets: Seq[(FirrtlNode, Int)],
                   regStateSize: Int): Seq[Statement] = {
    val (padRefs, offsetStmts) = makeOffset(ctrlOffsets, regStateSize)
    val (topXor, xorStmts) = makeXor(padRefs, regStateSize, 0)

    val stConnect = Connect(NoInfo, WRef(regState), topXor)

    offsetStmts ++ xorStmts :+ stConnect
  }

  def makeOffset(ctrlOffsets: Seq[(FirrtlNode, Int)], regStateSize: Int): (Seq[WRef], Seq[Statement]) = {
    var refs = Seq[WRef]()
    var stmts = Seq[Statement]()

    var i = -1
    var tmpWires = mutable.Map[Expression, DefWire]()
    val tmpStmts = ctrlOffsets.foldLeft(Seq[Statement]())(
      (seq, tuple) => tuple._1 match {
        case reg: DefRegister => seq
        case expr => { //TODO through exception when unexpected events
          i = i + 1
          val ctrlTmp = DefWire(NoInfo, s"mux_cond_${i}", UIntType(IntWidth(1)))
          tmpWires(expr.asInstanceOf[Expression]) = ctrlTmp
          seq ++ Seq[Statement](
            ctrlTmp,
            Connect(NoInfo, WRef(ctrlTmp), expr.asInstanceOf[Expression])
          )
        }
      }
    )
    stmts = stmts ++ tmpStmts

    val tmpOffsets = ctrlOffsets.map(tuple => tuple._1 match {
      case reg: DefRegister => (reg, tuple._2)
      case expr => (tmpWires(expr.asInstanceOf[Expression]), tuple._2)
    })

    for ((ctrl, offset) <- tmpOffsets) {
      val ctrlType = ctrl match {
        case reg: DefRegister => reg.tpe
        case wire: DefWire => wire.tpe
        case _ =>
          throw new Exception(s"${ctrl} is not DefRegister/DefWire")
      }

      val ctrlWidth = ctrlType match {
        case utpe: UIntType =>
          utpe.width.asInstanceOf[IntWidth].width.toInt
        case stpe: SIntType =>
          stpe.width.asInstanceOf[IntWidth].width.toInt
        case _ =>
          throw new Exception(s"${ctrl} doesn't have UIntType/SIntType")
      }
      val pad = regStateSize - ctrlWidth - offset
      val shl_wire = DefWire(NoInfo, ctrl.name + "_shl", UIntType(IntWidth(ctrlWidth + offset)))
      val pad_wire = DefWire(NoInfo, ctrl.name + "_pad", UIntType(IntWidth(regStateSize)))

      val ref = ctrl match {
        case reg: DefRegister => WRef(reg)
        case wire: DefWire => WRef(wire)
      }
      val shl_op = DoPrim(Shl, Seq(ref), Seq(offset), UIntType(IntWidth(regStateSize)))
      val shl_connect = Connect(NoInfo, WRef(shl_wire), shl_op)

      val pad_connect = pad match {
        case 0 => Connect(NoInfo, WRef(pad_wire), WRef(shl_wire))
        case pad_size => Connect(NoInfo, WRef(pad_wire),
          DoPrim(Cat, Seq(UIntLiteral(0, IntWidth(pad_size)),
            WRef(shl_wire)), Seq(), UIntType(IntWidth(regStateSize))))
      }

      refs = refs :+ WRef(pad_wire)
      stmts = stmts ++ Seq[Statement](shl_wire, shl_connect, pad_wire, pad_connect)
    }

    (refs, stmts)
  }

  // Recursive and divide and conquer manner Xor wiring
  def makeXor(padRefs: Seq[WRef], regStateSize: Int, id: Int): (WRef, Seq[Statement]) = {
    padRefs.length match {
      case 1 => {
        (padRefs.head, Seq[Statement]())
      }
      case 2 => {
        val xor_wire = DefWire(NoInfo, mName + s"_xor${id}", UIntType(IntWidth(regStateSize)))
        val xor_op = DoPrim(Xor, Seq(padRefs.head, padRefs.last), Seq(), UIntType(IntWidth(regStateSize)))
        val xor_connect = Connect(NoInfo, WRef(xor_wire), xor_op)
        (WRef(xor_wire), Seq[Statement](xor_wire, xor_connect))
      }
      case _ => {
        val (xor1, stmts1) = makeXor(padRefs.splitAt(padRefs.length / 2)._1, regStateSize, 2 * id + 1)
        val (xor2, stmts2) = makeXor(padRefs.splitAt(padRefs.length / 2)._2, regStateSize, 2 * id + 2)
        val xor_wire = DefWire(NoInfo, mName + s"_xor${id}", UIntType(IntWidth(regStateSize)))
        val xor_op = DoPrim(Xor, Seq(xor1, xor2), Seq(), UIntType(IntWidth(regStateSize)))
        val xor_connect = Connect(NoInfo, WRef(xor_wire), xor_op)
        (WRef(xor_wire), stmts1 ++ stmts2 :+ xor_wire :+ xor_connect)
      }
    }
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
        Field("data", Flip, UIntType(IntWidth(1)))
      ))),
      Field("write", Flip, BundleType(List(
        Field("addr", Default, UIntType(IntWidth(width))),
        Field("mask", Default, UIntType(IntWidth(1))),
        Field("en", Default, UIntType(IntWidth(1))),
        Field("clk", Default, ClockType),
        Field("data", Default, UIntType(IntWidth(1)))
      )))
    )),
      MemKind, SourceFlow)

    (mem, ref)
  }

  // Get RegState width and RegOffset values
  def getRegState(regs: Seq[DefRegister], ctrls: Seq[Expression]): (Int, Int, Seq[(FirrtlNode, Int)]) = {
    val totBitWidth = regs.foldLeft[Int](0)((x, reg) => x + regWidth(reg).toInt) + ctrls.size

    val widthSeq = regs.toSeq.map(regWidth(_).toInt) ++ ctrls.map(x => 1)
    val zipWidth = (regs ++ ctrls) zip widthSeq

    totBitWidth match {
      case 0 => (totBitWidth, 0, Seq[(FirrtlNode, Int)]())
      case x if x <= maxStateSize => {
        var sum_offset = 0
        (totBitWidth, x, zipWidth.map(tuple => {
          val offset = sum_offset
          sum_offset = sum_offset + tuple._2
          (tuple._1 , offset)
        }).toSeq)
      }
      case x => {
        val rand = Random
        val offsets = zipWidth.map { case (x, i) => (x, rand.nextInt(maxStateSize - i + 1)) }
        (totBitWidth, maxStateSize, offsets)
      }
    }
  }

  def regWidth(reg: DefRegister): Int = {
    val width = reg.tpe match {
      case UIntType(iw) => iw
      case SIntType(iw) => iw
      case _ => throw new Exception("Reg not UIntType or SIntType")
    }
    width match {
      case IntWidth(len) => len.toInt
      case _ => throw new Exception("Reg type width not IntWidth")
    }
  }

  def hasClockAndReset(mod: Module): (String, String, Boolean) = {
    val ports = mod.ports
    val (clockName, resetName) = ports.foldLeft[(String, String)](("None", "None"))(
      (tuple, p) => {
        if (p.name == "clock" || p.name == "gated_clock") (p.name, tuple._2)
        else if (p.name contains "reset") (tuple._1, p.name)
        else tuple
      })
    val hasClockAndReset = (clockName != "None") // && (resetName != "None")

    (clockName, resetName, hasClockAndReset)
  }
}



