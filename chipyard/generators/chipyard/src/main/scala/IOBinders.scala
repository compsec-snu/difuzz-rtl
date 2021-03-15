package chipyard
package object iobinders {

import chisel3._
import chisel3.experimental.{Analog, IO}

import freechips.rocketchip.config.{Field, Config, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule}
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.system.{SimAXIMem}
import freechips.rocketchip.amba.axi4.{AXI4Bundle, AXI4SlaveNode, AXI4EdgeParameters}
import freechips.rocketchip.util._

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.spi._

import barstools.iocell.chisel._

import testchipip._
import icenet.{CanHavePeripheryIceNICModuleImp, SimNetwork, NicLoopback, NICKey}
import tracegen.{HasTraceGenTilesModuleImp}

import scala.reflect.{ClassTag}

// System for instantiating binders based
// on the scala type of the Target (_not_ its IO). This avoids needing to
// duplicate harnesses (essentially test harnesses) for each target.

// IOBinders is map between string representations of traits to the desired
// IO connection behavior for tops matching that trait. We use strings to enable
// composition and overriding of IOBinders, much like how normal Keys in the config
// system are used/ At elaboration, the testharness traverses this set of functions,
// and functions which match the type of the DigitalTop are evaluated.

// You can add your own binder by adding a new (key, fn) pair, typically by using
// the OverrideIOBinder or ComposeIOBinder macros


// DOC include start: IOBinders
// This type describes a function callable on the TestHarness instance. Its return type is unused.
type TestHarnessFunction = (chipyard.TestHarness) => Seq[Any]
// IOBinders will return a Seq of this tuple, which contains three fields:
//  1. A Seq containing all IO ports created by the IOBinder function
//  2. A Seq containing all IO cell modules created by the IOBinder function
//  3. An optional function to call inside the test harness (e.g. to connect the IOs)
type IOBinderTuple = (Seq[Data], Seq[IOCell], Option[TestHarnessFunction])

case object IOBinders extends Field[Map[String, (Any) => Seq[IOBinderTuple]]](
  Map[String, (Any) => Seq[IOBinderTuple]]().withDefaultValue((Any) => Nil)
)

// This macro overrides previous matches on some Top mixin. This is useful for
// binders which drive IO, since those typically cannot be composed
class OverrideIOBinder[T](fn: => (T) => Seq[IOBinderTuple])(implicit tag: ClassTag[T]) extends Config((site, here, up) => {
  case IOBinders => up(IOBinders, site) + (tag.runtimeClass.toString ->
      ((t: Any) => {
        t match {
          case system: T => fn(system)
          case _ => Nil
        }
      })
  )
})

// This macro composes with previous matches on some Top mixin. This is useful for
// annotation-like binders, since those can typically be composed
class ComposeIOBinder[T](fn: => (T) => Seq[IOBinderTuple])(implicit tag: ClassTag[T]) extends Config((site, here, up) => {
  case IOBinders => up(IOBinders, site) + (tag.runtimeClass.toString ->
      ((t: Any) => {
        t match {
          case system: T => (up(IOBinders, site)(tag.runtimeClass.toString)(system)
            ++ fn(system))
          case _ => Nil
        }
      })
  )
})

// DOC include end: IOBinders

object AddIOCells {
  /**
   * Add IO cells to a SiFive GPIO devices and name the IO ports.
   * @param gpios A Seq of GPIO port bundles
   * @param genFn A callable function to generate a DigitalGPIOCell module to use
   * @return Returns a tuple of (a 2D Seq of Analog IOs corresponding to individual GPIO pins; a 2D Seq of IOCell module references)
   */
  def gpio(gpios: Seq[GPIOPortIO], genFn: () => DigitalGPIOCell = IOCell.genericGPIO): (Seq[Seq[Analog]], Seq[Seq[IOCell]]) = {
    gpios.zipWithIndex.map({ case (gpio, i) =>
      gpio.pins.zipWithIndex.map({ case (pin, j) =>
        val g = IO(Analog(1.W)).suggestName(s"gpio_${i}_${j}")
        val iocell = genFn().suggestName(s"iocell_gpio_${i}_${j}")
        iocell.io.o := pin.o.oval
        iocell.io.oe := pin.o.oe
        iocell.io.ie := pin.o.ie
        pin.i.ival := iocell.io.i
        iocell.io.pad <> g
        (g, iocell)
      }).unzip
    }).unzip
  }

  /**
   * Add IO cells to a SiFive UART devices and name the IO ports.
   * @param uartPins A Seq of UART port bundles
   * @return Returns a tuple of (A Seq of top-level UARTPortIO IOs; a 2D Seq of IOCell module references)
   */
  def uart(uartPins: Seq[UARTPortIO]): (Seq[UARTPortIO], Seq[Seq[IOCell]]) = {
    uartPins.zipWithIndex.map({ case (u, i) =>
      val (port, ios) = IOCell.generateIOFromSignal(u, Some(s"iocell_uart_${i}"))
      port.suggestName(s"uart_${i}")
      (port, ios)
    }).unzip
  }

  /**
   * Add IO cells to a SiFive SPI devices and name the IO ports.
   * @param spiPins A Seq of SPI port bundles
   * @param basename The base name for this port (defaults to "spi")
   * @param genFn A callable function to generate a DigitalGPIOCell module to use
   * @return Returns a tuple of (A Seq of top-level SPIChipIO IOs; a 2D Seq of IOCell module references)
   */
  def spi(spiPins: Seq[SPIPortIO], basename: String = "spi", genFn: () => DigitalGPIOCell = IOCell.genericGPIO): (Seq[SPIChipIO], Seq[Seq[IOCell]]) = {
    spiPins.zipWithIndex.map({ case (s, i) =>
      val port = IO(new SPIChipIO(s.c.csWidth)).suggestName(s"${basename}_${i}")
      val iocellBase = s"iocell_${basename}_${i}"

      // SCK and CS are unidirectional outputs
      val sckIOs = IOCell.generateFromSignal(s.sck, port.sck, Some(s"${iocellBase}_sck"))
      val csIOs = IOCell.generateFromSignal(s.cs, port.cs, Some(s"${iocellBase}_cs"))

      // DQ are bidirectional, so then need special treatment
      val dqIOs = s.dq.zip(port.dq).zipWithIndex.map { case ((pin, ana), j) =>
        val iocell = genFn().suggestName(s"${iocellBase}_dq_${j}")
        iocell.io.o := pin.o
        iocell.io.oe := pin.oe
        iocell.io.ie := true.B
        pin.i := iocell.io.i
        iocell.io.pad <> ana
        iocell
      }

      (port, dqIOs ++ csIOs ++ sckIOs)
    }).unzip
  }

  /**
   * Add IO cells to a debug module and name the IO ports.
   * @param psd A PSDIO bundle
   * @param resetctrlOpt An optional ResetCtrlIO bundle
   * @param debugOpt An optional DebugIO bundle
   * @return Returns a tuple3 of (Top-level PSDIO IO; Optional top-level DebugIO IO; a list of IOCell module references)
   */
  def debug(psd: PSDIO, resetctrlOpt: Option[ResetCtrlIO], debugOpt: Option[DebugIO])(implicit p: Parameters):
      (PSDIO, Option[ResetCtrlIO], Option[DebugIO], Seq[IOCell]) = {
    val (psdPort, psdIOs) = IOCell.generateIOFromSignal(
      psd, Some("iocell_psd"), abstractResetAsAsync = p(GlobalResetSchemeKey).pinIsAsync)
    val debugTuple = debugOpt.map(d =>
      IOCell.generateIOFromSignal(d, Some("iocell_debug"), abstractResetAsAsync = p(GlobalResetSchemeKey).pinIsAsync))
    val debugPortOpt: Option[DebugIO] = debugTuple.map(_._1)
    val debugIOs: Seq[IOCell] = debugTuple.map(_._2).toSeq.flatten
    debugPortOpt.foreach(_.suggestName("debug"))

    val resetctrlTuple = resetctrlOpt.map(d =>
      IOCell.generateIOFromSignal(d, Some("iocell_resetctrl"), abstractResetAsAsync = p(GlobalResetSchemeKey).pinIsAsync))
    val resetctrlPortOpt: Option[ResetCtrlIO] = resetctrlTuple.map(_._1)
    val resetctrlIOs: Seq[IOCell] = resetctrlTuple.map(_._2).toSeq.flatten
    resetctrlPortOpt.foreach(_.suggestName("resetctrl"))

    psdPort.suggestName("psd")
    (psdPort, resetctrlPortOpt, debugPortOpt, psdIOs ++ debugIOs ++ resetctrlIOs)
  }

  /**
   * Add IO cells to a serial module and name the IO ports.
   * @param serial A SerialIO bundle
   * @return Returns a tuple of (Top-level SerialIO IO; a list of IOCell module references)
   */
  def serial(serial: SerialIO): (SerialIO, Seq[IOCell]) = {
    val (port, ios) = IOCell.generateIOFromSignal(serial, Some("iocell_serial"))
    port.suggestName("serial")
    (port, ios)
  }

  def axi4(io: Seq[AXI4Bundle], node: AXI4SlaveNode): Seq[(AXI4Bundle, AXI4EdgeParameters, Seq[IOCell])] = {
    io.zip(node.in).map{ case (mem_axi4, (_, edge)) => {
      val (port, ios) = IOCell.generateIOFromSignal(mem_axi4, Some("iocell_mem_axi4"))
      port.suggestName("mem_axi4")
      (port, edge, ios)
    }}
  }

  def blockDev(bdev: BlockDeviceIO): (BlockDeviceIO, Seq[IOCell]) = {
    val (port, ios) = IOCell.generateIOFromSignal(bdev, Some("iocell_bdev"))
    port.suggestName("bdev")
    (port, ios)
  }
}

// DOC include start: WithGPIOTiedOff
class WithGPIOTiedOff extends OverrideIOBinder({
  (system: HasPeripheryGPIOModuleImp) => {
    val (ports2d, ioCells2d) = AddIOCells.gpio(system.gpio)
    val harnessFn = (th: chipyard.TestHarness) => { ports2d.flatten.foreach(_ <> AnalogConst(0)); Nil }
    Seq((ports2d.flatten, ioCells2d.flatten, Some(harnessFn)))
  }
})
// DOC include end: WithGPIOTiedOff

class WithUARTAdapter extends OverrideIOBinder({
  (system: HasPeripheryUARTModuleImp) => {
    val (ports, ioCells2d) = AddIOCells.uart(system.uart)
    val harnessFn = (th: chipyard.TestHarness) => { UARTAdapter.connect(ports)(system.p); Nil }
    Seq((ports, ioCells2d.flatten, Some(harnessFn)))
  }
})

class WithSimSPIFlashModel(rdOnly: Boolean = true) extends OverrideIOBinder({
  (system: HasPeripherySPIFlashModuleImp) => {
    val (ports, ioCells2d) = AddIOCells.spi(system.qspi, "qspi")
    val harnessFn = (th: chipyard.TestHarness) => { SimSPIFlashModel.connect(ports, th.reset, rdOnly)(system.p); Nil }
    Seq((ports, ioCells2d.flatten, Some(harnessFn)))
  }
})

class WithSimBlockDevice extends OverrideIOBinder({
  (system: CanHavePeripheryBlockDeviceModuleImp) => system.bdev.map { bdev =>
    val (port, ios) = AddIOCells.blockDev(bdev)
    val harnessFn = (th: chipyard.TestHarness) => {
      SimBlockDevice.connect(th.clock, th.reset.asBool, Some(port))(system.p)
      Nil
    }
    Seq((Seq(port), ios, Some(harnessFn)))
  }.getOrElse(Nil)
})

class WithBlockDeviceModel extends OverrideIOBinder({
  (system: CanHavePeripheryBlockDeviceModuleImp) => system.bdev.map { bdev =>
    val (port, ios) = AddIOCells.blockDev(bdev)
    val harnessFn = (th: chipyard.TestHarness) => {
      BlockDeviceModel.connect(Some(port))(system.p)
      Nil
    }
    Seq((Seq(port), ios, Some(harnessFn)))
  }.getOrElse(Nil)
})

class WithLoopbackNIC extends OverrideIOBinder({
  (system: CanHavePeripheryIceNICModuleImp) => system.connectNicLoopback(); Nil
})

class WithSimNIC extends OverrideIOBinder({
  (system: CanHavePeripheryIceNICModuleImp) => system.connectSimNetwork(system.clock, system.reset.asBool); Nil
})

// Note: The parameters instance is accessible only through the BaseSubsystem
// or some parent class (IsAttachable, BareSubsystem -> LazyModule). The
// self-type requirement in CanHaveMasterAXI4MemPort is insufficient to make it
// accessible to the IOBinder
// DOC include start: WithSimAXIMem
class WithSimAXIMem extends OverrideIOBinder({
  (system: CanHaveMasterAXI4MemPort with BaseSubsystem) => {
    val peiTuples = AddIOCells.axi4(system.mem_axi4, system.memAXI4Node)
    // TODO: we are inlining the connectMem method of SimAXIMem because
    //   it takes in a dut rather than seq of axi4 ports
    val harnessFn = (th: chipyard.TestHarness) => {
      peiTuples.map { case (port, edge, ios) =>
        val mem = LazyModule(new SimAXIMem(edge, size = system.p(ExtMem).get.master.size)(system.p))
        Module(mem.module).suggestName("mem")
        mem.io_axi4.head <> port
        }
      Nil
    }
    Seq((peiTuples.map(_._1), peiTuples.flatMap(_._3), Some(harnessFn)))
  }
})
// DOC include end: WithSimAXIMem

class WithBlackBoxSimMem extends OverrideIOBinder({
  (system: CanHaveMasterAXI4MemPort with BaseSubsystem) => {
    val peiTuples = AddIOCells.axi4(system.mem_axi4, system.memAXI4Node)
    val harnessFn = (th: chipyard.TestHarness) => {
      peiTuples.map { case (port, edge, ios) =>
        val memSize = system.p(ExtMem).get.master.size
        val lineSize = system.p(CacheBlockBytes)
        val mem = Module(new SimDRAM(memSize, lineSize, edge.bundle))
        mem.io.axi <> port
        mem.io.clock := th.clock
        mem.io.reset := th.reset
      }
      Nil
    }
    Seq((peiTuples.map(_._1), peiTuples.flatMap(_._3), Some(harnessFn)))
  }
})

class WithSimAXIMMIO extends OverrideIOBinder({
  (system: CanHaveMasterAXI4MMIOPort with BaseSubsystem) => SimAXIMem.connectMMIO(system)(system.p); Nil
})

class WithDontTouchPorts extends OverrideIOBinder({
  (system: DontTouch) => system.dontTouchPorts(); Nil
})

class WithTieOffInterrupts extends OverrideIOBinder({
  (system: HasExtInterruptsModuleImp) => {
    val (port, ioCells) = IOCell.generateIOFromSignal(system.interrupts, Some("iocell_interrupts"))
    port.suggestName("interrupts")
    val harnessFn = (th: chipyard.TestHarness) => { port := 0.U; Nil }
    Seq((Seq(port), ioCells, Some(harnessFn)))
  }
})

class WithTieOffL2FBusAXI extends OverrideIOBinder({
  (system: CanHaveSlaveAXI4Port with BaseSubsystem) => {
    system.l2_frontend_bus_axi4.foreach(axi => {
      axi.tieoff()
      experimental.DataMirror.directionOf(axi.ar.ready) match {
        case ActualDirection.Input =>
          axi.r.bits := DontCare
          axi.b.bits := DontCare
        case ActualDirection.Output =>
          axi.aw.bits := DontCare
          axi.ar.bits := DontCare
          axi.w.bits := DontCare
        case _ => throw new Exception("Unknown AXI port direction")
      }
    })
    Nil
  }
})

class WithTiedOffDebug extends OverrideIOBinder({
  (system: HasPeripheryDebugModuleImp) => {
    val (psdPort, resetctrlOpt, debugPortOpt, ioCells) =
      AddIOCells.debug(system.psd, system.resetctrl, system.debug)(system.p)
    val harnessFn = (th: chipyard.TestHarness) => {
      Debug.tieoffDebug(debugPortOpt, resetctrlOpt, Some(psdPort))(system.p)
      // tieoffDebug doesn't actually tie everything off :/
      debugPortOpt.foreach { d =>
        d.clockeddmi.foreach({ cdmi => cdmi.dmi.req.bits := DontCare; cdmi.dmiClock := th.clock })
        d.dmactiveAck := DontCare
        d.clock := th.clock
      }
      Nil
    }
    Seq((Seq(psdPort) ++ resetctrlOpt ++ debugPortOpt.toSeq, Nil, Some(harnessFn)))
  }
})

class WithSimDebug extends OverrideIOBinder({
  (system: HasPeripheryDebugModuleImp) => {
    val (psdPort, resetctrlPortOpt, debugPortOpt, ioCells) =
      AddIOCells.debug(system.psd, system.resetctrl, system.debug)(system.p)
    val harnessFn = (th: chipyard.TestHarness) => {
      val dtm_success = Wire(Bool())
      Debug.connectDebug(debugPortOpt, resetctrlPortOpt, psdPort, th.clock, th.harnessReset, dtm_success)(system.p)
      when (dtm_success) { th.success := true.B }
      th.dutReset := th.harnessReset | debugPortOpt.map { debug => AsyncResetReg(debug.ndreset).asBool }.getOrElse(false.B)
      Nil
    }
    Seq((Seq(psdPort) ++ debugPortOpt.toSeq, ioCells, Some(harnessFn)))
  }
})

class WithTiedOffSerial extends OverrideIOBinder({
  (system: CanHavePeripherySerialModuleImp) => system.serial.map({ serial =>
    val (port, ioCells) = AddIOCells.serial(serial)
    val harnessFn = (th: chipyard.TestHarness) => {
      SerialAdapter.tieoff(port)
      Nil
    }
    Seq((Seq(port), ioCells, Some(harnessFn)))
  }).getOrElse(Nil)
})

class WithSimSerial extends OverrideIOBinder({
  (system: CanHavePeripherySerialModuleImp) => system.serial.map({ serial =>
    val (port, ioCells) = AddIOCells.serial(serial)
    val harnessFn = (th: chipyard.TestHarness) => {
      val ser_success = SerialAdapter.connectSimSerial(port, th.clock, th.harnessReset)
      when (ser_success) { th.success := true.B }
      Nil
    }
    Seq((Seq(port), ioCells, Some(harnessFn)))
  }).getOrElse(Nil)
})

class WithTraceGenSuccessBinder extends OverrideIOBinder({
  (system: HasTraceGenTilesModuleImp) => {
    val (successPort, ioCells) = IOCell.generateIOFromSignal(system.success, Some("iocell_success"))
    successPort.suggestName("success")
    val harnessFn = (th: chipyard.TestHarness) => { when (successPort) { th.success := true.B }; Nil }
    Seq((Seq(successPort), ioCells, Some(harnessFn)))
  }
})

class WithSimDromajoBridge extends ComposeIOBinder({
   (system: CanHaveTraceIOModuleImp) => {
     system.traceIO match { case Some(t) => t.traces.map(tileTrace => SimDromajoBridge(tileTrace)(system.p)) }
     Nil
   }
})


} /* end package object */
