/* $Id$ */
package ibis.satin.impl.loadBalancing;

import ibis.satin.impl.ClientThread;
import ibis.satin.impl.Satin;
import ibis.satin.impl.spawnSync.InvocationRecord;

/**
 * The random work-stealing distributed computing algorithm.
 */
public final class RandomWorkStealing extends LoadBalancingAlgorithm {

    long failedAttempts;

    public RandomWorkStealing(Satin s) {
        super(s);
    }

    /**
     * Daniela:
     *
     * @param ct
     */
    public RandomWorkStealing(ClientThread ct) {
        super(ct);
    }

    public InvocationRecord clientIteration() {
        Victim v;

        synchronized (satin) {
            if (clientThread == null) {
                v = satin.victims.getRandomVictim();
            } else {
                v = clientThread.victims.getRandomVictim();
            }
            /*
             * Used for fault tolerance; we must know who the current victim is
             * in case it crashes..
             */
            if (v != null) {
                if (clientThread != null) {
                    clientThread.lb.setCurrentVictim(v.getIdent());
                } else {
                    satin.lb.setCurrentVictim(v.getIdent());
                }
            }
        }

        if (v == null) {
            return null; //can happen with open world if nobody joined.
        }

        InvocationRecord job;

        if (clientThread != null) {
            job = clientThread.lb.stealJob(v, false);
        } else {
            if (satin.isMaster()) {
                job = satin.lb.stealJob(v, false);
            } else {
                job = null;
            }
        }

        if (job != null) {
            failedAttempts = 0;
            return job;
        } else {
            failedAttempts++;
            throttle(failedAttempts);
        }

        return null;
    }
}
