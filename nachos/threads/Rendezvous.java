package nachos.threads;

import nachos.machine.*;

import java.util.*;

/**
 * A <i>Rendezvous</i> allows threads to synchronously exchange values.
 */
public class Rendezvous {
    /**
     * Allocate a new Rendezvous.
     */
    public Rendezvous () {
        locks = new HashMap<Integer, Lock>();
        threadsArrived = new HashMap<Integer, Integer>();
        conds = new HashMap<Integer, Condition2>();
        values = new HashMap<Integer, int[]>();
    }

    /**
     * Synchronously exchange a value with another thread.  The first
     * thread A (with value X) to exhange will block waiting for
     * another thread B (with value Y).  When thread B arrives, it
     * will unblock A and the threads will exchange values: value Y
     * will be returned to thread A, and value X will be returned to
     * thread B.
     *
     * Different integer tags are used as different, parallel
     * synchronization points (i.e., threads synchronizing at
     * different tags do not interact with each other).  The same tag
     * can also be used repeatedly for multiple exchanges.
     *
     * @param tag the synchronization tag.
     * @param value the integer to exchange.
     */
    public int exchange (int tag, int value) {

        if (!locks.containsKey(tag)) {
            Lock newLock = new Lock();
            locks.put(tag, newLock);
            conds.put(tag, new Condition2(newLock));

            // these two may change
            threadsArrived.put(tag, 0);
            values.put(tag, null);
        }

        
        Lock exchangeLock = locks.get(tag);
        Condition2 waiting = conds.get(tag);
    	exchangeLock.acquire();

        // trap threads number 3 and above here first
        while (threadsArrived.get(tag) >= 2) {
            waiting.sleepFor(10000);
        }

        // increment every time a thread calls exchange()
        threadsArrived.put(tag, threadsArrived.get(tag) + 1);

        if (threadsArrived.get(tag) == 1) {
            values.put(tag, new int[2]);
            values.get(tag)[0] = value;
            waiting.sleep();

            exchangeLock.release();
            threadsArrived.put(tag, 0);
            return values.get(tag)[1];

        } else {
            values.get(tag)[1] = value;
            waiting.wake();
            // status = true;
            exchangeLock.release();
            return values.get(tag)[0];

        }
    }

    public static void testNoTagImplementation() {
        System.out.println("---testNoTagImplementation---");
    	final Rendezvous r = new Rendezvous();
    	
    	KThread thread1 = new KThread(new Runnable() {
    		public void run() {
    			int tag = 1;
    			int value = 4;

    			System.out.println("Thread " + KThread.currentThread().getName() + " original value is: " + value);
    			int newVal = r.exchange(tag, value);
    			System.out.println("Thread " + KThread.currentThread().getName() + " new value is: " + newVal);
    		}
    	});
    	
    	KThread thread2 = new KThread(new Runnable() {
    		public void run() {
    			int tag = 1;
    			int value = 8;

    			System.out.println("Thread " + KThread.currentThread().getName() + " original value is: " + value);
    			int newVal = r.exchange(tag, value);
    			System.out.println("Thread " + KThread.currentThread().getName() + " new value is: " + newVal);
    		}
    	});
    	
    	thread1.setName("Thread 1");
    	thread2.setName("Thread 2");
    	
    	thread1.fork();
    	thread2.fork();
    	
    	thread1.join();
    	thread2.join();
    }

    public static void testWithTagImplementation() {
        System.out.println("---testWithTagImplementation---");
    	final Rendezvous r = new Rendezvous();
    	
    	KThread thread1A = new KThread(new Runnable() {
    		public void run() {
    			int tag = 1;
    			int value = 4;

    			System.out.println("Thread " + KThread.currentThread().getName() + " original value is: " + value);
    			int newVal = r.exchange(tag, value);
    			System.out.println("Thread " + KThread.currentThread().getName() + " new value is: " + newVal);
    		}
    	});
    	
    	KThread thread1B = new KThread(new Runnable() {
    		public void run() {
    			int tag = 1;
    			int value = 8;

    			System.out.println("Thread " + KThread.currentThread().getName() + " original value is: " + value);
    			int newVal = r.exchange(tag, value);
    			System.out.println("Thread " + KThread.currentThread().getName() + " new value is: " + newVal);
    		}
    	});

    	KThread thread2A = new KThread(new Runnable() {
    		public void run() {
    			int tag = 2;
    			int value = 1;

    			System.out.println("Thread " + KThread.currentThread().getName() + " original value is: " + value);
    			int newVal = r.exchange(tag, value);
    			System.out.println("Thread " + KThread.currentThread().getName() + " new value is: " + newVal);
    		}
    	});
    	
    	KThread thread2B = new KThread(new Runnable() {
    		public void run() {
    			int tag = 2;
    			int value = 0;

    			System.out.println("Thread " + KThread.currentThread().getName() + " original value is: " + value);
    			int newVal = r.exchange(tag, value);
    			System.out.println("Thread " + KThread.currentThread().getName() + " new value is: " + newVal);
    		}
    	});
    	
    	thread1A.setName("1A");
    	thread1B.setName("1B");
    	thread2A.setName("2A");
    	thread2B.setName("2B");
    	
    	thread1A.fork();
        thread2B.fork();
    	thread1B.fork();
        thread2A.fork();
    	
    	thread1A.join();
    	thread1B.join();
        thread2A.join();
        thread2B.join();
    }
    
    public static void testMultipleRendezvous() {
        System.out.println("---testMultipleRendezvous---");
    	final Rendezvous r1 = new Rendezvous();
    	
    	KThread thread1A = new KThread(new Runnable() {
    		public void run() {
    			int tag = 1;
    			int value = 4;

    			System.out.println("Thread " + KThread.currentThread().getName() + " original value is: " + value);
    			int newVal = r1.exchange(tag, value);
    			System.out.println("Thread " + KThread.currentThread().getName() + " new value is: " + newVal);
    		}
    	});
    	
    	KThread thread1B = new KThread(new Runnable() {
    		public void run() {
    			int tag = 1;
    			int value = 8;

    			System.out.println("Thread " + KThread.currentThread().getName() + " original value is: " + value);
    			int newVal = r1.exchange(tag, value);
    			System.out.println("Thread " + KThread.currentThread().getName() + " new value is: " + newVal);
    		}
    	});

    	KThread thread2A = new KThread(new Runnable() {
    		public void run() {
    			int tag = 2;
    			int value = 1;

    			System.out.println("Thread " + KThread.currentThread().getName() + " original value is: " + value);
    			int newVal = r1.exchange(tag, value);
    			System.out.println("Thread " + KThread.currentThread().getName() + " new value is: " + newVal);
    		}
    	});
    	
    	KThread thread2B = new KThread(new Runnable() {
    		public void run() {
    			int tag = 2;
    			int value = 0;

    			System.out.println("Thread " + KThread.currentThread().getName() + " original value is: " + value);
    			int newVal = r1.exchange(tag, value);
    			System.out.println("Thread " + KThread.currentThread().getName() + " new value is: " + newVal);
    		}
    	});

        final Rendezvous r2 = new Rendezvous();
    	
    	KThread thread3A = new KThread(new Runnable() {
    		public void run() {
    			int tag = 1;
    			int value = 7;

    			System.out.println("Thread " + KThread.currentThread().getName() + " original value is: " + value);
    			int newVal = r2.exchange(tag, value);
    			System.out.println("Thread " + KThread.currentThread().getName() + " new value is: " + newVal);
    		}
    	});
    	
    	KThread thread3B = new KThread(new Runnable() {
    		public void run() {
    			int tag = 1;
    			int value = 10;

    			System.out.println("Thread " + KThread.currentThread().getName() + " original value is: " + value);
    			int newVal = r2.exchange(tag, value);
    			System.out.println("Thread " + KThread.currentThread().getName() + " new value is: " + newVal);
    		}
    	});
    	
    	thread1A.setName("1A");
    	thread1B.setName("1B");
    	thread2A.setName("2A");
    	thread2B.setName("2B");
    	thread3A.setName("3A");
    	thread3B.setName("3B");
    	
    	thread1A.fork();
    	thread3B.fork();
    	thread3A.fork();
        thread2B.fork();
    	thread1B.fork();
        thread2A.fork();
    	
    	thread1A.join();
    	thread1B.join();
        thread2A.join();
        thread2B.join();
        thread3A.join();
        thread3B.join();
    }
    
    public static void testMultipleThreadOneTag() {
        System.out.println("---testMultipleThreadOneTag---");
    	final Rendezvous r = new Rendezvous();
    	
    	KThread thread1 = new KThread(new Runnable() {
    		public void run() {
    			int tag = 1;
    			int value = 4;
    			
    			System.out.println("Thread " + KThread.currentThread().getName() + " original value is: " + value);
    			int newVal = r.exchange(tag, value);
    			System.out.println("Thread " + KThread.currentThread().getName() + " new value is: " + newVal);
    		}
    	});
    	
    	KThread thread2 = new KThread(new Runnable() {
    		public void run() {
    			int tag = 1;
    			int value = 8;
    			
    			System.out.println("Thread " + KThread.currentThread().getName() + " original value is: " + value);
    			int newVal = r.exchange(tag, value);
    			System.out.println("Thread " + KThread.currentThread().getName() + " new value is: " + newVal);
    		}
    	});


    	
    	KThread thread3 = new KThread(new Runnable() {
    		public void run() {
    			int tag = 1;
    			int value = 2;
    			
    			System.out.println("Thread " + KThread.currentThread().getName() + " original value is: " + value);
    			int newVal = r.exchange(tag, value);
    			System.out.println("Thread " + KThread.currentThread().getName() + " new value is: " + newVal);
    		}
    	});
    	
    	KThread thread4 = new KThread(new Runnable() {
    		public void run() {
    			int tag = 1;
    			int value = 9;
    			
    			System.out.println("Thread " + KThread.currentThread().getName() + " original value is: " + value);
    			int newVal = r.exchange(tag, value);
    			System.out.println("Thread " + KThread.currentThread().getName() + " new value is: " + newVal);
    		}
    	});
    	
    	thread1.setName("1");
    	thread2.setName("2");
    	thread3.setName("3");
    	thread4.setName("4");
    	
    	thread4.fork();
    	thread1.fork();
    	thread3.fork();
    	thread2.fork();
    	
    	thread1.join();
    	thread2.join();
    	thread3.join();
    	thread4.join();
    }
    
    
    public static void selfTest() {
		testNoTagImplementation();
        testWithTagImplementation();
        testMultipleRendezvous();
        testMultipleThreadOneTag();
	}

    Map<Integer, Integer> threadsArrived;
    Map<Integer, Condition2> conds;
    Map<Integer, int[]> values;
    Map<Integer, Lock> locks;
}
