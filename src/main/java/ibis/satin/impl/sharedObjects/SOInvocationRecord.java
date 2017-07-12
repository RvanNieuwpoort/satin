/* $Id: SOInvocationRecord.java 15353 2015-01-28 10:07:31Z ceriel $ */

package ibis.satin.impl.sharedObjects;

import ibis.satin.SharedObject;

public abstract class SOInvocationRecord implements java.io.Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private String objectId;

    public SOInvocationRecord(String objectId) {
	this.objectId = objectId;
    }

    protected abstract void invoke(SharedObject object);

    protected String getObjectId() {
	return objectId;
    }
}
