#! /bin/bash

if [ $# -ne 1 ]; then
  echo 1>&2 Usage: $0 "(runA | runB | runC)"
  exit -1
fi


if [ ! -f ../$1/input/triangulation_java_wtest.sh ]; then
  echo 1>&2 Error: cannot find input script ../$1/input/triangulation_java_wtest.sh
  echo 1>&2 Usage: $0 "(runA | runB | runC )"
  exit -1
fi

rm -f triangulation.out
../$1/input/triangulation_java_wtest.sh $2 > triangulation.out && \
if diff -q -w triangulation.out ../$1/output/triangulation.out > /dev/null; then
  echo "completed successfully"
  rm -f triangulation.out
else
  echo "Error in output: triangulation.out and ../$1/output/triangulation.out differ"
fi
