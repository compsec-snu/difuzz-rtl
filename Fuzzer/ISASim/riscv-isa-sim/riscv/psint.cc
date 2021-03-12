#include "devices.h"
#include "encoding.h"
#include "processor.h"

psint_t::psint_t(std::vector<processor_t *>& procs, std::map<reg_t, reg_t> intrs, bool debug)
    : procs(procs), intrs(intrs), debug(debug)
{
    if (debug) {
        fprintf(stderr, "[psint_t] init\n");
        for(auto it = intrs.begin(); it != intrs.end(); it++)
            fprintf(stderr, "[psint_t] pc: %llx, intr: %x\n", it->first, it->second);
    }
}

void psint_t::assert_int(reg_t pc)
{

    // MEIP, SEIP, MTIP, MSIP
    if (intrs.count(pc)) {
        auto val = intrs.find(pc)->second;
        reg_t prev_mip = procs[0]->state.mip;
        reg_t assert_mip = ((val >> 2) & 1) << IRQ_M_EXT | ((val >> 3) & 1) << IRQ_S_EXT |
            (val & 1) << IRQ_M_TIMER | ((val >> 1) & 1) << IRQ_M_SOFT;

        if (debug)
            fprintf(stderr, "[psint_t] assert_int, pc: %llx, val: %x\n", pc, val);

        procs[0]->state.mip = prev_mip | assert_mip;
        procs[0]->take_pending_interrupt();
    }
}
