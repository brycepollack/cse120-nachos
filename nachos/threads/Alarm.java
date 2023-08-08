package nachos.threads;

import java.util.Comparator;
import java.util.PriorityQueue;

import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}


	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		// KThread.currentThread().yield();

		while ((!waitQueue.isEmpty()) && (waitQueue.peek().wakeTime <= Machine.timer().getTime())) {
			KThread nextThread = waitQueue.poll().getThread();
			
			if(nextThread != null){
				//Lib.debug(dbgThread, "Alarm: Setting thread " + nextThread.toString() + " to ready");
				//System.out.println("timerInterrupting " + nextThread.getName());
				nextThread.ready();
			}
		}

		KThread.currentThread().yield();
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		// 0 or negative
		if (x <= 0) return;

		long wakeTime = Machine.timer().getTime() + x;
		// while (wakeTime > Machine.timer().getTime())
		// 	KThread.yield();

		WaitThread waitThread = new WaitThread(KThread.currentThread(), wakeTime);
		waitQueue.add(waitThread);

		boolean intStatus = Machine.interrupt().disable();
		//Lib.debug(dbgThread, "Alarm: Setting thread " + KThread.currentThread().toString() + " to sleep");
		KThread.sleep();
		Machine.interrupt().restore(intStatus);

	}

        /**
	 * Cancel any timer set by <i>thread</i>, effectively waking
	 * up the thread immediately (placing it in the scheduler
	 * ready set) and returning true.  If <i>thread</i> has no
	 * timer set, return false.
	 * 
	 * <p>
	 * @param thread the thread whose timer should be cancelled.
	 */
    public boolean cancel(KThread thread) {

		for(WaitThread e : waitQueue){
			if(e.getThread() != null && e.getThread() == thread){
				//System.out.println("Cancelling " + thread.getName());
				waitQueue.remove(e);
				e.getThread().ready();
				return true;
			}
		}
		return false;
	}

	/**
	 * WaitThread class for queue
	 */
	private class WaitThread {
		private KThread thread;
		private long wakeTime;

		public WaitThread(KThread kThread, long wakeTime) {
			this.thread = kThread;
			this.wakeTime = wakeTime;
		}

		public KThread getThread() {
			return thread;
		}

		public long getWakeTime() {
			return wakeTime;
		}

	}

	private class WaitThreadComparator implements Comparator<WaitThread> {
		@Override
		public int compare(WaitThread o1, WaitThread o2) {
			if (o1.getWakeTime() < o2.getWakeTime()) {
				return -1;
			} else if (o1.getWakeTime() == o2.getWakeTime()) {
				return 0;
			} else {
				return 1;
			}
		}
	}

	private PriorityQueue<WaitThread> waitQueue = new PriorityQueue<>(1, new WaitThreadComparator());


    // Add Alarm testing code to the Alarm class
    
    public static void alarmTest1() {
		Lib.debug(dbgThread, "Enter Alarm.selfTest1");
		int durations[] = {1000, 10*1000, 100*1000};
		long t0, t1;
	
		for (int d : durations) {
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil (d);
			t1 = Machine.timer().getTime();
			System.out.println ("alarmTest1: waited for " + (t1 - t0) + " ticks");
		}
	}

	private static class LoopTest implements Runnable {
		LoopTest(int which, int wait) {
			this.which = which;
			this.wait = wait;
		}

		public void run() {
			long prev = Machine.timer().getTime();
			for (int i = 0; i < 5; i++) {
				System.out.println("*** thread " + which + " looped " + i
						+ " times");
				System.out.println((Machine.timer().getTime() - prev) + " time between loops");
				prev = Machine.timer().getTime();
				ThreadedKernel.alarm.waitUntil(wait);
				// KThread.currentThread().yield();
			}
		}

		private int which;
		private long wait;
	}

	private static class WakeTest implements Runnable {
		WakeTest(String name, int wait) {
			this.name = name;
			this.wait = wait;
		}

		public void run() {
			long prev = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil(wait);
			System.out.println(name + " has woken after " + (Machine.timer().getTime() - prev));
		}

		private String name;
		private int wait;
	}

	public static void alarmTestLeqZero() {
		Lib.debug(dbgThread, "Enter Alarm.testLeqZero");
		new LoopTest(0, -10).run();
	}

	public static void alarmTestOrder() {
		Lib.debug(dbgThread, "Enter Alarm.testOrder");

		int base = 50000;

		new KThread(new WakeTest("second", base * 2)).setName("second").fork();
		new KThread(new WakeTest("fourth", base * 4)).setName("fourth").fork();
		new KThread(new WakeTest("third", base * 3)).setName("third").fork();
		new KThread(new WakeTest("first", base)).setName("first").fork();
		new WakeTest("main", base * 8).run(); // longest sleep time for main to ensure forks finish running
	}

	// Invoke Alarm.selfTest() from ThreadedKernel.selfTest()
	public static void selfTest() {
		alarmTest1();
		alarmTestLeqZero();
		alarmTestOrder();
	}

	private static char dbgThread = 'a';
}
