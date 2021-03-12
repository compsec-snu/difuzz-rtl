import sys
import cocotb

from cocotb.decorators import coroutine
from cocotb.triggers import RisingEdge

from adapters.tilelink.adapter import tlAdapter
from adapters.tilelink.definitions import *

INT_MEIP = 0x4
INT_SEIP = 0x8
INT_MTIP = 0x1
INT_MSIP = 0x2

class intPorts():
    __slots__ = ('seip', 'meip', 'msip', 'mtip')

    def __init__(self):
        for attr in self.__slots__:
            setattr(self, attr, None)

class tileAdapter():
    def __init__(self, dut, port_names, monitor, debug=False):
        self.dut = dut
        self.debug = debug
        self.drive = False

        tl_port_names = []
        int_port_names = []
        others = []

        for name in port_names:
            if '_tl_' in name:
                tl_port_names.append(name)
            elif '_int' in name:
                int_port_names.append(name)
            elif 'reset_vector' in name:
                reset_vector_port = name
            else:
                others.append(name)

        pc_name = monitor[0]
        valid_name = monitor[1]

        for name in tl_port_names:
            if '_b_' in name:
                protocol = TL_C

        self.tl_adapter = tlAdapter(dut, tl_port_names, protocol, 64, debug)

        self.int_ports = intPorts()
        for name in int_port_names:
            if 'in_2_sync_0' in name: setattr(self.int_ports, 'seip', getattr(self.dut, name))
            if 'in_1_sync_0' in name: setattr(self.int_ports, 'meip', getattr(self.dut, name))
            if 'in_0_sync_0' in name: setattr(self.int_ports, 'msip', getattr(self.dut, name))
            if 'in_0_sync_1' in name: setattr(self.int_ports, 'mtip', getattr(self.dut, name))

        self.reset_vector_port = getattr(self.dut, reset_vector_port)

        self.reset_vector = 0x10000
        self.reset_vector_port <= self.reset_vector

        self.monitor_pc = getattr(self.dut, pc_name)
        self.monitor_valid = getattr(self.dut, valid_name)

        self.intr = 0

    def debug_print(self, message):
        if self.debug:
            print(message)

    def assert_intr(self, intr):
        if intr == self.intr:
            return

        self.intr = intr
        meip = int((intr & INT_MEIP) == INT_MEIP)
        seip = int((intr & INT_SEIP) == INT_SEIP)
        mtip = int((intr & INT_MTIP) == INT_MTIP)
        msip = int((intr & INT_MSIP) == INT_MSIP)

        self.int_ports.seip <= seip
        self.int_ports.meip <= meip
        self.int_ports.msip <= msip
        self.int_ports.mtip <= mtip

    def pc_valid(self):
        return self.monitor_valid.value

    @coroutine
    def interrupt_handler(self, ints):
        if not ints:
            return

        while self.drive:
            if self.pc_valid():
                pc = self.monitor_pc.value & ((1 << len(self.monitor_pc.value)) - 1)
                if pc in ints.keys():
                    self.debug_print('[RTLHost] interrupt_handler, pc: {:016x}, INT: {:01x}'.
                                     format(pc, ints[pc]))
                    self.assert_intr(ints[pc])
            yield RisingEdge(self.dut.clock)


    def probe_tohost(self, tohost_addr):
        self.tl_adapter.probe_block(tohost_addr)

    def check_assert(self):
        return self.dut.metaAssert.value

    def start(self, memory, ints):
        if memory.__class__.__name__ != 'dict':
            raise Exception('RocketTile Adapter must receive address map to drive DUT')

        self.drive = True
        self.tl_adapter.start(memory)
        self.intr_handler = cocotb.fork(self.interrupt_handler(ints))

    @coroutine
    def stop(self):
        self.drive = False
        while self.tl_adapter.onGoing():
            yield RisingEdge(self.dut.clock)
        self.tl_adapter.stop()
        while self.tl_adapter.isRunning():
            yield RisingEdge(self.dut.clock)

        self.int_ports.seip <= 0
        self.int_ports.meip <= 0
        self.int_ports.msip <= 0
        self.int_ports.mtip <= 0

        self.intr = 0
