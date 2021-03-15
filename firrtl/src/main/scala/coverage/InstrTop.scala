package coverage

import firrtl._
import firrtl.ir._

import scala.collection.mutable.ListBuffer

class InstrTop(mod: DefModule, topModuleName: String, innerModules: Set[String], mInfo: moduleInfo, covSumSize: Int=30) {
  private val mName = mod.name
  private val targetInsts = mInfo.insts.filter(x => {
    innerModules.contains(x.module) && !x.module.contains("SimpleLazyModule")
  })

  def instrument(): DefModule = mod match {
    case m: Module => {
      val stmts = m.body.asInstanceOf[Block].stmts
      if (mod.name == "DigitalTop") {
        val topModule = targetInsts.filter(_.module == topModuleName).toSeq.head
        print(s"Toplevel ${mod.name} Instrumentation -- targetInst: ${topModule.module}\n")
        val covInit = m.ports.filter(_.name == "difuzzrtl_io_covInit").head
        val covSum = m.ports.filter(_.name == "difuzzrtl_io_covSum").head
        val metaReset = m.ports.filter(_.name == "difuzzrtl_io_metaReset").head

        val initCon = Connect(NoInfo, WSubField(WRef(topModule), "io_covInit"), WRef(covInit))
        val sumCon = Connect(NoInfo, WRef(covSum), WSubField(WRef(topModule), "io_covSum"))
        val resetCon = Connect(NoInfo, WSubField(WRef(topModule), "metaReset"), WRef(metaReset))

        val newBlock = Block(stmts :+ initCon :+ sumCon :+ resetCon)

        Module(m.info, mName, m.ports, newBlock)
      } else if (!innerModules.contains(mod.name) && !targetInsts.isEmpty) {
        print(s"Toplevel ${mod.name} Instrumentation -- targetInsts: ${targetInsts.map(_.module)}\n")
        val covInit = DefWire(NoInfo, "covInit", UIntType(IntWidth(1)))
        val metaReset = DefWire(NoInfo, "metaReset", UIntType(IntWidth(1)))
        val initCovInit = Connect(NoInfo, WRef(covInit), UIntLiteral(0, IntWidth(1)))
        val initReset = Connect(NoInfo, WRef(metaReset), UIntLiteral(0, IntWidth(1)))

        val resetCons = ListBuffer[Statement]()
        for (inst <- targetInsts) {
          val instCovInitCon = Connect(NoInfo, WSubField(WRef(inst), "io_covInit"), WRef(covInit))
          val instResetCon = Connect(NoInfo, WSubField(WRef(inst), "metaReset"), WRef(metaReset))
          resetCons.append(instCovInitCon)
          resetCons.append(instResetCon)
        }

        val newBlock = Block((stmts :+ covInit :+ metaReset :+ initCovInit :+ initReset) ++ resetCons)

        Module(m.info, mName, m.ports, newBlock)
      } else m
    }
    case ext: ExtModule => ext
    case other => other
  }
}
