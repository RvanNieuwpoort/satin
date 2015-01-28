package ibis.satin.impl.checkPointing;

import ibis.ipl.IbisIdentifier;
import ibis.satin.impl.spawnSync.ReturnRecord;

/**
 * A Checkpoint contains a ReturnRecord and an IbisIdentifier, telling which
 * node sent the checkpoint. This data is needed when the checkpoint file is
 * written;
 **/

public class Checkpoint implements java.io.Serializable {

    /**
     * if serialVersionUID is not defined here, java redefines it for every new
     * build of Satin. Different serialVersionUID's can't be read by different
     * Satin builds. That would make checkpoint files created by a different
     * satin build useless
     **/
    private static final long serialVersionUID = 12345;

    public ReturnRecord rr;

    public IbisIdentifier sender;

    public Checkpoint(ReturnRecord rr, IbisIdentifier sender) {
	this.rr = rr;
	this.sender = sender;
    }
}
