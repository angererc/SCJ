#!/usr/bin/python

# Lonestar Benchmark Suite for irregular applications that exhibit 
# amorphous data-parallelism.
# 
# Center for Grid and Distributed Computing
# The University of Texas at Austin
# 
# Copyright (C) 2007, 2008, 2009 The University of Texas at Austin
# 
# Licensed under the Eclipse Public License, Version 1.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
# 
# http://www.eclipse.org/legal/epl-v10.html
# 
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# 
# File: dimacs2metis.py 





import sys
from collections import defaultdict

table = defaultdict(list)
seen = set()

for line in sys.stdin:
  if line.startswith('p max'):
    (p, m, numNodes, numEdges) = line.split()

  if not line.startswith('a'):
    continue

  (a, src, dst, cap) = line.split()

  # Make undirected
  isrc = int(src)
  idst = int(dst)
  if idst < isrc:
    isrc = idst
    idst = int(src)

  k = "%d %d" % (isrc, idst)
  if k not in seen:
    table[isrc] += [idst]
    table[idst] += [isrc]
    seen.add(k)

numNodes = int(numNodes)
numEdges = sum([len(x) for x in table.values()])
print "%d %d" % (numNodes, numEdges / 2)
for i in xrange(1, numNodes+1):
  print " ".join([str(x) for x in table[i]])
