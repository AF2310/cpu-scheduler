package se.lnu.os.ht25.a1.required;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import se.lnu.os.ht25.a1.provided.Reporter;
import se.lnu.os.ht25.a1.provided.Scheduler;
import se.lnu.os.ht25.a1.provided.data.ProcessInformation;

public class PrioritySchedulerImpl implements Scheduler {

	
	private final Reporter reporter;
	private final long startingTime;

	private final Deque<ProcessWrapper> queue0;  // Priority 0
	private final Deque<ProcessWrapper> queue1;  // Priority 1
	private final Deque<ProcessWrapper> queue2;	// Priority 2

	private Thread cpuThread;
	private ProcessWrapper currentProcess;
	private boolean schedulerRunning;
	private final Object lock = new Object(); 

	private PrioritySchedulerImpl(Reporter r) {
		this.reporter = r;
		startingTime = System.currentTimeMillis();

		// que initilization

		this.queue0 = new ArrayDeque<>();
		this.queue1 = new ArrayDeque<>();
		this.queue2 = new ArrayDeque<>();
		this.schedulerRunning = true;
	}

	// Factory method to create an instance of the scheduler
	public static Scheduler createInstance(Reporter reporter) {
		Scheduler s = (new PrioritySchedulerImpl(reporter)).initialize();
		
		return s;
	}

	// Fetches the report of all processes managed by the scheduler
	@Override
	public List<ProcessInformation> getProcessesReport() {
		return reporter.getProcessesReport();
	}

	private Scheduler initialize() {
		// TODO You have to write this method to initialize your Scheduler:
		// For instance, create the CPUthread, the ReporterManager thread, the necessary
		// queues lists/sets, etc.

		// Create the CPU thread
		cpuThread = new Thread(new Runnable() {
			@Override
      public void run() {
				// scheduling loop helper method
				schedulerLoop();
			}

		});
		// creates daemon thread to avoid specific error messages and starts thread
		cpuThread.setDaemon(true);
    cpuThread.start();
		return this;
	}



	/**
	 * Handles a new process to schedule from the user. When the user invokes it, a
	 * {@link ProcessInformation} object is created to record the process name,
	 * arrival time, and the length of the cpuBurst to schedule.
	 */
	@Override
	public void newProcess(String processName, int priority, double cpuBurstDuration) {
		// TODO You have to write this method.
		// Check if priority is correct
		if (priority < 0 || priority > 2) {
      throw new IllegalArgumentException("Priority must be 0, 1, or 2");
    }

		// synchronizes to ensure thread safety by using process wrapper
		synchronized (lock) {
			ProcessWrapper wrapper = new ProcessWrapper(
				processName, 
				priority, 
				cpuBurstDuration, 
				now()
			);

			// checks for appropriate que
			if (priority == 0) {
				queue0.addLast(wrapper);
			} else if (priority == 1) {
				queue1.addLast(wrapper);
			} else {
				queue2.addLast(wrapper);
			}
			
			// checks if current process needs to be preempted
			if (currentProcess != null) {
				if (priority < currentProcess.priority) {
					// interrupts thread
					cpuThread.interrupt();
				}
			}

			lock.notifyAll(); // notifies cpu thread
		}
		
	}

	private void schedulerLoop() {
		while (schedulerRunning) {
			try {
				ProcessWrapper nextProcess = getNextProcess();
				if (nextProcess != null) {
					executeProcess(nextProcess);

				}
			} catch (InterruptedException e) {

			}
		}
	}

	/**
	 * gets the next process to execute.
	 * Waits if no ques are empty and no processes.
	 * returns processes by priority.
	 */
	private ProcessWrapper getNextProcess() throws InterruptedException {
		synchronized (lock) {
			// loops until one que is not empty
			while (queue0.isEmpty() && queue1.isEmpty() && queue2.isEmpty()) {
				lock.wait();
			}

			// checks for available processes by priority que
			if (!queue0.isEmpty()) {
				return queue0.pollFirst();
			} else if (!queue1.isEmpty()) {

				return queue1.pollFirst();
			} else {
				return queue2.pollFirst();

			}
		}
	}

	// incomplete executje process method
	private void executeProcess(ProcessWrapper process) throws InterruptedException {
		// synchronizes to mark process as running 
		synchronized (lock) {
			currentProcess = process;
			if (process.isFirstRun) {
				// first run record when the process was first given
				process.processInfo.setCpuScheduledTime(now());
        process.isFirstRun = false;
			}
		}
		double startTime = now();

		try {
			// simulates cpu burst by sleeping the reaminder of the process time
			Thread.sleep((long)(process.remainingTime * 1000));

			synchronized (lock) {
				// if no interruption process finnished
				process.processInfo.setEndTime(now());
				try {
					reporter.addProcessReport(process.processInfo);
						
				} catch (InterruptedException e) {
				}
				
				
				currentProcess = null;
			}
				
		} catch (InterruptedException e) {
			//if process was pre empted calculate run time

			synchronized (lock){
				// calculate running time
				double elapsedTime = now() - startTime;

				// update remaining time
				process.remainingTime -= elapsedTime;

				if (process.remainingTime > 0.001) {
					// reque following FIFO structure bu using addfirst method

					if (process.priority == 0) {
						queue0.addFirst(process);
					} else if (process.priority == 1) {
						queue1.addFirst(process);

					} else {

						queue2.addFirst(process);

					}
				} else {
					 //the process finished in the interrupted window
					process.processInfo.setEndTime(now());
					try {
						reporter.addProcessReport(process.processInfo);
							
					} catch (InterruptedException ie) {
						// exit if already interrupted

					}

				}
				currentProcess = null;



			}
			throw e;
		}

		//releases synchronization for sleep
		//Thread.sleep((long)(process.remainingTime * 1000));

		// resets synchronization to update
		

		
	}

	/*
	 * This method may help you get the number of seconds since the execution
	 * started. Do not feel force to use it, only if you think that it helps your
	 * solution
	 */
	private double now() {
		return (System.currentTimeMillis() - startingTime) / 1000.0;
	}

	/**
	 * class that wraps ProcessInformation.
	 * tracks remaining time for processes.
	 */
	private static class ProcessWrapper {
		// The process information instance
		ProcessInformation processInfo;

		// time the process needs to run
		double remainingTime;

		// process priority different priority states ect
		int priority;
		// boolean to check wether its the first time process is running
		boolean isFirstRun = true;

		/**
		 * Creates adittional ProcessWrapper.
		 */
		ProcessWrapper(String name, int priority, double duration, double arrivalTime) {
			// Creates the object
			this.processInfo = ProcessInformation.createProcessInformation()
                .setProcessName(name)
                .setCpuBurstDuration(duration)
                .setArrivalTime(arrivalTime);
      this.remainingTime = duration; // starts with full duaration

      this.priority = priority; // stores priority
		}
	}
}
