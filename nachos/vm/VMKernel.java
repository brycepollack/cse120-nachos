package nachos.vm;

import java.util.LinkedList;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);

		swapFile = ThreadedKernel.fileSystem.open("swapFile", true);
		fspLock = new Lock();
		freeSwapPages = new LinkedList<>();
		numSwapPages = 0;
		ipt = new IPTEntry[Machine.processor().getNumPhysPages()]; //indexed by ppn

		iptLock = new Lock();
		oldestPPN = 0;
		numPagesPinned = 0;
		pinLock = new Lock();
		pinCV = new Condition(pinLock);

		fspLock.acquire();
		for(int i = 0; i < Machine.processor().getNumPhysPages(); i++){
			freeSwapPages.add(i);
			ipt[i] = new IPTEntry(null, null, false);
		}
		fspLock.release();
	}

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	public static int chooseEvictPPN() {
		int ppn;

		while(ipt[oldestPPN].entry.used || ipt[oldestPPN].isPinned){
			ipt[oldestPPN].entry.used = false;
			oldestPPN++;
			oldestPPN = oldestPPN % Machine.processor().getNumPhysPages();
		}
		ppn = oldestPPN;
		oldestPPN++;
		oldestPPN = oldestPPN % Machine.processor().getNumPhysPages();

		//evict
		int toEvictVPN = ipt[ppn].entry.vpn;
		VMProcess process = ipt[ppn].process;

		ipt[ppn].entry.valid = false;
		ipt[ppn].entry = null; // the new one will replace this
		Lib.debug(dbgVM, "ppn: " + ppn);
		process.evict(toEvictVPN, ppn);

		// ppn is now empty and ready to be used
		return ppn;
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		swapFile.close();
        ThreadedKernel.fileSystem.remove("swapFile");

		super.terminate();
	}

	public static class IPTEntry {
		VMProcess process;
		TranslationEntry entry;
		boolean isPinned;
		public IPTEntry(VMProcess process, TranslationEntry entry, boolean isPinned) {
			this.process = process;
			this.entry = entry;
			this.isPinned = isPinned;
		}
	}

	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';

	public static OpenFile swapFile;

	public static LinkedList<Integer> freeSwapPages;

	public static int numSwapPages;

	public static Lock fspLock;

	public static IPTEntry[] ipt;

	public static Lock iptLock;

	public static int oldestPPN;

	public static Lock pinLock;

	public static Condition pinCV;

	public static int numPagesPinned;
}
