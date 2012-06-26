/* $Id: LoadBalancingAlgorithm.java 3310 2005-12-08 08:52:02Z ceriel $ */

package ibis.satin.impl.loadBalancing;

import ibis.ipl.IbisIdentifier;
import ibis.satin.impl.ClientThread;
import ibis.satin.impl.Config;
import ibis.satin.impl.Satin;
import ibis.satin.impl.spawnSync.InvocationRecord;

public abstract class LoadBalancingAlgorithm implements Config {

    protected Satin satin;
    
    protected ClientThread clientThread;

    protected LoadBalancingAlgorithm(Satin s) {
        satin = s;
    }
    
    /**Daniela:
     * 
     * @param ct 
     */
    protected LoadBalancingAlgorithm(ClientThread ct) {
        clientThread = ct;
        satin = ct.satin;
    }

    /**
     * Handler that is called when new work is added to the queue. Default
     * implementation does nothing.
     */
    public void jobAdded() {
        // do nothing
    }

    /**
     * Called in every iteration of the client loop. It decides which jobs are
     * run, and what kind(s) of steal requests are done. returns a job an
     * success, null on failure.
     */
    abstract public InvocationRecord clientIteration();

    /**
     * This one is called for each steal reply by the MessageHandler, so the
     * algorithm knows about the reply (this is needed with asynchronous
     * communication)
     */
    public void stealReplyHandler(InvocationRecord ir, IbisIdentifier sender,
            int opcode) {
        satin.lb.gotJobResult(ir, sender);
    }

    /**
     * This one is called in the exit procedure so the algorithm can clean up,
     * e.g., wait for pending (async) messages Default implementation does
     * nothing.
     */
    public void exit() {
        synchronized (satin) {
            satin.notifyAll();
        }
    }

    public void handleCrash(IbisIdentifier ident) {
        // by default, do nothing
    }

    protected void throttle(long count) {
        if (!THROTTLE_STEALS) {
            return;
        }

        int participants;
        synchronized (satin) {
            participants = satin.victims.size();
        }

        long time = 1;
        long throttleSteps = count / participants;
        
        for (long i = 0; i < throttleSteps; i++) {
            time *= 2;
            if (time >= MAX_STEAL_THROTTLE) {
                time = MAX_STEAL_THROTTLE;
                break;
            }
        }

        if (time > 0) {
            if (clientThread == null) {
                satin.stats.stealThrottleTimer.start();
            } else {
                clientThread.stats.stealThrottleTimer.start();
            }

            try {
                Thread.sleep(time);
            } catch (InterruptedException e) {
                // ignore
            } finally {
                if (clientThread == null) {
                    satin.stats.stealThrottleTimer.stop();
                } else {
                    clientThread.stats.stealThrottleTimer.stop();
                }
            }
        }
    }
    
    //Daniela:
    public void asyncJobResultWorkerThread(InvocationRecord ir, IbisIdentifier sender) {
        //by default, do nothing
    }
}
