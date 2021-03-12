#!/usr/bin/python

from scipy.stats import mannwhitneyu
from a12 import *

def test(dists : dict):

    """ Mann Whitney U Test """
    print('-------------------------- Mann Whitney U Test --------------------------')
    for i in range(len(dists.keys())):
        n1 = list(dists.keys())[i]
        for j in range(i+1, len(dists.keys())):
            n2 = list(dists.keys())[j]
            print('{} vs. {}'.format(n1, n2))

            res = mannwhitneyu(dists[n1], dists[n2])
            print(res.pvalue)
    print('-------------------------------------------------------------------------')

    """ Vargha Delaney's A measure """ 
    print("---------------------- Vargha Delaney's A measure -----------------------")
    for n, i in dists.items():
        dists[n] = [n] + i

    for i in range(len(dists.keys())):
        n1 = list(dists.keys())[i]
        for j in range(i+1, len(dists.keys())):
            n2 = list(dists.keys())[j]
            print('{} vs. {}'.format(n1, n2))

            A = [ dists[n1], dists[n2]]
            for rx in a12s(A, rev=True, enough=0.71): print(rx)
    print('-------------------------------------------------------------------------')


if __name__ == '__main__':
    for c in [10000, 20000, 30000, 40000, 50000]:
        fnames = { x:'../results/reached_{}_v3.txt'.format(x)
                  for x in ['rand', 'mux', 'reg'] }
        dists = { x:None for x in fnames.keys() }

        for n, f in fnames.items():
            with open(f, 'r') as fd:
                lines = [ line for line in fd.readlines() if int(line[:5]) == c ]
                dists[n] = [ int(x.split(': ')[1][:-1]) for x in lines]

        print('Cycles: {}'.format(c))
        test(dists)

    # for v in ['v0', 'v1', 'v2', 'v3']:
    #     fnames = { x:'../results/micro_{}'.format(x) + '_{}.txt'.format(v) 
    #               for x in ['rand','mux','reg'] }

    #     dists = { x:None for x in fnames.keys() }
    #     for n, f in fnames.items():
    #         with open(f, 'r') as fd:
    #             lines = fd.readlines()[:-1]
    #             dists[n] = [ int(x.split('\t')[0]) for x in lines]

    #     print('Version {}'.format(v))
    #     test(dists)


