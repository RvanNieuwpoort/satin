package ibis.satin.impl;

import ibis.ipl.IbisIdentifier;
import ibis.satin.impl.aborts.AbortException;
import ibis.satin.impl.aborts.Aborts;
import ibis.satin.impl.communication.Communication;
import ibis.satin.impl.loadBalancing.ClusterAwareRandomWorkStealing;
import ibis.satin.impl.loadBalancing.LoadBalancing;
import ibis.satin.impl.loadBalancing.LoadBalancingAlgorithm;
import ibis.satin.impl.loadBalancing.MasterWorker;
import ibis.satin.impl.loadBalancing.RandomWorkStealing;
import ibis.satin.impl.loadBalancing.VictimTable;
import ibis.satin.impl.spawnSync.DoubleEndedQueue;
import ibis.satin.impl.spawnSync.IRStack;
import ibis.satin.impl.spawnSync.InvocationRecord;
import ibis.satin.impl.spawnSync.ReturnRecord;

/**
 * 
 * @author daniela
 */
public class ClientThread extends Thread implements Config {

    public final Satin satin;

    public final int id;

    public final Communication comm;

    public final LoadBalancing lb;

    public final Aborts aborts;

    public final IbisIdentifier ident;

    /**
     * My scheduling algorithm.
     */
    public LoadBalancingAlgorithm algorithm;

    /**
     * The work queue. Contains jobs that were spawned, but not yet executed.
     */
    public final DoubleEndedQueue q;

    /**
     * The jobs that are currently being executed, they are on the Java stack.
     */
    public final IRStack onStack;

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

    public ClientThread(Satin satin, int i) {
        this.satin = satin;

        id = i;

        q = new DoubleEndedQueue(this);

        onStack = new IRStack(this);

        comm = satin.comm;

        ident = satin.ident;

        aborts = new Aborts(this);

        lb = new LoadBalancing(this);

        victims = satin.victims;

        createLoadBalancingAlgorithm();

    }

    @Override
    public void run() {
        stats.totalTimer.start();

        if (!satin.isMaster()) {
            while (!satin.exiting) {
                // steal and run jobs
                noWorkInQueue();
            }
        } else {
            while (!satin.masterThreadsExiting) {
                // steal and run jobs
                noWorkInQueue();
            }
        }

        algorithm.exit();

        stats.totalTimer.stop();

        stats.handleStealTimer = satin.stats.handleStealTimer;

        stats.fillInStats();
        // stats.myThreadStatistics(id);

        satin.totalStats.add(stats);

        synchronized (satin.waitForThreads) {
            satin.threadsEnded++;
            satin.waitForThreads.notifyAll();
        }

        if (commLogger.isDebugEnabled()) {
            commLogger.debug("SATIN '" + ident + "' - THREAD " + id
                    + ": exited");
        }
    }

    public void noWorkInQueue() {
        stats.localStealAttempts++;
        InvocationRecord r = satin.returnSharedMemoryJob(this.id);

        if (r == null) {
            r = algorithm.clientIteration();
        } else {
            stats.localStealSuccess++;
        }

        if (r != null && satin.so.executeGuard(r, id)) {
            callSatinFunction(r);
        } else {
            handleDelayedMessages();
        }
    }

    public void handleDelayedMessages() {
        // Handle messages received in upcalls.
        aborts.handleDelayedMessages();
        lb.handleDelayedMessages();
        satin.ft.handleDelayedMessages();
        satin.so.handleDelayedMessages();
    }

    public void callSatinFunction(InvocationRecord r) {
        if (Satin.ASSERTS) {
            callSatinFunctionPreAsserts(r);
        }

        if (r.getParent() != null && r.getParent().aborted) {
            r.decrSpawnCounter();
            return;
        }

        if (ftLogger.isDebugEnabled()) {
            if (r.isReDone()) {
                ftLogger.debug("Thread " + id + ": Redoing job " + r.getStamp());
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
            if (satin.stampToThreadIdMap != null
                    && satin.stampToThreadIdMap.containsKey(r.getStamp())) {
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

    private void callSatinLocalFunction(InvocationRecord r) {
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
            } else if (Satin.abortLogger.isDebugEnabled()) {
                Satin.abortLogger.debug("Thread " + id
                        + ": Caught abort exception " + t, t);
            }
        }

        r.decrSpawnCounter();

        if (!Satin.FT_NAIVE) {
            r.jobFinished();
        }
    }

    private void callSatinSharedFunction(InvocationRecord r) {
        if (stealLogger.isInfoEnabled()) {
            stealLogger.info("SATIN '" + ident + "' - THREAD " + id
                    + ": RUNNING SHARED CODE, STAMP = " + r.getStamp() + "!");
        }

        stats.jobsExecuted++;
        ReturnRecord rr = null;

        rr = r.runRemote();

        rr.setEek(r.eek);

        if (r.eek != null && Satin.stealLogger.isInfoEnabled()) {
            Satin.stealLogger.info("SATIN '" + ident + "' - THREAD " + id
                    + ": RUNNING SHARED CODE GAVE EXCEPTION: " + r.eek, r.eek);
        } else {
            Satin.stealLogger.info("SATIN '" + ident + "' - THREAD " + id
                    + ": RUNNING SHARED CODE DONE!");
        }

        // send wrapper back to the owner thread, but on the same machine
        if (!r.aborted) {
            lb.handleSharedResult(r, rr);

            if (Satin.stealLogger.isInfoEnabled()) {
                Satin.stealLogger.info("SATIN '" + ident + "' - THREAD '" + id
                        + ": SHARED CODE SEND RESULT DONE!");
            }
        } else {
            r.decrSpawnCounter();
            if (Satin.stealLogger.isInfoEnabled()) {
                Satin.stealLogger.info("SATIN '" + ident + "' - THREAD " + id
                        + ": SHARED CODE WAS ABORTED! Exception = " + r.eek);
            }
        }
    }

    private void callSatinRemoteFunction(InvocationRecord r) {
        if (stealLogger.isInfoEnabled()) {
            stealLogger.info("SATIN '" + ident + "' - THREAD " + id
                    + "': RUNNING REMOTE CODE, STAMP = " + r.getStamp() + "!");
        }
        ReturnRecord rr = null;
        stats.jobsExecuted++;

        rr = r.runRemote();

        rr.setEek(r.eek);

        if (r.eek != null && Satin.stealLogger.isInfoEnabled()) {
            Satin.stealLogger.info("SATIN '" + ident + "' - THREAD " + id
                    + "': RUNNING REMOTE CODE GAVE EXCEPTION: " + r.eek, r.eek);
        } else {
            Satin.stealLogger.info("SATIN '" + ident + "' - THREAD " + id
                    + "': RUNNING REMOTE CODE DONE!");
        }

        // send wrapper back to the owner
        if (!r.aborted) {
            lb.sendResult(r, rr);

            if (Satin.stealLogger.isInfoEnabled()) {
                Satin.stealLogger.info("SATIN '" + ident + "' - THREAD " + id
                        + "': REMOTE CODE SEND RESULT DONE!");
            }
        } else {
            if (Satin.stealLogger.isInfoEnabled()) {
                Satin.stealLogger.info("SATIN '" + ident + "' - THREAD " + id
                        + "': REMOTE CODE WAS ABORTED! Exception = " + r.eek);
            }
        }
    }

    public void assertFailed(String reason, Throwable t) {
        if (reason != null) {
            Satin.mainLogger.error("SATIN '" + ident + "' - THREAD " + id
                    + "': ASSERT FAILED: " + reason, t);
        } else {
            Satin.mainLogger.error("SATIN '" + ident + "' - THREAD " + id
                    + "': ASSERT FAILED: ", t);
        }

        throw new Error(reason, t);
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
            assertFailed("satin_algorithm " + alg + "' unknown",
                    new Exception());
        }

        commLogger.info("SATIN '" + ident + "' - THREAD " + id
                + "': using algorithm '" + alg);
    }
}
