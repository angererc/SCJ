#! /bin/bash

if [ $# -ne 1 ]; then
  echo 1>&2 Usage: $0 "(runA | runB | runC)"
  exit -1
fi


if [ ! -f ../$1/input/triangulation_java.sh ]; then
  echo 1>&2 Error: cannot find input script ../$1/input/triangulation_java.sh
  echo 1>&2 Usage: $0 "(runA | runB | runC )"
  exit -1
fi

../$1/input/triangulation_java.sh > /dev/null
