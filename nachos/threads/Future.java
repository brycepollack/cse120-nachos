package nachos.threads;

import java.util.*;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import nachos.machine.*;

/**
 * A <i>Future</i> is a convenient mechanism for using asynchonous
 * operations.
 */
public class Future {
    /**
     * Instantiate a new <i>Future</i>.  The <i>Future</i> will invoke
     * the supplied <i>function</i> asynchronously in a KThread.  In
     * particular, the constructor should not block as a consequence
     * of invoking <i>function</i>.
     */
    private KThread asyncThread;
    Lock lock;
    Condition2 waitQueue;
    int result;
    boolean completed;

    public Future (IntSupplier function) {
        lock = new Lock();
        waitQueue = new Condition2(lock);
        result = 0;
        completed = false;

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                result = function.getAsInt();
                lock.acquire();
                completed = true;
                waitQueue.wakeAll();
                lock.release();
            }
        };

        asyncThread = new KThread(runnable).setName("Async function");
        asyncThread.fork();
    }

    /**
     * Return the result of invoking the <i>function</i> passed in to
     * the <i>Future</i> when it was created.  If the function has not
     * completed when <i>get</i> is invoked, then the caller is
     * blocked.  If the function has completed, then <i>get</i>
     * returns the result of the function.  Note that <i>get</i> may
     * be called any number of times (potentially by multiple
     * threads), and it should always return the same value.
     */
    public int get () {
        lock.acquire();
        while (!completed) {
            System.out.println("Result not out yet!");
            waitQueue.sleep();
        }
        lock.release();
        return result;
    }

    static class NumSupplier implements IntSupplier {
        @Override
        public int getAsInt() {
            ThreadedKernel.alarm.waitUntil(4000000);
            return 100;
        }
    }

    public static void futureTest1() {
        Future future = new Future(new NumSupplier());
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                // ThreadedKernel.alarm.waitUntil(4000000);
                long prev = Machine.timer().getTime();
                int result = future.get();
                System.out.println(KThread.currentThread().getName() + " waited for " + (Machine.timer().getTime() - prev) + " for result: " + result);
            }
        };

        KThread thread1 = new KThread(runnable).setName("Thread 1");
        KThread thread2 = new KThread(runnable).setName("Thread 2");
        thread1.fork();
        thread2.fork();
        thread1.join();
        thread2.join();
    }

    public static void selfTest() {
        futureTest1();
    }
}
