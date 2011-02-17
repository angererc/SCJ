#! /bin/bash
INPUT=airfoil10000
JFLAGS="-Xms1024M -Xmx1024M"

ABS_DIR=$(cd $(dirname $0); pwd)
source "$ABS_DIR/../../../config_java"

$JAVA $JFLAGS -cp ../../Lonestar-2.1.jar DelaunayTriangulation.src.java.SerialDelaunaytriangulation ../runB/input/$INPUT v

