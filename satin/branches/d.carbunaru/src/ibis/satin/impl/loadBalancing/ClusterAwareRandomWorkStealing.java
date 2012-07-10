/* $Id$ */

package ibis.satin.impl.loadBalancing;

import ibis.ipl.IbisIdentifier;
import ibis.satin.impl.ClientThread;
import ibis.satin.impl.Config;
import ibis.satin.impl.Satin;
import ibis.satin.impl.communication.Protocol;
import ibis.satin.impl.spawnSync.InvocationRecord;

import java.io.IOException;

public final class ClusterAwareRandomWorkStealing extends
        LoadBalancingAlgorithm implements Protocol, Config {

    private Satin s;
    
    //Daniela:
    private ClientThread ct;

    private boolean gotAsyncStealReply = false;

    private InvocationRecord asyncStolenJob = null;

    private IbisIdentifier asyncCurrentVictim = null;

    private long asyncStealStart;

    /**
     * This means we have sent an ASYNC request, and are waiting for the reply.
     * These are/should only (be) used in clientIteration.
     */
    private boolean asyncStealInProgress = false;

    private long failedLocalAttempts;
    private long failedRemoteAttempts;

    public ClusterAwareRandomWorkStealing(Satin s) {
        super(s);
        this.s = s;
    }

    /**Daniela:
     * 
     * @param ct 
     */
    public ClusterAwareRandomWorkStealing(ClientThread ct) {
        super(ct);
        this.ct = ct;
        this.s = ct.satin;
    }

    public InvocationRecord clientIteration() {
        Victim localVictim;
        Victim remoteVictim = null;
        boolean canDoAsync = true;

        // First look if there was an outstanding WAN steal request that resulted
        // in a job.
        InvocationRecord job = checkForAsyncReply();
        if (job != null) {
            if (stealLogger.isInfoEnabled()) {
                if (ct == null) {
                    stealLogger.info("Executing intercluster job " + job.getStamp());
                } else {
                    stealLogger.info("Thread " + ct.id
                            + ": Executing intercluster job " + job.getStamp());
                }
            }

            return job;
        }

        // Else .. we are idle, try to steal a job.
        synchronized (satin) {
            localVictim = satin.victims.getRandomLocalVictim();
            if (localVictim != null) {
                if (clientThread == null) {
                    satin.lb.setCurrentVictim(localVictim.getIdent());
                } else {
                    clientThread.lb.setCurrentVictim(localVictim.getIdent());
                }
            }
            if (!asyncStealInProgress) {
                remoteVictim = satin.victims.getRandomRemoteVictim();
                if (remoteVictim != null) {
                    asyncCurrentVictim = remoteVictim.getIdent();
                }
            }
            // Until we download the table, only the cluster coordinator can
            // issue wide-area steal requests 
            // @@@ why? --Rob
            if (satin.ft.getTable && !satin.clusterCoordinator) {
                canDoAsync = false;
            }
        }

        // Send an asynchronous wide-area steal request,
        // if not is outstanding
        // remoteVictim can be null on a single cluster run.
        if (clientThread == null) {
            if (remoteVictim != null && !asyncStealInProgress) {
                if (FT_NAIVE || canDoAsync) {
                    asyncStealInProgress = true;
                    s.stats.asyncStealAttempts++;
                    try {
                        asyncStealStart = System.currentTimeMillis();
                        satin.lb.sendStealRequest(remoteVictim, false, false);
                    } catch (IOException e) {
                        commLogger.warn("SATIN '" + s.ident
                                + "': Got exception during wa steal request: " + e);
                        // Ignore this?
                    }
                }
            }
        } else {
            if (remoteVictim != null && !asyncStealInProgress) {
                if (FT_NAIVE || canDoAsync) {
                    asyncStealInProgress = true;
                    ct.stats.asyncStealAttempts++;
                    try {
                        asyncStealStart = System.currentTimeMillis();
                        clientThread.lb.sendStealRequest(remoteVictim, false, false);
                    } catch (IOException e) {
                        commLogger.warn("SATIN '" + s.ident
                                + ", Thread " + ct.id
                                + "': Got exception during wa steal request: " + e);
                        // Ignore this?
                    }
                }
            }
        }

        // do a local steal, if possible (we might be the only node in this
        // cluster)
        if (localVictim != null) {
            if (ct == null) {
                job = satin.lb.stealJob(localVictim, false);
                if (job != null) {
                    failedLocalAttempts = 0;
                    return job;
                } else {
                    failedLocalAttempts++;
                    throttle(failedLocalAttempts);
                }
            } else {
                job = ct.lb.stealJob(localVictim, false);
                if (job != null) {
                    failedLocalAttempts = 0;
                    return job;
                } else {
                    failedLocalAttempts++;
                    throttle(failedLocalAttempts);
                }
            }
        }

        return null;
    }

    private InvocationRecord checkForAsyncReply() {
        if (!asyncStealInProgress) {
            return null;
        }

        boolean failedAttempt = false;
        synchronized (satin) {
            boolean gotTimeout =
                    System.currentTimeMillis() - asyncStealStart >= STEAL_WAIT_TIMEOUT;
            if (gotTimeout && !gotAsyncStealReply) {
                ftLogger
                        .warn("SATIN '"
                                + s.ident
                                + "': a timeout occurred while waiting for a wide-area steal reply from "
                                + asyncCurrentVictim + ", timeout = "
                                + STEAL_WAIT_TIMEOUT / 1000 + " seconds.");
            }

            if (gotAsyncStealReply || gotTimeout) {
                failedAttempt = true;
                gotAsyncStealReply = false;
                asyncStealInProgress = false;
                asyncCurrentVictim = null;
                InvocationRecord remoteJob = asyncStolenJob;
                asyncStolenJob = null;
                asyncStealStart = 0;

                if (remoteJob != null) {
                    if (ct == null) {
                        s.stats.asyncStealSuccess++;
                    } else {
                        ct.stats.asyncStealSuccess++;
                    }
                    failedRemoteAttempts = 0;
                    return remoteJob;
                }
            }
        }

        if (failedAttempt) {
            failedRemoteAttempts++;
            throttle(failedRemoteAttempts);
        }

        return null;
    }

    @Override
    public void stealReplyHandler(InvocationRecord ir, IbisIdentifier sender,
            int opcode) {
        switch (opcode) {
        case STEAL_REPLY_SUCCESS:
        case STEAL_REPLY_FAILED:
        case STEAL_REPLY_SUCCESS_TABLE:
        case STEAL_REPLY_FAILED_TABLE:
            satin.lb.gotJobResult(ir, sender);
            break;
        case ASYNC_STEAL_REPLY_SUCCESS:
        case ASYNC_STEAL_REPLY_FAILED:
        case ASYNC_STEAL_REPLY_SUCCESS_TABLE:
        case ASYNC_STEAL_REPLY_FAILED_TABLE:
            //Daniela:
            asyncStealReply(ir, sender); 
            break;
        default:
            s.assertFailed("illegal opcode in CRS stealReplyHandler",
                    new Exception());
        }
    }

    public void exit() {
        // wait for a pending async steal reply
        if (asyncStealInProgress) {
            if (clientThread == null) {
                stealLogger.info("waiting for a pending async steal reply from "
                    + asyncCurrentVictim);
                synchronized (satin) {
                    while (asyncStealInProgress && !gotAsyncStealReply) {
                        try {
                            satin.handleDelayedMessages(); //TODO move outside lock --Rob
                            satin.wait(250);
                        } catch (InterruptedException e) {
                            //ignore
                        }
                    }
                }
            } else {
                stealLogger.info("Thread " + clientThread.id +
                        ": waiting for a pending async steal reply from " + asyncCurrentVictim);
                synchronized (satin) {
                    while (asyncStealInProgress && !gotAsyncStealReply) {
                        try {
                            clientThread.handleDelayedMessages(); //TODO move outside lock --Rob
                            clientThread.wait(250);
                        } catch (InterruptedException e) {
                            //ignore
                        }
                    }
                }
            }
            if (ASSERTS && asyncStolenJob != null) {
                if (clientThread == null) {
                    stealLogger.warn("Satin: CRS: EEK, stole async job "
                            + "after exiting!");
                } else {
                    stealLogger.warn("Satin - Thread " + clientThread.id
                            + " - CRS: EEK, stole async job after exiting!");
                }
            }
        }
    }

    /**
     * Used in fault tolerance; check if the asynchronous steal victim crashed;
     * if so, cancel the steal request; if the job already arrived, remove it
     * (it should be aborted anyway, since it was stolen from a crashed machine)
     * if the owner of the asynchronously stolen job
     * crashed, abort the job.
     */
    public void handleCrash(IbisIdentifier crashedIbis) {
        Satin.assertLocked(satin);
        if (crashedIbis.equals(asyncCurrentVictim)) {
            /*
             * current async victim crashed, reset the flag, remove the stolen
             * job
             */
            asyncStealInProgress = false;
            asyncStolenJob = null;
            asyncCurrentVictim = null;
            gotAsyncStealReply = false;
        }

        if (asyncStolenJob != null) {
            if (asyncStolenJob.getOwner().equals(crashedIbis)) {
                asyncStolenJob = null;
            }
        }
    }

    // Daniela:
    private void asyncStealReply(InvocationRecord ir, IbisIdentifier sender) {
        synchronized (satin) {
            int threadId = s.waitingStealMap.get(sender).remove(0);

            if (s.waitingStealMap.get(sender).isEmpty()) {
                s.waitingStealMap.remove(sender);
            }

            if (threadId != -1) {
                if (stealLogger.isInfoEnabled() && ir != null) {
                    stealLogger.info("Thread " + threadId + 
                            ": Stole intercluster job " + ir.getStamp());
                }
                s.clientThreads[threadId].algorithm.asyncJobResultWorkerThread(ir, sender);
            } else {
                if (stealLogger.isInfoEnabled() && ir != null) {
                    stealLogger.info("Stole intercluster job " + ir.getStamp());
                }
                if (sender.equals(asyncCurrentVictim)) {
                    gotAsyncStealReply = true;
                    asyncStolenJob = ir;
                    //satin.notifyAll(); 
                    // not needed I think, we never wait for the async job.
                } else {
                    ftLogger.warn("SATIN '"
                            + s.ident
                            + "': received an async job from a node that caused a timeout before.");
                    if (ir != null) {
                        s.q.addToTail(ir);
                    }
                }
            }
        }
    }
    
    
    public void asyncJobResultWorkerThread(InvocationRecord ir, IbisIdentifier sender) {
        if (sender.equals(asyncCurrentVictim)) {
            gotAsyncStealReply = true;
            asyncStolenJob = ir;
        } else {
            ftLogger.warn("SATIN '"
                    + s.ident
                    + "': received an async job from a node that caused a timeout before.");
            if (ir != null) {
                ct.q.addToTail(ir);
            }
        }
    }
}
