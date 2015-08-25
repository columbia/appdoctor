#!/bin/sh

round=$1

cat cov-$round-detail.txt | grep xcov | sed -e 's/xcov: //' | sed -e 's/\(.*\): \(.*\)/\2: \1/' | sort -h > cov-$round-sorted.txt
