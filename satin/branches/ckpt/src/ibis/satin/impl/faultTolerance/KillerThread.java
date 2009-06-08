/* $Id$ */

package ibis.satin.impl.faultTolerance;

import ibis.satin.impl.Config;
import ibis.satin.impl.Satin;

class KillerThread extends Thread implements Config {

    private int milis; //wait that long before dying

//    private String cluster = null; //die only if your are in this cluster

    KillerThread(int time) {
        super("SatinKillerThread");
        this.milis = time * 1000;
    }

    public void run() {
        try {
            sleep(milis);
        } catch (InterruptedException e) {
            //ignore
        }
        // Satin satin = Satin.this_satin;
        // if (satin.allIbises.indexOf(satin.ident)
        //         >= (satin.allIbises.size() / 2)) {
        if (STATS && DETAILED_STATS) {
            Satin.getSatin().stats.printDetailedStats(Satin.getSatin().ident);
        }
        System.exit(1); // Kills this satin on purpose, this is a killerthread!
    }

}
