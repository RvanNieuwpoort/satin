/*
 * Created on May 2, 2006 by rob
 */
package ibis.satin.impl.faultTolerance;

import ibis.ipl.IbisIdentifier;
import ibis.ipl.ReadMessage;
import ibis.ipl.ReceivePortConnectUpcall;
import ibis.ipl.RegistryEventHandler;
import ibis.ipl.WriteMessage;
import ibis.satin.impl.Config;
import ibis.satin.impl.Satin;
import ibis.satin.impl.checkPointing.Checkpoint;
import ibis.satin.impl.checkPointing.CheckpointAndQuitThread;
import ibis.satin.impl.checkPointing.CheckpointFile;
import ibis.satin.impl.checkPointing.CheckpointThread;
import ibis.satin.impl.communication.Protocol;
import ibis.satin.impl.loadBalancing.Victim;
import ibis.satin.impl.spawnSync.InvocationRecord;
import ibis.satin.impl.spawnSync.ReturnRecord;
import ibis.satin.impl.spawnSync.Stamp;
import ibis.satin.impl.spawnSync.StampVector;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.gridlab.gat.GAT;
import org.gridlab.gat.GATContext;
import org.gridlab.gat.URI;
import org.gridlab.gat.io.FileOutputStream;

public final class FaultTolerance implements Config {
    private Satin s;

    /** True if the master crashed and the whole work was restarted. */
    private boolean restarted = false;

    /* use these to avoid locking */
    protected volatile boolean gotCrashes = false;

    protected volatile boolean gotAbortsAndStores = false;

    protected volatile boolean gotDelete = false;

    protected volatile boolean gotDeleteCluster = false;

    protected volatile boolean updatesToSend = false;

    protected volatile boolean masterHasCrashed = false;

    protected volatile boolean clusterCoordinatorHasCrashed = false;

    private StampVector abortAndStoreList;

    protected IbisIdentifier clusterCoordinatorIdent;

    /** Historical name; it's the global job table used in fault tolerance. */
    protected GlobalResultTable globalResultTable;

    /** True if the node needs to download the contents of the global result
     *  table; protected by the Satin lock. */
    public boolean getTable = true;

    /**
     * Used for fault tolerance Ibises that crashed recently, and whose crashes
     * still need to be handled.
     */
    protected ArrayList<IbisIdentifier> crashedIbises = new ArrayList<IbisIdentifier>();

    protected FTCommunication ftComm;

    private int checkpointAndQuitTime;

    IbisIdentifier coordinatorIdent;
    
    IbisIdentifier tempCoordinatorIdent;

    private boolean findNewCoordinator;

    boolean setCoordinator = false;

    boolean gotCheckpoints;

    boolean becomeCoordinator;

    private boolean gotCheckpointAndQuit;

    public boolean takeCheckpoint;

    boolean coordinator = false;
    
    boolean resumeOld = false;
    
    int fileWriteMinimum = Integer.MAX_VALUE;
    
    int totalFileWriteInfoMsgs = 0;
    
    private CheckpointFile checkpointFile = null;
    
    ArrayList<Checkpoint> checkpoints = new ArrayList<Checkpoint>();
    
    CheckpointThread cpThread = null;

    public FaultTolerance(Satin s) {
        this.s = s;

        ftComm = new FTCommunication(s); // must be created first, it handles registry upcalls.

        /* the threads below are used for debugging */
        if (KILL_TIME > 0) {
            (new KillerThread(KILL_TIME)).start();
        }
        if (DELETE_TIME > 0) {
            (new DeleteThread(DELETE_TIME)).start();
        }
        if (DELETE_CLUSTER_TIME > 0) {
            (new DeleteClusterThread(DELETE_CLUSTER_TIME)).start();
        }
        

    }

    public void electClusterCoordinator() {
        ftComm.electClusterCoordinator();    	
    }
    
    public void init() {

        if(!FT_NAIVE) {
            globalResultTable = new GlobalResultTable(s);
        }
        abortAndStoreList = new StampVector();

        if (FT_NAIVE) {
            ftLogger.info("naive FT on");
        } else {
            ftLogger.info("FT on, with GRT enabled");
        }

        if (s.isMaster()) {
            getTable = false;
        }

        s.comm.ibis.registry().enableEvents();

        if (CLOSED) {
            s.comm.waitForAllNodes();
        }

        //[KRIS]
        if (CHECKPOINTING){
            // in case of a CHECKPOINT_PUSH, every node has to decide when
            // to send the checkpoint itself. That's what the CheckpointThread
            // does
            if (CHECKPOINT_INTERVAL > 0 && CHECKPOINT_PUSH){
                cpThread = new CheckpointThread(CHECKPOINT_INTERVAL, CHECKPOINT_FIRST);
                cpThread.start();
            }

            if (checkpointAndQuitTime > 0) {
                (new CheckpointAndQuitThread(checkpointAndQuitTime)).start();
            }

            // we don't want the coordinator to request for checkpoints
            // immediately after starting the computation
            // previousCheckpoint = System.currentTimeMillis();

            // let the master know about he conenction speed to stable storage
            // so that it can select the coordinator
            if (s.isMaster()){
                synchronized(s){
                    int time = Integer.MAX_VALUE;
                    if (time < fileWriteMinimum){
                        fileWriteMinimum = time;
                        tempCoordinatorIdent = s.ident;
                    }   
                }
            } else {
                Victim v;
                synchronized(s) {
                    v = s.victims.getVictim(s.getMasterIdent());
                }
                try {
                    WriteMessage w = v.newMessage();
                    w.writeByte(Protocol.FILE_WRITE_TIME);
                    w.writeInt(computeConnectionSpeed());
                    w.finish();
                } catch (Exception e){
                    System.out.println("Sending FILE_WRITE_TIME failed to " +
                                       s.getMasterIdent() + ": " + e);
                }
            }  
        }
    }

    // The core of the fault tolerance mechanism, the crash recovery procedure
    public void handleCrashes() {
        ftLogger.debug("SATIN '" + s.ident + ": handling crashes");

        s.stats.crashTimer.start();

        HashSet<IbisIdentifier> crashedCopy;

        try {

            ArrayList<IbisIdentifier> crashesToHandle;

            synchronized (s) {                
                crashesToHandle = new ArrayList<IbisIdentifier>(crashedIbises);
                crashedCopy = new HashSet<IbisIdentifier>(crashedIbises);
                crashedIbises.clear();
                gotCrashes = false;
            }

            // Let the Ibis registry know, but only if this is the master or
            // a cluster coordinator, otherwise everything gets terribly slow.
            // Don't hold the lock while doing this.
            for (int i = 0; i < crashesToHandle.size(); i++) {
                IbisIdentifier id = crashesToHandle.get(i);
                //[KRIS]
                if (CHECKPOINTING || checkpointAndQuitTime > 0) {
                    if (id.equals(coordinatorIdent)){
                        coordinatorIdent = null;
                        if (s.isMaster()){
                            findNewCoordinator = true;
                            setCoordinator  = false;
                        }
                    }
                }
                
                if (id.equals(s.getMasterIdent()) || id.equals(clusterCoordinatorIdent)) {
                    try {
                        s.comm.ibis.registry().maybeDead(id);
                    } catch (IOException e) {
                        // ignore exception
                        ftLogger.info("SATIN '" + s.ident
                            + "' :exception while notifying registry about "
                            + "crash of " + id + ": " + e, e);
                    }
                }
            }

            synchronized (s) {
                while (crashesToHandle.size() > 0) {
                    IbisIdentifier id = crashesToHandle.remove(0);
                    ftLogger.debug("SATIN '" + s.ident + ": handling crash of "
                        + id);

                    // give the load-balancing algorith a chance to clean up
                    s.algorithm.handleCrash(id);

                    if (!FT_NAIVE) {
                        // abort all jobs stolen from id or descendants of jobs
                        // stolen from id
                        killAndStoreSubtreeOf(id);
                    }

                    s.outstandingJobs.redoStolenBy(id);
                    s.stats.numCrashesHandled++;
                    
                    s.so.handleCrash(id);
                }

                s.notifyAll();
            }
        } finally {
            s.stats.crashTimer.stop();
        }
        //[KRIS]
        if (CHECKPOINTING && coordinator){
            synchronized(s) {
                s.stats.useCheckpointTimer.start();
                checkpointFile.read(crashedCopy, globalResultTable);
                s.stats.useCheckpointTimer.stop();
            }
        }
    }

    public void handleMasterCrash() {
        ftLogger.info("SATIN '" + s.ident + "': MASTER (" + s.getMasterIdent()
            + ") HAS CRASHED");

        // master has crashed, let's elect a new one
        IbisIdentifier newMaster = null;
        try {
            newMaster = s.comm.ibis.registry().elect("satin master");
        } catch (Exception e) {
            ftLogger.error("SATIN '" + s.ident
                + "' :exception while electing a new master " + e, e);
            System.exit(1);
        }

        synchronized (s) {
            masterHasCrashed = false;
            s.setMaster(newMaster);
            if (s.getMasterIdent().equals(s.ident)) {
                ftLogger.info("SATIN '" + s.ident + "': I am the new master");
            } else {
                ftLogger.info("SATIN '" + s.ident + "': " + s.getMasterIdent()
                    + "is the new master");
            }
            restarted = true;
        }
    }

    public void handleClusterCoordinatorCrash() {
        clusterCoordinatorHasCrashed = false;
        try {
            ftComm.electClusterCoordinator();
        } catch (Exception e) {
            ftLogger.warn("SATIN '" + s.ident
                + "' :exception while electing a new cluster coordinator " + e,
                e);
        }
    }

    public void killAndStoreChildrenOf(Stamp targetStamp) {
        Satin.assertLocked(s);
        // try work queue, outstanding jobs and jobs on the stack
        // but try stack first, many jobs in q are children of stack jobs
        ArrayList<InvocationRecord> toStore = s.onStack.killChildrenOf(targetStamp, true);

        //update the global result table

        for (int i = 0; i < toStore.size(); i++) {
            storeFinishedChildrenOf(toStore.get(i));
        }

        s.q.killChildrenOf(targetStamp);
        s.outstandingJobs.killChildrenOf(targetStamp, true);
    }

    private void storeFinishedChildrenOf(InvocationRecord r) {
        InvocationRecord child = r.getFinishedChild();
        while (child != null) {
            s.ft.storeResult(child);
            child = child.getFinishedSibling();
        }
    }

    public void killAndStoreSubtreeOf(IbisIdentifier targetOwner) {
        ArrayList<InvocationRecord> toStore = s.onStack.killSubtreesOf(targetOwner);

        // update the global result table
        for (int i = 0; i < toStore.size(); i++) {
            storeFinishedChildrenOf(toStore.get(i));
        }

        s.q.killSubtreeOf(targetOwner);
        s.outstandingJobs.killAndStoreSubtreeOf(targetOwner);
    }

    public void addToAbortAndStoreList(Stamp stamp) {
        Satin.assertLocked(s);
        abortLogger.debug("SATIN '" + s.ident + ": got abort message");
        abortAndStoreList.add(stamp);
        gotAbortsAndStores = true;
    }

    public void handleAbortsAndStores() {
        synchronized (s) {
            Stamp stamp;

            while (true) {
                if (abortAndStoreList.getCount() > 0) {
                    stamp = abortAndStoreList.getStamp(0);
                    abortAndStoreList.removeIndex(0);
                } else {
                    gotAbortsAndStores = false;
                    return;
                }

                killAndStoreChildrenOf(stamp);
            }
        }
    }

    public void deleteCluster(String cluster) {
        ftLogger.info("SATIN '" + s.ident + "': delete cluster " + cluster);

        if (Victim.clusterOf(s.ident).equals(cluster)) {
            gotDeleteCluster = true;
        }
    }

    public void handleDelete() {
        Victim victim;
        
        synchronized(s) {
            victim = s.victims.getRandomLocalVictim();
        }
        pushJobs(victim);
        System.exit(0);
    }

    public void handleDeleteCluster() {
        Victim victim;
        
        synchronized(s) {
            Victim victim = s.victims.getRandomRemoteVictim();
        }
        pushJobs(victim);
        System.exit(0);
    }

    private void pushJobs(Victim v) {
        Map<Stamp, GlobalResultTableValue> toPush = new HashMap<Stamp, GlobalResultTableValue>();
        synchronized (s) {
            ArrayList<InvocationRecord> tmp = s.onStack.getAllFinishedChildren(v);

            for (int i = 0; i < tmp.size(); i++) {
                InvocationRecord curr = tmp.get(i);
                Stamp key = curr.getStamp();
                GlobalResultTableValue value = new GlobalResultTableValue(
                    GlobalResultTableValue.TYPE_RESULT, curr);
                toPush.put(key, value);
            }

            s.stats.killedOrphans += s.onStack.size();
            s.stats.killedOrphans += s.q.size();
        }

        ftComm.pushResults(v, toPush);
    }

    public void handleDelayedMessages() {
        if (gotCrashes) {
            s.ft.handleCrashes();
        }
        if (gotAbortsAndStores) {
            s.ft.handleAbortsAndStores();
        }
        if (gotDelete) {
            s.ft.handleDelete();
        }
        if (gotDeleteCluster) {
            s.ft.handleDeleteCluster();
        }
        if (masterHasCrashed) {
            s.ft.handleMasterCrash();
        }
        if (clusterCoordinatorHasCrashed) {
            s.ft.handleClusterCoordinatorCrash();
        }
        if (updatesToSend) {
            globalResultTable.sendUpdates();
        }
        if (CHECKPOINTING) {
            if (gotCheckpoints){
                synchronized (s){
                    s.stats.writeCheckpointTimer.start();
                    try {
                        checkpointFile.write(checkpoints);
                    } finally {
                        s.stats.writeCheckpointTimer.stop();
                    }
                }
                gotCheckpoints = false;
            }
            if (becomeCoordinator){
                coordinatorInit();
                becomeCoordinator = false;
            }
            if (findNewCoordinator){
                findNewCoordinator();
                findNewCoordinator = false;
            }
            if (setCoordinator){
                setCoordinator();
                setCoordinator = false;
            }   
            if (takeCheckpoint){
                /*              if (coordinator && !CHECKPOINT_PUSH){
                    broadcastCheckpointRequest();
                    takeCoordinatorCheckpoint();
                } else if (coordinator && CHECKPOINT_PUSH){
                    takeCoordinatorCheckpoint();
                } else if (!coordinator && CHECKPOINT_PUSH){
                    takeAndSendCheckpoint();
                    }*/
                if (coordinator) {
                    if (!CHECKPOINT_PUSH) {
                        broadcastCheckpointRequest();
                    }
                    takeCoordinatorCheckpoint();
                } else {
                    if (CHECKPOINT_PUSH) {
                        takeAndSendCheckpoint();
                    }
                }
                takeCheckpoint = false;
            }
            if (gotCheckpointAndQuit) {
                handleCheckpointAndQuit();
            }
        }

    }
    
    public void checkpointAndQuit() {
        gotCheckpointAndQuit = true;
    }

    void handleCheckpointAndQuit() {
        synchronized(s) {
            if (coordinator) {
                takeCoordinatorCheckpoint();
                System.out.println("SATIN '" + s.ident
                                   + "': checkpoint taken");
                /*wait for a while and receive checkpoints from other nodes*/
                try {
                    wait(COORDINATOR_QUIT_DELAY_TIME);
                } catch (InterruptedException e) {
                    //ignore
                }
                if (gotCheckpoints) {
                    synchronized(s) {
                        s.stats.writeCheckpointTimer.start();
                        checkpointFile.write(checkpoints);
                        s.stats.writeCheckpointTimer.stop();
                    }
                }
                System.out.println("SATIN '" + s.ident
                                   + "': coordinator quits");
            } else {
                takeAndSendCheckpoint();
                System.out.println("SATIN '" + s.ident
                                   + "': checkpoint taken");
            }
        }
        System.exit(0);
    }
            

    //[KRIS]
    /**
     * Request checkpoints of all the nodes, and takes it's own checkpoint
     **/
    public void doPullCheckpoint(){
        if (!coordinator){
            // I am no coordinator (yet), so let's skip this for now
            return;
        }
        broadcastCheckpointRequest();
        takeCoordinatorCheckpoint();
    }

    /**
     * Take own checkpoint in case I am the coordinator. Otherwise send
     * checkpoint
     **/
    public void doPushCheckpoint(){
        if (becomeCoordinator){
            return;
        } else if (coordinator){
            takeCoordinatorCheckpoint();
        } else {
            takeAndSendCheckpoint();
        }
        
    }

    /**
     * Sends a CHECKPOINT_REQUEST to all the other nodes.
     **/
    public void broadcastCheckpointRequest(){
        if (!coordinator){
            // I am no coordinator (yet), so let's skip this for now
            return;
        }

        s.stats.requestCheckpointTimer.start();
        int size = s.victims.size();
        for (int i = 0; i < size; i++){
            WriteMessage writeMessage;
            Victim victim = s.victims.getVictim(i);
            try {
                writeMessage = victim.newMessage();
                writeMessage.writeByte(Protocol.CHECKPOINT_REQUEST);
                writeMessage.finish();
            } catch (Exception e){
                System.out.println("sending CHECKPOINT_REQUEST failed to " +
                                   victim.getIdent() + ": " + e);
            }
        }
        s.stats.requestCheckpointTimer.stop();
    }
    
    /**
     * Retrieve checkpoints from local queue, and store them in the global
     * variable 'checkpoints' 
     **/
    public void takeCoordinatorCheckpoint(){
        if (!coordinator){
            // I am no coordinator (yet), so let's skip this for now
            return;
        }

        s.stats.makeCheckpointTimer.start();
        ArrayList<ReturnRecord> myCheckpoints;
        synchronized(s){
            myCheckpoints = s.onStack.peekFinishedJobs();
        }
        for (ReturnRecord r : myCheckpoints) {
            if (r == null) {
                System.out.println("OOPS1: returnrecord is null!");
            }
            checkpoints.add(new Checkpoint(r, s.ident));
        }
        gotCheckpoints = true;
        s.stats.makeCheckpointTimer.stop();
    }

    /**
     * Retrieves checkpoints from local queue, and sends them to coordinator
     **/
    public void takeAndSendCheckpoint(){
        if (coordinator || becomeCoordinator){
            // I am/become coordinator, so no need to send myselve checkpoints
            return;
        }

        if (findNewCoordinator || setCoordinator){
            // no coordinator available  yet, so let's skip this for now
            return;
        }

        if (coordinatorIdent == null){
            if (s.isMaster()){
                findNewCoordinator = true;
            }
            return;
        }

        Victim co;
        synchronized(s) {
            co = s.victims.getVictim(coordinatorIdent);
            if (co == null) {
                return;
            }
        }

        s.stats.makeCheckpointTimer.start();
        try {
            WriteMessage w = co.newMessage();
            w.writeByte(Protocol.CHECKPOINT);
            synchronized (s){
                w.writeObject(s.onStack.peekFinishedJobs());
            }
            w.finish();
        } catch (IOException e){
            System.out.println("sending CHECKPOINT failed to "+ 
                               coordinatorIdent + ": " + e);
        }
        s.stats.makeCheckpointTimer.stop();              
    }

    public void broadcastCheckpointInfo(){
        int size = s.victims.size();
        for (int i = 0; i < size; i++) {
            Victim v;
            synchronized(s) {
                v = s.victims.getVictim(i);
            }
            try {
                WriteMessage w = v.newMessage();
                w.writeByte(Protocol.CHECKPOINT_INFO);
                w.writeInt(globalResultTable.size());
                w.finish();
            } catch (IOException e){
                System.out.println("sending CHECKPOINT_INFO failed to " +
                                   v.getIdent() + ": " + e);
            }
        }
    }

    public int computeConnectionSpeed(){
        GATContext context = new GATContext();
        String filename = CHECKPOINT_FILE + s.ident.hashCode();
        int result = Integer.MAX_VALUE;
        try {
            FileOutputStream outFile = GAT.createFileOutputStream(context,
                                       new URI(filename));
            double begin = System.currentTimeMillis();
            for (int i = 0; i < 1024; i++){
                outFile.write(i);
            }
            outFile.flush();
            result = (int)(System.currentTimeMillis() - begin);
            outFile.close();
        } catch (Exception e){
            System.out.println("computeConnectionSpeed failed: " + e);
        }
        try {
            GAT.createFile(context, new URI(filename)).delete();
        } catch (Exception e){
            System.out.println("failed to remove temp-file " + filename);
        }
        return result;
    }

    /**
     * Makes the tempCoordinator (i.e. the node which has the fastes
     * filewrite time uptill now) the coordinator and lets the other
     * nodes know about the new coordinator
     **/
    public void setCoordinator(){
        if (tempCoordinatorIdent == null){
            System.out.println("unexpected setCoordinator");
            System.exit(1);
        }

        if (s.deadIbises.contains(tempCoordinatorIdent)){
            findNewCoordinator = true;
            return;
        }

        s.stats.createCoordinatorTimer.start();

        // let other nodes know about new coordinator
        int size = s.victims.size();
        for (int i = 0; i < size; i++) {
            Victim v;
            synchronized(s) {
                v = s.victims.getVictim(i);
                if (v == null){
                    continue;
                }
            }
            try {
                WriteMessage w = v.newMessage();
                w.writeByte(Protocol.COORDINATOR_INFO);
                w.writeObject(tempCoordinatorIdent);
                w.finish();
            } catch (Exception e){
                System.out.println("sending COORDINATOR_INFO failed to " +
                                   v.getIdent() + ": " + e);
            }
        }

        // set new coordinator myself
        coordinatorIdent = tempCoordinatorIdent;
        if (coordinatorIdent.equals(s.ident)){
            becomeCoordinator = true;
        }
        s.stats.createCoordinatorTimer.stop();
    }

    /**
     * Makes this node coordinator:
     *  - start the necessairy threads
     *  - initialize the checkpoint file
     *  - broadcast checkpoint info to other nodes
     **/
    public void coordinatorInit(){
        s.stats.createCoordinatorTimer.start();

        // initialize possible helper threads
        // if CHECKPOINT_PUSH = true, then the cpThread is already created
        // at initialization
        if (!CHECKPOINT_PUSH){
            cpThread = new CheckpointThread(CHECKPOINT_INTERVAL, CHECKPOINT_FIRST);
            cpThread.start();
        }

        // initialize checkpoint file
        checkpointFile = new CheckpointFile(CHECKPOINT_FILE,
                                            CHECKPOINT_MAXFILESIZE);

        if (resumeOld){
            // if resumeOld is set, the checkpoints are already put in the grt
            // by an other coordinator
            checkpointFile.init(null);
        } else {
            // otherwise, all the checkpoints in the file need to be inserted
            // in the globalResultTable
            int reusable;
            synchronized(s){
                reusable = checkpointFile.init(globalResultTable);
            }
            if (reusable > 0){
                getTable = false;
                resumeOld = true;
                broadcastCheckpointInfo();
            }
        }           

        // i am the coordinator
        coordinator = true;
        s.stats.createCoordinatorTimer.stop();
    }

    public void findNewCoordinator(){
        s.stats.createCoordinatorTimer.start();

        // reset all bandwidth measure information
        tempCoordinatorIdent = null;
        fileWriteMinimum = Integer.MAX_VALUE;
        totalFileWriteInfoMsgs = 0;

        // ask all nodes for new measure information
        int size = s.victims.size();
        for (int i = 0; i < size; i++) {
            Victim v = s.victims.getVictim(i);
            if (v == null){
                continue;
            }
            try {
                WriteMessage w = v.newMessage();
                w.writeByte(Protocol.FILE_WRITE_TIME_REQ);
                w.finish();
            } catch (Exception e){
                System.out.println("sending FILE_WRITE_TIME_REQ failed to " +
                                   v.getIdent() + ": " + e);
            }       
        }
        s.stats.createCoordinatorTimer.stop();
    }


    public boolean checkForDuplicateWork(InvocationRecord parent,
        InvocationRecord r) {
        if (FT_NAIVE) return false;

        if (parent != null && parent.isReDone() || parent == null && restarted) {
            r.setReDone(true);
        }

        if (r.isReDone() || resumeOld) {
            if (ftComm.askForJobResult(r)) {
                return true;
            }
        }

        return false;
    }

    public void storeResult(InvocationRecord r) {
        globalResultTable.storeResult(r);
    }

    public void print(PrintStream out) {
        globalResultTable.print(out);
    }

    public IbisIdentifier lookupOwner(InvocationRecord r) {
        return globalResultTable.lookup(r.getStamp()).sendTo;
    }

    public Map<Stamp, GlobalResultTableValue> getContents() {
        return globalResultTable.getContents();
    }

    public void addContents(Map<Stamp, GlobalResultTableValue> contents) {
        globalResultTable.addContents(contents);
    }

    public RegistryEventHandler getRegistryEventHandler() {
        return ftComm;
    }

    public ReceivePortConnectUpcall getReceivePortConnectHandler() {
        return ftComm;
    }

    public void disableConnectionUpcalls() {
        ftComm.disableConnectionUpcalls();
    }

    public void handleAbortAndStore(ReadMessage m) {
        ftComm.handleAbortAndStore(m);
    }

    public void handleResultRequest(ReadMessage m) {
        ftComm.handleResultRequest(m);
    }

    public void handleResultPush(ReadMessage m) {
        ftComm.handleResultPush(m);
    }

    public void sendAbortAndStoreMessage(InvocationRecord r) {
        ftComm.sendAbortAndStoreMessage(r);
    }
    
    public void handleGRTUpdate(ReadMessage m) {
        globalResultTable.handleGRTUpdate(m);
    }

    public void handleCoordinatorInfo(ReadMessage m) {
        ftComm.handleCoordinatorInfo(m);      
    }

    public void handleCheckpoint(ReadMessage m) {
        ftComm.handleCheckpoint(m);        
    }

    public void handleCheckpointInfo(ReadMessage m) {
        ftComm.handleCheckpointInfo(m);        
    }

    public void handleFileWriteTime(ReadMessage m) {
        ftComm.handleFileWriteTime(m);   
    }

    public void handleFileWriteTimeReq(ReadMessage m) {
        ftComm.handleFileWriteTimeReq(m);
    }
    
    public void end() {
        if (CHECKPOINTING){
            if (coordinator){
                checkpointFile.close();
            }

            //      if (CHECKPOINT_PUSH || coordinator){
            if (cpThread != null) {
                cpThread.setExitCondition(true);
            }
            //}
        }
    }
}
