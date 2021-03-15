// cc file for DifuzzRTL
#ifdef DIFUZZRTLBRIDGEMODULE_struct_guard

#include "difuzzrtl.h"
#include <sys/stat.h>
#include <fcntl.h>

#define _XOPEN_SOURCE
#include <stdlib.h>
#include <stdio.h>

#ifndef _WIN32
#include <unistd.h>
#endif

difuzzrtl_t::difuzzrtl_t(simif_t *sim, DIFUZZRTLBRIDGEMODULE_struct *mmio_addrs): bridge_driver_t(sim)
{
    printf("DifuzzRTL Bridge is here (stdin/stdout).\n");
    this->mmio_addrs = mmio_addrs;
    inputfd = STDIN_FILENO;
    outputfd = STDOUT_FILENO;

    // Don't block on reads if there is nothing typed in
    fcntl(inputfd, F_SETFL, fcntl(inputfd, F_GETFL) | O_NONBLOCK);
}

difuzzrtl_t::~difuzzrtl_t() {
    free(this->mmio_addrs);
}

void difuzzrtl_t::send() {
    // write(this->mmio_addrs->in_metaReset, 0);
}

void difuzzrtl_t::recv() {
    // covSum = read(this->mmio_addrs->out_covSum);
}

void difuzzrtl_t::cov_init(int onoff) {
    write(this->mmio_addrs->in_covInit, onoff);
}

void difuzzrtl_t::bridge_reset(int onoff) {
    write(this->mmio_addrs->in_bridgeReset, onoff);
}

void difuzzrtl_t::meta_reset(int onoff) {
    write(this->mmio_addrs->in_metaReset, onoff);
}

unsigned int difuzzrtl_t::read_covsum() {
    covSum = read(this->mmio_addrs->out_covSum);
    return covSum;
}

void difuzzrtl_t::tick() {
}

#endif //DIFUZZRTLBRIDGEMODULE_struct_guard
