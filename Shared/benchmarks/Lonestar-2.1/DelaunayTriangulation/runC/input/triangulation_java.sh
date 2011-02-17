#! /bin/bash
INPUT=random250000
JFLAGS="-Xms2048M -Xmx2048M"

ABS_DIR=$(cd $(dirname $0); pwd)
source "$ABS_DIR/../../../config_java"

$JAVA $JFLAGS -cp ../../Lonestar-2.1.jar DelaunayTriangulation.src.java.SerialDelaunaytriangulation ../runC/input/$INPUT

