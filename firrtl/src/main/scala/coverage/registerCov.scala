// Modified version,
// Instrumentation for control coverage

package coverage

import java.io.{File, PrintWriter}

import firrtl._
import firrtl.ir._
import firrtl.Mappers._

import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.collection.{Map, Set, mutable}

object moduleInfo {
  def apply(mod: DefModule, gLedger: graphLedger): moduleInfo = {
    val ctrlSrcs = gLedger.findMuxSrcs
    val muxSrcs = gLedger.getMuxSrcs
    var insts = gLedger.getInstances
    val regs = gLedger.findRegs
    val vecRegs = gLedger.findVecRegs
    val dirInRegs = gLedger.findDirInRegs

    new moduleInfo(mod.name, ctrlSrcs, muxSrcs, insts, regs, vecRegs, dirInRegs)
  }
}

class moduleInfo(val mName: String,
                 val ctrlSrcs: Map[String, Set[Node]],
                 val muxSrcs: Map[Mux, Set[String]],
                 var insts: Set[WDefInstance],
                 val regs: Set[DefRegister],
                 val vecRegs: Set[Tuple3[Int, String, Set[String]]],
                 val dirInRegs: Set[DefRegister]) {

  var covSize: Int = 0
  var regNum: Int = 0
  var ctrlRegNum: Int = 0
  var muxNum: Int = 0
  var regBitWidth: Int = 0
  var ctrlRegBitWidth: Int = 0
  var ctrlBitWidth: Int = 0
  var assertReg: Option[DefRegister] = None

  def printInfo(): Unit = {
    print(s"${mName} Information\n")
  }

  def saveCovResult(instrCov: InstrCov): Unit = {
    covSize = instrCov.covMapSize
    regNum = regs.size
    ctrlRegNum = ctrlSrcs("DefRegister").size
    regBitWidth = regs.toSeq.map(reg => reg.tpe match {
      case utpe: UIntType => utpe.width.asInstanceOf[IntWidth].width.toInt
      case stpe: SIntType => stpe.width.asInstanceOf[IntWidth].width.toInt
      case _ => throw new Exception(s"${reg.name} does not have IntType\n")
    }).sum

    ctrlRegBitWidth = ctrlSrcs("DefRegister").toSeq.map(reg => {
      reg.node.asInstanceOf[DefRegister].tpe match {
        case utpe: UIntType => utpe.width.asInstanceOf[IntWidth].width.toInt
        case stpe: SIntType => stpe.width.asInstanceOf[IntWidth].width.toInt
        case _ => throw new Exception(s"${reg.name} does not have IntType\n")
      }
    }).sum

    ctrlBitWidth = instrCov.totBitWidth
    muxNum = muxSrcs.size
  }
}

class regCoverage extends Transform {

  def inputForm: LowForm.type = LowForm
  def outputForm: LowForm.type = LowForm

  val moduleInfos = mutable.Map[String, moduleInfo]()
  def execute(state: CircuitState): CircuitState = {
    val circuit = state.circuit

    print("==================== Finding Control Registers ====================\n")
    for (m <- circuit.modules) {
      val gLedger = new graphLedger(m)

      gLedger.parseModule
      moduleInfos(m.name) = moduleInfo(m, gLedger)
      gLedger.printLog()
    }

    print("===================================================================\n")

    print("====================== Instrumenting Coverage =====================\n")

    // Change env variable topModule to either RocketTile or BoomTile
    val topModule = sys.env("TOPMODULE")
    // val innerModules = moduleInfos.map(tuple => {
    //   (tuple._1, findModules(topModule, tuple._1))
    // }).filter(_._2 > 0).map(_._1).toSeq

    var targetModules = Seq[String]()
    val l1Modules = if (topModule == "BoomTile")
      Seq("BoomFrontend", "TLXbar_8", "PTW", "BoomCore",
        "LSU", "BoomNonBlockingDCache", "IntXbar_4", "HellaCacheArbiter")
      else
      Seq("PTW", "HellaCacheArbiter", "TLXbar_8", "DCache", "TLBuffer_13",
        "Rocket", "FPU", "IntXbar_4", "Frontend", "TLXbar_9")

    moduleInfos(topModule).insts = moduleInfos(topModule).insts
      .filter(i => l1Modules.contains(i.module))

    for (mName <- l1Modules) {
      val tModules = moduleInfos.map(tuple => {
        (tuple._1, findModules(mName, tuple._1))
      }).filter(_._2 > 0).map(_._1).toSeq
      targetModules = targetModules ++ tModules
    }
    val innerModules = (targetModules :+ topModule).toSet

    val extModules = circuit.modules.filter(_.isInstanceOf[ExtModule]).map(_.name)
    val instrCircuit = circuit map { m: DefModule =>
      if (innerModules.contains(m.name)) {
        val instrCov = new InstrCov(m, moduleInfos(m.name), extModules)
        val mod = instrCov.instrument()
        instrCov.printLog()

        moduleInfos(m.name).saveCovResult(instrCov)
        mod
      } else m
    }

    print("===================================================================\n")

    // print("====================== Instrumenting metaAssert ===================\n")
    // val assertCircuit = instrCircuit map { m: DefModule =>
    //   val instrAssert = new InstrAssert(m, moduleInfos(m.name))
    //   val mod = instrAssert.instrument()
    //   // convertStop.printLog()
    //   mod
    // }

    // print("===================================================================\n")

    print("====================== Instrumenting MetaReset ====================\n")

    val metaResetCircuit = instrCircuit map { m: DefModule =>
      if (innerModules.contains(m.name)) {
        val instrReset = new InstrReset(m, moduleInfos(m.name))
        val mod = instrReset.instrument()
        instrReset.printLog()
        mod
      } else m
    }

    print("===================================================================\n")

    print("====================== Instrumenting Toplevel Module ==============\n")

    val finalCircuit = metaResetCircuit map { m: DefModule =>
      val instrTop = new InstrTop(m, topModule, innerModules, moduleInfos(m.name))
      instrTop.instrument()
    }

    print("===================================================================\n")

    print("\n====================== Instrumentation Summary ==================\n")
    printSummary(circuit.main)
    print("===================================================================\n")

    /* Dump hierarchy of the modules to instrument system tasks  */
    val hierWriter = new PrintWriter(new File(s"${finalCircuit.main}_hierarchy.txt"))
    for ((mName, mInfo) <- moduleInfos) {
      val insts = mInfo.insts
      hierWriter.write(s"$mName\t${insts.size}\t${mInfo.covSize}\n")
      insts.map(inst => hierWriter.write(s"\t${inst.module}\t${inst.name}\n"))
    }
    hierWriter.close()

    /* Emit Lowered firrtl after DifuzzRTL Instrumentation */
    val lowFormWriter = new PrintWriter(new File(s"${finalCircuit.main}_low.fir"))
    lowFormWriter.write(finalCircuit.serialize)
    lowFormWriter.close()

    state.copy(finalCircuit)
  }

  def printSummary(topName: String) : Unit = {
    assert(moduleInfos.size > 0, "printSummary must be called after instrumentation\n")

    val moduleNums: Map[String, Int] = moduleInfos.map(tuple => {
      (tuple._1, findModules(topName, tuple._1))
    }).toMap

    val totRegNum = moduleInfos.foldLeft(0)((totNum, tuple) => {
      totNum + (tuple._2.regNum * moduleNums(tuple._1))
    })

    val totCtrlRegNum = moduleInfos.foldLeft(0)((totNum, tuple) => {
      totNum + (tuple._2.ctrlRegNum * moduleNums(tuple._1))
    })

    val totMuxNum = moduleInfos.foldLeft(0)((totNum, tuple) => {
      totNum + (tuple._2.muxNum * moduleNums(tuple._1))
    })

    val totRegBitWidth = moduleInfos.foldLeft(0)((totBitWidth, tuple) => {
      totBitWidth + (tuple._2.regBitWidth * moduleNums(tuple._1))
    })

    val totCtrlRegBitWidth = moduleInfos.foldLeft(0)((totBitWidth, tuple) => {
      totBitWidth + (tuple._2.ctrlRegBitWidth * moduleNums(tuple._1))
    })

    val totCtrlBitWidth_optimized = moduleInfos.foldLeft(0)((totBitWidth, tuple) => {
      totBitWidth + (tuple._2.ctrlBitWidth * moduleNums(tuple._1))
    })

    val totMuxBitWidth = totMuxNum

    print(s"Total number of registers: ${totRegNum}\n" +
      s"Total number of control registers: ${totCtrlRegNum}\n" +
      s"Total number of muxes: ${totMuxNum}\n" +
      s"Total bit width of registers: ${totRegBitWidth}\n" +
      s"Total bit width of control registers: ${totCtrlRegBitWidth}\n" +
      s"Optimized total bit width of control registers: ${totCtrlBitWidth_optimized}\n" +
      s"Total bit width of muxes: ${totMuxBitWidth}\n"
    )
  }

  def findModules(topName: String, moduleName: String): Int = {
    if (topName == moduleName) 1
    else {
      moduleInfos(topName).insts.foldLeft(0)((num, inst) => {
        num + findModules(inst.module, moduleName)
      })
    }
  }
}
