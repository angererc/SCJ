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

File: GaloisMap.java 

*/



package objects;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hand wrapper around a ConcurrentHashMap. Currently, we do not
 * wrap library classes; this user object allows us to wrap calls
 * to a Map object.
 */

public class GaloisMap<K, V> implements Map<K, V> {
  private Map<K, V> map = new ConcurrentHashMap<K, V>();

  @Override
  public int size() {
    return map.size();
  }

  @Override
  public boolean isEmpty() {
    return map.isEmpty();
  }

  @Override
  public boolean containsKey(Object arg0) {
    return map.containsKey(arg0);
  }

  @Override
  public boolean containsValue(Object arg0) {
    return map.containsValue(arg0);
  }

  @Override
  public V get(Object arg0) {
    return map.get(arg0);
  }

  @Override
  public V put(K arg0, V arg1) {
    return map.put(arg0, arg1);
  }

  @Override
  public V remove(Object arg0) {
    return map.remove(arg0);
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> arg0) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<K> keySet() {
    return Collections.unmodifiableSet(map.keySet());
  }

  @Override
  public Collection<V> values() {
    return Collections.unmodifiableCollection(map.values());
  }

  @Override
  public Set<Map.Entry<K, V>> entrySet() {
    return Collections.unmodifiableSet(map.entrySet());
  }

  @Override
  public boolean equals(Object arg0) {
    return map.equals(arg0);
  }

  @Override
  public int hashCode() {
    return map.hashCode();
  }
}
