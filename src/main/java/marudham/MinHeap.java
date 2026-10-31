package marudham;

import java.util.NoSuchElementException;

/**
 * A hand-written binary min-heap keyed by distance. It is used only by
 * Dijkstra's algorithm (RoutePlanner) and is deliberately separate from
 * java.util.PriorityQueue, which is used for the request queue.
 *
 * Duplicate entries for the same node are allowed ("lazy deletion"):
 * the planner ignores stale entries when they are extracted.
 */
public class MinHeap {

    /** One heap element: a node id and the tentative distance it was inserted with. */
    public static class Entry {
        public final String node;
        public final double distance;

        public Entry(String node, double distance) {
            this.node = node;
            this.distance = distance;
        }
    }

    private Entry[] data = new Entry[16];
    private int size = 0;

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }

    public void insert(String node, double distance) {
        if (size == data.length) {
            Entry[] bigger = new Entry[data.length * 2];
            System.arraycopy(data, 0, bigger, 0, size);
            data = bigger;
        }
        data[size] = new Entry(node, distance);
        siftUp(size);
        size++;
    }

    public Entry peekMin() {
        if (size == 0) {
            throw new NoSuchElementException("Heap is empty");
        }
        return data[0];
    }

    public Entry extractMin() {
        if (size == 0) {
            throw new NoSuchElementException("Heap is empty");
        }
        Entry min = data[0];
        size--;
        data[0] = data[size];
        data[size] = null;
        if (size > 0) {
            siftDown(0);
        }
        return min;
    }

    private void siftUp(int i) {
        while (i > 0) {
            int parent = (i - 1) / 2;
            if (data[i].distance < data[parent].distance) {
                swap(i, parent);
                i = parent;
            } else {
                break;
            }
        }
    }

    private void siftDown(int i) {
        while (true) {
            int left = 2 * i + 1;
            int right = left + 1;
            int smallest = i;
            if (left < size && data[left].distance < data[smallest].distance) {
                smallest = left;
            }
            if (right < size && data[right].distance < data[smallest].distance) {
                smallest = right;
            }
            if (smallest == i) {
                return;
            }
            swap(i, smallest);
            i = smallest;
        }
    }

    private void swap(int a, int b) {
        Entry t = data[a];
        data[a] = data[b];
        data[b] = t;
    }
}
