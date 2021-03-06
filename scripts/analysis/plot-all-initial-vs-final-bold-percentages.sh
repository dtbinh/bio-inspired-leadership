#!/bin/bash

# =========================================================

DATA_PREFIX=$1
EPS_PREFIX=$2

./plot-initial-vs-final-bold-percentages.pl \
        $DATA_PREFIX/indcount-010 \
        $EPS_PREFIX-010.eps \
        0.2
# 0.2, 0.5, 0.8 runs
#        0.1 0.1 0.112

./plot-initial-vs-final-bold-percentages.pl \
        $DATA_PREFIX/indcount-020 \
        $EPS_PREFIX-020.eps \
        0.35
#        0.087 0.122 0.251

./plot-initial-vs-final-bold-percentages.pl \
        $DATA_PREFIX/indcount-030 \
        $EPS_PREFIX-030.eps \
        0.4
#        0.154664 0.184678 0.31867

./plot-initial-vs-final-bold-percentages.pl \
        $DATA_PREFIX/indcount-040 \
        $EPS_PREFIX-040.eps \
        0.45
#        0.2385 0.248 0.366

./plot-initial-vs-final-bold-percentages.pl \
        $DATA_PREFIX/indcount-050 \
        $EPS_PREFIX-050.eps \
        0.42
#        0.2928 0.2956 0.3896

./plot-initial-vs-final-bold-percentages.pl \
        $DATA_PREFIX/indcount-100 \
        $EPS_PREFIX-100.eps \
        0.44
#        0.397 0.3998 0.4498

#pdftk ~/tmp/initial-vs-final*.pdf cat output ~/tmp/all-initial-vs-final.pdf
