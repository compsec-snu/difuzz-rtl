//See LICENSE for license details.
#ifndef RTLSIM
#include "simif_f1.h"
#else
#include "simif_emul.h"
#endif

#include "fasedtests_top.h"
#include "test_harness_bridge.h"
// MIDAS-defined bridges
#include "bridges/fased_memory_timing_model.h"
#include "bridges/synthesized_assertions.h"
#include "bridges/synthesized_prints.h"

fasedtests_top_t::fasedtests_top_t(int argc, char** argv)
{
    std::vector<std::string> args(argv + 1, argv + argc);
    max_cycles = -1;
    profile_interval = max_cycles;

    for (auto &arg: args) {
        if (arg.find("+max-cycles=") == 0) {
            max_cycles = atoi(arg.c_str()+12);
        }
        if (arg.find("+profile-interval=") == 0) {
            profile_interval = atoi(arg.c_str()+18);
        }
        if (arg.find("+zero-out-dram") == 0) {
            do_zero_out_dram = true;
        }
    }


#ifdef FASEDMEMORYTIMINGMODEL_0
    INSTANTIATE_FASED(fpga_models.push_back, 0)
#endif
#ifdef FASEDMEMORYTIMINGMODEL_1
    INSTANTIATE_FASED(fpga_models.push_back, 1)
#endif
#ifdef FASEDMEMORYTIMINGMODEL_2
    INSTANTIATE_FASED(fpga_models.push_back, 2)
#endif
#ifdef FASEDMEMORYTIMINGMODEL_3
    INSTANTIATE_FASED(fpga_models.push_back, 3)
#endif
#ifdef FASEDMEMORYTIMINGMODEL_4
    INSTANTIATE_FASED(fpga_models.push_back, 4)
#endif
#ifdef FASEDMEMORYTIMINGMODEL_5
    INSTANTIATE_FASED(fpga_models.push_back, 5)
#endif
#ifdef FASEDMEMORYTIMINGMODEL_6
    INSTANTIATE_FASED(fpga_models.push_back, 6)
#endif
#ifdef FASEDMEMORYTIMINGMODEL_7
    INSTANTIATE_FASED(fpga_models.push_back, 7)
#endif
#ifdef FASEDMEMORYTIMINGMODEL_8
    INSTANTIATE_FASED(fpga_models.push_back, 8)
#endif

    // Add functions you'd like to periodically invoke on a paused simulator here.
    if (profile_interval != -1) {
        register_task([this](){ return this->profile_models();}, 0);
    }
    // Test harness.
    AddressMap fased_addr_map = AddressMap(FASEDMEMORYTIMINGMODEL_0_R_num_registers,
                                           (const unsigned int*) FASEDMEMORYTIMINGMODEL_0_R_addrs,
                                           (const char* const*) FASEDMEMORYTIMINGMODEL_0_R_names,
                                           FASEDMEMORYTIMINGMODEL_0_W_num_registers,
                                           (const unsigned int*) FASEDMEMORYTIMINGMODEL_0_W_addrs,
                                           (const char* const*) FASEDMEMORYTIMINGMODEL_0_W_names);
    add_bridge_driver(new test_harness_bridge_t(this, fased_addr_map, args));
}

bool fasedtests_top_t::simulation_complete() {
    bool is_complete = false;
    for (auto &e: bridges) {
        is_complete |= e->terminate();
    }
    return is_complete;
}

uint64_t fasedtests_top_t::profile_models(){
    for (auto mod: fpga_models) {
        mod->profile();
    }
    return profile_interval;
}

int fasedtests_top_t::exit_code(){
    for (auto &e: bridges) {
        if (e->exit_code())
            return e->exit_code();
    }
    return 0;
}


void fasedtests_top_t::run() {
    for (auto &e: fpga_models) {
        e->init();
    }

    for (auto &e: bridges) {
        e->init();
    }

    if (do_zero_out_dram) {
        fprintf(stderr, "Zeroing out FPGA DRAM. This will take a few seconds...\n");
        zero_out_dram();
    }
    fprintf(stderr, "Commencing simulation.\n");
    uint64_t start_hcycle = hcycle();
    uint64_t start_time = timestamp();

    // Assert reset T=0 -> 50
    target_reset(50);

    while (!simulation_complete() && !has_timed_out()) {
        run_scheduled_tasks();
        step(get_largest_stepsize(), false);
        while(!done() && !simulation_complete()){
            for (auto &e: bridges) e->tick();
        }
    }

    uint64_t end_time = timestamp();
    uint64_t end_cycle = actual_tcycle();
    uint64_t hcycles = hcycle() - start_hcycle;
    double sim_time = diff_secs(end_time, start_time);
    double sim_speed = ((double) end_cycle) / (sim_time * 1000.0);
    // always print a newline after target's output
    fprintf(stderr, "\n");
    int exitcode = exit_code();
    if (exitcode) {
        fprintf(stderr, "*** FAILED *** (code = %d) after %llu cycles\n", exitcode, end_cycle);
    } else if (!simulation_complete() && has_timed_out()) {
        fprintf(stderr, "*** FAILED *** (timeout) after %llu cycles\n", end_cycle);
    } else {
        fprintf(stderr, "*** PASSED *** after %llu cycles\n", end_cycle);
    }
    if (sim_speed > 1000.0) {
        fprintf(stderr, "time elapsed: %.1f s, simulation speed = %.2f MHz\n", sim_time, sim_speed / 1000.0);
    } else {
        fprintf(stderr, "time elapsed: %.1f s, simulation speed = %.2f KHz\n", sim_time, sim_speed);
    }
    double fmr = ((double) hcycles / end_cycle);
    fprintf(stderr, "FPGA-Cycles-to-Model-Cycles Ratio (FMR): %.2f\n", fmr);
    expect(!exitcode, NULL);

    for (auto e: fpga_models) {
        e->finish();
    }
#ifdef PRINTBRIDGEMODULE_0_PRESENT
    print_bridge->finish();
#endif
}


// top for RTL sim
class fasedtests_driver_t:
#ifdef RTLSIM
    public simif_emul_t, public fasedtests_top_t
#else
    public simif_f1_t, public fasedtests_top_t
#endif
{
    public:
#ifdef RTLSIM
        fasedtests_driver_t(int argc, char** argv): fasedtests_top_t(argc, argv) {};
#else
        fasedtests_driver_t(int argc, char** argv): simif_f1_t(argc, argv), fasedtests_top_t(argc, argv) {};
#endif
};

int main(int argc, char** argv) {
    fasedtests_driver_t driver(argc, argv);
    driver.init(argc, argv);
    driver.run();
    return driver.finish();
}
