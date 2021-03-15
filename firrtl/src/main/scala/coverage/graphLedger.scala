package coverage

import firrtl._
import firrtl.ir._

import scala.collection.mutable.ListBuffer
import scala.collection.{Map, Set, mutable}

// graphLedger sweeps fir file, build graphs of elements
object Node {
  val types = Set("Port", "DefWire", "DefRegister", "DefNode", "DefMemory", "DefInstance", "WDefInstance")

  def apply(node: FirrtlNode): Node = {
    assert(Node.types.contains(node.getClass.getSimpleName),
      s"${node.serialize} is not an instance of Port/DefStatement\n")

    val name = node match {
      case port: Port => port.name
      case wire: DefWire => wire.name
      case reg: DefRegister => reg.name
      case nod: DefNode => nod.name
      case mem: DefMemory => mem.name
      case inst: DefInstance => inst.name
      case winst: WDefInstance => winst.name
      case _ =>
        throw new Exception(s"${node.serialize} does not have name")
    }
    new Node(node, name)
  }

  def findName(expr: Expression): String = expr match {
    case WRef(refName, _, _, _) => refName
    case WSubField(e, _, _, _) => findName(e)
    case WSubIndex(e, _, _, _) => findName(e)
    case WSubAccess(e, _, _, _) => findName(e)
    case Reference(refName, _) => refName
    case SubField(e, _, _) => findName(e)
    case SubIndex(e, _, _) => findName(e)
    case SubAccess(e, _, _) => findName(e)
    case _ => // Mux, DoPrim, etc
      throw new Exception(s"${expr.serialize} does not have statement")
  }

  def findNames(expr: Expression): ListBuffer[String] = expr match {
    case WRef(refName, _, _, _) => ListBuffer(refName)
    case WSubField(e, _, _, _) => findNames(e)
    case WSubIndex(e, _, _, _) => findNames(e)
    case WSubAccess(e, _, _, _) => findNames(e)
    case Reference(refName, _) => ListBuffer(refName)
    case SubField(e, _, _) => findNames(e)
    case SubIndex(e, _, _) => findNames(e)
    case SubAccess(e, _, _) => findNames(e)
    case Mux(_, tval, fval, _) => findNames(tval) ++ findNames(fval)
    case DoPrim(_, args, _, _) => {
      var list = ListBuffer[String]()
      for (arg <- args) {
        list = list ++ findNames(arg)
      }
      list
    }
    case _ => ListBuffer[String]()
  }

}

class Node(val node: FirrtlNode, val name: String) {

  def serialize: String = this.node.serialize

  def isUsed(expr: Expression): Boolean = expr match {
    case WRef(refName, _, _, _) => refName == name
    case WSubField(e, _, _, _) => isUsed(e)
    case WSubIndex(e, _, _, _) => isUsed(e) // Actually, it is not used in loFirrtl
    case WSubAccess(e, _, _, _) => isUsed(e) // This too
    case Reference(refName, _) => refName == name
    case SubField(e, _, _) => isUsed(e)
    case SubIndex(e, _, _) => isUsed(e)
    case SubAccess(e, _, _) => isUsed(e)
    case Mux(_, tval, fval, _) => (isUsed(tval) || isUsed(fval))
    case DoPrim(_, args, _, _) => {
      var used = false
      for (arg <- args) {
        used = used | isUsed(arg)
      }
      used
    }
    case _ => false
  }
}


// graphLeger
// 1) Generate graph which consists of Statements (DefRegister, DefNode, DefMemory, DefInstance,
// WDefInstance, Port)
// 2) Find all muxes and statements related to mux control signals
class graphLedger(val module: DefModule) {
  val mName = module.name
  private var defInstances = ListBuffer[FirrtlNode]()

  private val graphMap = mutable.Map[String, Tuple2[Node, Set[String]]]()
  private val reverseMap = mutable.Map[String, Tuple2[Node, Set[String]]]()
  private val Muxes = ListBuffer[Mux]()
  private val muxSrcs = mutable.Map[Mux, Set[String]]()
  private val ctrlSrcs = Map[String, ListBuffer[Node]](
    "DefRegister" -> ListBuffer[Node](), "DefMemory" -> ListBuffer[Node](),
    "DefInstance" -> ListBuffer[Node](), "WDefInstance" -> ListBuffer[Node](),
    "Port" -> ListBuffer[Node]())

  /* Variables for Optimization
  infoToVec: Identify vector registers utilizing Info annotation
  */
  private val portNames = module.ports.map(_.name).toSet
  private val infoToVec = mutable.Map[Info, Tuple3[Int, String, Set[String]]]()

  private var numRegs = 0
  private var numCtrlRegs = 0
  private var numMuxes = 0

  def printLog(): Unit = {
    print("=============================================\n")
    print(s"${mName}\n")
    print("---------------------------------------------\n")
    print(s"numRegs: ${numRegs}, numCtrlRegs: ${numCtrlRegs}, numMuxes: ${numMuxes}\n")
    print("=============================================\n")

    // print("reverseMap\n")
    // for ((n, tuple) <- reverseMap) {
    //   print(s"[$n] -- {${tuple._2.mkString(", ")}}\n")
    // }

    // print("muxes\n")
    // for ((mux, list) <- muxSrcs) {
    //   print(s"${mux.serialize} -- {${list.mkString(", ")}}\n")
    // }

    // ctrlSrcs.foreach(tuple => {
    //   val list = tuple._2.toSet
    //   print(s"${tuple._1} -- {${list.map(i => i.name).mkString(", ")}}\n")
    // })

    // print("infoToVec\n")
    // infoToVec.foreach(tuple => {
    //   print(s"${tuple._1.serialize} -- [${tuple._2._1}][${tuple._2._2}]\n" +
    //     s"{${tuple._2._3.mkString(", ")}}\n")
    // })

    // print("\n\n")

    // print("Instances: \n")
    // defInstances.foreach(x => print(s"${x.asInstanceOf[WDefInstance].name}\n"))
  }

  def parseModule: Unit = {
    this.module match {
      case ext: ExtModule =>
        print(s"$mName is external module\n")
      case mod: Module =>
        buildMap
    }
  }

  def buildMap: Unit = {
    this.module foreachPort findNode
    this.module foreachStmt findNode


    for ((n, tuple) <- graphMap) {
      if (tuple._1.node.getClass.getSimpleName == "DefRegister")
        numRegs = numRegs + 1

      var sinks = ListBuffer[String]()
      this.module foreachStmt findEdge(tuple._1, sinks)
      graphMap(n) = (tuple._1, sinks.toSet)
    }

  }

  def findNode(s: FirrtlNode): Unit = {
    if (Node.types.contains(s.getClass.getSimpleName)) {
      val n = Node(s)
      graphMap(n.name) = (n, Set[String]())
    }

    // Additionally, find instances
    if (Set("WDefInstance", "DefInstance").contains(s.getClass.getSimpleName))
      defInstances.append(s)

    s match {
      case stmt: Statement =>
        stmt foreachStmt findNode
      case other => Unit
    }
  }

  def findEdge(n: Node, sinks: ListBuffer[String])(s: Statement): Unit = {
    s match {
      case reg: DefRegister =>
        if (n.isUsed(reg.reset)) {
          sinks.append(reg.name)
        }
      case node: DefNode =>
        if (n.isUsed(node.value)) {
          sinks.append(node.name)
        }
      case Connect(_, loc, expr) =>
        if (n.isUsed(expr)) {
          sinks.append(Node.findName(loc))
        }
      case _ => Unit // Port, DefWire, DefMemory, DefInstance, WDefInstance
    }
    s foreachStmt findEdge(n, sinks)
  }

  def findMuxSrcs: Map[String, Set[Node]] = {
    this.module foreachStmt findMuxes
    reverseEdge

    val muxCtrls = Muxes.map(mux => Node.findNames(mux.cond)).toSet
    val ctrlMuxesMap = muxCtrls.map(_.toString).zip(
      Seq.fill[ListBuffer[Mux]](muxCtrls.size)(ListBuffer[Mux]())).toMap
    for (mux <- Muxes) {
      ctrlMuxesMap(Node.findNames(mux.cond).toString).append(mux)
    }

    for (ctrls <- muxCtrls) {

      var srcs = ListBuffer[String]()
      for (ctrl <- ctrls) {
        srcs = srcs ++ findSrcs(ctrl, ListBuffer[String]())
      }

      for (mux <- ctrlMuxesMap(ctrls.toString)) {
        muxSrcs(mux) = srcs.toSet
      }
    }

    val allSrcs = muxSrcs.flatMap(tuple => tuple._2)
    for (src <- allSrcs) {
      graphMap(src)._1.node.getClass.getSimpleName match {
        case "DefRegister" => ctrlSrcs("DefRegister").append(graphMap(src)._1)
        case "DefMemory" => ctrlSrcs("DefMemory").append(graphMap(src)._1)
        case "DefInstance" => ctrlSrcs("DefInstance").append(graphMap(src)._1)
        case "WDefInstance" => ctrlSrcs("WDefInstance").append(graphMap(src)._1)
        case "Port" => ctrlSrcs("Port").append(graphMap(src)._1)
        case _ =>
          throw new Exception(s"${src} not in ctrl type")
      }
    }

    numMuxes = muxSrcs.size
    numCtrlRegs = ctrlSrcs("DefRegister").toSet.size
    ctrlSrcs.map(tuple => (tuple._1, tuple._2.toSet))
  }

  // Find registers which get input directly from input ports
  def findDirInRegs: Set[DefRegister] = {
    if (reverseMap.size == 0)
      return Set[DefRegister]()

    val ctrlRegs = ctrlSrcs("DefRegister").
      map(_.node.asInstanceOf[DefRegister]).toSet

    val ctrlRegSrcs = ctrlRegs.map(reg =>
      (reg, reverseMap(reg.name)._2.map(src =>
        findSrcs(src, ListBuffer[String]())).flatten)
    ).map(tuple => (tuple._1, tuple._2.filter(src => src != tuple._1.name))
    )

    val firstInRegs = ctrlRegSrcs.filter(tuple => tuple._2.diff(portNames).isEmpty).map(_._1)

    ctrlRegSrcs.filter(tuple =>
      tuple._2.diff(portNames.union(firstInRegs.map(_.name))).isEmpty
    ).map(_._1)
  }

  // Find vector registers using the feature of Chisel.
  // Vertor registers in Chisel leave Source information
  def findVecRegs: Set[Tuple3[Int, String, Set[String]]] = {
    if (reverseMap.size == 0)
      return Set[Tuple3[Int, String, Set[String]]]()

    val ctrlRegs = ctrlSrcs("DefRegister").map(_.node).toSet

    val infoRegMap: Map[Info, ListBuffer[String]] = {
      ctrlRegs.foldLeft(ListBuffer[Info]())((list, reg) => {
        if (list.contains(reg.asInstanceOf[DefRegister].info)) list
        else list :+ reg.asInstanceOf[DefRegister].info
      }).map(info => (info, ListBuffer[String]())).toMap
    }

    for (reg <- ctrlRegs) {
      infoRegMap(reg.asInstanceOf[DefRegister].info).append(reg.asInstanceOf[DefRegister].name)
    }

    // DefRegisters which have same Info must be
    // 1) a definition of a vector, 2) a definition of a bundle, 3) multiple call of a definition
    val MINVECSIZE = 2
    val sortedInfoRegMap = infoRegMap.map(tuple =>
      (tuple._1, tuple._2.sorted)).filter(tuple =>
      (tuple._1.getClass.getSimpleName != "NoInfo" && tuple._2.length >= MINVECSIZE)
    )

    for((info, regs) <- sortedInfoRegMap) {
      val prefix = regs.foldLeft(regs.head.inits.toSet)((set, reg) => {
        reg.inits.toSet.intersect(set)
      }).maxBy(_.length)

      prefix.length match {
        case 0 => Unit
        case n => {
          val bodies = regs.map(x => {
            x.substring(n, x.length)
          })

          // If body does not start with a number, then it is a bundle
          // print(s"Info: ${info}, prefix: ${prefix}\n")
          if (bodies.forall(body => (body.length > 0 && body(0).isDigit))) {
            val vElements = bodies.foldLeft(Map[Int, ListBuffer[String]]())((map, body) => {
              val idx = body.substring(0, if (body.contains('_')) body.indexOf('_') else body.length).toInt
              map + (idx -> (map.getOrElse(idx, ListBuffer[String]()) :+ body.substring(idx.toString.length, body.length)))
            })

            // Indices of vector elements should be continuous and starting from 0
            if ((vElements.keySet.toSeq.sorted.sliding(2).count(keys => {
              keys(0) + 1 == keys(1) }) == (vElements.keySet.size - 1)) &&
              vElements.keySet.toSeq.head == 0 &&
              vElements.forall(_._2.toSet == vElements.head._2.toSet)) {

              infoToVec(info) = (vElements.size, prefix, vElements.head._2.toSet)
            }
          }
        }
      }
    }

    infoToVec.toSet.map((tuple: (Info, Tuple3[Int, String, Set[String]])) => tuple._2)
  }

  //Find first sinks (DefRegister).
  def findSinks(src: String): ListBuffer[String] = {
    assert(graphMap.keySet.contains(src),
      s"graphMap does not contain $src")
    val tuple = graphMap(src)

    tuple._2.foldLeft(tuple._1.node match {
      case port: Port => ListBuffer[String]()
      case reg: DefRegister => ListBuffer(src)
      case wire: DefWire => ListBuffer[String]()
      case mem: DefMemory => ListBuffer[String]()
      case inst: DefInstance => ListBuffer[String]()
      case winst: WDefInstance => ListBuffer[String]() //TODO Queue registers can start from instance
      case _ => ListBuffer[String]() // DefNode (nodes must not make loop)
    }) ((list, str) => if (Set("DefWire", "DefNode", "Port").contains(tuple._1.node.getClass.getSimpleName)) {
      list ++ findSinks(str)
    } else {
      list
    })
  }

  def findMuxes(e: FirrtlNode): Unit = {
    e match {
      case stmt: Statement =>
        stmt foreachStmt findMuxes
        stmt foreachExpr findMuxes
      case expr: Expression =>
        if (expr.getClass.getSimpleName == "Mux") {
          Muxes.append(expr.asInstanceOf[Mux])
        }
        expr foreachExpr findMuxes
      case _ =>
        throw new Exception("Statement should have only Statement/Expression")
    }
  }

  def findRegs: Set[DefRegister] = {
    graphMap.filter(tuple => {
      tuple._2._1.node.getClass.getSimpleName == "DefRegister"
    }).map(tuple => tuple._2._1.node.asInstanceOf[DefRegister]).toSet
  }

  def reverseEdge: Unit = {
    for ((n, tuple) <- graphMap) {
      reverseMap(n) = (tuple._1, Set[String]())
    }

    for ((n, tuple) <- reverseMap) {
      var sources = ListBuffer[String]()
      for ((m, tup) <- graphMap) {
        if (tup._2.contains(n)) {
          sources.append(m)
        }
      }
      reverseMap(n) = (tuple._1, sources.toSet)
    }
  }

  //Find first sources (Port, DefRegister, DefMemory, DefInstance, WDefInstance).
  def findSrcs(sink: String, visited: ListBuffer[String]): ListBuffer[String] = {
    assert(reverseMap.keySet.contains(sink),
      s"reverseMap does not contain $sink")

    if (visited.contains(sink))
      return ListBuffer[String]()

    visited.append(sink)
    val tuple = reverseMap(sink)

    tuple._2.foldLeft(tuple._1.node match {
      case port: Port => ListBuffer(sink)
      case reg: DefRegister => ListBuffer(sink)
      case mem: DefMemory => ListBuffer(sink)
      case inst: DefInstance => ListBuffer(sink)
      case winst: WDefInstance => ListBuffer(sink)
      case _ => ListBuffer[String]() // DefNode (nodes must not make loop), DefWire
    }) ((list, str) => if (Set("DefNode", "DefWire").contains(tuple._1.node.getClass.getSimpleName)) {
      list ++ findSrcs(str, visited)
    } else {
      list
    })
  }

  def getInstances: Set[WDefInstance] = {
    for (inst <- defInstances) {
      if (inst.getClass.getSimpleName != "WDefInstance")
        throw new Exception(s"${inst.serialize} is not WDefInstance class\n")
    }
    defInstances.map(_.asInstanceOf[WDefInstance]).toSet
  }

  def getMuxSrcs: Map[Mux, Set[String]] = {
    muxSrcs
  }
}

