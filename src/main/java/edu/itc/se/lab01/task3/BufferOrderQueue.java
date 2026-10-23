package edu.itc.se.lab01.task3;

public class  BufferOrderQueue extends BoundedBuffer<Order> implements OrderQueue {
    public BufferOrderQueue(int capacity) {
        super(capacity);
    }
}
