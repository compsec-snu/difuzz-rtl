import os
import random
import time
import sysv_ipc as ipc
import cocotb

from cocotb.decorators import coroutine
from cocotb.triggers import RisingEdge, Timer

NORMAL          = 0
ERR_COMPILE     = 1
ERR_ISA_ASSERT  = 2
ERR_ISA_TIMEOUT = 3
ERR_RTL_SIM     = 4
ERR_SI_READ     = 5

class procState():
    def __init__(self):
        self.NORMAL = NORMAL
        self.ERR_COMPILE = ERR_COMPILE
        self.ERR_ISA_TIMEOUT = ERR_ISA_TIMEOUT
        self.ERR_ISA_ASSERT = ERR_ISA_ASSERT
        self.ERR_RTL_SIM = ERR_RTL_SIM
        self.ERR_SI_READ = ERR_SI_READ

        self.tpe = {
            NORMAL: 'NORMAL',
            ERR_COMPILE: 'ERR_COMPILE',
            ERR_ISA_TIMEOUT: 'ERR_ISA_TIMEOUT',
            ERR_ISA_ASSERT: 'ERR_ISA_ASSERT',
            ERR_RTL_SIM: 'ERR_RTL_SIM',
            ERR_SI_READ: 'ERR_SI_READ'
        }


proc_state = procState()

class procManager():
    def __init__(self, multicore: int, out: str, date: str):
        random.seed(time.time())

        self.out = out
        self.cov_log = out + '/cov_log_{}.txt'.format(date)
       
        self.mNum = len(os.listdir(out + '/mismatch/sim_input'))
        self.cNum = len(os.listdir(out + '/corpus'))

        self.num_cores = multicore
       
        self.mNum_shm = None
        self.cNum_shm = None
        self.mNum_sem = None
        self.cNum_sem = None
        self.covMap_sem = None
        self.proc_states = None
        self.state_sem = None

        while True:
            try:
                key = random.randint(0, 0xffffffff)
                self.mNum_shm = ipc.SharedMemory(key+1, ipc.IPC_CREX, 0x01b4, ipc.PAGE_SIZE)
                self.cNum_shm = ipc.SharedMemory(key, ipc.IPC_CREX, 0x01b4, ipc.PAGE_SIZE)
                self.mNum_sem = ipc.Semaphore(key+3, ipc.IPC_CREX, 0x01b4, 0)
                self.cNum_sem = ipc.Semaphore(key+2, ipc.IPC_CREX, 0x01b4, 0)
                self.covMap_sem = ipc.Semaphore(key+4, ipc.IPC_CREX, 0x01b4, 1)
                self.proc_states = ipc.SharedMemory(key+5, ipc.IPC_CREX, 0x01b4, ipc.PAGE_SIZE)
                self.state_sem = ipc.Semaphore(key+6, ipc.IPC_CREX, 0x01b4, 1)
            except ipc.ExistentialError:
                self.delete_ipc(self.cNum_shm)
                self.delete_ipc(self.mNum_shm)
                self.delete_ipc(self.cNum_sem)
                self.delete_ipc(self.mNum_sem)
                self.delete_ipc(self.covMap_sem)
            else:
                break

        self.write_num('mNum', self.mNum)
        self.write_num('cNum', self.cNum)

        for i in range(self.num_cores):
            self.set_state(i, 0)

    def set_state(self, proc_num, state):
        self.state_sem.P()
        states = self.proc_states.read(self.num_cores)

        new_states = b''
        for i in range(self.num_cores):
            if i == proc_num:
                new_states += state.to_bytes(1, 'little')
            else:
                new_states += states[i].to_bytes(1, 'little')

        self.proc_states.write(new_states)
        self.state_sem.V()

    def get_state(self, proc_num):
        self.state_sem.P()
        states = self.proc_states.read(self.num_cores)
        self.state_sem.V()

        return states[proc_num]

    def delete_ipc(self, src):
        if src is not None:
            src.remove()

    def remove(self):
        self.mNum_shm.remove()
        self.cNum_shm.remove()
        self.mNum_sem.remove()
        self.cNum_sem.remove()
        self.covMap_sem.remove()
        self.proc_states.remove()


    def read_num(self, name):
        assert name in ['mNum', 'cNum'], '{} is not mNum/cNum'
        shm = getattr(self, name + '_shm')
        sem = getattr(self, name + '_sem')

        sem.P()
        num = int.from_bytes(shm.read(4), 'little')
        return num

    def write_num(self, name, num):
        assert name in ['mNum', 'cNum'], '{} is not mNum/cNum'
        shm = getattr(self, name + '_shm')
        sem = getattr(self, name + '_sem')

        num_bytes = num.to_bytes(4, 'little')
        shm.write(num_bytes)
        sem.V()

    def P(self, name):
        assert name in ['mNum', 'cNum', 'covMap', 'state'], \
            '{} is not mNum/cNum/covMap/state'

        sem = getattr(self, name + '_sem')
        sem.P()

    def V(self, name):
        assert name in ['mNum', 'cNum', 'covMap', 'state'], \
            '{} is not mNum/cNum/covMap/state'

        sem = getattr(self, name + '_sem')
        sem.V()

    def store_covmap(self, proc_num, start_time, start_iter, num_iter):
        self.covMap_sem.P()
        cov_sum = 0
        cov_files = []
        covmaps = os.listdir(self.out + '/covmap-{:02}'.format(proc_num))

        for cov_file in covmaps:
            cov_map = [ 0 for i in
                        range(os.path.getsize(self.out + '/covmap-{:02}/{}'.
                                              format(proc_num, cov_file)))]
            if os.path.isfile(self.out + '/covmap/{}'.format(cov_file)):
                fd = open(self.out + '/covmap/{}'.format(cov_file), 'r')
                line = fd.readline()
                fd.close()

                for n in range(len(cov_map)):
                    cov_map[n] = int(line[n])

            fd = open(self.out + '/covmap-{:02}/{}'.format(proc_num, cov_file), 'r')
            line = fd.readline()
            fd.close()

            for n in range(len(cov_map)):
                cov_map[n] = cov_map[n] | int(line[n])
                cov_sum = cov_sum + cov_map[n]

            cov_string = ''.join(str(e) for e in cov_map)
            fd = open(self.out + '/covmap/{}'.format(cov_file), 'w')
            fd.write(cov_string)
            fd.close()

        elapsed_time = time.time() - start_time
        fd = open(self.cov_log, 'a')
        fd.write('{:<10}\t{:<10}\t{:<10}\n'.
                 format(elapsed_time, start_iter + num_iter, cov_sum))
        fd.close()
        self.covMap_sem.V()

    @coroutine
    def clock_gen(self, clock, period=2):
        while True:
            clock <= 1
            yield Timer(period / 2)
            clock <= 0
            yield Timer(period / 2)

    @coroutine
    def cov_restore(self, dut):
        clkedge = RisingEdge(dut.clock)

        clk_driver = cocotb.fork(self.clock_gen(dut.clock))

        dut.cov_restore <= 1
        yield clkedge
        dut.cov_restore <= 0
        yield clkedge

        clk_driver.kill()

    @coroutine
    def cov_store(self, dut, proc_num):
        clkedge = RisingEdge(dut.clock)

        clk_driver = cocotb.fork(self.clock_gen(dut.clock))

        dut.cov_store <= 1
        dut.proc_num <= proc_num
        yield clkedge
        dut.cov_store <= 0
        yield clkedge

        clk_driver.kill()
