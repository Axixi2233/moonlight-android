package com.limelight.binding.input.driver;

import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.util.Log;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public final class RazerKishiHapticSender {
    private static final String TAG = "RazerKishiDebug";
    private static final int QUEUE_CAPACITY = 6;
    private static final int TRANSFER_TIMEOUT_MS = 100;

    private final UsbDeviceConnection connection;
    private final BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY, false);
    private volatile boolean running;
    private Thread worker;
    private UsbEndpoint endpoint;
    private int sentFrameCount;

    public RazerKishiHapticSender(UsbDeviceConnection connection) {
        this.connection = connection;
    }

    public synchronized void start(UsbEndpoint endpoint) {
        if (running) {
            Log.d(TAG, "Sender already running");
            return;
        }

        this.endpoint = endpoint;
        this.running = true;
        this.queue.clear();
        this.sentFrameCount = 0;
        Log.i(TAG, "Starting Kishi sender endpoint=0x" +
                Integer.toHexString(endpoint.getAddress()) +
                " maxPacket=" + endpoint.getMaxPacketSize());

        worker = new Thread(() -> {
            while (running) {
                try {
                    byte[] frame = queue.take();
                    if (frame == null || frame.length == 0) {
                        continue;
                    }

                    int result = connection.bulkTransfer(endpoint, frame, frame.length, TRANSFER_TIMEOUT_MS);
                    sentFrameCount++;
                    if (sentFrameCount <= 5 || sentFrameCount % 50 == 0 || result < 0) {
                        Log.d(TAG, "bulkTransfer #" + sentFrameCount +
                                " result=" + result + " size=" + frame.length +
                                " queueRemaining=" + queue.size());
                    }
                }
                catch (InterruptedException ignored) {
                    Log.d(TAG, "Sender interrupted");
                    break;
                }
            }

            queue.clear();
        }, "RazerKishi-Haptics");

        worker.setDaemon(true);
        worker.start();
    }

    public synchronized void stop() {
        running = false;
        Log.i(TAG, "Stopping Kishi sender");
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
        queue.clear();
    }

    public boolean enqueue(byte[] frame) {
        if (!running || frame == null || frame.length == 0) {
            Log.w(TAG, "enqueue rejected: running=" + running +
                    " frameLength=" + (frame == null ? -1 : frame.length));
            return false;
        }

        if (queue.offer(frame)) {
            if (sentFrameCount < 5) {
                Log.d(TAG, "enqueue accepted, queueSize=" + queue.size());
            }
            return true;
        }

        Log.w(TAG, "enqueue full, dropping oldest frame");
        queue.poll();
        return queue.offer(frame);
    }
}
