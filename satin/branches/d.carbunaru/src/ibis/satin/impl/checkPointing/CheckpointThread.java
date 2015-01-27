package ibis.satin.impl.checkPointing;

import ibis.satin.impl.Satin;

public class CheckpointThread extends Thread {

    int milis;
    int firstTime;
    boolean stopped;

    public CheckpointThread(int milis, int firstTime) {
	super("SatinCheckpointThread");
	this.milis = milis;
	this.firstTime = firstTime;
	stopped = false;
    }

    public void setExitCondition(boolean stop) {
	this.stopped = stop;
    }

    public void run() {
	Satin satin = Satin.getSatin();
	// boolean checkpointPush = satin.CHECKPOINT_PUSH;
	try {
	    sleep(firstTime);
	} catch (InterruptedException e) {
	    System.out.println("CheckpointThread interrupted for some reason");
	}
	while (true) {
	    if (stopped) {
		return;
	    } else {
		satin.ft.takeCheckpoint = true;
	    }
	    try {
		sleep(milis);
	    } catch (InterruptedException e) {
		System.out
			.println("CheckpointThread interrupted for some reason");
	    }
	}
    }
}
