package coverage

import java.io.{File, PrintWriter}

import firrtl._
import firrtl.ir._
import firrtl.Mappers._
import firrtl.PrimOps.Or

class bridgeReset extends Transform {
  def inputForm: LowForm.type = LowForm
  def outputForm: LowForm.type = LowForm

  def execute(state: CircuitState): CircuitState = {
    val circuit = state.circuit

    print("==================== Connecting Bridge Reset to metaReset ==========\n")
    val outCircuit = circuit.map { m: DefModule =>
      if (m.name == "FPGATop") {
        val instrBridgeReset = new InstrBridgeReset(m)
        val newMod = instrBridgeReset.instrument()
        instrBridgeReset.printLog()

        newMod
      } else {
        m
      }
    }

    print("====================================================================\n")

    val FPGALowFormWriter = new PrintWriter(new File(s"${circuit.main}_FPGA_low.fir"))
    FPGALowFormWriter.write(outCircuit.serialize)
    FPGALowFormWriter.close()

    state.copy(outCircuit)
  }
}

