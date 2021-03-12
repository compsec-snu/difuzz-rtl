import os
import sys
import cocotb
import random
import time

from cocotb.regression import TestFactory
from cocotb.decorators import coroutine
from cocotb.triggers import Timer, RisingEdge
from cocotb.result import TestError, TestFailure

@coroutine
def clock_gen(clock, period=2):
    while True:
        clock <= 1
        yield Timer(period/2)
        clock <= 0
        yield Timer(period/2)

@coroutine
def run_test(dut):
    cov = os.environ['COV']
    assert cov in ['rand', 'mux', 'reg'], \
        'COV must be one of rand, mux, cov'

    chances = int(os.environ['CHANCES'])
    try: max_cycles = int(os.environ['MAX'])
    except: max_cycles = sys.maxint

    mutator = bitMutator()
    monitor = covMonitor(cov)
    cocotb.fork(clock_gen(dut.clock))
    clkedge = RisingEdge(dut.clock)

    # num_iter = 1000

    last_cov = 0

    total_state_sum = 0
    bug_catch = 0
    total_cycle = 0
    start_time = time.time()
    for chance in range(chances):
        hit_bug = False

        mutator.init()
        monitor.init()

        dut.meta_reset <= 1
        yield clkedge
        dut.meta_reset <= 0

        cycle = 0
        last_covsum = 0
        while cycle < max_cycles:
        # for iter1 in range(num_iter):
            in_bits = mutator.get_input()
            bits_list = []

            for i in range(0, len(in_bits), 3):
                bits_list.append(in_bits[i:i+3])

            dut.sdram_valid <= 0
            dut.flash_valid <= 0
            dut.rom_valid <= 0
            dut.sdram_data_i <= 0
            dut.flash_data_i <= 0
            dut.rom_data_i <= 0
            dut.reset <= 1
            yield clkedge
            dut.reset <= 0

            for bits in bits_list:
                sdram_valid = bits[0]
                # sdram_data = (bits[2] << 1 | bits[1]) & 0xf
                flash_valid = bits[1]
                # flash_data = (bits[7] << 3 | bits[6] << 2 | bits[5] << 1 | bits[4]) & 0xf
                rom_valid = bits[2]
                # rom_data = (bits[10] << 1 | bits[9]) & 0x3;
                dut.sdram_valid <= sdram_valid
                # dut.sdram_data_i <= sdram_data
                dut.flash_valid <= flash_valid
                # dut.flash_data_i <= flash_data
                dut.rom_valid <= rom_valid
                # dut.rom_data_i <= rom_data
                
                yield clkedge
                cycle = cycle + 1

                if (dut.io_cov_sum.value & 0xfffff) > last_covsum:
                    last_covsum = dut.io_cov_sum.value & 0xfffff
                    # print('{}, {}'.format(cycle, last_covsum))

                if cycle % 10000 == 0:
                    print('{}: {}'.format(cycle, last_covsum))

                # if (dut.bug.value & 0x1):
                #     hit_bug = True
                #     bug_catch = bug_catch + 1
                #     break

            # if hit_bug:
            #     break

            mytime = time.time() - start_time
            new, cov = monitor.interesting(dut.coverage.value & 0xfffff)
            if new:
                mutator.save_corpus()

        # if hit_bug:
        #     print('-------------------------------------------------------')
        #     # print('{}\t{}'.format(cycle, dut.io_cov_sum.value & 0xfffff))
        # else:
        #     print('Failed\t{}'.format(dut.io_cov_sum.value & 0xfffff))

        total_cycle = total_cycle + cycle
        total_state_sum = total_state_sum + (dut.io_cov_sum.value & 0xfffff)

    if bug_catch != 0:
        print('Average cycles to catch bug: {}, total state reached: {}'.format(total_cycle / bug_catch, total_state_sum))
    else:
        print('No bug found')


class bitMutator():
    def __init__(self):
        self.corpus = []
        self.corpus_size = 100
        self.new_seed = None

    def init(self):
        self.corpus = []

    def get_input(self):
        if not self.corpus or random.random() < 0.5:
            seed = [ random.randint(0,1) for i in range(3 * 10)]
        else:
            seed = random.choice(self.corpus)

        self.new_seed = self.mutate(seed)
        return self.new_seed

    def mutate(self, seed):
        new_seed = []
        for i in range(len(seed)):
            if random.random() < 0.2:
                new_seed.append(1^seed[i])
            else:
                new_seed.append(seed[i])

        if random.random() < 0.1 and len(new_seed) < 30:
            new_seed = new_seed + [ random.randint(0,1) for i in range(3)]
        if random.random() < 0.1 and len(new_seed) > 3:
            new_seed = new_seed[0:len(new_seed) - 3]

        return new_seed

    def save_corpus(self):
        self.corpus.append(self.new_seed)

class covMonitor():
    def __init__(self, cov):
        self.last_reg_cov = 0
        self.cov = cov
        self.mux_covs = []
        self.tot_covs = 0

    def init(self):
        self.last_reg_cov = 0
        self.mux_covs = []

    def interesting(self, coverage):
        if self.cov == 'mux':
            covsum = 0

            if coverage not in self.mux_covs:
                self.mux_covs.append(coverage)
                self.tot_covs = self.tot_covs | coverage

                for i in range(18):
                    covsum = covsum + (self.tot_covs >> i & 1)

                return True, covsum
            else:
                return False, covsum

        elif self.cov == 'reg':
            if coverage > self.last_reg_cov:
                self.last_reg_cov = coverage
                return True, coverage
            else:
                return False, coverage
        else:
            return False, 0

factory = TestFactory(run_test)
factory.generate_tests()
