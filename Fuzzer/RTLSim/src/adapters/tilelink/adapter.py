import sys
import cocotb
import math
import random
import queue
from cocotb.decorators import coroutine
from cocotb.triggers import Timer, RisingEdge

from adapters.tilelink.definitions import *
from adapters.tilelink.utils import *

""" Tilelink adapter
, which acts as a tilelink slave 

tilelink specification

Mandatory (TL-UL, TL-UH)
    Channel A       |    Channel D 
        a_opcode    |        d_opcode    
        a_param     |        d_param
        a_size      |        d_size     
        a_source    |        d_source   
        a_address   |        d_sink  
        a_mask      |        d_data     
        a_data      |        d_corrupt     
        a_valid     |        d_denied
        a_ready     |        d_valid
                    |        d_ready
TL-C
    Channel B       |   Channel C       |   Channel E   
        b_opcode    |       c_opcode    |       e_sink
        b_param     |       c_param     |       e_valid
        b_size      |       c_size      |       e_ready
        b_source    |       c_source    |
        b_address   |       c_address   |
        b_mask      |       c_data      |
        b_data      |       c_corrupt   |
        b_valid     |       c_valid     |
        b_ready     |       c_ready     |

"""

class tlAdapter():
    def __init__(self, dut, port_names, protocol=TL_UL, block_size=64, debug=False):
        self.dut = dut
        self.protocol = protocol
        self.drive = False
        self.stopped = True
        self.ongoing = False

        self.debug = debug

        self.max_wait_cycle = 100

        self.block_size = block_size
        self.block_mask = ~(self.block_size - 1)

        self.sink_id = 0
        self.a_ports = Ports(dut, 'A', TL_A_FIELDS, port_names)
        self.d_ports = Ports(dut, 'D', TL_D_FIELDS, port_names)

        self.b_ports = Ports(dut, 'B', TL_B_FIELDS, port_names)
        self.c_ports = Ports(dut, 'C', TL_C_FIELDS, port_names)
        self.e_ports = Ports(dut, 'E', TL_E_FIELDS, port_names)

        self.a_datalen = self.a_ports.data_len // 8 # Byte width of a port data
        self.d_datalen = self.d_ports.data_len // 8 # Byte width of d port data
        self.addr_mask_a = ~((1 << int(math.log(self.a_datalen, 2))) - 1)
        self.addr_mask_d = ~((1 << int(math.log(self.d_datalen, 2))) - 1)

        self.nop_data = 0

        if self.a_datalen != self.d_datalen:
            raise Exception('{} a_data and d_data must have same width'.format(dut.name))

        if self.protocol == TL_C:
            self.b_datalen = self.b_ports.data_len // 8
            self.c_datalen = self.c_ports.data_len // 8
            self.addr_mask_b =  ~((1 << int(math.log(self.b_datalen, 2))) - 1)
            self.addr_mask_c =  ~((1 << int(math.log(self.c_datalen, 2))) - 1)

            if self.a_datalen != self.c_datalen:
                raise Exception('{} a_data and d_data must have same width'.format(dut.name))

        self.d_queue = tlDQueue()
        self.b_queue = tlBQueue()

        """ On going TL-C transaction addresses """
        self.ongoing_tlc = {} # TODO, Need to implement

        """ For probe tohost_addr """
        self.probe = 0
        self.probe_en = 1
        self.probe_addr = 0

    def set_src_msgs(self, src_msgs, src, msgs):
        assert src not in src_msgs.keys(), \
            '{} already in src_msgs'.format(src)

        src_msgs[src] = msgs

    def get_d_messages(self, message, memory, burst_len, addr_aligned, **kwargs):
        assert message in ['AccessAckData', 'GrantData'], \
            'get_d_messages only returns AccessAckData/GrantAckData'

        param = kwargs.get('param', 0)
        size = kwargs['size']
        source = kwargs['source']
        sink = kwargs.get('sink', 0)

        d_msgs = []
        for i in range(burst_len):
            get_addr = addr_aligned + i * self.d_datalen
            if get_addr not in memory.keys():
                memory[get_addr] = self.nop_data

            get_data = memory[get_addr]
            d_msgs.append(tlDMessage(message, param=param, size=size, source=source, \
                                     sink=sink, data=get_data))

        return d_msgs

    def enableProbe(self):
        self.probe_en = 1


    def updateMem(self, memory, burst_data):
        for addr, value in burst_data:
            bit_mask = value[0]
            data = value[1]

            memory[addr] = (memory.get(get_addr, 0) & (~bit_mask)) | (get_data & bit_mask)

        memory.update(burst_data)

    def updatePerm(self, block_perm, block_addr, param):
        if param == toT:
            block_perm[block_addr] = TRUNK

    def retrieveBlock(self, b_srcs, b_callback, callback, param, size, addr, mask, msg='ProbeBlock'):
        if not b_srcs.empty():
            self.retrieveBlock_cb(msg, b_srcs, b_callback, callback, param, \
                                  size, addr, mask)
        else:
            b_reserve = CallBack(self.retrieveBlock_cb, msg, b_srcs, b_callback, \
                                 callback, param, size, addr, mask)
            b_srcs.reserve(b_reserve)

    def retrieveBlock_cb(self, msg, b_srcs, b_callback, callback, param, size, addr, mask):
        b_src = b_srcs.get()
        b_callback.set(b_src, callback)
        self.b_queue.push(msg, param=param, size=size, \
                          source=b_src, address=addr, mask=mask)

    def AccessAck_cb(self, memory, ongoings, burst_len, burst_data, size, source):
        # TODO, source in ongoings can collide
        remain_clks = burst_len - ongoings.get(source, burst_len)
        callback_d = CallBack(self.updateMem, memory, burst_data)

        # TODO, Not the final solution (remain_clks can be longer)
        for clk in range(remain_clks):
            self.d_queue.push('Bubble', None)
        self.d_queue.push('AccessAck', callback_d, size=size, source=source)

    def AccessAckData_cb(self, memory, burst_len, addr_aligned, size, source):
        d_msgs = self.get_d_messages('AccessAckData', memory, burst_len, addr_aligned, \
                                size=size, source=source)

        self.d_queue.push_msgs(d_msgs)

    def ArithmeticAck_cb(self, operand1, memory, burst_len, addr_aligned, bit_mask, offset, size, source):
        operand2 = (memory[addr_aligned] & bit_mask) >> offset
        result = (self._arithmetic_op(param, operand1, operand2, mask) << offset) & \
            self.a_ports.data_mask

        assert burst_len == 1, 'ArithmeticAck_cb, burst_len should be 1'

        memory[addr_aligned] = (memory.get(addr_aligned, 0) & (~bit_mask)) | (result & bit_mask)
        self.AccessAckData_cb(memory, burst_len, addr_aligned, size, source)

    def LogicalAck_cb(self, operand1, memory, burst_len, addr_aligned, bit_mask, offset, size, source):
        operand2 = (memory[addr_aligned] & bit_mask) >> offset
        result = (self._logical_op(param, operand1, operand2, mask) << offset) & \
            self.a_ports.data_mask

        assert burst_len == 1, 'LogicalAck_cb, burst_len should be 1'

        memory[addr_aligned] = (memory.get(addr_aligned, 0) & (~bit_mask)) | (result & bit_mask)
        self.AccessAckData_cb(memory, burst_len, addr_aligned, size, source)

    def Grant_cb(self, param, sink, size, source, block_perm, block_addr):
        callback_d = CallBack(self.updatePerm, block_perm, block_addr, param)

        self.ongoing_tlc[sink] = block_addr
        self.d_queue.push('Grant', callback_d, param=param, size=size, source=source)

    def GrantData_cb(self, memory, burst_len, addr_aligned, param, sink, size, source, \
                     block_perm, block_addr):
        callback_d = CallBack(self.updatePerm, block_perm, block_addr, param)
        d_msgs = self.get_d_messages('GrantData', memory, burst_len, addr_aligned, \
                                param=param, sink=sink, size=size, source=source)
        cbs = [ callback_d ] + [None for i in range(len(d_msgs) - 1)]

        self.ongoing_tlc[sink] = block_addr
        self.d_queue.push_msg_cbs(d_msgs, cbs)


    def _arithmetic_op(self, param, operand1, operand2, mask):
        # Count the number of nonzero bits in mask
        size_op = 0
        while mask >= 1:
            if mask % 2 == 0:
                mask = mask // 2
            else:
                size_op = size_op + 8
                mask = mask // 2
        op_mask = (1 << size_op) - 1
        signed_op1 = operand1
        signed_op2 = operand2

        if (operand1 >> (size_op - 1)) & 0x1 == 1:
            signed_op1 = - ((~operand1 + 1) & op_mask)
        if (operand2 >> (size_op - 1)) & 0x1 == 1:
            signed_op2 = - ((~operand2 + 1) & op_mask)

        if param == MIN:
            return min(signed_op1, signed_op2)
        elif param ==  MAX:
            return max(signed_op1, signed_op2)
        elif param == MINU:
            uoperand1 = operand1 & (0xffffffff << 32 | 0xffffffff)
            uoperand2 = operand2 & (0xffffffff << 32 | 0xffffffff)
            return min(uoperand1, uoperand2)
        elif param == MAXU:
            uoperand1 = operand1 & (0xffffffff << 32 | 0xffffffff)
            uoperand2 = operand2 & (0xffffffff << 32 | 0xffffffff)
            return max(uoperand1, uoperand2)
        elif param == ADD:
            return (signed_op1 + signed_op2)

    def _logical_op(self, param, operand1, operand2):
        if param == XOR:
            return operand1 ^ operand2
        elif param == OR:
            return operand1 | operand2
        elif param == AND:
            return operand1 & operand2
        elif param == SWAP:
            return operand1

    def probe_blocks(self, block_perm, b_srcs, b_callback):
        probe_addrs = [ addr for addr in block_perm.keys() if block_perm[addr] != TIP ]

        self.probe_blocks_rec(probe_addrs, b_srcs, b_callback)

    def probe_blocks_rec(self, probe_addrs, b_srcs, b_callback):
        mask = (1 << self.b_datalen) - 1
        size = int(math.log(self.block_size, 2))

        if probe_addrs:
            addr = probe_addrs.pop(0)

            callback = CallBack(self.probe_blocks_rec, probe_addrs, b_srcs, b_callback)
            self.retrieveBlock(b_srcs, b_callback, callback, toN, size, \
                               addr, mask)

        else:
            self.drive = False


    def drive_input(self, memory):
        assert memory.__class__.__name__ == 'dict', \
            'tlAdapter.drive_input need dict'

        block_perm = {}
        # TODO, check the resolution of block permissions
        for addr in set([i & self.block_mask for i in memory.keys()]):
            block_perm[addr] = TIP

        self.b_queue.clear()
        self.d_queue.clear()

        d_sink_list = [i for i in range(0, 4)]
        d_sinks = FreeList('d_sinks', d_sink_list)
        b_src_list = [i for i in range(0, 1)] # TODO, BoomTile has 3 b_src
        b_srcs = FreeList('b_srcs', b_src_list)

        b_callback = srcToCallback('b_callback', b_src_list)

        self.a_monitor = cocotb.fork(self.a_port_monitor(memory, block_perm, d_sinks, \
                                                         b_srcs, b_callback))
        self.c_monitor = cocotb.fork(self.c_port_monitor(memory, block_perm, b_srcs, \
                                                         b_callback))
        self.e_monitor = cocotb.fork(self.e_port_monitor(memory, d_sinks))

        self.d_driver = cocotb.fork(self.d_port_driver())
        self.b_driver = cocotb.fork(self.b_port_driver())

        self.retriever = cocotb.fork(self.data_retriever(block_perm, b_srcs, b_callback))
        self.host_if = cocotb.fork(self.host_interface(block_perm, b_srcs, b_callback))

    @coroutine
    def a_port_monitor(self, memory, block_perm, d_sinks, b_srcs, b_callback):

        clkedge = RisingEdge(self.dut.clock)
        a_ports = self.a_ports

        ongoings = {} # On going TL-A transactions (src - count)

        a_ports.ready <= 1
        while self.drive:
            if a_ports.fire():
                opcode = a_ports.get('opcode')
                param = a_ports.get('param')
                size = a_ports.get('size')
                source = a_ports.get('source')
                addr = a_ports.get('address')
                mask = a_ports.get('mask')
                data = a_ports.get('data')

                A_assertions(opcode, param, size, addr, mask, self.debug)

                assert not ongoings or source in ongoings.keys(), \
                    'Messages in A channel can not be interleaved'

                addr_aligned = addr & self.addr_mask_d
                block_addr = addr & self.block_mask
                burst_len = int(max(pow(2, size) / self.d_datalen, 1))
                bit_mask = int(''.join([ '{:02x}'.format(0xff * int(i)) \
                                        for i in ('{:0b}'.format(mask))]), 16)

                block_perm[block_addr] = block_perm.get(block_addr, TIP)

                " TL-UL "
                if opcode == GET:
                    " Check block permission "
                    if block_perm[block_addr] != TIP:
                        callback = CallBack(self.AccessAckData_cb, memory, burst_len, \
                                                 addr_aligned, size, source)
                        self.retrieveBlock(b_srcs, b_callback, callback, toT, size, \
                                           addr, mask)

                    else:
                        d_msgs = self.get_d_messages('AccessAckData', memory, burst_len, addr_aligned, \
                                                     size=size, source=source)
                        self.d_queue.push_msgs(d_msgs)

                if opcode == PUT_FULL_DATA:
                    count = ongoings.get(source, 0)
                    get_addr = addr_aligned + count * self.a_datalen

                    # TODO, Block_perm should not change during burst
                    if block_perm[block_addr] != TIP:
                        if count == 0:
                            burst_data = {}
                            callback = CallBack(self.AccessAck_cb, memory, ongoings, \
                                                burst_len, burst_data, size, source)
                            self.retrieveBlock(b_srcs, b_callback, callback, toN, size, \
                                               addr, mask)

                        burst_data[get_addr] = (bit_mask, data)
                    else:
                        masked_data = memory.get(get_addr, 0) & (~bit_mask)
                        get_data = masked_data | (data & bit_mask)

                        memory[get_addr] = get_data

                        if count + 1 == burst_len:
                            self.d_queue.push('AccessAck', None, size=size, source=source)
                            if count: ongoings.pop(source)
                        else:
                            ongoings[source] = count + 1

                if opcode == PUT_PARTIAL_DATA:
                    count = ongoings.get(source, 0)
                    get_addr = addr_aligned + count * self.a_datalen

                    # TODO, Block_perm should not change during burst
                    if block_perm[block_addr] != TIP:
                        if count == 0:
                            burst_data = {}
                            callback = CallBack(self.AccessAck_cb, memory, ongoings, \
                                                burst_len, burst_data, size, source)
                            self.retrieveBlock(b_srcs, b_callback, callback, toN, size, \
                                               addr, mask)

                        burst_data[get_addr] = (bit_mask, data)
                    else:
                        masked_data = memory.get(get_addr, 0) & (~bit_mask)
                        get_data = masked_data | (data & bit_mask)

                        memory[get_addr] = get_data

                        if count + 1 == burst_len:
                            self.d_queue.push('AccessAck', None, size=size, source=source)
                            if count: ongoings.pop(source)
                        else:
                            ongoings[source] = count + 1

                " TL-UH "
                if opcode == ARITHMETIC_DATA and \
                   self.protocol >= TL_UH:

                    count = ongoings.get(source, 0)

                    # TODO, extend to multiple block
                    assert burst_len == 1, \
                        'ARITHMETIC_DATA can not span over multiple block'

                    total_mask = 0
                    offset = int(math.log((mask & -mask), 2) * 8)

                    get_addr = addr_aligned + count * self.a_datalen
                    get_data = data & bit_mask

                    operand1 = get_data >> offset

                    # TODO, Block_perm should not change during burst
                    if block_perm[block_addr] != TIP:
                        callback = CallBack(self.ArithmeticAck_cb, operand1, memory, burst_len, \
                                            addr_aligned, bit_mask, offset, size, source)
                        self.retrieveBlock(b_srcs, b_callback, callback, toN, size, \
                                           addr, mask)

                    else:
                        if get_addr not in memory.keys():
                            memory[get_addr] = self.nop_data
                        # TODO, operand2 offset?
                        operand2 = (memory[get_addr] & bit_mask) >> offset
                        result = (self._arithmetic_op(param, operand1, operand2, mask) << offset) & \
                            self.a_ports.data_mask # TODO, check _arithmetic_op

                        memory[get_addr] = (memory[get_addr] & (~bit_mask)) | (result & bit_mask)
                        self.d_queue.push('AccessAckData', None, size=size, source=source, data=operand2)

                if opcode == LOGICAL_DATA and \
                   self.protocol >= TL_UH:

                    count = ongoings.get(source, 0)

                    # TODO, extend to multiple block
                    assert burst_len == 1, \
                        'LOGICAL_DATA can not span over multiple block'

                    total_mask = 0
                    offset = int(math.log((mask & -mask), 2) * 8)

                    get_addr = addr_aligned + count * self.a_datalen
                    get_data = data & bit_mask

                    operand1 = get_data >> offset

                    # TODO, Block_perm should not change during burst
                    if block_perm[block_addr] != TIP:

                        callback = CallBack(self.LogicalAck_cb, operand1, memory, burst_len, \
                                            addr_aligned, bit_mask, offset, size, source)
                        self.retrieveBlock(b_srcs, b_callback, callback, toN, size, \
                                           addr, mask)

                    else:
                        if get_addr not in memory.keys():
                            memory[get_addr] = self.nop_data
                        operand2 = (memory[get_addr] & bit_mask) >> offset
                        result = (self._logical_op(param, operand1, operand2) << offset) & \
                            self.a_ports.data_mask # TODO, check _logical_op

                        memory[get_addr] = (memory[get_addr] & (~bit_mask)) | (result & bit_mask)
                        self.d_queue.push('AccessAckData', None, size=size, source=source, data=operand2)

                if opcode == INTENT and \
                   self.protocol >= TL_UH:

                    self.d_queue.push('HintAck', None, size=size, source=source)

                " TL-C "
                if opcode == ACQUIRE_BLOCK and \
                   self.protocol == TL_C:

                    d_sink = d_sinks.get()

                    if param == NtoB: d_param = toB
                    else: d_param = toT

                    if block_perm[block_addr] != TIP:
                        if param == NtoB: b_param = toB
                        else: b_param = toN

                        callback = CallBack(self.GrantData_cb, memory, burst_len, addr_aligned, \
                                                 d_param, d_sink, size, source, block_perm, block_addr)
                        self.retrieveBlock(b_srcs, b_callback, callback, b_param, size, \
                                           addr, mask)

                    else:
                        callback_d = CallBack(self.updatePerm, block_perm, block_addr, d_param)
                        d_msgs = self.get_d_messages('GrantData', memory, burst_len, addr_aligned, \
                                                param=d_param, size=size, source=source, sink=d_sink)
                        cbs = [ callback_d ] + [ None for i in range(len(d_msgs) - 1) ]

                        self.ongoing_tlc[d_sink] = block_addr
                        self.d_queue.push_msg_cbs(d_msgs, cbs)

                if opcode == ACQUIRE_PERM and \
                   self.protocol == TL_C:

                    d_sink = d_sinks.get()

                    if param == NtoB: d_param = toB
                    else: d_param = toT

                    if block_perm[block_addr] != TIP:
                        if param == NtoB: b_param = toB
                        else: b_param = toN

                        callback = CallBack(self.Grant_cb, d_param, d_sink, size, source, \
                                                 block_perm, block_addr)
                        self.retrievePerm(b_srcs, b_callback, callback, b_param, size, \
                                           addr, mask, 'ProbePerm')

                    else:
                        callback_d = CallBack(self.updatePerm, block_perm, block_addr, d_param)

                        self.ongoing_tlc[d_sink] = block_addr
                        self.d_queue.push('Grant', callback_d, param=d_param, size=size, \
                                          source=source, sink=d_sink)

            yield clkedge

        a_ports.ready <= 0

    @coroutine
    def c_port_monitor(self, memory, block_perm, b_srcs, b_callback):

        clkedge = RisingEdge(self.dut.clock)
        c_ports = self.c_ports

        ongoings = {} # On going transactions (src - count)

        c_ports.ready <= 1
        while self.drive:
            if c_ports.fire():
                opcode = c_ports.get('opcode')
                param = c_ports.get('param')
                size = c_ports.get('size')
                source = c_ports.get('source')
                addr = c_ports.get('address')
                data = c_ports.get('data')
                corrupt = c_ports.get('corrupt')

                C_assertions(opcode, param, size, addr, corrupt, self.debug)

                assert not ongoings or source in ongoings.keys(), \
                    'Messages in C channel can not be interleaved'

                addr_aligned = addr & self.addr_mask_c
                block_addr = addr & self.block_mask
                burst_len = int(max(pow(2, size) / self.c_datalen, 1))

                if opcode == ACCESS_ACK:
                    raise NotImplementedError()

                if opcode == ACCESS_ACK_DATA:
                    raise NotImplementedError()

                if opcode == HINT_ACK:
                    raise NotImplementedError()

                if opcode == PROBE_ACK:
                    if param in [ TtoB, TtoN ]:
                        block_perm[block_addr] = TIP

                    b_callback.call(source)
                    b_srcs.release(source)

                if opcode == PROBE_ACK_DATA:
                    count = ongoings.get(source, 0)
                    get_addr = addr_aligned + count * self.c_datalen

                    memory[get_addr] = data

                    if count + 1 == burst_len:
                        if param in [ TtoB, TtoN ]:
                            block_perm[block_addr] = TIP

                        b_callback.call(source)
                        b_srcs.release(source)

                        if count: ongoings.pop(source)
                    else:
                        ongoings[source] = count + 1

                if opcode == RELEASE:
                    if param in [ TtoB, TtoN ]:
                        block_perm[block_addr] = TIP

                    self.d_queue.push('ReleaseAck', None, size=size, source=source)

                if opcode == RELEASE_DATA:
                    count = ongoings.get(source, 0)
                    get_addr = addr_aligned + count * self.c_datalen

                    memory[get_addr] = data

                    if count + 1 == burst_len:
                        if param in [ TtoB, TtoN ]:
                            block_perm[block_addr] = TIP

                        self.d_queue.push('ReleaseAck', None, size=size, source=source)

                        if count: ongoings.pop(source)
                    else:
                        ongoings[source] = count + 1

            yield clkedge

        c_ports.ready <= 0

    @coroutine
    def e_port_monitor(self, memory, d_sinks):

        clkedge = RisingEdge(self.dut.clock)
        e_ports = self.e_ports

        e_ports.ready <= 1
        while self.drive:
            if e_ports.fire():
                sink = e_ports.get('sink')
                d_sinks.release(sink)
                self.ongoing_tlc.pop(sink)

            yield clkedge

        e_ports.ready <= 0

    @coroutine
    def d_port_driver(self):

        clkedge = RisingEdge(self.dut.clock)
        d_ports = self.d_ports

        d_ports.clear()
        while self.drive:
            if not self.d_queue.empty():
                msg_callback = self.d_queue.pop()
                msg = msg_callback[0]
                if msg:
                    callback = msg_callback[1]
                    if callback:
                        callback.call()

                    d_ports.opcode <= msg.opcode
                    d_ports.param <= msg.param
                    d_ports.size <= msg.size
                    d_ports.source <= msg.source
                    d_ports.sink <= msg.sink
                    d_ports.data <= msg.data
                    d_ports.corrupt <= msg.corrupt
                    d_ports.denied <= msg.denied

                    d_ports.valid <= 1
                    yield clkedge
                    while not d_ports.fire():
                        yield clkedge

                    d_ports.clear()
                    d_ports.valid <= 0

                else:
                    yield clkedge
            else:
                yield clkedge

        d_ports.clear()

    @coroutine
    def b_port_driver(self):

        clkedge = RisingEdge(self.dut.clock)
        b_ports = self.b_ports

        b_ports.clear()
        while self.drive:
            if not self.b_queue.empty():
                msg = self.b_queue.pop()
                if msg:
                    b_ports.opcode <= msg.opcode
                    b_ports.param <= msg.param
                    b_ports.size <= msg.size
                    b_ports.source <= msg.source
                    b_ports.address <= msg.address
                    b_ports.mask <= msg.mask
                    b_ports.data <= msg.data

                    b_ports.valid <= 1
                    yield clkedge
                    while not b_ports.fire():
                        yield clkedge

                    b_ports.clear()
                    b_ports.valid <= 0

                else:
                    yield clkedge
            else:
                yield clkedge

        b_ports.clear()

    @coroutine
    def data_retriever(self, block_perm, b_srcs, b_callback):
        clkedge = RisingEdge(self.dut.clock)

        while not self.retrieve:
            yield clkedge

        self.probe_blocks(block_perm, b_srcs, b_callback)

    @coroutine
    def host_interface(self, block_perm, b_srcs, b_callback):
        clkedge = RisingEdge(self.dut.clock)

        while self.drive:
            if (self.probe & self.probe_en) and self.probe_addr not in self.ongoing_tlc.values():
                block_addr = self.probe_addr & self.block_mask
                mask = (1 << self.b_datalen) - 1
                size = int(math.log(self.block_size, 2))

                assert block_addr in block_perm.keys(), \
                    '{:016x} not in block_perm.keys()'.format(block_addr)

                if block_perm[block_addr] != TIP:
                    callback = CallBack(self.enableProbe)
                    self.retrieveBlock(b_srcs, b_callback, callback, toN, size, \
                                       self.probe_addr, mask)

                    self.probe = 0
                    self.probe_en = 0
                    self.probe_addr = 0

            yield clkedge

    def probe_block(self, probe_addr):
        self.probe = 1
        self.probe_addr = probe_addr

    def start(self, memory):
        self.drive = True
        self.retrieve = False

        self.drive_input(memory)

    def stop(self):
        self.retrieve = True

    def onGoing(self):
        return self.a_ports.valid.value | self.c_ports.valid.value

    def isRunning(self):
        return self.drive
