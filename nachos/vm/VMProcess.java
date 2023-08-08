package nachos.vm;

import java.util.Arrays;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		super.saveState();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		super.restoreState();
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {

		Lib.debug(dbgProcess, "VMprocess");

		pageTable = new TranslationEntry[numPages];

		for (int i = 0; i < numPages; i++) {
			pageTable[i] = new TranslationEntry(i, -1, false, false, false, false);
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {

		for (int i = 0; i < pageTable.length; i++) {
			if (!pageTable[i].valid) continue;
			UserKernel.freePage(pageTable[i].ppn);
		}

		coff.close();

		pageTable = null;
	}

	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		if (data == null || vaddr < 0 || vaddr >= numPages * pageSize){return -1;}

		int amount = 0;

		int vpn = Processor.pageFromAddress(vaddr);

		if (!pageTable[vpn].valid) {
			int faultVaddr = Processor.makeAddress(vpn, 0);
			handlePageFault(faultVaddr);
		}
			
		int offset_from_vaddr = Processor.offsetFromAddress(vaddr);
		int ppn = pageTable[vpn].ppn;
		int paddr = pageSize * ppn + offset_from_vaddr;
		int bytesRemaining = length;

		while(bytesRemaining > 0){

			VMKernel.pinLock.acquire();
			VMKernel.ipt[ppn].isPinned = true;
			VMKernel.numPagesPinned++;
			VMKernel.pinLock.release();

			if(paddr < 0 || paddr >= memory.length){
				VMKernel.pinLock.acquire();
				VMKernel.ipt[ppn].isPinned = false;
				VMKernel.numPagesPinned--;
				VMKernel.pinCV.wake();
				VMKernel.pinLock.release();
				break;
			}

			int maxSingleCopy = pageSize - offset_from_vaddr;
			int amountToCopy = Math.min(bytesRemaining, maxSingleCopy);
			
			System.arraycopy(memory, paddr, data, offset, amountToCopy);

			amount += amountToCopy;

			offset += amountToCopy;
			pageTable[vpn].used = true;
			vpn++;

			VMKernel.pinLock.acquire();
			VMKernel.ipt[ppn].isPinned = false;
			VMKernel.numPagesPinned--;
			VMKernel.pinCV.wake();
			VMKernel.pinLock.release();

			if(vpn >= pageTable.length) {break;}


			if (!pageTable[vpn].valid) {
				int faultVaddr = Processor.makeAddress(vpn, 0);
				handlePageFault(faultVaddr);
			}

			offset_from_vaddr = 0;
			ppn = pageTable[vpn].ppn;
			paddr = pageSize * ppn + offset_from_vaddr;
			bytesRemaining -= amountToCopy;
			
		}

		return amount;
	}

	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		if (data == null || vaddr < 0 || vaddr >= numPages * pageSize){return -1;}

		int amount = 0;

		int vpn = Processor.pageFromAddress(vaddr);

		if (!pageTable[vpn].valid) {
			int faultVaddr = Processor.makeAddress(vpn, 0);
			handlePageFault(faultVaddr);
		}

		int offset_from_vaddr = Processor.offsetFromAddress(vaddr);
		int ppn = pageTable[vpn].ppn;
		int paddr = pageSize * ppn + offset_from_vaddr;
		int bytesRemaining = length;
		while(bytesRemaining > 0){

			VMKernel.pinLock.acquire();
			VMKernel.ipt[ppn].isPinned = true;
			VMKernel.numPagesPinned++;
			VMKernel.pinLock.release();

			if(pageTable[vpn].readOnly || paddr < 0 || paddr >= memory.length){
				VMKernel.pinLock.acquire();
				VMKernel.ipt[ppn].isPinned = false;
				VMKernel.numPagesPinned--;
				VMKernel.pinCV.wake();
				VMKernel.pinLock.release();
				break;
			}

			if (!pageTable[vpn].valid) {
				int faultVaddr = Processor.makeAddress(vpn, 0);
				handlePageFault(faultVaddr);
			}

			int maxSingleCopy = pageSize - offset_from_vaddr;
			int amountToCopy = Math.min(bytesRemaining, maxSingleCopy);
			
			System.arraycopy(data, offset, memory, paddr, amountToCopy);

			amount += amountToCopy;

			offset += amountToCopy;
			pageTable[vpn].used = true;
			pageTable[vpn].dirty = true;
			vpn++;

			VMKernel.pinLock.acquire();
			VMKernel.ipt[ppn].isPinned = false;
			VMKernel.numPagesPinned--;
			VMKernel.pinCV.wake();
			VMKernel.pinLock.release();

			if(vpn >= pageTable.length) {break;}
			
			offset_from_vaddr = 0;

			if (!pageTable[vpn].valid) {
				int faultVaddr = Processor.makeAddress(vpn, 0);
				handlePageFault(faultVaddr);
			}

			ppn = pageTable[vpn].ppn;
			paddr = pageSize * ppn + offset_from_vaddr;
			bytesRemaining -= amountToCopy;
			
		}

		return amount;
	}

	/**
	 * Handle page fault exception, more details below
	 */
	public void handlePageFault(int vaddr){
		// Once virtual address caused page fault is known, system checks to see if address is valid and checks if there is no protection access problem.
		// If the virtual address is valid, the system checks to see if a page frame is free. If no frames are free, the page replacement algorithm is run to remove a page.
		// If frame selected is dirty, page is scheduled for transfer to disk, context switch takes place, fault process is suspended and another process is made to run until disk transfer is completed.
		// As soon as page frame is clean, operating system looks up disk address where needed page is, schedules disk operation to bring it in.
		// When disk interrupt indicates page has arrived, page tables are updated to reflect its position, and frame marked as being in normal state

		VMKernel.iptLock.acquire(); 

		int faultingVPN = Processor.pageFromAddress(vaddr);
		boolean isDirty = pageTable[faultingVPN].dirty;

		/** CASE I: Page is in swap file */
		if(isDirty){
			int vpn = faultingVPN;
			int ppn = 0;

			UserKernel.fppLock.acquire();
			//free page already exists
			if(!UserKernel.freePhysicalPages.isEmpty()){
				ppn = UserKernel.freePhysicalPages.removeFirst();
			}
			//page replacement
			else{
				ppn = VMKernel.chooseEvictPPN();
			}
			UserKernel.fppLock.release();

			swapIn(vpn, ppn);

			pageTable[vpn].ppn = ppn;
			pageTable[vpn].valid = true;
			pageTable[vpn].used = true;

			VMKernel.ipt[ppn].process = this;
			VMKernel.ipt[ppn].entry = pageTable[vpn];
			VMKernel.ipt[ppn].isPinned = false;

			VMKernel.iptLock.release();
			return;
		}

		/** CASE II: Page is in coff file */

		int vpnCounter = 0;

		//CASE IIa: Load from coff pages
		for (int s = 0; s < coff.getNumSections(); s++) {

			CoffSection section = coff.getSection(s); 

			//check if faulting vpn is in section
			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				vpnCounter = vpn;
				int ppn = 0;
				
				if(faultingVPN == vpn){
					UserKernel.fppLock.acquire();
					//free page already exists
					if(!UserKernel.freePhysicalPages.isEmpty()){
						ppn = UserKernel.freePhysicalPages.removeFirst();
					}
					//page replacement
					else{
						ppn = VMKernel.chooseEvictPPN();
					}
					UserKernel.fppLock.release();

					pageTable[vpn].ppn = ppn;
					pageTable[vpn].valid = true;
					pageTable[vpn].used = true;
					
					section.loadPage(i, ppn);

					VMKernel.ipt[ppn].process = this;
					VMKernel.ipt[ppn].entry = pageTable[vpn];
					VMKernel.ipt[ppn].isPinned = false;

					VMKernel.iptLock.release();
					return;
				}
			}
		}

		//CASE IIb: Load from stack pages
		for (int i = vpnCounter + 1; i < numPages; i++) {
			int vpn = i;
			int ppn = 0;

			if (faultingVPN == vpn) {
				UserKernel.fppLock.acquire();
				//free page already exists
				if(!UserKernel.freePhysicalPages.isEmpty()){
					ppn = UserKernel.freePhysicalPages.removeFirst();
					Lib.debug(dbgProcess, "ppn from free: " + ppn);
				}
				//page replacement
				else{
					// ppn = replacePage(vpn);
					ppn = VMKernel.chooseEvictPPN();
					Lib.debug(dbgProcess, "ppn from evict: " + ppn);
				}
				UserKernel.fppLock.release();

				// pageTable[vpn] = new TranslationEntry(vpn, ppn, true, false, true, isDirty);
				pageTable[vpn].ppn = ppn;
				pageTable[vpn].valid = true;
				pageTable[vpn].used = true;

				byte[] data = new byte[pageSize];
				Arrays.fill(data, (byte) 0);
				System.arraycopy(data, 0, Machine.processor().getMemory(), Processor.makeAddress(ppn, 0), pageSize);

				// Lib.debug(dbgProcess, "Not dirty, stack; vpn " + vpn + " assigned to ppn " + ppn);
				// VMKernel.ipt[ppn] = new VMKernel.IPTEntry(this, pageTable[vpn], false);

				VMKernel.ipt[ppn].process = this;
				VMKernel.ipt[ppn].entry = pageTable[vpn];
				VMKernel.ipt[ppn].isPinned = false;
			}
		}

		VMKernel.iptLock.release();
	}

	// called by VMKernel
	public int evict(int toEvictVPN, int ppn) {
		pageTable[toEvictVPN].valid = false;
		if (pageTable[toEvictVPN].dirty) {
			int spn = swapOut(-1, ppn); // first param not really used
			pageTable[toEvictVPN].ppn = spn;
		} else {
			pageTable[toEvictVPN].ppn = -1;
		}
		return -1;
	}

	public int swapOut(int vpn, int ppn){
		VMKernel.fspLock.acquire();

		int spn = 0;
		if (!VMKernel.freeSwapPages.isEmpty()) {
			spn = VMKernel.freeSwapPages.removeFirst();
		} 
		else {
			spn = VMKernel.numSwapPages;
		}
		VMKernel.numSwapPages++;
		// writing ppn (page to be swapped out) from memory to swap file on disk (pos is spn)
		VMKernel.swapFile.write(spn * pageSize, Machine.processor().getMemory(), ppn * pageSize, pageSize);

		VMKernel.fspLock.release();

		// where the swapped out page is store in swap file
		return spn;
	}

	public void swapIn(int vpn, int ppn){
		VMKernel.fspLock.acquire();


		int spn = pageTable[vpn].ppn; // find where swapped out file is located at in swap file

		VMKernel.swapFile.read(spn * pageSize, Machine.processor().getMemory(), ppn * pageSize, Processor.pageSize);
        VMKernel.freeSwapPages.add(spn);

		VMKernel.fspLock.release();

	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionPageFault:
			handlePageFault(processor.readRegister(Processor.regBadVAddr));
			break;
		default:
			super.handleException(cause);
			break;
		}
	}

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}
