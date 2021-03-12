import random
import queue

from adapters.tilelink.definitions import *


""" Tilelink ports and real name mappings """
class Ports:
    def __init__(self, dut, channel, fields, port_names):
        self.fields = fields
        self.bit_fields = []
        self.fire_fields = []

        channel = channel.lower()
        assert channel in ['a', 'b', 'c', 'd', 'e'], \
            '{} is not Tilelink channel'.format(channel)

        for attr in fields:
            bits_port = [p for p in port_names if '_{}_bits_{}'.format(channel, attr) in p]
            fire_port = [p for p in port_names if '_{}_{}'.format(channel, attr) in p]

            assert len(bits_port) < 2 and len(fire_port) < 2 and not (bits_port and fire_port), \
                'multiple attribute: ' + ', '.join([str(e) for e in bits_port + fire_port])

            if bits_port:
                self.bit_fields.append(attr)
                setattr(self, attr, getattr(dut, bits_port[0]))
            else:
                self.fire_fields.append(attr)
                setattr(self, attr, getattr(dut, fire_port[0]))

        for attr in fields:
            if not getattr(self, attr):
                raise Exception('{} has incomplete tl_{}_ports'.format(dut.name, channel))

        for attr in fields:
            attr_len = len(getattr(self, attr))
            setattr(self, attr + '_len', attr_len)

            attr_mask = (1 << attr_len) - 1
            setattr(self, attr + '_mask', attr_mask)

    def get(self, attr):
        return getattr(self, attr).value & getattr(self, attr + '_mask')

    def fire(self):
        return self.ready.value & self.valid.value

    def clear(self):
        for field in self.bit_fields:
            port = getattr(self, field)
            port <= 0


""" CallBack functions which tilelink adapter should run """
class CallBack():
    def __init__(self, func, *args, **kwargs):
        self.func = func
        self.args = args
        self.kwargs = kwargs

    def call(self):
        self.func(*self.args, **self.kwargs)


""" Tilelinke D_ports/B_ports Queue
Merge responses from TL_A_port and TL_C_port into queue.
Used for serializing the responses into the responses in ports
"""
class tlDMessage():
    __slots__ = ('opcode', 'param', 'size', 'source', 'sink', 'data', 'corrupt', 'denied')
    messages = ('AccessAckData', 'AccessAck', 'HintAck', 'Grant', 'GrantData', \
                'ReleaseAck')

    def __init__(self, message, **kwargs):
        assert message in self.messages, \
            '{} is not in tlDMessages'.format(message)

        for attr in self.__slots__:
            setattr(self, attr, 0)

        if message == 'AccessAckData':
            self.opcode = ACCESS_ACK_DATA
            self.param = 0
            self.data = kwargs['data']

        elif message == 'AccessAck':
            self.opcode = ACCESS_ACK
            self.param = 0

        elif message == 'HintAck':
            self.opcode = HINT_ACK
            self.param = 0

        elif message == 'Grant':
            self.opcode = GRANT
            self.param = kwargs['param']
            self.sink = kwargs['sink']

        elif message == 'GrantData':
            self.opcode = GRANT_DATA
            self.param = kwargs['param']
            self.sink = kwargs['sink']
            self.data = kwargs['data']

        else: # ReleaseAck
            self.opcode = RELEASE_ACK
            self.param = 0

        self.size = kwargs['size']
        self.source = kwargs['source']


class tlBMessage():
    __slots__ = ('opcode', 'param', 'size', 'source', 'address', 'mask', 'data')
    messages = ('Get', 'PutFullData', 'PutPartialData', 'ArithmeticData', \
                'LogicalData', 'Intent', 'ProbeBlock', 'ProbePerm')

    def __init__(self, message, **kwargs):
        assert message in self.messages, \
            '{} is not in tlBMessages'.format(message)

        for attr in self.__slots__:
            setattr(self, attr, 0)

        if message == 'Get':
            self.opcode = GET
            self.param = 0

        elif message == 'PutFullData':
            self.opcode = PUT_FULL_DATA
            self.param = 0
            self.data = kwargs['data']

        elif message == 'PutPartialData':
            self.opcode = PUT_PARTIAL_DATA
            self.param = 0
            self.data = kwargs['data']

        elif message == 'ArithmeticData':
            self.opcode = ARITHMETIC_DATA
            self.param = kwargs['param']
            self.data = kwargs['data']

        elif message == 'LogicalData':
            self.opcode = LOGICAL_DATA
            self.param = kwargs['param']
            self.data = kwargs['data']

        elif message == 'Intent':
            self.opcode = INTENT
            self.param = kwargs['param']

        elif message == 'ProbeBlock':
            self.opcode = PROBE_BLOCK
            self.param = kwargs['param']

        elif message == 'ProbePerm':
            self.opcode = PROBE_PERM
            self.param = kwargs['param']

        self.size = kwargs['size']
        self.source = kwargs['source']
        self.address = kwargs['address']
        self.mask = kwargs['mask']

class Queue():
    def __init__(self):
        self.queue = queue.Queue()

    def clear(self):
        while not self.queue.empty():
            self.queue.get()

    def push(self, message, **kwargs):
        raise NotImplementedError()

    def check_msg(self, message):
        raise NotImplementedError()

    def push_msgs(self, messages):
        for msg in messages:
            self.check_msg(msg)
            self.queue.put((msg, None))

    def push_msg_cbs(self, msgs, cbs):
        assert len(msgs) == len(cbs), \
            'push_msg_cbs the number of msgs and cbs should be same'
        for (msg, cb) in zip(msgs, cbs):
            self.check_msg(msg)
            self.queue.put((msg, cb))

    def pop(self):
        return self.queue.get()

    def empty(self):
        return self.queue.empty()

class tlDQueue(Queue):
    def __init__(self):
        super().__init__()

    def push(self, message, callback, **kwargs):
        if message == 'Bubble':
            self.queue.put((None, None))
        else:
            entry = (tlDMessage(message, **kwargs), callback)
            self.queue.put(entry)

    def check_msg(self, message):
        assert message.__class__.__name__ == 'tlDMessage', \
            '{} is not DMessage'.format(message)

class tlBQueue(Queue):
    def __init__(self):
        super().__init__()

    def push(self, message, **kwargs):
        if message == 'Bubble':
            self.queue.put(None)
        else:
            self.queue.put(tlBMessage(message, **kwargs))

    def check_msg(self, message):
        assert message.__class__.__name__ == 'tlBMessage', \
            '{} is not DMessage'.format(message)

class FreeList():
    def __init__(self, name, init_list):
        self.name = name
        self.init_list = init_list
        self.free_list = init_list.copy()
        self.event_queue = queue.Queue()

    def get(self):
        assert self.free_list, \
            '{} is empty'.format(self.name)

        ret = random.choice(self.free_list)

        self.free_list.remove(ret)

        return ret

    def empty(self):
        return not bool(self.free_list)

    def reserve(self, callback):
        self.event_queue.put(callback)

    def release(self, ret):
        assert ret in self.init_list, \
            '{} not in {} init_list'.format(ret, self.name)
        assert ret not in self.free_list, \
            '{} already in {} free_list'.format(ret, self.name)

        self.free_list.append(ret)

        if not self.event_queue.empty():
            event = self.event_queue.get()
            event.call()

        return

class srcToCallback():
    def __init__(self, name, init_srcs):
        self.name = name
        self.srcs = init_srcs
        self.c_map = {}
        for src in self.srcs:
            self.c_map[src] = None

    def set(self, src, callback):
        assert src in self.srcs, \
            '{} not in {} srcs'.format(src, self.name)
        assert self.c_map[src] == None, \
            '{} duplicate callback setup'.format(self.name)

        self.c_map[src] = callback

    def call(self, src):
        if self.c_map[src] is not None:
            callback = self.c_map[src]

            self.c_map[src] = None
            callback.call()

""" TL_A Assertions """
def A_assertions(opcode, param, size, addr, mask, debug=False):
    if debug:
        if opcode == GET:
            assert param == 0, 'GET: param must be 0'
            assert addr % pow(2, size) == 0, \
                'GET: address must be aligned to size'
            assert (mask & (mask + (mask & -mask))) == 0, \
                'GET: bits in mask must be contiguous'

        elif opcode == PUT_FULL_DATA:
            assert param == 0, 'PUT_FULL_DATA: param must be 0'
            assert addr % pow(2, size) == 0, \
                'PUT_FULL_DATA: address must be aligned to size'
            assert (mask & (mask + (mask & -mask))) == 0, \
                'PUT_FULL_DATA: bits in mask must be contiguous'

        elif opcode == PUT_PARTIAL_DATA:
            assert param == 0, 'PUT_PARTIAL_DATA: param must be 0'
            assert addr % pow(2, size) == 0, \
                'PUT_PARTIAL_DATA: address must be aligned to size'

        elif opcode == ARITHMETIC_DATA:
            assert param < 5, \
                'ARITHMETIC_DATA: param must be lower than 5'
            assert addr % pow(2, size) == 0, \
                'ARITHMETIC_DATA: address must be aligned to size'
            assert (mask & (mask + (mask & -mask))) == 0, \
                'ARITHMETIC_DATA: bits in mask must be contiguous'

        elif opcode == LOGICAL_DATA:
            assert param < 4, \
                'LOGICAL_DATA: param must be lower than 4'
            assert addr % pow(2, size) == 0, \
                'LOGICAL_DATA: address must be aligned to size'
            assert (mask & (mask + (mask & -mask))) == 0, \
                'LOGICAL_DATA: bits in mask must be contiguous'

        elif opcode == INTENT:
            assert param < 2, \
                'INTENT: param must be lower than 2'
            assert addr % pow(2, size) == 0, \
                'INTENT: address must be aligned to size'

        elif opcode == ACQUIRE_BLOCK:
            assert param in [NtoB, NtoT, BtoT], \
                'ACQUIRE_BLOCK: param must be in GROW transactions'
            assert addr % pow(2, size) == 0, \
                'ACQUIRE_BLOCK: address must be aligned to size'
            assert (mask & (mask + (mask & -mask))) == 0, \
                'ACQUIRE_BLOCK: bits in mask must be contiguous'

        elif opcode == ACQUIRE_PERM:
            assert param in [NtoB, NtoT, BtoT], \
                'ACQUIRE_PERM: param must be in GROW transactions'
            assert addr % pow(2, size) == 0, \
                'ACQUIRE_PERM: address must be aligned to size'
            assert (mask & (mask + (mask & -mask))) == 0, \
                'ACQUIRE_PERM: bits in mask must be contiguous'

""" TL_C Assertions """
def C_assertions(opcode, param, size, addr, corrupt, debug=False):
    if debug:
        # TODO, Add assertions for ACCESS_ACK, ACCESS_ACK_DATA, HINT_ACK
        if opcode == PROBE_ACK:
            assert param in [TtoB, TtoN, BtoN, TtoT, BtoB, NtoN], \
                'PROBE_ACK: param must be PRUNE or REPORT'
            assert addr % pow(2, size) == 0, \
                'PROBE_ACK: address must be aligned to size'
            assert corrupt == 0, \
                'PROBE_ACK: corrupt must be 0'

        elif opcode == PROBE_ACK_DATA:
            assert param in [TtoB, TtoN, BtoN, TtoT, BtoB, NtoN], \
                'PROBE_ACK: param must be PRUNE or REPORT'
            assert addr % pow(2, size) == 0, \
                'PROBE_ACK: address must be aligned to size'
            assert corrupt == 0, \
                'PROBE_ACK: corrupt must be 0'

        elif opcode == RELEASE:
            assert param in [TtoB, TtoN, BtoN, TtoT, BtoB, NtoN], \
                'RELEASE: param must be in PRUNE or REPORT transitions'
            assert addr % pow(2, size) == 0, \
                'RELEASE: address must be aligned to size'

        elif opcode == RELEASE_DATA:
            assert param in [TtoB, TtoN, BtoN], \
                'RELEASE: param must be in PRUNE transitions'
            assert addr % pow(2, size) == 0, \
                'RELEASE: address must be aligned to size'
