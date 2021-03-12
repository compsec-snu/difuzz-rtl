#!/bin/python

import os
from matplotlib import pyplot as plt
from matplotlib import rcParams

if __name__ == '__main__':
    rcParams.update({'figure.autolayout': True})
    times = {}

    times = []
    iters = []
    covs = []

    fd = open('time_cov_mux.txt', 'r')
    lines = fd.readlines()
    fd.close()
    for line in lines:
        time, iter, cov = line.split(', ')
        time = float(time)
        iter = int(iter)
        cov = int(cov[:-1])

        times.append(time)
        iters.append(iter)
        covs.append(cov)

    fig = plt.figure()
    ax = fig.add_axes([0.1, 0.1, 0.8, 0.8])
    ax.plot(iters, covs, label='mux')

    ax.set_xlabel('Iteration')
    ax.set_ylabel('Mux coverage')

    plt.legend()
    plt.savefig('mux_cov.pdf', format='pdf', bbox_inches='tight')

    fd = open('time_cov_reg.txt', 'r')
    lines = fd.readlines()
    fd.close()

    tims = []
    iters = []
    covs = []
    for line in lines:
        time, iter, cov = line.split(', ')
        time = float(time)
        iter = int(iter)
        cov = int(cov[:-1])

        times.append(time)
        iters.append(iter)
        covs.append(cov)

    fig = plt.figure()
    ax = fig.add_axes([0.1, 0.1, 0.8, 0.8])
    ax.plot(iters, covs, label='mux')

    ax.set_xlabel('Iteration')
    ax.set_ylabel('Control register coverage')

    plt.legend()
    plt.savefig('reg_cov.pdf', format='pdf', bbox_inches='tight')

