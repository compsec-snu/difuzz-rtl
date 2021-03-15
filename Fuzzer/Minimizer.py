import os
import math
from threading import Timer
from copy import deepcopy

from RTLSim.host import ILL_MEM, SUCCESS, TIME_OUT, ASSERTION_FAIL
from src.word import PREFIX, MAIN, SUFFIX

from src.utils import *
from src.multicore_manager import proc_state

def Minimize(template='Template', out='output', num_cores=1, proc_num=0,
             scheduler=None, debug=False):

    (mutator, preprocessor, isaHost, checker) = \
        setup_fpga(template, out, proc_num, debug, minimizing=True)

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
        try_count = 0

        minName = min_dir + '/' + siName.split('.si')[0] + '_min.si'
        (sim_input, data, _) = mutator.read_siminput(in_dir + '/' + siName)

        if debug:
            print('[DifuzzRTl] Original Instructions')
            for inst in sim_input.get_insts():
                print(inst)

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

                for j in range(num_test):
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

                    if debug:
                        print('[DifuzzRTL] Minimized Instructions')
                        for inst in tmp_input.get_insts():
                            print(inst)

                    (isa_input, rtl_input, symbols) = preprocessor.process(tmp_input, data, False)

                    if isa_input:
                        timer = Timer(ISA_TIME_LIMIT, isa_timeout, [out, stop, 0])
                        timer.start()
                        isaHost.run_test(isa_input)
                        timer.cancel()

                        if stop[0] == proc_state.ERR_ISA_TIMEOUT:
                            stop[0] = proc_state.NORMAL
                            continue

                        # Yield control to run FPGA simulation
                        scheduler.yield_control()
                        ret = SUCCESS
                        # try:
                        #     (ret, coverage) = yield rtlHost.run_test(rtl_input)
                        # except:
                        #     stop[0] = proc_state.ERR_RTL_SIM
                        #     break

                        match = False
                        if ret == SUCCESS:
                            match = checker.check(symbols)
                        elif ret == ILL_MEM:
                            match = True

                        if not match:
                            min_input = tmp_input
                            min_mask = tmp_mask
                            print('[DifuzzRTL] iter [{}], minimized'.format(try_count))
                            try_count += 1

                    else:
                        stop[0] = proc_state.ERR_COMPILE
                        break

                min_input.save(minName, data)

    debug_print('[DifuzzRTL] Stop Minimizing', debug)
