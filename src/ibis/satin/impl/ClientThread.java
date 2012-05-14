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
import ibis.satin.impl.spawnSync.DoubleEndedQueue;
import ibis.satin.impl.spawnSync.IRStack;
import ibis.satin.impl.spawnSync.IRVector;
import ibis.satin.impl.spawnSync.InvocationRecord;
import ibis.satin.impl.spawnSync.ReturnRecord;
import ibis.satin.impl.spawnSync.SpawnCounter;
import java.util.Vector;

/**
 *
 * @author daniela
 */
public class ClientThread extends Thread implements Config {
    
    public static Satin satin;
    
    public final Communication comm;

    public final LoadBalancing lb;

    public final FaultTolerance ft;

    public final SharedObjects so;

    public final Aborts aborts;

    public final IbisIdentifier ident; // this ibis

    /** Am I the root (the one running main)? */
    private boolean master;

    /** The ibis identifier for the master (the one running main). */
    private IbisIdentifier masterIdent;

    /** Am I the cluster coordinator? */
    public boolean clusterCoordinator;

    /** My scheduling algorithm. */
    public LoadBalancingAlgorithm algorithm;

//    /** Set to true if we need to exit for some reason. */
//    public volatile boolean exiting;

    /** The work queue. Contains jobs that were spawned, but not yet executed. */
    public final DoubleEndedQueue q;

    /**
     * This vector contains all jobs that were stolen from me. 
     * Used to locate the invocation record corresponding to the result of a
     * remote job.
     */
    public final IRVector outstandingJobs;

    /** The jobs that are currently being executed, they are on the Java stack. */
    public final IRStack onStack;

    public Statistics stats = new Statistics();

    /** The invocation record that is the parent of the current job. */
    public InvocationRecord parent;

    public volatile boolean currentVictimCrashed;

    /** All victims, myself NOT included. The elements are Victims. */
    public final VictimTable victims;

    /**
     * Used for fault tolerance. All ibises that once took part in the
     * computation, but then crashed. Assumption: ibis identifiers are uniqe in
     * time; the same ibis cannot crash and join the computation again.
     */
    public final Vector<IbisIdentifier> deadIbises = new Vector<IbisIdentifier>();
    
    
    public ClientThread(Satin satin) {
        this.satin = satin;

        q = new DoubleEndedQueue(satin);

        outstandingJobs = new IRVector(satin);
        onStack = new IRStack(satin);

        // TODO: change this or not?! 1 ft per thread or per machine
        ft = new FaultTolerance(satin); // this must be first, it handles registry upcalls 
        
        // TODO: 1 comm/thread or 1 comm/machine
        comm = satin.comm;//new Communication(satin); // creates ibis
        
        // TODO: be careful: if 1 comm/machine, ident will have same value for all threads.
        ident = comm.ibis.identifier();
        ft.electClusterCoordinator(); // need ibis for this

        so = satin.so;
        aborts = satin.aborts;
        lb = satin.lb;
        victims = satin.victims;
        algorithm = satin.algorithm;
        
//        so = new SharedObjects(satin);
//        aborts = new Aborts(satin);
//        victims = new VictimTable(satin); // need ibis for this

//        lb = new LoadBalancing(satin);
//        createLoadBalancingAlgorithm();
        
//        // elect the master
//        setMaster(comm.electMaster());
//        comm.enableConnections();

        // this opens the world, other ibises might join from this point
        // we need the master to be set before this call
        ft.init(); 

        stats.totalTimer.start();
    }
    
    @Override
    public void run(){

        while (!satin.exiting) {
            // steal and run jobs
            System.out.println("Trying to get jobs...");
            noWorkInQueue();

            // Maybe the master crashed, and we were elected the new master.
            // trebuie sa ai mare grija: ca e 1 master per masina, asadar, toate
            // threadurile de pe master se considera master.
            if (master) return;
        }
    }
    
    private void noWorkInQueue() {
        System.out.println("algorithm is " + SUPPLIED_ALG);
        
        InvocationRecord r = algorithm.clientIteration();
        
        if (r == null) {
            System.out.println("r e null");
        } else {
            System.out.println("r nu e null");
        }
        if (r != null /* && so.executeGuard(r) */) {
            System.out.println("Got 1 job: " + r.toString());
            callSatinFunction(r);
        } else {
            handleDelayedMessages();
        }
    }
    
    public void handleDelayedMessages() {
        // Handle messages received in upcalls.
        aborts.handleDelayedMessages();
        lb.handleDelayedMessages();
        ft.handleDelayedMessages();
        so.handleDelayedMessages();
    }
    
    private void callSatinFunction(InvocationRecord r) {
        if (Satin.ASSERTS) callSatinFunctionPreAsserts(r);

        if (r.getParent() != null && r.getParent().aborted) {
            r.decrSpawnCounter();
            return;
        }

        /*if (ftLogger.isDebugEnabled()) {
            if (r.isReDone()) {
                ftLogger.debug("Redoing job " + r.getStamp());
            }
        }*/
    
        InvocationRecord oldParent = parent;
        onStack.push(r);
        parent = r;

        // We MUST make sure that steals don't overtake aborts.
        // Therefore, we must poll for messages here.
        handleDelayedMessages();

        if (r.getOwner().equals(ident)) {
            callSatinLocalFunction(r);
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
                Satin.abortLogger.debug("Caught abort exception " + t, t);
            }
        }

        r.decrSpawnCounter();

        if (!Satin.FT_NAIVE) r.jobFinished();
    }

    private void callSatinRemoteFunction(InvocationRecord r) {
        /*if (stealLogger.isInfoEnabled()) {
            stealLogger.info("SATIN '" + ident
                    + "': RUNNING REMOTE CODE, STAMP = " + r.getStamp() + "!");
        }*/
        ReturnRecord rr = null;
        stats.jobsExecuted++;
        rr = r.runRemote();
        rr.setEek(r.eek);

        if (r.eek != null && Satin.stealLogger.isInfoEnabled()) {
            Satin.stealLogger.info("SATIN '" + ident
                    + "': RUNNING REMOTE CODE GAVE EXCEPTION: " + r.eek, r.eek);
        } else {
            Satin.stealLogger
                .info("SATIN '" + ident + "': RUNNING REMOTE CODE DONE!");
        }

        // send wrapper back to the owner
        if (!r.aborted) {
            lb.sendResult(r, rr);
            if (Satin.stealLogger.isInfoEnabled()) {
                Satin.stealLogger.info("SATIN '" + ident
                        + "': REMOTE CODE SEND RESULT DONE!");
            }
        } else {
            if (Satin.stealLogger.isInfoEnabled()) {
                Satin.stealLogger.info("SATIN '" + ident
                        + "': REMOTE CODE WAS ABORTED! Exception = " + r.eek);
            }
        }
    }
    
    public void assertFailed(String reason, Throwable t) {
        if(reason != null) {
            Satin.mainLogger.error("SATIN '" + ident
                    + "': ASSERT FAILED: " + reason, t);
        } else {
            Satin.mainLogger.error("SATIN '" + ident
                    + "': ASSERT FAILED: ", t);
        }

        throw new Error(reason, t);
    }
    
}
