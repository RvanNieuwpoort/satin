/* $Id$ */

package ibis.satin.impl.loadBalancing;

import ibis.satin.impl.ClientThread;
import ibis.satin.impl.Config;
import ibis.satin.impl.Satin;
import ibis.satin.impl.spawnSync.InvocationRecord;

/** The master-worker distribution algorithm. */

public final class MasterWorker extends LoadBalancingAlgorithm implements
        Config {

    public MasterWorker(Satin s) {
        super(s);
    }

    /** Daniela:
     * 
     * @param ct 
     */
    public MasterWorker(ClientThread ct) {
        super(ct);
    }

    public InvocationRecord clientIteration() {
        Victim v;

        if (satin.isMaster()) {
            return null;
        }

        synchronized (satin) {
            v = satin.victims.getVictim(satin.getMasterIdent());
        }

        if (v == null) return null; // node might have crashed
        
        //DAniela:
            //satin.lb.setCurrentVictim(v.getIdent());
            //return satin.lb.stealJob(v, true); // blocks at the server side
            clientThread.lb.setCurrentVictim(v.getIdent());
            return clientThread.lb.stealJob(v, true);        
    }

    public void jobAdded() {
        synchronized (satin) {
            if (!satin.isMaster()) {
                spawnLogger.error("with the master/worker algorithm, "
                    + "work can only be spawned on the master!");
                System.exit(1); // Failed assertion
            }

            satin.notifyAll();
        }
    }

    public void exit() {
        synchronized (satin) {
            satin.notifyAll();
        }
    }
}
