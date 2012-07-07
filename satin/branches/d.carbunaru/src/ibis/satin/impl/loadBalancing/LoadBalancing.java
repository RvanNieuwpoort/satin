/*
 * Created on Jun 20, 2006 by rob
 */
package ibis.satin.impl.loadBalancing;

import ibis.ipl.IbisIdentifier;
import ibis.ipl.ReadMessage;
import ibis.ipl.SendPortIdentifier;
import ibis.satin.impl.ClientThread;
import ibis.satin.impl.Config;
import ibis.satin.impl.Satin;
import ibis.satin.impl.spawnSync.IRVector;
import ibis.satin.impl.spawnSync.InvocationRecord;
import ibis.satin.impl.spawnSync.ReturnRecord;
import ibis.satin.impl.spawnSync.Stamp;

import java.io.IOException;
import java.util.ArrayList;

public final class LoadBalancing implements Config {

    static final class StealRequest {

        int opcode;
        SendPortIdentifier sp;
    }

    final class StealRequestHandler extends Thread {

        static final boolean CONTINUOUS_STATS = false;
        static final long CONTINUOUS_STATS_INTERVAL = 60 * 1000;

        public StealRequestHandler() {
            setDaemon(true);
            setName("Satin StealRequestHandler");
        }

        public void run() {
            long lastPrintTime = 0;
            if (CONTINUOUS_STATS) {
                lastPrintTime = System.currentTimeMillis();
            }

            while (true) {
                if (CONTINUOUS_STATS
                        && System.currentTimeMillis() - lastPrintTime > CONTINUOUS_STATS_INTERVAL) {
                    lastPrintTime = System.currentTimeMillis();
                    //s.stats.printDetailedStats(s.ident);
                }

                StealRequest sr = null;
                if (stealLogger.isDebugEnabled()) {
                    stealLogger.debug("StealRequestHandler woke up");
                }
                synchronized (stealQueue) {
                    if (stealQueue.size() > 0) {
                        sr = stealQueue.remove(0);
                    } else {
                        try {
                            stealQueue.wait();
                        } catch (Exception e) {
                            // ignore
                        }
                        continue;
                    }
                }
                if (stealLogger.isDebugEnabled()) {
                    stealLogger.debug("StealRequestHandler dealing with steal request");
                }

                // don't hold lock while sending reply 
                lbComm.handleStealRequest(sr.sp, sr.opcode);
            }
        }
    }
    private LBCommunication lbComm;
    private Satin s;
    //Daniela:
    private ClientThread ct;
    private volatile boolean receivedResults = false;
    /**
     * Used to store reply messages.
     */
    private boolean gotStealReply = false;
    private InvocationRecord stolenJob = null;
    private final IRVector resultList;
    private final ArrayList<StealRequest> stealQueue;
    /**
     * Used for fault tolerance, we must know who the current victim is, in case
     * it crashes.
     */
    private IbisIdentifier currentVictim = null;

    public LoadBalancing(Satin s) {
        this.s = s;
        this.ct = null;

        if (QUEUE_STEALS) {
            stealQueue = new ArrayList<StealRequest>();
            new StealRequestHandler().start();
        } else {
            stealQueue = null;
        }

        resultList = new IRVector(s);
        lbComm = new LBCommunication(s, this);
    }

    /**
     * Daniela:
     *
     * @param ct
     */
    public LoadBalancing(ClientThread ct) {
        this.ct = ct;
        this.s = ct.satin;

        if (QUEUE_STEALS) {
            stealQueue = new ArrayList<StealRequest>();
            new StealRequestHandler().start();
        } else {
            stealQueue = null;
        }

        resultList = new IRVector(ct);
        lbComm = new LBCommunication(ct, this);
    }

    public synchronized void gotJobResult(InvocationRecord ir, IbisIdentifier sender) {
        // This might be a job that came in after a STEAL_WAIT_TIMEOUT.
        // If this is the case, this job has to be added to the queue,
        // it is not the result of the current steal request.

        if (ct == null) {
            // we are in the Satin's loadbalancer
            // get a thread from the queue of waiting stealing threads and:
            int threadId;
            synchronized (s) {
                // there is always at least 1 thread id in the list in the map: <ibisident, list<threadid>>.
                // because this is a steal response, and at the steal request
                // there was an inserted threadId in the map.

                threadId = s.waitingStealMap.get(sender).remove(0);

                if (s.waitingStealMap.get(sender).isEmpty()) {
                    s.waitingStealMap.remove(sender);
                }
            }

            if (threadId != -1) {
                s.clientThreads[threadId].lb.gotJobResult(ir, sender);
            } else {
                if (!sender.equals(currentVictim)) {
                    if (s.deadIbises.contains(sender)) {
                        // A dead Ibis is alive after all. Ignore it.
                        ftLogger.warn("SATIN '" + s.ident + "': received a reply from "
                                + sender + " which is supposed to be dead");
                        return;
                    }
                    ftLogger.warn("SATIN '" + s.ident + "': received a reply from "
                            + sender + " who caused a timeout before. I am stealing from " + currentVictim);

                    if (ir != null) {
                        s.q.addToTail(ir);
                    }
                    return;
                }

                if (stolenJob != null) {
                    ftLogger.warn("SATIN '" + s.ident + "': EEK: setting stolenJob when it is non-null!");
                }

                gotStealReply = true;
                stolenJob = ir;
                currentVictim = null;
                notifyAll();
            }
        } else {
            if (!sender.equals(currentVictim)) {
                if (s.deadIbises.contains(sender)) {
                    // A dead Ibis is alive after all. Ignore it.
                    ftLogger.warn("SATIN '" + s.ident + "': received a reply from "
                            + sender + " which is supposed to be dead");
                    return;
                }
                ftLogger.warn("SATIN '" + s.ident + "': received a reply from "
                        + sender + " who caused a timeout before. I am stealing from " + currentVictim);

                // Daniela:
                if (ir != null) {
                    // we are good: in a thread's load balancer.
                    ct.q.addToTail(ir);
                }
                return;
            }

            if (stolenJob != null) {
                ftLogger.warn("SATIN '" + s.ident + "': EEK: setting stolenJob when it is non-null!");
            }

            gotStealReply = true;
            stolenJob = ir;
            currentVictim = null;
            notifyAll();
        }
    }

    public void addToOutstandingJobList(InvocationRecord r) {
        Satin.assertLocked(s);
        s.outstandingJobs.add(r);
    }

    /**
     * does a synchronous steal. If blockOnServer is true, it blocks on server
     * side until work is available, or we must exit. This is used in
     * MasterWorker algorithms.
     */
    public InvocationRecord stealJob(Victim v, boolean blockOnServer) {
        if (ASSERTS) {
            synchronized (this) {
                if (stolenJob != null) {
                    ftLogger.error("SATIN '" + s.ident + "': EEK: stealing while stolenJob is non-null!");
                    throw new Error(
                            "EEEK, trying to steal while an unhandled stolen job is available.");
                }
            }
        }

        if (s.exiting) {
            return null;
        }

        if (Satin.GLOBAL_PAUSE_RESUME) {
            synchronized (s) {
                if (s.comm.paused) {
                    long start = System.currentTimeMillis();
                    while (s.comm.paused) {
                        try {
                            s.wait();
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                    long end = System.currentTimeMillis();
                    commLogger.info("SATIN '" + s.ident + "': paused for "
                            + (end - start) + " ms");
                }
            }
        }

        if (ct == null) {
            s.stats.stealTimer.start();
            s.stats.stealAttempts++;
        } else {
            ct.stats.stealTimer.start();
            ct.stats.stealAttempts++;
        }

        try {
            lbComm.sendStealRequest(v, true, blockOnServer);
            return waitForStealReply(v);
        } catch (IOException e) {
            ftLogger.info("SATIN '" + s.ident
                    + "': got exception during steal request", e);
            return null;
        } finally {
            if (ct == null) {
                s.stats.stealTimer.stop();
            } else {
                ct.stats.stealTimer.stop();
            }
        }
    }

    public void handleDelayedMessages() {
        if (!receivedResults) {
            return;
        }

        synchronized (s) {
            while (true) {
                InvocationRecord r = resultList.removeIndex(0);
                if (r == null) {
                    break;
                }

                if (r.eek != null) {
                    if (ct == null) {
                        s.aborts.handleInlet(r);
                    } else {
                        ct.aborts.handleInlet(r);
                    }
                }

                r.decrSpawnCounter();

                if (stealLogger.isInfoEnabled()) {
                    stealLogger.info("Got result for job " + r.getStamp());
                }

                if (!FT_NAIVE) {
                    r.jobFinished();
                }
            }

            receivedResults = false;
        }
    }

    private void waitForStealReplyMessage(Victim v) {
        long start = System.currentTimeMillis();
        while (true) {
            synchronized (this) {
                boolean gotTimeout = System.currentTimeMillis() - start >= STEAL_WAIT_TIMEOUT;
                if (gotTimeout && !gotStealReply) {
                    if (!("MW".equals(SUPPLIED_ALG))) {
                        ftLogger.warn("SATIN '"
                                + s.ident
                                + "': a timeout occurred while waiting for a steal reply from victim " + v.getIdent() + ", timeout = "
                                + STEAL_WAIT_TIMEOUT / 1000 + " seconds.");
                    }
                }

                // At least handle aborts! Otherwise an older abort
                // can kill a job that was stolen later.
                if (ct == null) {
                    s.aborts.handleDelayedMessages();
                } else {
                    ct.aborts.handleDelayedMessages();
                }

                if (gotStealReply || gotTimeout) {
                    // Immediately reset gotStealReply, a reply has arrived.
                    gotStealReply = false;
                    if (ct == null) {
                        s.currentVictimCrashed = false;
                    } else {
                        ct.currentVictimCrashed = false;
                    }
                    return;
                }

                if (ct == null) {
                    if (s.currentVictimCrashed) {
                        s.currentVictimCrashed = false;
                        ftLogger.debug("SATIN '" + s.ident
                                + "': current victim crashed");
                        return;
                    }
                } else {
                    if (ct.currentVictimCrashed) {
                        ct.currentVictimCrashed = false;
                        ftLogger.debug("SATIN '" + s.ident
                                + "': current victim crashed");
                        return;
                    }
                }

                if (s.exiting) {
                    return;
                }

                if (!HANDLE_MESSAGES_IN_LATENCY) { // a normal blocking steal
                    try {
                        wait(1000);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }

            }

            if (HANDLE_MESSAGES_IN_LATENCY) {
                if (ct == null) {
                    s.handleDelayedMessages();
                } else {
                    ct.handleDelayedMessages();
                }
            }
        }
    }

    private InvocationRecord waitForStealReply(Victim v) {
        waitForStealReplyMessage(v);

        synchronized (this) {
            /*
             * If successfull, we now have a job in stolenJob.
             */
            if (stolenJob == null) {
                return null;
            }

            /*
             * I love it when a plan comes together! We stole a job.
             */
            if (ct == null) {
                s.stats.stealSuccess++;
            } else {
                ct.stats.stealSuccess++;
            }
            InvocationRecord myJob = stolenJob;
            stolenJob = null;

            return myJob;
        }
    }

    private void addToJobResultList(InvocationRecord r) {
        Satin.assertLocked(s);
        resultList.add(r);
    }

    private InvocationRecord getStolenInvocationRecord(Stamp stamp) {
        Satin.assertLocked(s);
        return s.outstandingJobs.remove(stamp);
    }

    protected void addJobResult(ReturnRecord rr, Throwable eek, Stamp stamp) {
        synchronized (s) {
            receivedResults = true;
            InvocationRecord r = null;

            if (rr != null) {
                r = getStolenInvocationRecord(rr.getStamp());
            } else {
                r = getStolenInvocationRecord(stamp);
            }

            if (r != null) {
                if (rr != null) {
                    rr.assignTo(r);
                } else {
                    r.eek = eek;
                }
                if (r.eek != null) {
                    // we have an exception, add it to the list.
                    // the list will be read during the sync
                    if (ct == null) {
                        s.aborts.addToExceptionList(r);
                    } else {
                        ct.aborts.addToExceptionList(r);
                    }
                } else {
                    addToJobResultList(r);
                }
            } else {
                abortLogger.debug("SATIN '" + s.ident
                        + "': got result for aborted job, ignoring.");
            }
        }
    }

    // throws an IO exception when the ibis that tried to steal the job dies
    protected InvocationRecord stealJobFromLocalQueue(SendPortIdentifier ident,
            boolean blocking) throws IOException {
        InvocationRecord result = null;

        while (true) {
            /**
             * Daniela:
             */
            // TODO aici ar trebui sa caut prin q-urile threadurilor
            result = s.returnJob();//q.getFromTail();
            if (result != null) {
                result.setStealer(ident.ibisIdentifier());

                // store the job in the outstanding list
                addToOutstandingJobList(result);
                return result;
            }

            if (!blocking || s.exiting) {
                return null; // the steal request failed
            }

            try {
                s.wait();
            } catch (Exception e) {
                // Ignore.
            }

            Victim v = s.victims.getVictim(ident.ibisIdentifier());
            if (v == null) {
                throw new IOException("the stealing ibis died");
            }
        }
    }

    public void sendResult(InvocationRecord r, ReturnRecord rr) {
        lbComm.sendResult(r, rr);
    }

    public void handleStealRequest(SendPortIdentifier ident, int opcode) {
        lbComm.handleStealRequest(ident, opcode);
    }

    public void queueStealRequest(SendPortIdentifier ident, int opcode) {
        StealRequest r = new StealRequest();
        r.opcode = opcode;
        r.sp = ident;
        synchronized (stealQueue) {
            if (stealLogger.isDebugEnabled()) {
                stealLogger.debug("Queueing steal request from " + s);
            }
            stealQueue.add(r);
            stealQueue.notifyAll();
        }
    }

    public void handleReply(ReadMessage m, int opcode) {
        lbComm.handleReply(m, opcode);
    }

    public void handleJobResult(ReadMessage m, int opcode) {
        lbComm.handleJobResult(m, opcode);
    }

    public void sendStealRequest(Victim v, boolean synchronous, boolean blocking)
            throws IOException {
        lbComm.sendStealRequest(v, synchronous, blocking);
    }

    public void handleSharedResult(InvocationRecord r, ReturnRecord rr) {
        Stamp stamp = r.getStamp();
        int threadId = s.stampToThreadIdMap.get(stamp);

        synchronized (s.stampToThreadIdMap) {
            s.stampToThreadIdMap.remove(stamp);
        }

        if (threadId != -1) {
            s.clientThreads[threadId].lb.addJobResult(rr, r.eek, stamp);
        } else {
            addJobResult(rr, r.eek, stamp);
        }
    }

    /**
     * Used for fault tolerance, we must know who the current victim is, in case
     * it crashes.
     */
    public IbisIdentifier getCurrentVictim() {
        return currentVictim;
    }

    public void setCurrentVictim(IbisIdentifier ident) {
        if (stealLogger.isDebugEnabled()) {
            stealLogger.debug("setCurrentVictim: " + ident);
        }
        currentVictim = ident;
    }
}
