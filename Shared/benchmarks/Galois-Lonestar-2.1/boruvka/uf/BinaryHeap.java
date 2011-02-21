/*
Galois, a framework to exploit amorphous data-parallelism in irregular
programs.

Copyright (C) 2010, The University of Texas at Austin. All rights reserved.
UNIVERSITY EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES CONCERNING THIS SOFTWARE
AND DOCUMENTATION, INCLUDING ANY WARRANTIES OF MERCHANTABILITY, FITNESS FOR ANY
PARTICULAR PURPOSE, NON-INFRINGEMENT AND WARRANTIES OF PERFORMANCE, AND ANY
WARRANTY THAT MIGHT OTHERWISE ARISE FROM COURSE OF DEALING OR USAGE OF TRADE.
NO WARRANTY IS EITHER EXPRESS OR IMPLIED WITH RESPECT TO THE USE OF THE
SOFTWARE OR DOCUMENTATION. Under no circumstances shall University be liable
for incidental, special, indirect, direct or consequential damages or loss of
profits, interruption of business, or related expenses which may arise from use
of Software or Documentation, including but not limited to those resulting from
defects in Software and/or Documentation, or loss or inaccuracy of data of any
kind.

File: BinaryHeap.java 

*/



package boruvka.uf;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;

public class BinaryHeap<E> implements Collection<E>, Queue<E> {
  private Map<Object, Integer> items;
  private Object[] heap;
  private Comparator<? super E> comparator;

  public BinaryHeap() {
    this(11, null);
  }

  public BinaryHeap(int initialCapacity, Comparator<? super E> comparator) {
    items = new HashMap<Object, Integer>(initialCapacity);
    heap = new Object[initialCapacity];
    this.comparator = comparator;
  }

  public BinaryHeap(Collection<? extends E> c, Comparator<? super E> comparator) {
    this(c.size(), comparator);
    int idx = 0;
    for (E e : c) {
      if (items.put(e, idx) != null) {
        heap[idx++] = e;
      }
    }
    for (int i = (size() >> 1); i >= 0; i--)
      heapify(i);
  }

  public boolean add(E e) {
    if (items.containsKey(e))
      return false;

    if (size() >= heap.length)
      resize();

    int cur = size();
    items.put(e, cur);
    heap[cur] = e;
    decreaseKey(cur);
    return true;
  }

  @SuppressWarnings("unchecked")
  private boolean lessThan(Object e1, Object e2) {
    if (comparator == null) {
      Comparable<E> ee1 = (Comparable<E>) e1;
      return ee1.compareTo((E) e2) < 0;
    } else {
      return comparator.compare((E) e1, (E) e2) < 0;
    }
  }

  private void decreaseKey(int cur) {
    int parent = (cur - 1) >> 1;
    while (cur > 0 && lessThan(heap[cur], heap[parent])) {
      swap(cur, parent);
      cur = parent;
      parent = (cur - 1) >> 1;
    }
  }

  private void swap(int a, int b) {
    Object tmp = heap[a];
    heap[a] = heap[b];
    items.put(heap[a], a);
    heap[b] = tmp;
    items.put(heap[b], b);
  }

  private void resize() {
    Object[] h = new Object[heap.length << 1];
    System.arraycopy(heap, 0, h, 0, size());
    heap = h;
  }

  private void heapify(int start) {
    int left = (start << 1) + 1;
    int right = (start << 1) + 2;
    int least;
    if (left < size() && lessThan(heap[left], heap[start])) {
      least = left;
    } else {
      least = start;
    }
    if (right < size() && lessThan(heap[right], heap[least])) {
      least = right;
    }
    if (least != start) {
      swap(least, start);
      heapify(least);
    }
  }

  public void decreaseKey(E e) {
    decreaseKey(items.get(e));
  }

  public boolean addAll(Collection<? extends E> c) {
    throw new UnsupportedOperationException();
  }

  public void clear() {
    throw new UnsupportedOperationException();
  }

  public boolean contains(Object o) {
    return items.containsKey(o);
  }

  public boolean containsAll(Collection<?> c) {
    for (Object o : c) {
      if (!items.containsKey(o)) {
        return false;
      }
    }
    return true;
  }

  @SuppressWarnings("unchecked")
  public E element() {
    return (E) heap[0];
  }

  public boolean isEmpty() {
    return items.isEmpty();
  }

  public Iterator<E> iterator() {
    throw new UnsupportedOperationException();
  }

  public boolean offer(E e) {
    return add(e);
  }

  public E peek() {
    if (isEmpty())
      return null;
    else
      return element();
  }

  public E poll() {
    if (isEmpty())
      return null;
    else
      return remove();
  }

  @SuppressWarnings("unchecked")
  public E remove() {
    E retval = (E) heap[0];
    int cur = size() - 1;
    heap[0] = heap[cur];
    heap[cur] = null;
    int prev = items.remove(retval);
    assert prev == 0;
    if (heap[0] != null)
      items.put(heap[0], 0);
    heapify(0);
    return retval;
  }

  public boolean remove(Object o) {
    throw new UnsupportedOperationException();
  }

  public boolean removeAll(Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  public boolean retainAll(Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  public int size() {
    return items.size();
  }

  public Object[] toArray() {
    throw new UnsupportedOperationException();
  }

  public <T> T[] toArray(T[] a) {
    throw new UnsupportedOperationException();
  }
}
