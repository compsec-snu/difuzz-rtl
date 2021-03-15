//See LICENSE for license details
#ifndef RTLSIM
#include "simif_f1.h"
#else
#include "simif_emul.h"
#endif
#include "firesim_top.h"

#include <fstream>

// top for RTL sim
class firesim_f1_t:
#ifdef RTLSIM
    public simif_emul_t, public firesim_top_t
#else
    public simif_f1_t, public firesim_top_t
#endif
{
    public:
#ifdef RTLSIM
        firesim_f1_t(int argc, char** argv): firesim_top_t(argc, argv) {};
#else
        firesim_f1_t(int argc, char** argv): simif_f1_t(argc, argv), firesim_top_t(argc, argv) {};
#endif
};

#ifdef DIFUZZRTL
void bridge_reset(firesim_f1_t *firesim) {
    firesim->bridge_reset(1);
    firesim->yield_tick(5);
    firesim->bridge_reset(0);
}

int save_covsum(unsigned int covsum) {
    std::ofstream covFile("/tmp/.covsum_0.txt", std::ofstream::out);
    covFile << std::to_string(covsum).c_str();
    covFile.close();
}

void fuzz(firesim_f1_t *firesim) {
    firesim->cov_init(1048576);
    while (true) {
        bridge_reset(firesim);
        firesim->reload();
        firesim->run();
        save_covsum(firesim->get_covsum());
    }
}
#endif

int main(int argc, char** argv) {
    firesim_f1_t firesim(argc, argv);
    firesim.init(argc, argv);
#ifdef DIFUZZRTL
    fuzz(&firesim);
#else
    firesim.run();
#endif
    return firesim.finish();
}
