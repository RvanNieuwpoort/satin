/* $Id: Satin.java 3687 2006-04-26 09:39:58Z rob $ */
package ibis.satin.impl;

import ibis.io.DeepCopy;
import ibis.ipl.IbisIdentifier;
import ibis.satin.impl.aborts.AbortException;
import ibis.satin.impl.aborts.Aborts;
import ibis.satin.impl.communication.Communication;
import ibis.satin.impl.communication.Protocol;
import ibis.satin.impl.faultTolerance.FaultTolerance;
import ibis.satin.impl.loadBalancing.ClusterAwareRandomWorkStealing;
import ibis.satin.impl.loadBalancing.LoadBalancing;
import ibis.satin.impl.loadBalancing.LoadBalancingAlgorithm;
import ibis.satin.impl.loadBalancing.MasterWorker;
import ibis.satin.impl.loadBalancing.RandomWorkStealing;
import ibis.satin.impl.loadBalancing.VictimTable;
import ibis.satin.impl.sharedObjects.SOInvocationRecord;
import ibis.satin.impl.sharedObjects.SharedObjects;
import ibis.satin.impl.spawnSync.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Satin implements Config {

    //Daniela:
    public ClientThread[] clientThreads;
    
    public final Map<Stamp, Integer> stampToThreadIdMap;
    
    public final Map<IbisIdentifier, List<Integer>> waitingStealMap;
    
    public final Map<String, ClientThread> threadIdToThreadMap;
    
    public final Object waitForThreads = new Object();
    
    public volatile int threadsEnded = 0;
    
    public volatile boolean masterThreadsExiting = false;
    // end
    
    private static final int SUGGESTED_QUEUE_SIZE = 1000;
    
    public static final boolean GLOBAL_PAUSE_RESUME = false;
    
    public static Satin thisSatin;
    
    public final Communication comm;
    
    public final LoadBalancing lb;
    
    public final FaultTolerance ft;
    
    public final SharedObjects so;
    
    public final Aborts aborts;
    
    public final IbisIdentifier ident; // this ibis
    /**
     * Am I the root (the one running main)?
     */
    private boolean master = false;
    /**
     * The ibis identifier for the master (the one running main).
     */
    private IbisIdentifier masterIdent;
    /**
     * Am I the cluster coordinator?
     */
    public boolean clusterCoordinator;
    /**
     * My scheduling algorithm.
     */
    public LoadBalancingAlgorithm algorithm;
    /**
     * Set to true if we need to exit for some reason.
     */
    public volatile boolean exiting = false;
    /**
     * The work queue. Contains jobs that were spawned, but not yet executed.
     */
    public final DoubleEndedQueue q;
    /**
     * This vector contains all jobs that were stolen from me. Used to locate
     * the invocation record corresponding to the result of a remote job.
     */
    public final IRVector outstandingJobs;
    /**
     * The jobs that are currently being executed, they are on the Java stack.
     */
    public final IRStack onStack;
    
    public Statistics totalStats;
    
    public Statistics stats = new Statistics();
    /**
     * The invocation record that is the parent of the current job.
     */
    public InvocationRecord parent;
    
    public volatile boolean currentVictimCrashed;
    /**
     * All victims, myself NOT included. The elements are Victims.
     */
    public final VictimTable victims;
    
    /**
     * Used for fault tolerance. All ibises that once took part in the
     * computation, but then crashed. Assumption: ibis identifiers are unique in
     * time; the same ibis cannot crash and join the computation again.
     */
    public final List<IbisIdentifier> deadIbises = new LinkedList<IbisIdentifier>();

    static {
        properties.checkProperties(PROPERTY_PREFIX, sysprops, null, true);
    }

    /**
     * Creates a Satin instance and also an Ibis instance to run Satin on. This
     * constructor gets called by the rewritten main() from the application, and
     * the argument array from main is passed to this constructor. Which ibis is
     * chosen depends, a.o., on these arguments.
     */
    public Satin() {
        if (thisSatin != null) {
            throw new Error(
                    "multiple satin instances are currently not supported");
        }
        
        thisSatin = this;

        q = new DoubleEndedQueue(this);

        outstandingJobs = new IRVector(this);
        onStack = new IRStack(this);

        ft = new FaultTolerance(this); // this must be first, it handles registry upcalls 
        comm = new Communication(this); // creates ibis
        ident = comm.ibis.identifier();
        ft.electClusterCoordinator(); // need ibis for this
        victims = new VictimTable(this); // need ibis for this

        lb = new LoadBalancing(this);
        so = new SharedObjects(this);
        aborts = new Aborts(this);

        // elect the master
        setMaster(comm.electMaster());

        createLoadBalancingAlgorithm();

        if (DUMP) {
            DumpThread dumpThread = new DumpThread(this);
            Runtime.getRuntime().addShutdownHook(dumpThread);
        }

        comm.enableConnections();

        // this opens the world, other ibises might join from this point
        // we need the master to be set before this call
        ft.init();

        //Daniela:
        //Maps r.getStamp -> threadId: from which thread was the job stolen
        stampToThreadIdMap = new HashMap<Stamp, Integer>();
        
        //Maps ibisIdent -> List<threadId>: when a thread does a steal,
        //it first registers in this map. This way the Satin's load balancer will know to
        //which thread to give the STEAL_REPLY received
        waitingStealMap = new HashMap<IbisIdentifier, List<Integer>>();
        
        //Maps a threadId to a ClientThread reference
        threadIdToThreadMap = new HashMap<String, ClientThread>();

        // Initialize the ClientThreads
        if (master) {
            clientThreads = new ClientThread[NO_THREADS - 1];
            for (int i = 0; i < NO_THREADS - 1; i++) {
                clientThreads[i] = new ClientThread(thisSatin, i);
                clientThreads[i].setName("thread" + i);
                threadIdToThreadMap.put(clientThreads[i].getName(), clientThreads[i]);
            }
        } else {
            clientThreads = new ClientThread[NO_THREADS];
            for (int i = 0; i < NO_THREADS; i++) {
                clientThreads[i] = new ClientThread(thisSatin, i);
                clientThreads[i].setName("thread" + i);
                threadIdToThreadMap.put(clientThreads[i].getName(), clientThreads[i]);
            }
        }

        // Only the master starts its ClientThreads at the end of the Satin
        // constructor. The clients will start their threads in the client() method.
        if (master) {
            for (Thread clientThread : clientThreads) {
                clientThread.start();
            }
        }

        stats.totalTimer.start();
    }

    /**
     * Called at the end of the rewritten "main", to do a synchronized exit.
     */
    public void exit() {
        exit(0);
    }

    private void exit(int status) {

        stats.totalTimer.stop();

        // the master threads need a different flag to exit.
        masterThreadsExiting = true;

        // witing for the threads to end
        synchronized (waitForThreads) {
            while (threadsEnded < clientThreads.length) {
                try {
                    waitForThreads.wait();
                } catch (InterruptedException ex) {
                    Logger.getLogger(Satin.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        
        if (STATS && DETAILED_STATS) {
            stats.printDetailedStats(ident);
            try {
                comm.ibis.setManagementProperty("statistics", "");
            } catch (Throwable e) {
                // ignored
            }
        }

        // Do not accept new connections and joins.
        comm.disableUpcallsForExit();
        
        if (master) {
            synchronized (this) {
                exiting = true;
                notifyAll();
            }
            comm.bcastMessage(Protocol.EXIT);
            comm.waitForExitReplies();

            // OK, we have got the ack from everybody, now we know that there will be no 
            // further communication between nodes. Broadcast this again.
            comm.bcastMessage(Protocol.EXIT_STAGE2);
        } else {
            comm.sendExitAck();
            comm.waitForExitStageTwo();
        }
        
        // OK, we have got the ack from everybody, 
        // now we know that there will be no further communication between nodes.

        algorithm.exit(); // give the algorithm time to clean up
        
        int size;
        synchronized (this) {
            size = victims.size() + 1;
        }

        if (master && STATS) {
            // add my own stats
            stats.fillInStats();
            totalStats.add(stats);

            totalStats.printStats(size, stats.totalTimer.totalTimeVal());
        }
        
        so.exit();
        comm.closeSendPorts();
        comm.closeReceivePort();

        comm.end();

        ft.end();

        if (commLogger.isDebugEnabled()) {
            commLogger.debug("SATIN '" + ident + "': exited");
        }

        // Do a gc, and run the finalizers. Useful for printing statistics in
        // Satin applications.
        // The app should register a shutdownhook. --Rob
        System.gc();
        System.runFinalization();

        if (status != 0) {
            System.exit(status);
        }
    }

    /**
     * Called at the end of the rewritten main in case the original main threw
     * an exception.
     */
    public void exit(Throwable e) {
        System.err.println("Exception in main: " + e);
        e.printStackTrace(System.err);
        exit(1);
    }

    /**
     * Spawns the method invocation as described by the specified invocation
     * record. The invocation record is added to the job queue maintained by
     * this Satin.
     *
     * @param r the invocation record specifying the spawned invocation.
     */
    public void spawn(InvocationRecord r) {
        ClientThread t = getThread();
        if (t == null) {
            stats.spawns++;

            // If my parent is aborted, so am I.
            if (parent != null && parent.aborted) {
                return;
            }

            // Maybe this job is already in the global result table.
            // If so, we don't have to do it again.
            // Ouch, this cannot be the right place: there is no stamp allocated
            // yet for the job! --Ceriel
            // Fixed by first calling r.spawn.
            r.spawn(ident, parent);
            if (ft.checkForDuplicateWork(parent, r)) {
                return;
            }

            //stats.addToHeadTimer.start();
            q.addToHead(r);
            //stats.addToHeadTimer.stop();
            algorithm.jobAdded();
        } else {
            t.stats.spawns++;

            // If my parent is aborted, so am I.
            if (t.parent != null && t.parent.aborted) {
                return;
            }

            // Maybe this job is already in the global result table.
            // If so, we don't have to do it again.
            // Ouch, this cannot be the right place: there is no stamp allocated
            // yet for the job! --Ceriel
            // Fixed by first calling r.spawn.
            r.spawn(ident, t.parent);
            if (ft.checkForDuplicateWork(t.parent, r)) {
                return;
            }

            //t.stats.addToHeadTimer.start();
            t.q.addToHead(r);
            //t.stats.addToHeadTimer.stop();
            t.algorithm.jobAdded();
        }

    }

    /**
     * Waits for the jobs as specified by the spawncounter given, but meanwhile
     * execute jobs from the end of the jobqueue (or rather, the head of the job
     * queue, where new jobs are added).
     *
     * @param s the spawncounter.
     */
    public void sync(SpawnCounter s) {
        ClientThread thread = getThread();

        if (thread == null) {
            stats.syncs++;
        } else {
            thread.stats.syncs++;
        }

        if (s.getValue() == 0) { // A sync without spawns is a poll.
            if (thread == null) {
                handleDelayedMessages();
            } else {
                thread.handleDelayedMessages();
            }
        } else {
            while (s.getValue() > 0) {
                if (thread == null) {
                    InvocationRecord ir = q.getFromHead(); // Try the local queue  

                    if (ir != null) {
                        callSatinFunction(ir);
                    } else {
                        Thread.yield();
                        noWorkInQueue();
                    }

                    // Wait for abort sender. Otherwise, if the current job starts
                    // spawning again, jobs may be aborted that are spawned after
                    // this sync!
                    aborts.waitForAborts();
                } else {
                    InvocationRecord r = thread.q.getFromHead();

                    if (r != null) {
                        thread.callSatinFunction(r);
                    } else {
                        Thread.yield();
                        thread.noWorkInQueue();
                    }

                    // Wait for abort sender. Otherwise, if the current job starts
                    // spawning again, jobs may be aborted that are spawned after
                    // this sync!
                    thread.aborts.waitForAborts();
                }
            }
        }
    }

    private void noWorkInQueue() {
        stats.localStealAttempts++;
        InvocationRecord r = returnSharedMemoryJob(-1);

        if (r == null) {
            r = algorithm.clientIteration();
        } else {
            stats.localStealSuccess++;
        }

        if (r != null && so.executeGuard(r, -1)) {
            callSatinFunction(r);
        } else {
            handleDelayedMessages();
        }
    }

    /**
     * Implements the main client loop: steal jobs and execute them.
     */
    public void client() {
        if (mainLogger.isDebugEnabled()) {
            mainLogger.debug("SATIN '" + ident + "': starting ClientThreads!");
        }

        for (Thread clientThread : clientThreads) {
            clientThread.start();
        }

    }

    /**
     * Daniela:
     * goes through each q until finds a non-null job.
     */
    public InvocationRecord returnJob() {
        InvocationRecord ir = null;
        int threadId = -1;

        if (master) {
            ir = q.getFromTail();
        }

        if (ir == null) {
            if (clientThreads == null) {
                return null;
            }

            for (ClientThread ct : clientThreads) {
                if (ct == null) {
                    return null;
                }
                ir = ct.q.getFromTail();
                threadId = ct.id;
                if (ir != null) {
                    break;
                }
            }
        }

        if (ir != null) {
            synchronized (stampToThreadIdMap) {
                stampToThreadIdMap.put(ir.getStamp(), threadId);
            }
        }

        return ir;
    }

    /**
     * Daniela: steal from the same machine.
     * Thread-safe.
     *
     * @param t
     * @return
     */
    public InvocationRecord returnSharedMemoryJob(int stealingThreadId) {
        InvocationRecord ir = null;
        int threadId = -1;

        if (master && stealingThreadId != -1) {
            ir = q.getFromTail();
        }

        if (ir == null) {
            for (ClientThread ct : clientThreads) {
                if (ct == null) {
                    return null;
                }
                if (ct.id != stealingThreadId) {
                    ir = ct.q.getFromTail();
                    threadId = ct.id;

                    if (ir != null) {
                        break;
                    }
                }
            }
        }

        if (ir != null) {
            synchronized (stampToThreadIdMap) {
                stampToThreadIdMap.put(ir.getStamp(), threadId);
            }

            ir.setStealer(ident);

            // store the job in the outstanding list
            synchronized (this) {
                outstandingJobs.add(ir);
            }
        }

        return ir;
    }

    public void handleDelayedMessages() {
        // Handle messages received in upcalls.
        aborts.handleDelayedMessages();
        lb.handleDelayedMessages();
        ft.handleDelayedMessages();
        so.handleDelayedMessages();
    }

    /**
     * Aborts the spawns that are the result of the specified invocation record.
     * The invocation record of the invocation actually throwing the exception
     * is also specified, but it is valid only for clones with inlets.
     *
     * @param outstandingSpawns parent of spawns that need to be aborted.
     * @param exceptionThrower invocation throwing the exception.
     */
    public synchronized void abort(InvocationRecord outstandingSpawns,
            InvocationRecord exceptionThrower) {
        // We do not need to set outstanding Jobs in the parent frame to null,
        // it is just used for assigning results.
        // get the lock, so no-one can steal jobs now, and no-one can change my
        // tables.

        if (abortLogger.isDebugEnabled()) {
            abortLogger.debug("ABORT called: outstandingSpanws = "
                    + outstandingSpawns
                    + ", exceptionThrower = " + exceptionThrower);
        }

        ClientThread t = getThread();

        if (t == null) {
            stats.abortsDone++;

            if (exceptionThrower != null) { // can be null if root does an abort.
                // kill all children of the parent of the thrower.
                aborts.killChildrenOf(exceptionThrower.getParentStamp());
            }

            // now kill mine
            if (outstandingSpawns != null) {
                aborts.killChildrenOf(outstandingSpawns.getParentStamp());
            }

            // now inform the other threads also...
            for (ClientThread ct : clientThreads) {
                if (exceptionThrower != null) {
                    ct.aborts.addToAbortList(exceptionThrower.getParentStamp());
                }
                if (outstandingSpawns != null) {
                    ct.aborts.addToAbortList(outstandingSpawns.getParentStamp());
                }
            }
        } else {
            t.stats.abortsDone++;

            if (exceptionThrower != null) { // can be null if root does an abort.
                // kill all children of the parent of the thrower.
                t.aborts.killChildrenOf(exceptionThrower.getParentStamp());
            }

            // now kill mine
            if (outstandingSpawns != null) {
                t.aborts.killChildrenOf(outstandingSpawns.getParentStamp());
            }

            // now inform the other threads also.
            if (master) {
                if (exceptionThrower != null) {
                    aborts.addToAbortList(exceptionThrower.getParentStamp());
                }
                if (outstandingSpawns != null) {
                    aborts.addToAbortList(outstandingSpawns.getParentStamp());
                }
            }

            for (ClientThread ct : clientThreads) {
                if (ct.id != t.id) {
                    if (exceptionThrower != null) {
                        ct.aborts.addToAbortList(exceptionThrower.getParentStamp());
                    }
                    if (outstandingSpawns != null) {
                        ct.aborts.addToAbortList(outstandingSpawns.getParentStamp());
                    }
                }
            }
        }
    }

    /**
     * Pause Satin operation. This method can optionally be called before a
     * large sequential part in a program. This will temporarily pause Satin's
     * internal load distribution strategies to avoid communication overhead
     * during sequential code.
     */
    public static void pause() {
        if (thisSatin == null) {
            return;
        }

        if (GLOBAL_PAUSE_RESUME) {
            thisSatin.comm.pause();
        } else {
            thisSatin.comm.receivePort.disableMessageUpcalls();
        }
    }

    /**
     * Resume Satin operation. This method can optionally be called after a
     * large sequential part in a program.
     */
    public static void resume() {
        if (thisSatin == null) {
            return;
        }

        if (GLOBAL_PAUSE_RESUME) {
            thisSatin.comm.resume();
        } else {
            thisSatin.comm.receivePort.enableMessageUpcalls();
        }
    }

    /**
     * Returns whether it might be useful to spawn more methods. If there is
     * enough work in the system to keep all processors busy, this method
     * returns false.
     */
    public static boolean needMoreJobs() {
        // This can happen in sequential programs.
        if (thisSatin == null) {
            return false;
        }
        synchronized (thisSatin) {
            int size = thisSatin.victims.size();
            if (size == 0 && CLOSED) {
                // No need to spawn work on one machine.
                return false;
            }

            if (thisSatin.q.size() / (size + 1) > SUGGESTED_QUEUE_SIZE) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns whether the current method was generated by the machine it is
     * running on. Methods can be distributed to remote machines by the Satin
     * runtime system, in which case this method returns false.
     */
    public static boolean localJob() {
        if (thisSatin == null) {
            return true; // sequential run
        }

        if (thisSatin.parent == null) {
            return true; // root job
        }

        return thisSatin.parent.getOwner().equals(thisSatin.ident);
    }

    public ClientThread getThread() {
        String threadName = Thread.currentThread().getName();
        ClientThread t = (ClientThread) threadIdToThreadMap.get(threadName);

        return t;
    }

    /**
     * Returns true if this is the instance that is running main().
     *
     * @return
     * <code>true</code> if this is the instance running main().
     */
    public boolean isMaster() {
        if (ASSERTS && masterIdent == null) {
            throw new Error("asked for master before he was elected");
        }
        return master;
    }

    public void setMaster(IbisIdentifier newMaster) {
        masterIdent = newMaster;

        if (masterIdent.equals(ident)) {
            /*
             * I am the master.
             */
            commLogger.info(
                    "SATIN '" + ident
                    + "': init ibis: I am the master");
            master = true;
        } else {
            commLogger.info("SATIN '" + ident
                    + "': init ibis I am slave");
        }

        if (STATS && master) {
            totalStats = new Statistics();
        }
    }

    // called from generated code
    public void broadcastSOInvocation(SOInvocationRecord r) {
        so.broadcastSOInvocation(r);
    }

    public static void assertLocked(Object o) {
        if (!ASSERTS) {
            return;
        }

        if (!trylock(o)) {
            assertFailedStatic("AssertLocked failed", new Exception());
        }
    }

    public IbisIdentifier getMasterIdent() {
        if (ASSERTS && masterIdent == null) {
            throw new Error("asked for master before he was elected");
        }
        return masterIdent;
    }

    private void callSatinFunction(InvocationRecord r) {
        if (ASSERTS) {
            callSatinFunctionPreAsserts(r);
        }

        if (r.getParent() != null && r.getParent().aborted) {
            r.decrSpawnCounter();
            return;
        }

        if (ftLogger.isDebugEnabled()) {
            if (r.isReDone()) {
                ftLogger.debug("Redoing job " + r.getStamp());
            }
        }

        InvocationRecord oldParent = parent;

        onStack.push(r);
        parent = r;

        // We MUST make sure that steals don't overtake aborts.
        // Therefore, we must poll for messages here.
        handleDelayedMessages();

        if (r.getOwner().equals(ident)) {
            // maybe r was stolen from this machine, but from another thread.
            if (stampToThreadIdMap != null &&
                    stampToThreadIdMap.containsKey(r.getStamp())) {
                callSatinSharedFunction(r);
            } else {
                callSatinLocalFunction(r);
            }
        } else { // we are running a job that I stole from another machine
            callSatinRemoteFunction(r);
        }

        // restore this, there may be more spawns afterwards...
        parent = oldParent;
        onStack.pop();
    }

    private void callSatinSharedFunction(InvocationRecord r) {
        if (stealLogger.isInfoEnabled()) {
            stealLogger.info("SATIN '" + ident
                    + "': RUNNING SHARED CODE, STAMP = " + r.getStamp() + "!");
        }

        stats.jobsExecuted++;
        ReturnRecord rr = null;

        rr = r.runRemote();

        rr.setEek(r.eek);

        if (r.eek != null && Satin.stealLogger.isInfoEnabled()) {
            Satin.stealLogger.info("SATIN '" + ident
                    + "': RUNNING SHARED CODE GAVE EXCEPTION: " + r.eek, r.eek);
        } else {
            Satin.stealLogger.info("SATIN '" + ident + "': RUNNING SHARED CODE DONE!");
        }

        // assign the result to the owner on the same machine
        if (!r.aborted) {
            lb.handleSharedResult(r, rr);

            if (Satin.stealLogger.isInfoEnabled()) {
                Satin.stealLogger.info("SATIN '" + ident
                        + "': SHARED CODE SEND RESULT DONE!");
            }
        } else {
            r.decrSpawnCounter();
            if (Satin.stealLogger.isInfoEnabled()) {
                Satin.stealLogger.info("SATIN '" + ident
                        + "': SHARED CODE WAS ABORTED! Exception = " + r.eek);
            }
        }
    }

    private void callSatinFunctionPreAsserts(InvocationRecord r) {
        if (r == null) {
            assertFailed("r == null in callSatinFunc", new Exception());
        }

        if (r.aborted) {
            assertFailed("spawning aborted job!", new Exception());
        }

        if (r.getOwner() == null) {
            assertFailed("r.owner = null in callSatinFunc, r = " + r,
                    new Exception());
        }
    }

    public void callSatinLocalFunction(InvocationRecord r) {
        stats.jobsExecuted++;
        try {
            r.runLocal();
        } catch (Throwable t) {
            // This can only happen if an inlet has thrown an
            // exception, or if there was no try-catch block around
            // the spawn (i.e. no inlet).
            // The semantics of this: all work is aborted,
            // and the exception is passed on to the spawner.
            // The parent is aborted, it must handle the exception.
            // Note: this can now also happen on an abort. Check for
            // the AbortException!
            if (!(t instanceof AbortException)) {
                r.eek = t;
                aborts.handleInlet(r);
            } else if (abortLogger.isDebugEnabled()) {
                abortLogger.debug("Caught abort exception " + t, t);
            }
        }

        r.decrSpawnCounter();

        if (!FT_NAIVE) {
            r.jobFinished();
        }
    }

    public void callSatinRemoteFunction(InvocationRecord r) {
        if (stealLogger.isInfoEnabled()) {
            stealLogger.info("SATIN '" + ident
                    + "': RUNNING REMOTE CODE, STAMP = " + r.getStamp() + "!");
        }
        ReturnRecord rr = null;
        stats.jobsExecuted++;

        rr = r.runRemote();
        rr.setEek(r.eek);

        if (r.eek != null && stealLogger.isInfoEnabled()) {
            stealLogger.info("SATIN '" + ident
                    + "': RUNNING REMOTE CODE GAVE EXCEPTION: " + r.eek, r.eek);
        } else {
            stealLogger.info("SATIN '" + ident + "': RUNNING REMOTE CODE DONE!");
        }

        // send wrapper back to the owner
        if (!r.aborted) {
            lb.sendResult(r, rr);
            if (stealLogger.isInfoEnabled()) {
                stealLogger.info("SATIN '" + ident
                        + "': REMOTE CODE SEND RESULT DONE!");
            }
        } else {
            if (stealLogger.isInfoEnabled()) {
                stealLogger.info("SATIN '" + ident
                        + "': REMOTE CODE WAS ABORTED! Exception = " + r.eek);
            }
        }
    }

    private void createLoadBalancingAlgorithm() {
        String alg = SUPPLIED_ALG;

        if (SUPPLIED_ALG == null) {
            alg = "CRS";
        }

        if (alg.equals("RS")) {
            algorithm = new RandomWorkStealing(this);
        } else if (alg.equals("CRS")) {
            algorithm = new ClusterAwareRandomWorkStealing(this);
        } else if (alg.equals("MW")) {
            algorithm = new MasterWorker(this);
        } else {
            assertFailed("satin_algorithm " + alg + "' unknown", new Exception());
        }

        commLogger.info("SATIN '" + "- " + "': using algorithm '" + alg);
    }

    private static boolean trylock(Object o) {
        try {
            o.notifyAll();
        } catch (IllegalMonitorStateException e) {
            return false;
        }

        return true;
    }

    public static void assertFailedStatic(String reason, Throwable t) {
        if (reason != null) {
            mainLogger.error("ASSERT FAILED: " + reason, t);
        } else {
            mainLogger.error("ASSERT FAILED: ", t);
        }

        throw new Error(reason, t);
    }

    public void assertFailed(String reason, Throwable t) {
        if (reason != null) {
            mainLogger.error("SATIN '" + ident
                    + "': ASSERT FAILED: " + reason, t);
        } else {
            mainLogger.error("SATIN '" + ident
                    + "': ASSERT FAILED: ", t);
        }

        throw new Error(reason, t);
    }

    public static synchronized void addInterClusterStats(long cnt) {
        thisSatin.stats.interClusterMessages++;
        thisSatin.stats.interClusterBytes += cnt;
    }

    public static synchronized void addIntraClusterStats(long cnt) {
        thisSatin.stats.intraClusterMessages++;
        thisSatin.stats.intraClusterBytes += cnt;
    }

    public static java.io.Serializable deepCopy(java.io.Serializable o) {
        return DeepCopy.deepCopy(o);
    }

    /**
     * @return Returns the current Satin instance.
     */
    public final static Satin getSatin() {
        return thisSatin;
    }

    /**
     * Returns the parent of the current job, used in generated code.
     */
    public InvocationRecord getParent() {
        ClientThread t = getThread();

        if (t == null) {
            return parent;
        } else {
            return t.parent;
        }
    }
}
