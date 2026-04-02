package com.limelight.binding.input.driver;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public final class DualSenseHapticSender {
    private static final int QUEUE_CAPACITY = 3;
    private static final int DIRECT_BUFFER_SIZE = 1024;

    private static final class HapticFrame {
        final byte[] data;
        final float gain;

        HapticFrame(byte[] data, float gain) {
            this.data = data;
            this.gain = gain;
        }
    }

    private final BlockingQueue<HapticFrame> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY, false);
    private volatile boolean running;
    private Thread worker;

    public synchronized void start() {
        if (running) {
            return;
        }

        running = true;
        queue.clear();

        worker = new Thread(() -> {
            ByteBuffer directBuffer = ByteBuffer.allocateDirect(DIRECT_BUFFER_SIZE)
                    .order(ByteOrder.LITTLE_ENDIAN);
            while (running) {
                try {
                    HapticFrame frame = queue.take();
                    if (frame == null || frame.data == null || frame.data.length == 0 ||
                            frame.data.length > DIRECT_BUFFER_SIZE) {
                        continue;
                    }

                    directBuffer.clear();
                    directBuffer.put(frame.data);
                    directBuffer.flip();
                    HapticNative.nativeSendHapticFeedback(directBuffer, frame.data.length, frame.gain);
                }
                catch (InterruptedException ignored) {
                    break;
                }
            }

            queue.clear();
        }, "DualSense-Haptics");

        worker.setDaemon(true);
        worker.start();
    }

    public synchronized void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
        queue.clear();
    }

    public boolean enqueue(byte[] frame, float intensityGain) {
        if (!running || frame == null || frame.length == 0) {
            return false;
        }

        HapticFrame hapticFrame = new HapticFrame(frame, intensityGain);
        if (queue.offer(hapticFrame)) {
            return true;
        }

        queue.poll();
        return queue.offer(hapticFrame);
    }
}
