package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.io.EOFException;
import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		fileTable = new OpenFile[16];
		fileTable[0] = UserKernel.console.openForReading();
		fileTable[1] = UserKernel.console.openForWriting();

		PID = UserKernel.getPid();
		//parentPID = -1;
		//exitStatus = -1; // 0 if success, 1 if error

		parentProcess = null;
		children = new HashMap<Integer, UserProcess>();
		childrenExitStatus = new HashMap<Integer, Integer>();

		// int numPhysPages = Machine.processor().getNumPhysPages();
		// pageTable = new TranslationEntry[numPhysPages];
		// for (int i = 0; i < numPhysPages; i++)
		// 	pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
	        String name = Machine.getProcessClassName ();

		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader.  Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals ("nachos.userprog.UserProcess")) {
		    return new UserProcess ();
		} else if (name.equals ("nachos.vm.VMProcess")) {
		    return new VMProcess ();
		} else {
		    return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		thread = new UThread(this);
		thread.setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		// if (vaddr < 0 || vaddr >= memory.length)
		// 	return 0;

		// int amount = Math.min(length, memory.length - vaddr);
		// System.arraycopy(memory, vaddr, data, offset, amount);

		if (data == null || vaddr < 0 || vaddr >= memory.length){return -1;}

		int amount = 0;

		int vpn = Processor.pageFromAddress(vaddr);
		
		if(vpn >= pageTable.length) {return -1;}
			
		int offset_from_vaddr = Processor.offsetFromAddress(vaddr);
		int ppn = pageTable[vpn].ppn;
		int paddr = pageSize * ppn + offset_from_vaddr;
		int bytesRemaining = length;

		while(bytesRemaining > 0){

			if(!pageTable[vpn].valid || paddr < 0 || paddr >= memory.length){break;}

			int maxSingleCopy = pageSize - offset_from_vaddr;
			int amountToCopy = Math.min(bytesRemaining, maxSingleCopy);

			Lib.debug(dbgProcess, "Offset: " + offset);
			Lib.debug(dbgProcess, "AmountToCopy: " + amountToCopy);
			
			System.arraycopy(memory, paddr, data, offset, amountToCopy);

			amount += amountToCopy;

			offset += amountToCopy;
			pageTable[vpn].used = true;
			vpn++;

			if(vpn >= pageTable.length) {break;}

			offset_from_vaddr = 0;
			ppn = pageTable[vpn].ppn;
			paddr = pageSize * ppn + offset_from_vaddr;
			bytesRemaining -= amountToCopy;
			
		}

	

		return amount;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		// if (vaddr < 0 || vaddr >= memory.length)
		// 	return 0;

		// int amount = Math.min(length, memory.length - vaddr);
		// System.arraycopy(data, offset, memory, vaddr, amount);

		if (data == null || vaddr < 0 || vaddr >= memory.length){return -1;}

		int amount = 0;

		int vpn = Processor.pageFromAddress(vaddr);

		if(vpn >= pageTable.length) {return -1;}

		int offset_from_vaddr = Processor.offsetFromAddress(vaddr);
		int ppn = pageTable[vpn].ppn;
		int paddr = pageSize * ppn + offset_from_vaddr;
		int bytesRemaining = length;

		while(bytesRemaining > 0){

			if(!pageTable[vpn].valid || pageTable[vpn].readOnly || paddr < 0 || paddr >= memory.length){break;}

			int maxSingleCopy = pageSize - offset_from_vaddr;
			int amountToCopy = Math.min(bytesRemaining, maxSingleCopy);
			
			System.arraycopy(data, offset, memory, paddr, amountToCopy);

			amount += amountToCopy;

			offset += amountToCopy;
			pageTable[vpn].used = true;
			vpn++;

			if(vpn >= pageTable.length) {break;}
			
			offset_from_vaddr = 0;
			ppn = pageTable[vpn].ppn;
			paddr = pageSize * ppn + offset_from_vaddr;
			bytesRemaining -= amountToCopy;
			
		}




		return amount;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		// TranslationEntry currTranslation;

		// if (numPages > Machine.processor().getNumPhysPages()) {
		// 	coff.close();
		// 	Lib.debug(dbgProcess, "\tinsufficient physical memory");
		// 	return false;
		// }


		int[] ppnPages = UserKernel.allocatePages(numPages);

		if (ppnPages == null) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}
		
		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i++) {
			pageTable[i] = new TranslationEntry(i, ppnPages[i], true, false, false, false);
		}

		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				int ppn = ppnPages[vpn];

				pageTable[vpn] = new TranslationEntry(vpn, ppn, true, section.isReadOnly(), false, false);

				// currTranslation = pageTable[vpn];
				// // returns false if the TranslationEntry does not exist
				// if(currTranslation == null) {
				// 	return false;
				// }
				// // otherwise sets the readOnly bit
				// currTranslation.readOnly = section.isReadOnly();

				section.loadPage(i, ppn);
			}

			// stackPages
			for (int i = numPages - stackPages - 1; i < numPages; i++) {
				pageTable[i] = new TranslationEntry(i, ppnPages[i], true, false, false, false);
			}

		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {

		for (int i = 0; i < pageTable.length; i++) {
			UserKernel.freePage(pageTable[i].ppn);
		}

		coff.close();

		pageTable = null;
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {

		if (parentProcess != null) {
			Lib.debug('a', "Not invoked by parent process; not halting");
			return -1;
		}

		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	/**
	 * Handle the exit() system call.
	 */
	private int handleExit(int status) {
	        // Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.

		Lib.debug(dbgProcess, "UserProcess.handleExit (" + status + ")");

		System.out.println("UserProcess.handleExit (" + status + ")");

		if(parentProcess != null){
			parentProcess.childrenExitStatus.put(PID, status);
		}

		//exitStatus = status;

		unloadSections();

		for (int i = 0; i < 16; i++) {
			if (fileTable[i] == null) continue;
			fileTable[i].close();
			fileTable[i] = null;
		}
		fileTable = null;

		for (Map.Entry<Integer,UserProcess> e : children.entrySet()) {
			e.getValue().parentProcess = null;
		}
		children.clear();

		if(PID == 0){
			Kernel.kernel.terminate();
		}
		else{
			thread.finish();
		}

		//Kernel.kernel.terminate();

		// when process count reaches 0
		return status;
	}

	/**
	 * Handle the creat() system call.
	 */
	private int handleCreate(int vaName) {
	    String fileName = readVirtualMemoryString(vaName, 256);

		if(fileName == null){return -1;}

		//Check fileTable[2-15] for open slot
		int idx = -1;
		for(int i = 0; i < 16; i++){
			if(fileTable[i] == null){
				idx = i;
				break;
			}
		}

		if(idx == -1){return -1;}

		OpenFile of = ThreadedKernel.fileSystem.open(fileName, true);

		if(of == null){return -1;}

		fileTable[idx] = of;
		return idx;
	}

	/**
	 * Handle the open() system call.
	 */
	private int handleOpen(int vaName) {
	    String fileName = readVirtualMemoryString(vaName, 256);

		if(fileName == null){return -1;}

		//Check fileTable[2-15] for open slot
		int idx = -1;
		for(int i = 0; i < 16; i++){
			if(fileTable[i] == null){
				idx = i;
				break;
			}
		}

		if(idx == -1){return -1;}

		OpenFile of = ThreadedKernel.fileSystem.open(fileName, false);

		if(of == null){return -1;}

		fileTable[idx] = of;
		return idx;
	}

	/**
	 * Handle the read() system call.
	 */
	private int handleRead(int fd, int buffer, int count) {

		// if(fd < 0 || fd > 15 || fileTable[fd] == null || buffer < 0 || count < 0){return -1;}

		// OpenFile toRead = fileTable[fd];
		// byte[] localBuffer = new byte[count];
		// int numBytesRead = toRead.read(localBuffer, 0, count);

		// if(numBytesRead == -1){return -1;}

		// int numBytesReadVirt = writeVirtualMemory(buffer, localBuffer, 0, numBytesRead);

		// return numBytesReadVirt;

	    if(fd < 0 || fd > 15 || fileTable[fd] == null || buffer < 0 || count < 0){return -1;}

		OpenFile toRead = fileTable[fd];

		int amount = 0;

		int bytesRemaining = count;

		while(bytesRemaining > 0){

			int localBufferSize = Math.min(pageSize, bytesRemaining);
			byte[] localBuffer = new byte[localBufferSize];

			int numBytesRead = toRead.read(localBuffer, 0, localBufferSize);

			if(numBytesRead < 0){return -1;}
			// Lib.debug(dbgProcess, "numBytesRead " + numBytesRead);

			//if(numBytesRead == 0){break;}

			int numBytesWritten = writeVirtualMemory(buffer, localBuffer, 0, numBytesRead);

			// Lib.debug(dbgProcess, "wrote " + numBytesWritten + " read " + numBytesRead);
			if(numBytesWritten != numBytesRead){return -1;}

			buffer += numBytesWritten;
			amount += numBytesWritten;
			bytesRemaining -= numBytesWritten;

			if (numBytesRead < localBufferSize) break;

		}

		return amount;

		//Read from file to buffer
		//Read from fileTable[fd] to local buffer
		//Write to user inputted buffer using writeVirtualMemory
	}

	/**
	 * Handle the write() system call.
	 */
	private int handleWrite(int fd, int buffer, int count) {

		// if(fd < 0 || fd > 15 || fileTable[fd] == null || buffer < 0 || count < 0){return -1;}

		// OpenFile toWrite = fileTable[fd];
		// byte[] localBuffer = new byte[count];
		// int numBytesWrittenVirt = readVirtualMemory(buffer, localBuffer, 0, count);

		// if(numBytesWrittenVirt < count){return -1;}

		// int numBytesWritten = toWrite.write(localBuffer, 0, count);

		// if(numBytesWritten == -1){return -1;}

		// return numBytesWritten;

		if(fd < 0 || fd > 15 || fileTable[fd] == null || buffer < 0 || count < 0){return -1;}

		OpenFile toWrite = fileTable[fd];

		int amount = 0;

		int bytesRemaining = count;

		while(bytesRemaining > 0){

			int localBufferSize = Math.min(pageSize, bytesRemaining);
			byte[] localBuffer = new byte[localBufferSize];

			int numBytesRead = readVirtualMemory(buffer, localBuffer, 0, localBufferSize);

			// Lib.debug(dbgProcess, "read " + numBytesRead);
			if(numBytesRead < 0){return -1;}

			//if(numBytesRead == 0){break;}

			int numBytesWritten = toWrite.write(localBuffer, 0, numBytesRead);

			// if(numBytesWritten == -1){return -1;}

			// if(numBytesWritten == 0){break;}

			// Lib.debug(dbgProcess, "read " + numBytesRead + " wrote " + numBytesWritten);
			if (numBytesWritten != numBytesRead) return -1;

			buffer += numBytesWritten;
			amount += numBytesWritten;
			bytesRemaining -= numBytesWritten;

			if (numBytesRead < localBufferSize) break;

		}

		return amount;

		//Write into file from buffer
		//Read data from user buffer into local buffer
		//Then write to fileTable[fd] using fileTable[fd].write()
	}

	/**
	 * Handle the close() system call.
	 */
	private int handleClose(int fd) {
		if(fd < 0 || fd > 15 || fileTable[fd] == null){return -1;}

		OpenFile toClose = fileTable[fd];
		toClose.close();
		fileTable[fd] = null;

		return 0;
	    
	}

	/**
	 * Handle the unlink() system call.
	 */
	private int handleUnlink(int vaName) {
		String fileName = readVirtualMemoryString(vaName, 256);

		if(fileName == null){return -1;}

		boolean successfullyRemoved = ThreadedKernel.fileSystem.remove(fileName);

		if(!successfullyRemoved){return -1;}

		return 0;
	}

	private int handleExec(int vaName, int argc, int argv) {
		// int vaName is virtual address to filename
		// int argc number of arguments
		// int argv is virtual address, double pointer: argv (va) -> another va -> string

		// bullet proof
		if (vaName < 0 || argc < 0 || argv < 0) return -1;

		String filename = readVirtualMemoryString(vaName, 256);

		if (filename == null) return -1; // should check if this is coff file?

		String[] args = new String[argc];
		byte[] buffer = new byte[4]; // for reading va to string

		for (int i = 0; i < argc; i++) {
			int bytesRead = readVirtualMemory(argv + (4 * i), buffer);
			if (bytesRead < 4) return -1;

			int vaArg = Lib.bytesToInt(buffer, 0);
			String arg = readVirtualMemoryString(vaArg, 256);

			if (arg == null) return -1;
			args[i] = arg;
		}

		UserProcess childProcess = new VMProcess();
		childProcess.parentProcess = this;
		int childPID = childProcess.PID;

		children.put(childPID, childProcess);

		boolean childIsRun = childProcess.execute(filename, args);
		if (childIsRun) return childPID;

		return -1;
	}

	private int handleJoin(int processID, int status_addr) {

		if(!children.containsKey(processID)){return -1;}

		if(status_addr < 0 || Processor.pageFromAddress(status_addr) >= pageTable.length){return -1;}

		UserProcess childProcess = children.get(processID);

		if(childProcess.thread == null){ return 1;}

		childProcess.thread.join();

		children.remove(processID);
		childProcess.parentProcess = null;


		if(!childrenExitStatus.containsKey(processID)){
			Lib.debug(dbgProcess, "join unhandled");
			return 0;
		}
		
		//lock?
		int childExitStatus = childrenExitStatus.get(processID);

		byte[] childExitStatusBytes = Lib.bytesFromInt(childExitStatus);
		int bytesWritten = writeVirtualMemory(status_addr, childExitStatusBytes);

		return 1;
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallExit:
			return handleExit(a0);
		case syscallCreate:
			return handleCreate(a0);
		case syscallOpen:
			return handleOpen(a0);
		case syscallRead:
			return handleRead(a0, a1, a2);
		case syscallWrite:
			return handleWrite(a0, a1, a2);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);
		case syscallExec:
			return handleExec(a0, a1, a2);
		case syscallJoin:
			return handleJoin(a0, a1);

		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
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
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
					
			
			// Lib.assertNotReached("Unexpected exception");

			unloadSections();

			for (int i = 0; i < 16; i++) {
				if (fileTable[i] == null) continue;
				fileTable[i].close();
				fileTable[i] = null;
			}
			fileTable = null;

			for (Map.Entry<Integer,UserProcess> e : children.entrySet()) {
				e.getValue().parentProcess = null;
			}
			children.clear();

			if(PID == 0){
				Kernel.kernel.terminate();
			}
			else{
				thread.finish();
			}
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	/** The thread that executes the user-level program. */
        protected UThread thread;
    
	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private OpenFile[] fileTable; //fileTable[0] stdin, fileTable[1] stdout

	//public int exitStatus;

	//public int parentPID;

	public int PID;

	private UserProcess parentProcess;

	private HashMap<Integer, UserProcess> children;

	private HashMap<Integer,Integer> childrenExitStatus;
}
