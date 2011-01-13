/* $Id$ */

package ibis.satin.impl.loadBalancing;

import ibis.ipl.IbisIdentifier;
import ibis.ipl.ReceivePortIdentifier;
import ibis.ipl.SendPort;
import ibis.ipl.WriteMessage;
import ibis.satin.impl.Config;
import ibis.satin.impl.Satin;
import ibis.satin.impl.communication.Communication;

import java.io.IOException;

/**
 * 
 * @author rob
 *
 * A Victim represents an Ibis we can steal work from.
 * This class is immutable, only the sendport itself could be connected and 
 * disconnected.
 *  
 */
public final class Victim implements Config {

    // @@@ TODO should be synchronized on static object, not sendport! 
    private static volatile int connectionCount = 0;

    private IbisIdentifier ident;

    private SendPort sendPort;

    private ReceivePortIdentifier r;

    private boolean connected = false;

    private boolean closed = false;

    private final boolean inDifferentCluster;

    private int referenceCount = 0;
    
    private long suspectedTime = 0;
    
    public Victim(IbisIdentifier ident, SendPort s) {
        this.ident = ident;
        this.sendPort = s;
        if (s != null) {
            inDifferentCluster = !clusterOf(ident).equals(clusterOf(s.identifier().ibisIdentifier()));
        } else {
            inDifferentCluster = false;
        }
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof Victim) {
            Victim other = (Victim) o;
            return other.ident.equals(ident);
        }
        return false;
    }

    public boolean equals(Victim other) {
        if (other == this) {
            return true;
        }
        return other.ident.equals(ident);
    }

    public int hashCode() {
        return ident.hashCode();
    }

    public String toString() {
        return ident.toString();
    }
    
    public boolean isConnected() {
        return connected;
    }

    private void disconnect() throws IOException {
        if (connected) {
            connected = false;
            connectionCount--;
            sendPort.disconnect(r);
        }
    }

    public void connect() {
        synchronized (sendPort) {
            getSendPort();
        }
    }
    
    private SendPort getSendPort() {
        if (closed) {
            return null;
        }

        
        
        if (!connected) {
   
          	// FIXME: this is for debugging!!!
        	//
        	// Lets give us a 10% change of failing! 
            
        	//if (Math.random() < 0.1) {        		
        	//	System.err.println("XXXXX FTTEST XXXXX -- Refusing connection to " + ident);        		        		
        	//	return null;
        	//}
        	
        	r = Communication.connect(sendPort, ident, "satin port",
                Satin.CONNECT_TIMEOUT);
            if (r == null) {
                commLogger.warn("SATIN '" + sendPort.identifier().ibisIdentifier()
                    + "': unable to connect to " + ident
                    + ", might have crashed");
                
                // ADDED: not sure if this is the most appropriate spot... -- Jason 
                Satin.getSatin().ft.unreachableIbis(ident, null);
                return null;
            }

            // We've managed to get a connection, so we may reset the 'suspected' field 
            // (if it was set in the first place) 
            suspectedTime = 0;            
            connected = true;
            connectionCount++;

            // ADDED: not sure if this is the most appropriate spot... -- Jason 
            Satin.getSatin().ft.reachableIbis(ident);
        } 
        /*else {
        	
        	// FIXME: this is for debugging!!!
        	//
        	// Lets give the connection a 10% change of failing! 
            
        	if (Math.random() < 0.1) {        		
        		System.err.println("XXXXX FTTEST XXXXX -- Dropping connection to " + ident);        		        		
        
        		try {
					disconnect();
				} catch (IOException e) {
					e.printStackTrace();
				}
        		return null;
        	}
        }*/
        
        return sendPort;
    }

    public WriteMessage newMessage() throws IOException {
        SendPort send;

        synchronized (sendPort) {
            send = getSendPort();
            if (send != null) {
                referenceCount++;
            } else {
                throw new IOException("SATIN '" + sendPort.identifier().ibisIdentifier()
                        + "': Could not connect to " + ident);
            }
        }
        
        return send.newMessage();
    }

    public long finish(WriteMessage m) throws IOException {
        try {
            long cnt = m.finish();
            if (inDifferentCluster) {
                Satin.addInterClusterStats(cnt);
            } else {
                Satin.addIntraClusterStats(cnt);
            }
            return cnt;
        }  finally {
            synchronized (sendPort) {
                referenceCount--;
                optionallyDropConnection();
            }
        }
    }

    private void optionallyDropConnection() throws IOException {
        if (CLOSE_CONNECTIONS) {
            if (referenceCount == 0) {
                if (KEEP_INTRA_CONNECTIONS) {
                    if (inDifferentCluster) {
                        disconnect();
                    }
                    return;
                } 
                if (connectionCount >= MAX_CONNECTIONS) {
                    disconnect();
                }
            }
        }
    }

    // This  will be called when we could not create or lost a connection. It attempts 
    // to reset the victim to a decent state, and marks it as being 'fishy'
    //   -- Jason
    
    public void setSuspected() {
    	
    	// Should this be locked ???
    	suspectedTime = System.currentTimeMillis();

    	try { 
    		disconnect();
    	} catch (IOException e) { 
    		// ignore ?
    	}
    }

    public boolean isSuspected() {
    	
    	if (suspectedTime == 0) { 
    		return false;
    	}
    
    	if (System.currentTimeMillis() - suspectedTime > MAX_SUSPICION_TIMEOUT) { 
    		suspectedTime = 0;
    		return false;
    	}
    	
    	return true;
    }
    
    public void close() {
        synchronized (sendPort) {
            if (connected) {
                connected = false;
                connectionCount--;
            }
            closed = true;
            try {
                sendPort.close();
            } catch (Exception e) {
                // ignore
                Config.commLogger.warn("SATIN '" + sendPort.identifier().ibisIdentifier()
                    + "': port.close() throws exception (ignored)", e);
            }
        }
    }

    public IbisIdentifier getIdent() {
        return ident;
    }

    public static String clusterOf(IbisIdentifier id) {
        return id.location().getParent().toString();
    }
}