// Header file DifuzzRTL Bridge IO

#ifndef __DIFUZZRTL_H
#define __DIFUZZRTL_H

#include "bridges/bridge_driver.h"

#ifdef DIFUZZRTLBRIDGEMODULE_struct_guard
class difuzzrtl_t: public bridge_driver_t
{
    public:
        difuzzrtl_t(simif_t* sim, DIFUZZRTLBRIDGEMODULE_struct *mmio_addrs);
        ~difuzzrtl_t();

        virtual void tick();
        virtual void init() {};
        virtual void finish() {};
        virtual bool terminate() { return false; }
        virtual int exit_code() { return 0; }

        void cov_init(int onoff);
        void bridge_reset(int onoff);
        void meta_reset(int onoff);
        unsigned int read_covsum();

    private:
        DIFUZZRTLBRIDGEMODULE_struct *mmio_addrs;
        unsigned int covSum;
        int inputfd;
        int outputfd;

        void send();
        void recv();
};
#endif // DIFUZZRTLBRIDGEMODULE_struct_guard

#endif // __DIFUZZRTL_H
