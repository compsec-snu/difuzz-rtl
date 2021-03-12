import os
import math
from threading import Timer
from copy import deepcopy

from cocotb.decorators import coroutine
from RTLSim.host import ILL_MEM, SUCCESS, TIME_OUT, ASSERTION_FAIL
from src.word import PREFIX, MAIN, SUFFIX

from src.utils import *
from src.multicore_manager import proc_state

@coroutine
def Minimize(dut, toplevel,
             template='Template', out='output', num_cores=1, proc_num=0,
             debug=False):

    assert toplevel in ['RocketTile', 'BoomTile' ], \
        '{} is not toplevel'.format(toplevel)

    (mutator, preprocessor, isaHost, rtlHost, checker) = \
        setup(dut, toplevel, template, out, proc_num, debug, minimizing=True)

    in_dir = out + '/mismatch/sim_input'
    stop = [ proc_state.NORMAL ]

    min_dir = out + '/mismatch/min_input'
    if not os.path.isdir(min_dir):
        os.makedirs(min_dir)

    print('[DifuzzRTL] Start Minimizing')

    siNames = os.listdir(in_dir)
    start = proc_num * ((len(siNames) // num_cores) + 1)
    end = (proc_num + 1) * ((len(siNames) // num_cores) + 1)
    for siName in siNames[start:end]:
        print('[DifuzzRTL] Minimizing {}'.format(siName))

        minName = min_dir + '/' + siName.split('.si')[0] + '_min.si'
        (sim_input, data, assert_intr) = mutator.read_siminput(in_dir + '/' + siName)

        if debug:
            print('[DifuzzRTl] Original Instructions')
            for inst, INT in zip(sim_input.get_insts(), sim_input.ints + [0]):
                print('{:<50}{:04b}'.format(inst, INT))

        min_input = deepcopy(sim_input)

        for part in [ PREFIX, MAIN, SUFFIX ]:
            if part == PREFIX:
                len_mask = min_input.num_prefix
            elif part == MAIN:
                len_mask = min_input.num_words
            else: # SUFFIX
                len_mask = min_input.num_suffix

            if len_mask == 0:
                continue

            nop_mask = [ 0 for i in range(len_mask) ]
            min_mask = deepcopy(nop_mask)

            for i in range(int(math.log(len_mask, 2))):
                num_nop = len_mask // min(pow(2, i+1), len_mask)
                num_test = len_mask // num_nop
                rest = num_nop + (len_mask % num_nop)

                delete_nop = 1 if part == SUFFIX and (i == int(math.log(len_mask, 2)) - 1) else 0
                for j in range(num_test + delete_nop):

                    if j < num_test:
                        tmp_mask = []
                        tmp_mask += [ 0 for k in range(num_nop * j) ]

                        if j != num_test - 1:
                            tmp_mask += [ 1 for k in range(num_nop) ]
                            tmp_mask += [ 0 for k in range(len_mask - num_nop * (j + 1)) ]
                        else:
                            tmp_mask += [ 1 for k in range(rest) ]

                        for (n, tup) in enumerate(zip(tmp_mask, min_mask)):
                            tmp_mask[n] = tup[0] | tup[1]

                        if tmp_mask == min_mask:
                            continue

                        (tmp_input, data) = mutator.make_nop(min_input, tmp_mask, part)

                    else:
                        (tmp_input, data) = mutator.delete_nop(min_input)

                    if debug:
                        print('[DifuzzRTL] Minimized Instructions')
                        for inst in tmp_input.get_insts():
                            print(inst)

                    (isa_input, rtl_input, symbols) = preprocessor.process(tmp_input, data, assert_intr)

                    if isa_input and rtl_input:
                        ret = run_isa_test(isaHost, isa_input, stop, out, proc_num)
                        if ret == proc_state.ERR_ISA_TIMEOUT: continue

                        try:
                            (ret, coverage) = yield rtlHost.run_test(rtl_input, assert_intr)
                        except:
                            stop[0] = proc_state.ERR_RTL_SIM
                            break

                        if assert_intr and ret == SUCCESS:
                            (intr_prv, epc) = checker.check_intr(isa_input, rtl_input, epc)
                            if epc != 0:
                                ret = run_isa_test(isaHost, isa_input, stop, out, proc_num, True)
                                if ret == proc_state.ERR_ISA_TIMEOUT: continue
                            else: continue

                        match = False
                        if ret == SUCCESS:
                            match = checker.check(symbols)
                        elif ret == ILL_MEM:
                            match = True

                        if not match:
                            min_input = tmp_input
                            min_mask = tmp_mask

                    else:
                        stop[0] = proc_state.ERR_COMPILE
                        break

                min_input.save(minName, data)

    debug_print('[DifuzzRTL] Stop Minimizing', debug)
