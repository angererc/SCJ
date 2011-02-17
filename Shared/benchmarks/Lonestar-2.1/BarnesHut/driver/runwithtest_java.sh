#! /bin/bash

if [ $# -ne 1 ]; then
  echo 1>&2 Usage: $0 "(runA | runB | runC)"
  exit -1
fi

if [ ! -f ../$1/input/BarnesHut.in ]; then
  echo 1>&2 Error: cannot find input file ../$1/input/BarnesHut.in
  echo 1>&2 Usage: $0 "(runA | runB | runC)"
  exit -1
fi

ABS_DIR=$(cd $(dirname $0); pwd)
source "$ABS_DIR/../../config_java"

rm -f barneshut.out
$JAVA -Xms1024M -Xmx1024M -cp ../../Lonestar-2.1.jar BarnesHut.src.java.SerialBarneshut ../$1/input/BarnesHut.in > barneshut.out && \
if diff -q -w barneshut.out ../$1/output/BarnesHut.out > /dev/null; then
  echo "completed successfully"
  rm -f barneshut.out
else
  echo "Error in output: barneshut.out and ../$1/output/BarnesHut.out differ"
fi
