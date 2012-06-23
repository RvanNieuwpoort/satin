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
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author daniela
 */
public class ClientThread extends Thread implements Config {
    
    public Satin satin;

    public final int id;
    
    public final Communication comm;

    public final LoadBalancing lb;

    //public final FaultTolerance ft;

    //public final SharedObjects so;

    public final Aborts aborts;

    public final IbisIdentifier ident; // this ibis

    /** Am I the root (the one running main)? */
    //private boolean master;

    /** The ibis identifier for the master (the one running main). */
    //private IbisIdentifier masterIdent;

    /** Am I the cluster coordinator? */
    //public boolean clusterCoordinator;

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
     * computation, but then crashed. Assumption: ibis identifiers are unique in
     * time; the same ibis cannot crash and join the computation again.
     */
    public final Vector<IbisIdentifier> deadIbises = new Vector<IbisIdentifier>();
    
    
    public ClientThread(Satin satin, int i) {
        this.satin = satin;
        
        id = i;

        //q = new DoubleEndedQueue(satin);
        q = new DoubleEndedQueue(this);

        outstandingJobs = new IRVector(this);
        onStack = new IRStack(this);

        // TODO: change this or not?! 1 ft per thread or per machine
        //ft = new FaultTolerance(this); // this must be first, it handles registry upcalls 
        
        // TODO: 1 comm/thread or 1 comm/machine
        comm = satin.comm;//new Communication(satin); // creates ibis
        
        // TODO: be careful: if 1 comm/machine, ident will have same value for all threads.
        ident = satin.ident; //comm.ibis.identifier();
        
        
        // does every thread need to elect a cCoord?
        //ft.electClusterCoordinator(); // need ibis for this

        //so = new SharedObjects(this);
        aborts = new Aborts(this);
        lb = new LoadBalancing(this); //satin.lb;
        victims = satin.victims;
        createLoadBalancingAlgorithm(); //algorithm = satin.algorithm;
        
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
        //ft.init(); // TODO 

//        stats.totalTimer.start();
    }
    
    @Override
    public void run(){

        while (!satin.exiting) {
            // steal and run jobs
            // System.out.println("Thread " + id + ": Trying to get jobs...");
            noWorkInQueue();

            // Maybe the master crashed, and we were elected the new master.
            // trebuie sa ai mare grija: ca e 1 master per masina, asadar, toate
            // threadurile de pe master se considera master.
            if (satin.isMaster()) return;
        }
        
        System.out.println("exiting");

        // Hold thread alive:
        synchronized (satin.keepAlive) {
            while (!satin.endThread) {
                try {
                    satin.keepAlive.wait();
                } catch (InterruptedException ex) {
                    Logger.getLogger(ClientThread.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    public void noWorkInQueue() {
        InvocationRecord r = satin.returnSharedMemoryJob(this);

        if (r == null) {
            //System.out.println("Thread " + id + " trying to steal from remote machine.");
            r = algorithm.clientIteration();
        }
        
        if (r != null && satin.so.executeGuard(r)) {
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
        if (Satin.ASSERTS) callSatinFunctionPreAsserts(r);

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
            if (satin.stampToThreadIdMap != null) {
                if (satin.stampToThreadIdMap.containsKey(r.getStamp())) {               
                    callSatinSharedFunction(r);
                } else {
                    callSatinLocalFunction(r);
                }
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
//        stats.jobsExecuted++;
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
        

        if (!Satin.FT_NAIVE) {
            r.jobFinished();
        }
    }

    private void callSatinSharedFunction(InvocationRecord r) {
        if (stealLogger.isInfoEnabled()) {
            stealLogger.info("SATIN '" + ident
                    + "': RUNNING SHARED CODE, STAMP = " + r.getStamp() + "!");
        }

//        stats.jobsExecuted++;
        ReturnRecord rr = null;
        
        rr = r.runRemote();
        
        rr.setEek(r.eek);

        if (r.eek != null && Satin.stealLogger.isInfoEnabled()) {
            Satin.stealLogger.info("SATIN '" + ident
                    + "': RUNNING SHARED CODE GAVE EXCEPTION: " + r.eek, r.eek);
        } else {
            Satin.stealLogger.info("SATIN '" + ident + "': RUNNING SHARED CODE DONE!");
        }

        // send wrapper back to the owner thread, but on the same machine
        if (!r.aborted) {
            lb.handleSharedResult(r, rr);
            
            if (Satin.stealLogger.isInfoEnabled()) {
                Satin.stealLogger.info("SATIN '" + ident
                        + "': SHARED CODE SEND RESULT DONE!");
            }
        } else {
            if (Satin.stealLogger.isInfoEnabled()) {
                Satin.stealLogger.info("SATIN '" + ident
                        + "': SHARED CODE WAS ABORTED! Exception = " + r.eek);
            }
        }
    }

    private void callSatinRemoteFunction(InvocationRecord r) {
        if (stealLogger.isInfoEnabled()) {
            stealLogger.info("SATIN '" + ident
                    + "': RUNNING REMOTE CODE, STAMP = " + r.getStamp() + "!");
        }
        ReturnRecord rr = null;
        //stats.jobsExecuted++;
        
        rr = r.runRemote();
        
//        satin.log.log(Level.INFO, 
//                "Thread {0}: Just resolved job {1}.", new Object[]{this.id, r.getStamp()});
        
        rr.setEek(r.eek);

        if (r.eek != null){ //&& Satin.stealLogger.isInfoEnabled()) {
            satin.log.log(Level.INFO, 
                "Thread {0}: RUNNING REMOTE CODE GAVE EXCEPTION: {1}", new Object[]{this.id, r.eek});
            Satin.stealLogger.info("SATIN '" + ident
                    + "': RUNNING REMOTE CODE GAVE EXCEPTION: " + r.eek, r.eek);
        } else {
            Satin.stealLogger
                .info("SATIN '" + ident + "': RUNNING REMOTE CODE DONE!");
        }

        // send wrapper back to the owner
        if (!r.aborted) {
            lb.sendResult(r, rr);
            
//            satin.log.log(Level.INFO, 
//                "Thread {0}: Just sent the result for job {1}.", new Object[]{this.id, r.getStamp()});
//            
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
    
}
