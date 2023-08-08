package nachos.threads;

import java.util.LinkedList;

import nachos.machine.*;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 * 
 * <p>
 * You must implement this.
 * 
 * @see nachos.threads.Condition
 */
public class Condition2 {
	/**
	 * Allocate a new condition variable.
	 * 
	 * @param conditionLock the lock associated with this condition variable.
	 * The current thread must hold this lock whenever it uses <tt>sleep()</tt>,
	 * <tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */
	public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;
	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The current
	 * thread must hold the associated lock. The thread will automatically
	 * reacquire the lock before <tt>sleep()</tt> returns.
	 */
	public void sleep() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		boolean intStatus = Machine.interrupt().disable();

		waitQueue.waitForAccess(KThread.currentThread());

		conditionLock.release();
		KThread.sleep();
		conditionLock.acquire();

		Machine.interrupt().restore(intStatus);

		/**Condition implementation */

		// Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		// Semaphore waiter = new Semaphore(0);
		// waitQueue.add(waiter);

		// conditionLock.release();
		// waiter.P();
		// conditionLock.acquire();
	}

	/**
	 * Wake up at most one thread sleeping on this condition variable. The
	 * current thread must hold the associated lock.
	 */
	public void wake() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		boolean intStatus = Machine.interrupt().disable();

		KThread toWake = waitQueue.nextThread();

		if (toWake != null && !ThreadedKernel.alarm.cancel(toWake)){
			toWake.ready();
		}

		Machine.interrupt().restore(intStatus);

		/**Condition implementation */

		// Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		// if (!waitQueue.isEmpty())
		//	 ((Semaphore) waitQueue.removeFirst()).V();
	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		boolean intStatus = Machine.interrupt().disable();

		KThread toWake = waitQueue.nextThread();

		while (toWake != null){
			//System.out.println("wakeAll waking " + toWake.getName());
			if(!ThreadedKernel.alarm.cancel(toWake)){
				toWake.ready();
			}
			toWake = waitQueue.nextThread();
		}

		Machine.interrupt().restore(intStatus);

		/**Condition implementation */

		// Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		// while (!waitQueue.isEmpty())
		// 	wake();
	}

        /**
	 * Atomically release the associated lock and go to sleep on
	 * this condition variable until either (1) another thread
	 * wakes it using <tt>wake()</tt>, or (2) the specified
	 * <i>timeout</i> elapses.  The current thread must hold the
	 * associated lock.  The thread will automatically reacquire
	 * the lock before <tt>sleep()</tt> returns.
	 */
    public void sleepFor(long timeout) {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		boolean intStatus = Machine.interrupt().disable();

		waitQueue.waitForAccess(KThread.currentThread());

		conditionLock.release();
		ThreadedKernel.alarm.waitUntil(timeout);
		conditionLock.acquire();

		Machine.interrupt().restore(intStatus);
	}


	public static void cvTestDefault() {
        final Lock lock = new Lock();
        // final Condition empty = new Condition(lock);
        final Condition2 empty = new Condition2(lock);
        final LinkedList<Integer> list = new LinkedList<>();

        KThread consumer = new KThread( new Runnable () {
                public void run() {
                    lock.acquire();
                    while(list.isEmpty()){
                        empty.sleep();
                    }
                    Lib.assertTrue(list.size() == 5, "List should have 5 values.");
                    while(!list.isEmpty()) {
                        // context swith for the fun of it
                        KThread.currentThread().yield();
                        System.out.println("Removed " + list.removeFirst());
                    }
                    lock.release();
                }
            });

        KThread producer = new KThread( new Runnable () {
                public void run() {
                    lock.acquire();
                    for (int i = 0; i < 5; i++) {
                        list.add(i);
                        System.out.println("Added " + i);
                        // context swith for the fun of it
                        KThread.currentThread().yield();
                    }
                    empty.wake();
                    lock.release();
                }
            });

        consumer.setName("Consumer");
        producer.setName("Producer");
        consumer.fork();
        producer.fork();

        // We need to wait for the consumer and producer to finish,
        // and the proper way to do so is to join on them.  For this
        // to work, join must be implemented.  If you have not
        // implemented join yet, then comment out the calls to join
        // and instead uncomment the loop with yield; the loop has the
        // same effect, but is a kludgy way to do it.
        consumer.join();
        producer.join();
        //for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
    }

	//wake wakes up at most one thread, even if multiple threads are waiting

	public static void cvTest1() {
		final Lock lock = new Lock();
		// final Condition empty = new Condition(lock);
        final Condition2 empty = new Condition2(lock);
        final LinkedList<Integer> list1 = new LinkedList<>();
		final LinkedList<Integer> list2 = new LinkedList<>();

		KThread waiter1 = new KThread( new Runnable () {
			public void run() {
				lock.acquire();
				while(list1.isEmpty()){
					empty.sleep();
				}
				Lib.assertTrue(list1.size() == 5, "List should have 5 values.");
				while(!list1.isEmpty()) {
					// context swith for the fun of it
					KThread.currentThread().yield();
					System.out.println("list1: Removed " + list1.removeFirst());
				}
				empty.wake();
				lock.release();
			}
		});

		KThread waiter2 = new KThread( new Runnable () {
			public void run() {
				lock.acquire();
				while(list2.isEmpty()){
					empty.sleep();
				}
				Lib.assertTrue(list2.size() == 5, "List should have 5 values.");
				while(!list2.isEmpty()) {
					// context swith for the fun of it
					KThread.currentThread().yield();
					System.out.println("list2: Removed " + list2.removeFirst());
				}
				lock.release();
			}
		});

		KThread producer = new KThread( new Runnable () {
			public void run() {
				lock.acquire();
				for (int i = 0; i < 5; i++) {
					list1.add(i);
					list2.add(i);
					System.out.println("Added " + i);
					// context swith for the fun of it
					KThread.currentThread().yield();
				}

				System.out.print("What's in waitQueue before wake: ");
				boolean intStatus = Machine.interrupt().disable();
				empty.waitQueue.print();
				System.out.print("");
				Machine.interrupt().restore(intStatus);

				empty.wake();

				System.out.print("What's in waitQueue after wake: ");
				intStatus = Machine.interrupt().disable();
				empty.waitQueue.print();
				System.out.println("");
				Machine.interrupt().restore(intStatus);

				lock.release();
			}
		});

		waiter1.setName("Waiter1");
		waiter2.setName("Waiter2");
        producer.setName("Producer");
        waiter1.fork();
		waiter2.fork();
        producer.fork();

		waiter1.join();
        waiter2.join();
		producer.join();
        //for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
	}

	//wakeAll wakes up all waiting threads
	public static void cvTest2() {
		final Lock lock = new Lock();
		// final Condition empty = new Condition(lock);
        final Condition2 empty = new Condition2(lock);
        final LinkedList<Integer> list1 = new LinkedList<>();
		final LinkedList<Integer> list2 = new LinkedList<>();

		KThread waiter1 = new KThread( new Runnable () {
			public void run() {
				lock.acquire();
				while(list1.isEmpty()){
					empty.sleep();
				}
				Lib.assertTrue(list1.size() == 5, "List should have 5 values.");
				while(!list1.isEmpty()) {
					// context swith for the fun of it
					KThread.currentThread().yield();
					System.out.println("list1: Removed " + list1.removeFirst());
				}
				lock.release();
			}
		});

		KThread waiter2 = new KThread( new Runnable () {
			public void run() {
				lock.acquire();
				while(list2.isEmpty()){
					empty.sleep();
				}
				Lib.assertTrue(list2.size() == 5, "List should have 5 values.");
				while(!list2.isEmpty()) {
					// context swith for the fun of it
					KThread.currentThread().yield();
					System.out.println("list2: Removed " + list2.removeFirst());
				}
				lock.release();
			}
		});

		KThread producer = new KThread( new Runnable () {
			public void run() {
				lock.acquire();
				for (int i = 0; i < 5; i++) {
					list1.add(i);
					list2.add(i);
					System.out.println("Added " + i);
					// context swith for the fun of it
					KThread.currentThread().yield();
				}

				System.out.print("What's in waitQueue before wakeAll: ");
				boolean intStatus = Machine.interrupt().disable();
				empty.waitQueue.print();
				System.out.println("");
				Machine.interrupt().restore(intStatus);

				empty.wakeAll();

				System.out.print("What's in waitQueue after wakeAll: ");
				intStatus = Machine.interrupt().disable();
				empty.waitQueue.print();
				System.out.println("");
				Machine.interrupt().restore(intStatus);

				lock.release();
			}
		});

		waiter1.setName("Waiter1");
		waiter2.setName("Waiter2");
        producer.setName("Producer");
        waiter1.fork();
		waiter2.fork();
        producer.fork();

		waiter1.join();
        waiter2.join();
		producer.join();
        //for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
	}

	//if a thread calls any of the synchronization methods without holding the lock Nachos asserts
	public static void cvTest3(){
		final Lock lock = new Lock();
		// final Condition empty = new Condition(lock);
        final Condition2 empty = new Condition2(lock);

		KThread sleepTester = new KThread( new Runnable () {
			public void run() {
				try{
					empty.sleep();
				}
				catch(Error e){
					System.out.println("Tried to sleep");
				}
				
			}
		});

		KThread wakeTester = new KThread( new Runnable () {
			public void run() {
				try{
					empty.wake();
				}
				catch(Error e){
					System.out.println("Tried to wake");
				}
				
			}
		});

		KThread wakeAllTester = new KThread( new Runnable () {
			public void run() {
				try{
					empty.wakeAll();
				}
				catch(Error e){
					System.out.println("Tried to wakeAll");
				}
				
			}
		});

		sleepTester.setName("Sleeper");
		wakeTester.setName("Waker");
		wakeAllTester.setName("Waker");
		sleepTester.fork();
		wakeTester.fork();
        wakeAllTester.fork();

		sleepTester.join();
        wakeTester.join();
		wakeAllTester.join();
        //for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
	}

	//wake and wakeAll with no waiting threads have no effect, yet future threads that sleep will still block
	public static void cvTest4(){
		final Lock lock = new Lock();
		// final Condition empty = new Condition(lock);
        final Condition2 empty = new Condition2(lock);
		final LinkedList<Integer> list = new LinkedList<>();

		KThread consumer = new KThread( new Runnable () {
			public void run() {
				lock.acquire();
				while(list.isEmpty()){
					System.out.println("Adding thread to waitQueue...");
					empty.sleep();
				}
				Lib.assertTrue(list.size() == 5, "List should have 5 values.");
				while(!list.isEmpty()) {
					// context swith for the fun of it
					KThread.currentThread().yield();
					System.out.println("Removed " + list.removeFirst());
				}
				lock.release();
			}
		});

		KThread producer1 = new KThread( new Runnable () {
			public void run() {
				lock.acquire();
				System.out.println("Doing nothing in producer1!");

				System.out.print("Waiting threads: ");
				boolean intStatus = Machine.interrupt().disable();
				empty.waitQueue.print();
				System.out.println("");
				Machine.interrupt().restore(intStatus);

				System.out.println("Trying to wake...");
				empty.wake();
				empty.wakeAll();
				lock.release();
			}
		});

		KThread producer2 = new KThread( new Runnable () {
			public void run() {
				lock.acquire();
				for (int i = 0; i < 5; i++) {
					list.add(i);
					System.out.println("Added " + i);
					// context swith for the fun of it
					KThread.currentThread().yield();
				}
				empty.wake();
				lock.release();
			}
		});

		consumer.setName("Consumer");
        producer1.setName("Producer1");
		producer2.setName("Producer2");
		producer1.fork();
        consumer.fork();
		producer2.fork();

		producer1.join();
		consumer.join();
		producer2.join();
        //for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
	}

	//verify that a thread that calls sleepFor will timeout and return after x ticks if no other thread calls wake to wake it up
	public static void cvsfTest1(){
		final Lock lock = new Lock();
		// final Condition empty = new Condition(lock);
        final Condition2 cv = new Condition2(lock);
		//final LinkedList<Integer> list = new LinkedList<>();

		KThread waiter = new KThread( new Runnable () {
			public void run() {
				boolean flag = false;
				lock.acquire();
				System.out.println("Sleeping for 10000000...");
				cv.sleepFor(10000000);
				lock.release();
				flag = true;
				System.out.println("Woken up by timer!");
				Lib.assertTrue(flag);
			}
		});


		waiter.setName("Waiter");
		waiter.fork();

		waiter.join();

	}

	//a thread that calls sleepFor will wake up and return if another thread calls wake before the timeout expires
	public static void cvsfTest2(){
		final Lock lock = new Lock();
		// final Condition empty = new Condition(lock);
        final Condition2 cv = new Condition2(lock);
		//final LinkedList<Integer> list = new LinkedList<>();

		KThread waiter = new KThread( new Runnable () {
			public void run() {
				boolean flag = false;
				lock.acquire();
				System.out.println("Sleeping for 10000000...");
				cv.sleepFor(10000000);
				lock.release();
				flag = true;
				System.out.println("Woken up!");
				Lib.assertTrue(flag);
			}
		});

		KThread producer = new KThread( new Runnable () {
			public void run() {
				lock.acquire();
				System.out.println("Trying to wake...");
				cv.wake();
				lock.release();
			}
		});

		waiter.setName("Waiter");
		producer.setName("Producer");
		waiter.fork();
		producer.fork();

		waiter.join();
		producer.join();

	}

	//sleepFor handles multiple threads correctly (e.g., different timeouts, all are woken up with wakeAll
	public static void cvsfTest3(){
		final Lock lock = new Lock();
		// final Condition empty = new Condition(lock);
        final Condition2 cv = new Condition2(lock);
		//final LinkedList<Integer> list = new LinkedList<>();

		KThread waiter1 = new KThread( new Runnable () {
			public void run() {
				boolean flag = false;
				lock.acquire();
				System.out.println("Waiter1 sleeping for 10000000...");
				cv.sleepFor(10000000);
				lock.release();
				flag = true;
				System.out.println("Waiter1 woken up!");
				Lib.assertTrue(flag);
			}
		});

		KThread waiter2 = new KThread( new Runnable () {
			public void run() {
				boolean flag = false;
				lock.acquire();
				System.out.println("Waiter2 sleeping for 50000000...");
				cv.sleepFor(50000000);
				lock.release();
				flag = true;
				System.out.println("Waiter2 woken up!");
				Lib.assertTrue(flag);
			}
		});

		KThread producer = new KThread( new Runnable () {
			public void run() {
				lock.acquire();
				System.out.println("Trying to wakeAll...");
				cv.wakeAll();
				lock.release();
			}
		});

		waiter1.setName("Waiter1");
		waiter2.setName("Waiter2");
		producer.setName("Producer");
		waiter1.fork();
		waiter2.fork();
		producer.fork();

		waiter1.join();
		waiter2.join();
		producer.join();

	}


	public static void selfTest() {
		
		//cvTestDefault();
		//cvTest1();
		//cvTest2();
		//cvTest3();
		//cvTest4();
		//cvsfTest1();
		//cvsfTest2();
		cvsfTest3();
	}

    private Lock conditionLock;

	private ThreadQueue waitQueue = ThreadedKernel.scheduler.newThreadQueue(false);
}
