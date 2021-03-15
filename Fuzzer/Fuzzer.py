import os
import time
import random
import shutil
from threading import Timer

from RTLSim.host import ILL_MEM, SUCCESS, TIME_OUT, ASSERTION_FAIL

from src.utils import *
from src.multicore_manager import proc_state


def Run(num_iter=1, template='Template', in_file=None,
        out='output', record=False, cov_log=None, scheduler=None,
        proc_num=0, start_time=0, start_iter=0, start_cov=0,
        no_guide=False, debug=False):

    random.seed(time.time() * (proc_num + 1))

    (mutator, preprocessor, isaHost, checker) = \
        setup_fpga(template, out, proc_num, debug, no_guide=no_guide)

    if in_file: num_iter = 1

    stop = [ proc_state.NORMAL ]
    mNum = 0
    cNum = 0
    iNum = 0
    last_coverage = 0

    debug_print('[DifuzzRTL] Start Fuzzing', debug)

    eNum = 0
    for it in range(num_iter):
        debug_print('[DifuzzRTL] Iteration [{}]'.format(it), debug)

        if in_file: (sim_input, data, _) = mutator.read_siminput(in_file)
        else: (sim_input, data) = mutator.get()

        if debug:
            print('[DifuzzRTL] Fuzz Instructions')
            for inst in sim_input.get_insts():
                print(inst)

        (isa_input, rtl_input, symbols) = preprocessor.process(sim_input, data, False)

        if isa_input:
            timer = Timer(ISA_TIME_LIMIT, isa_timeout, [out, stop, proc_num])
            timer.start()
            isa_ret = isaHost.run_test(isa_input)
            timer.cancel()

            if stop[0] == proc_state.ERR_ISA_TIMEOUT:
                if not os.path.isdir(out + '/err'):
                    os.makedirs(out + '/err')

                shutil.copyfile(out + '/.input_{}.si'.format(proc_num),
                                out + '/err/err_{}.si'.format(eNum))

                eNum = eNum + 1

                stop[0] = proc_state.NORMAL
                continue
            elif isa_ret != 0:
                stop[0] = proc_state.ERR_ISA_ASSERT
                break

            # Yield control to run FPGA simulation
            scheduler.yield_control()
            ret = SUCCESS
            try:
                fd = open('/tmp/.covsum_0.txt')
                coverage = int(fd.readline())
                fd.close()
                os.remove('/tmp/.covsum_0.txt')
            except:
                raise Exception('No /tmp/.covsum_0.txt')

            cause = '-'
            match = False
            if ret == SUCCESS:
                match = checker.check(symbols)
            elif ret == ILL_MEM:
                match = True
                debug_print('[DifuzzRTL] Memory access outside DRAM -- {}'. \
                            format(iNum), debug, True)
                if record:
                    save_mismatch(out, proc_num, out + '/illegal',
                                  sim_input, data, iNum)
                iNum += 1

            if not match or ret not in [SUCCESS, ILL_MEM]:
                if record:
                    save_mismatch(out, proc_num, out + '/mismatch',
                                  sim_input, data, mNum)

                mNum += 1
                if ret == TIME_OUT: cause = 'Timeout'
                elif ret == ASSERTION_FAIL: cause = 'Assertion fail'
                else: cause = 'Mismatch'

                # debug_print('[DifuzzRTL] Bug -- {} [{}]'. \
                #             format(mNum, cause), debug, not match or (ret != SUCCESS))

            if coverage > last_coverage:
                if record:
                    save_file(cov_log, 'a', '{:<10}\t{:<10}\t{:<10}\n'.
                              format(time.time() - start_time, start_iter + it,
                                     start_cov + coverage))
                    sim_input.save(out + '/corpus/id_{}.si'.format(cNum))

                cNum += 1
                mutator.add_corpus(sim_input)
                last_coverage = coverage

                print('[DifuzzRTL] iter [{}] new coverage -- {}'.format(it, coverage))

            mutator.update_phase(it)

        else:
            stop[0] = proc_state.ERR_COMPILE
            # Compile failed
            break

    debug_print('[DifuzzRTL] Stop Fuzzing', debug)
