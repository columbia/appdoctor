#!/bin/sh

round=26

grep "$1" log-*-$round/err_* | cut -d : -f 1-1 | uniq | xargs grep -H app\ package: | uniq
