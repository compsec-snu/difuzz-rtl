import os
import math
import matplotlib.pyplot as plt
import matplotlib.font_manager as font_manager
import numpy as np
from matplotlib import rcParams

# RAND_FREQ=2.36e6
# MUX_FREQ=2.02e6
# REG_FREQ=2.29e6

RAND_FREQ=2.36
MUX_FREQ=2.02
REG_FREQ=2.29

color = ['limegreen', '#5B8AC0', '#FF6961', 'limegreen', 'r', 'gold', 'k', 'y','k']
marker = ['x', '^', 'o']

freq = {
    'rand': RAND_FREQ,
    'mux': MUX_FREQ,
    'reg': REG_FREQ
}

font = font_manager.FontProperties(family='Times New Roman', size=22)

if __name__ == '__main__':
    rcParams.update({'figure.autolayout': True})

    """ Drawing time to find bug """
    times = {}
    for v in range(4):
        times[v] = {}
        for mutation in ['rand', 'mux', 'reg']:
            fd = open('results/micro_{}_v{}.txt'.format(mutation, v), 'r')
            lines = fd.readlines()[:-1]
            fd.close()

            cycles = [ int(i.split('\t')[0]) for i in lines ]
            times[v][mutation] = [ c / freq[mutation] for c in cycles ]
            # times[v][mutation] = [ math.log(c / freq[mutation], 10) for c in cycles ]

    fig = plt.figure()
    ax = fig.add_axes([0.1, 0.1, 0.8, 0.8])
    ci = {'mux': 1, 'rand': 0, 'reg': 2}
    for v in range(4):
        bplot = ax.boxplot([ times[v][m] for m in ['mux', 'rand', 'reg'] ],
                           positions=[v + 0.2, v, v - 0.2],
                           sym='', widths=0.2, patch_artist=True)

        for i, m in enumerate(['mux', 'rand', 'reg']):
            patch = bplot['boxes'][i]
            patch.set_facecolor(color[ci[m]])

    ax.set_xlabel('Number of finite states', fontname='Times New Roman', fontsize=24)
    ax.set_ylabel('Elapsed time (s) to find bug', fontname='Times New Roman', fontsize=24)

    ax.set_xticklabels([None, 27, None, None, 64, None, None, 125, None, None, 216, None],
                       fontname='Times New Roman', fontsize=22)
    # ax.set_yticklabels([0.00001, 0.0001, 0.001, 0.01, 0.1, 1, 10], fontname='Times New Roman', fontsize=22)
    ax.set_yticklabels([0.00, 0.00, 0.02, 0.04, 0.06, 0.08, 0.1], fontname='Times New Roman', fontsize=22)
    # ax.yaxis.grid(True)

    plt.legend([bplot['boxes'][1], bplot['boxes'][0], bplot['boxes'][2]], ['no-cov', 'mux-cov', 'reg-cov'],
               prop=font, loc='upper left')
    # plt.show()
    plt.savefig('out.pdf', format='pdf', bbox_inches='tight')

    """ Drawing Reached states """
    # vals = {}
    # for mutation in [ 'rand', 'mux', 'reg' ]:
    #     fd = open('results/states_{}_v3.txt'.format(mutation), 'r')
    #     lines = fd.readlines()
    #     fd.close()

    #     i = 0
    #     max_cycles = 0
    #     tmps = [ [] for i in range(100) ]
    #     while True:
    #         try: line = lines.pop(0)
    #         except: break

    #         if '-----' in line:
    #             i += 1
    #         else:
    #             cycles, nstates = line.split(', ')
    #             cycles = int(cycles)
    #             nstates = int(nstates[:-1])

    #             if tmps[i]:
    #                 last_cycles = len(tmps[i])
    #                 last_nstates = tmps[i][-1]
    #             else:
    #                 last_cycles = 0
    #                 last_nstates = 0

    #             ncycles = cycles - last_cycles

    #             for j in range(ncycles-1):
    #                 tmps[i].append(last_nstates)
    #             tmps[i].append(nstates)

    #             if cycles > max_cycles:
    #                 max_cycles = cycles

    #     max_cycles = min(max_cycles, 50000)
    #     avg = [ 0 for i in range(max_cycles) ]
    #     std = [ 0 for i in range(max_cycles) ]
    #     for i in range(max_cycles):
    #         n = 0
    #         cs = []
    #         for j in range(100):
    #             try:
    #                 val = tmps[j].pop(0)
    #                 cs.append(val)
    #                 n += 1
    #             except: continue
    #         avg[i] = np.mean(cs)
    #         std[i] = np.std(cs)

    #     last = 0
    #     data = []
    #     for i in range(max_cycles):
    #         if avg[i] > last:
    #             data.append((i, avg[i], std[i]))
    #             last = avg[i]

    #     vals[mutation] = data

    fig = plt.figure()
    ax = fig.add_axes([0.1, 0.1, 0.8, 0.8])
    for i, mutation in enumerate([ 'rand', 'mux', 'reg' ]):
        # data = vals[mutation]
        # with open('results/avg_reached_{}.txt'.format(mutation), 'w') as fd:
        #     fd.writelines([ '{}\t{}\t{}\n'.format(x[0], x[1], x[2])
        #                     for x in data ])
        with open('results/avg_reached_{}.txt'.format(mutation), 'r') as fd:
            lines = [ line.split('\t') for line in fd.readlines() ]

        data = [ (int(x[0]), float(x[1]), float(x[2][:-1])) for x in lines ]
        ax.plot([x[0] for x in data], [x[1] for x in data], label=mutation,
                color=color[i], linewidth=3)
        ax.fill_between([x[0] for x in data],
                        [x[1] + x[2] for x in data],
                        [x[1] - x[2] for x in data],
                        color=color[i], alpha=0.2)

    ax.set_xlabel('Number of fuzzing iterations', fontname="Times New Roman", fontsize=24)
    ax.set_ylabel('Number of states covered',fontname="Times New Roman", fontsize=24)

    ax.set_xticks([0, 10000, 20000, 30000, 40000, 50000])
    ax.set_xticklabels([0, 1000, 2000, 3000, 4000, 5000], fontname='Times New Roman', fontsize=22)

    ax.set_yticks([0, 50, 100, 150, 200])
    ax.set_yticklabels([0, 50, 100, 150, 200], fontname='Times New Roman', fontsize=22)

    # ax.set_xticks([0,1,2,3])
    # ax.set_xticklabels([27, 64, 125, 216])
    plt.legend(['no-cov', 'mux-cov', 'reg-cov'], prop=font, loc='lower right')
    plt.savefig('out1.pdf', format='pdf', bbox_inches='tight')
    plt.show()
