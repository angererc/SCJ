/*
Lonestar Benchmark Suite for irregular applications that exhibit 
amorphous data-parallelism.

Center for Grid and Distributed Computing
The University of Texas at Austin

Copyright (C) 2007, 2008, 2009 The University of Texas at Austin

Licensed under the Eclipse Public License, Version 1.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.eclipse.org/legal/epl-v10.html

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

File: UndirectedEdgeGraph.java 

*/



package objects.graph;

import java.util.HashMap;

public class UndirectedEdgeGraph<NodeData, EdgeData> extends DirectedEdgeGraph<NodeData, EdgeData> {

  @Override
  public Edge<EdgeData> createEdge(Node<NodeData> src, Node<NodeData> dest, EdgeData e) {
    UndirectedEdgeGraphNode gsrc = (UndirectedEdgeGraphNode) src;
    UndirectedEdgeGraphNode gdest = (UndirectedEdgeGraphNode) dest;
    if (gsrc.compareTo(gdest) > 0) {
      return new GraphEdge(gsrc, gdest, e);
    } else {
      return new GraphEdge(gdest, gsrc, e);
    }
  }

  @Override
  public Node<NodeData> createNode(NodeData n) {
    return new UndirectedEdgeGraphNode(n);
  }

  @Override
  public boolean isDirected() {
    return false;
  }

  protected class UndirectedEdgeGraphNode extends EdgeGraphNode implements Comparable<UndirectedEdgeGraphNode> {

    UndirectedEdgeGraphNode(NodeData d) {
      this.data = d;
      inEdges = new HashMap<EdgeGraphNode, GraphEdge>();
      outEdges = inEdges;
    }

    @Override
    public int compareTo(UndirectedEdgeGraphNode n) {
      return (n.hashCode() - hashCode());
    }
  }
}
