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

File: GaloisCollection.java 

*/



package objects;

import java.util.Collection;
import java.util.Iterator;

/**
 * Simple accumulator class.
 */

public class GaloisCollection<T> implements Collection<T> {
  /**
   * Use the underlying GaloisMap to provide the appropriate
   * Galois-safety.
   */
  private GaloisMap<Object, T> map;

  public GaloisCollection() {
    map = new GaloisMap<Object, T>();
  }

  @Override
  public boolean add(T o) {
    // Manage concurrent add methods by associating
    // a unique object with each add
    map.put(new Object(), o);
    return true;
  }

  @Override
  public boolean addAll(Collection<? extends T> c) {
    for (T o : c) {
      add(o);
    }
    return true;
  }

  @Override
  public void clear() {
    map.clear();
  }

  @Override
  public boolean contains(Object o) {
    return map.values().contains(o);
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    return map.values().containsAll(c);
  }

  @Override
  public boolean equals(Object o) {
    return map.values().equals(o);
  }

  @Override
  public int hashCode() {
    return map.values().hashCode();
  }

  @Override
  public boolean isEmpty() {
    return map.isEmpty();
  }

  @Override
  public Iterator<T> iterator() {
    return map.values().iterator();
  }

  @Override
  public boolean remove(Object o) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int size() {
    return map.size();
  }

  @Override
  public Object[] toArray() {
    return map.values().toArray();
  }

  @Override
  public <U> U[] toArray(U[] a) {
    return map.values().toArray(a);
  }
}
