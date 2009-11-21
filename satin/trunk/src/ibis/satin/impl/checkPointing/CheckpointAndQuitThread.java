package ibis.satin.impl.checkPointing;

import ibis.satin.impl.Satin;

public class CheckpointAndQuitThread extends Thread {

    int milis; //wait that long before dying

    public CheckpointAndQuitThread(int time) {
        super("CheckpointAndQuitThread");
        this.milis = time * 1000;
    }

    public void run() {
        try {
            sleep(milis);
        } catch (InterruptedException e) {	    
            //ignore
        }
        Satin satin = Satin.getSatin();
        satin.ft.checkpointAndQuit();	        
    }

}
