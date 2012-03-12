/*
 * Created on Apr 26, 2006 by rob
 */
package ibis.satin.impl.communication;

import ibis.ipl.AlreadyConnectedException;
import ibis.ipl.Ibis;
import ibis.ipl.IbisCapabilities;
import ibis.ipl.IbisFactory;
import ibis.ipl.IbisIdentifier;
import ibis.ipl.PortType;
import ibis.ipl.ReadMessage;
import ibis.ipl.ReceivePort;
import ibis.ipl.ReceivePortIdentifier;
import ibis.ipl.Registry;
import ibis.ipl.SendPort;
import ibis.ipl.SendPortIdentifier;
import ibis.ipl.WriteMessage;
import ibis.satin.impl.Config;
import ibis.satin.impl.Satin;
import ibis.satin.impl.Statistics;
import ibis.satin.impl.loadBalancing.Victim;
import ibis.satin.impl.sharedObjects.SharedObjects;

import java.io.IOException;
import java.net.InetAddress;

public final class Communication implements Config, Protocol {

    private static final long EXIT_TIMEOUT = 60000;

    private Satin s;

    public PortType portType;

    public ReceivePort receivePort;

    private volatile boolean exitStageTwo = false;

    private volatile int barrierRequests = 0;

    private volatile boolean gotBarrierReply = false;

    private volatile int exitReplies = 0;

    public Ibis ibis;

    public boolean paused = false;

    public Communication(Satin s) {
        this.s = s;

        IbisCapabilities ibisProperties = createIbisCapabilities();

        commLogger.debug("SATIN '" + "- " + "': init ibis");

        portType = createSatinPortType();

        try {
            ibis = IbisFactory.createIbis(ibisProperties, null, true, s.ft
                    .getRegistryEventHandler(), portType, SharedObjects
                    .getSOPortType());
        } catch (Exception e) {
            s.assertFailed("Could not start ibis: ", e);
        }

        IbisIdentifier ident = ibis.identifier();

        commLogger.debug("SATIN '" + "- " + "': init ibis DONE, "
                + "my cluster is '" + Victim.clusterOf(ident) + "'");

        try {
            MessageHandler messageHandler = new MessageHandler(s);

            receivePort = ibis.createReceivePort(portType, "satin port",
                    messageHandler, s.ft.getReceivePortConnectHandler(), null);
        } catch (Exception e) {
            s.assertFailed("Could not start ibis: ", e);
        }

        if (CLOSED) {
            commLogger.info("SATIN '" + ident
                    + "': running with closed world, "
                    + ibis.registry().getPoolSize() + " host(s)");
        } else {
            commLogger.info("SATIN '" + ident + "': running with open world");
        }
    }

    public IbisIdentifier electMaster() {
        String election = "satin.master";
        Registry r = ibis.registry();
        IbisIdentifier winner = null;

        String canonicalMasterHost = null;
        String localHostName = null;

        while (winner == null) {
            if (MASTER_HOST != null) {
                try {
                    InetAddress a = InetAddress.getByName(MASTER_HOST);
                    canonicalMasterHost = a.getCanonicalHostName();
                } catch (Exception e) {
                    commLogger.warn("satin.masterhost is set to an unknown "
                            + "name: " + MASTER_HOST);
                    commLogger.warn("continuing with default election");
                }
                try {
                    localHostName = InetAddress.getLocalHost()
                            .getCanonicalHostName();
                } catch (Exception e) {
                    commLogger.warn("Could not get local hostname");
                    canonicalMasterHost = null;
                }

                try {
                    if (canonicalMasterHost == null
                            || !canonicalMasterHost.equals(localHostName)) {
                        winner = r.getElectionResult(election);
                    } else {
                        winner = r.elect(election);
                    }
                } catch (Exception e) {
                    s.assertFailed("Could not do an election for " + election
                            + ": ", e);
                }
            } else if (CLIENT) {
                try {
                    winner = r.getElectionResult(election);
                } catch (Exception e) {
                    s.assertFailed("Could not do an election for " + election
                            + ": ", e);
                }
            } else {
                try {
                    winner = r.elect(election);
                } catch (Exception e) {
                    s.assertFailed("Could not do an election for " + election
                            + ": ", e);
                }
            }
        }

        return winner;
    }

    public IbisIdentifier elect(String election) {
        Registry r = ibis.registry();
        IbisIdentifier winner = null;

        while (winner == null) {
            try {
                winner = r.elect(election);
            } catch (Exception e) {
                s.assertFailed("Could not do an election for " + election
                        + ": ", e);
            }
        }

        return winner;
    }

    public void enableConnections() {
        receivePort.enableMessageUpcalls();
        receivePort.enableConnections();
    }

    public IbisCapabilities createIbisCapabilities() {
        if (CLOSED) {
            return new IbisCapabilities(IbisCapabilities.CLOSED_WORLD,
                    IbisCapabilities.MEMBERSHIP_TOTALLY_ORDERED,
                    IbisCapabilities.ELECTIONS_STRICT);
        } else if (UNRELIABLE) {
            // NOTE: using this breaks both lrmc and the master election.
            return new IbisCapabilities(IbisCapabilities.MEMBERSHIP_UNRELIABLE,
                    IbisCapabilities.ELECTIONS_UNRELIABLE, IbisCapabilities.SIGNALS);
        } else {
            return new IbisCapabilities(
                    IbisCapabilities.MEMBERSHIP_TOTALLY_ORDERED,
                    IbisCapabilities.ELECTIONS_STRICT);
        }
    }

    public PortType createSatinPortType() {
        return new PortType(PortType.SERIALIZATION_OBJECT,
                PortType.COMMUNICATION_RELIABLE,
                PortType.CONNECTION_MANY_TO_ONE, PortType.CONNECTION_UPCALLS,
                PortType.RECEIVE_EXPLICIT, PortType.RECEIVE_AUTO_UPCALLS);
    }

    public void bcastMessage(byte opcode) {
        Victim[] victims;
        synchronized (s) {
            victims = s.victims.victims();
        }


        for (int i = 0; i < victims.length; i++) {
            WriteMessage writeMessage = null;
            Victim v = victims[i];
            commLogger.debug("SATIN '" + s.ident + "': sending "
                    + opcodeToString(opcode) + " message to " + v.getIdent());

            try {
                writeMessage = v.newMessage();
                writeMessage.writeByte(opcode);
                v.finish(writeMessage);

            } catch (IOException e) {
                if (writeMessage != null) {
                    writeMessage.finish(e);
                }
                synchronized (s) {
                    ftLogger.info("SATIN '" + s.ident
                            + "': could not send bcast message to "
                            + v.getIdent(), e);
                    try {
                        ibis.registry().maybeDead(v.getIdent());
                    } catch (IOException e2) {
                        ftLogger.warn("SATIN '" + s.ident
                                + "': got exception in maybeDead", e2);
                    }
                }
            }
        }

	if (UNRELIABLE && opcode == EXIT) {
	    IbisIdentifier[] ibises;
	  
	   synchronized(s) { 
	    ibises = s.victims.getIbises();
	   }
	   try {
	    ibis.registry().signal("EXIT", ibises);
	     } catch (IOException e) {
		 System.err.println("error while signalling" + e);
	     }
	}

    }

    public static void disconnect(SendPort s, ReceivePortIdentifier ident) {
        try {
            s.disconnect(ident);
        } catch (IOException e) {
            // ignored
        }
    }

    public static ReceivePortIdentifier connect(SendPort s,
            IbisIdentifier ident, String name, long timeoutMillis) {

        long startTime = System.currentTimeMillis();
        long deadLine = startTime + timeoutMillis;
        long timeLeft = deadLine - System.currentTimeMillis();
        ReceivePortIdentifier r = null;
        IbisIdentifier id = s.identifier().ibisIdentifier();
        connLogger.info("SATIN '" + id + "': connecting to " + name + " at "
                + ident);
        while (r == null && timeLeft > 0) {
            try {
                r = s.connect(ident, name, timeLeft, false);
            } catch (AlreadyConnectedException x) {
                connLogger.info("SATIN '" + id + "': already connected to "
                        + name + " at " + ident, x);
                ReceivePortIdentifier[] ports = s.connectedTo();
                for (int i = 0; i < ports.length; i++) {
                    if (ports[i].ibisIdentifier().equals(ident)
                            && ports[i].name().equals(name)) {
                        connLogger
                                .info("SATIN '"
                                        + id
                                        + "': the port was already connected, found it");
                        return ports[i];
                    }
                }
                connLogger
                        .info("SATIN '"
                                + id
                                + "': the port was already connected, but could not find it, retry!");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e2) {
                    // ignore
                }
                // return null;
            } catch (IOException e) {
                connLogger
                        .info("SATIN '" + id + "': IOException in connect to "
                                + ident + ": " + e, e);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e2) {
                    // ignore
                }
            }
            timeLeft = deadLine - System.currentTimeMillis();
        }

        if (r == null) {
            connLogger.info("could not connect port within given time ("
                    + timeoutMillis + " ms)");
        }
        return r;
    }

    /* Only allowed when not stealing. And with a closed world */
    private void barrier() {
        IbisIdentifier ident = ibis.identifier();
        commLogger.debug("SATIN '" + ident + "': barrier start");

        int size;
        synchronized (s) {
            size = s.victims.size();
        }

        try {
            if (s.isMaster()) {
                synchronized (s) {
                    while (barrierRequests != size) {
                        try {
                            s.wait();
                        } catch (Exception e) {
                            // ignore
                        }
                    }

                    barrierRequests = 0;
                }

                for (int i = 0; i < size; i++) {
                    Victim v;
                    WriteMessage writeMessage = null;
                    synchronized (s) {
                        v = s.victims.getVictim(i);
                    }
                    if (v == null) {
                        s.assertFailed("a machine crashed with closed world",
                                new Exception());
                    }

                    try {
                        writeMessage = v.newMessage();
                        writeMessage.writeByte(Protocol.BARRIER_REPLY);
                        v.finish(writeMessage);
                    } catch (IOException e) {
                        if (writeMessage != null) {
                            writeMessage.finish(e);
                        }
                        throw e;
                    }
                }
            } else {
                Victim v;

                synchronized (s) {
                    v = s.victims.getVictim(s.getMasterIdent());
                }

                if (v == null) {
                    s.assertFailed("could not get master victim.",
                            new Exception());
                }

                WriteMessage writeMessage = null;
                try {
                    writeMessage = v.newMessage();
                    writeMessage.writeByte(Protocol.BARRIER_REQUEST);
                    writeMessage.finish();
                } catch (IOException e) {
                    if (writeMessage != null) {
                        writeMessage.finish(e);
                    }
                    throw e;
                }

                while (!gotBarrierReply/* && !exiting */) {
                    s.handleDelayedMessages();
                }
                /*
                 * Imediately reset gotBarrierReply, we know that a reply has
                 * arrived.
                 */
                gotBarrierReply = false;
            }
        } catch (IOException e) {
            commLogger.warn("SATIN '" + ident + "': error in barrier", e);
        }

        commLogger.debug("SATIN '" + ident + "': barrier DONE");
    }

    public void waitForExitReplies() {
        long deadline = Long.MAX_VALUE;

        if (UNRELIABLE) {
            deadline = System.currentTimeMillis() + EXIT_TIMEOUT;
        }

        int size;
        synchronized (s) {
            size = s.victims.size();
        }

        // wait until everybody has send an ACK
        synchronized (s) {
            while (exitReplies != size && System.currentTimeMillis() < deadline) {
                try {
                    s.handleDelayedMessages();
                    s.wait(250);
                } catch (Exception e) {
                    // Ignore.
                }
                size = s.victims.size();
            }
        }
    }

    public void sendExitAck() {
        Victim v = null;
        WriteMessage writeMessage = null;

        synchronized (s) {
            v = s.victims.getVictim(s.getMasterIdent());
        }

        if (v == null)
            return; // node might have crashed

        try {
            commLogger.debug("SATIN '" + s.ident
                    + "': sending exit ACK message to " + s.getMasterIdent());

            writeMessage = v.newMessage();
            writeMessage.writeByte(Protocol.EXIT_REPLY);
            if (STATS) {
                s.stats.fillInStats();
                writeMessage.writeObject(s.stats);
            }
            v.finish(writeMessage);
        } catch (IOException e) {
            if (writeMessage != null) {
                writeMessage.finish(e);
            }
            ftLogger.info(
                    "SATIN '" + s.ident + "': could not send exit message to "
                            + s.getMasterIdent(), e);
            try {
                ibis.registry().maybeDead(s.getMasterIdent());
            } catch (IOException e2) {
                ftLogger.warn("SATIN '" + s.ident
                        + "': got exception in maybeDead", e2);
            }
        }
    }

    public void waitForExitStageTwo() {
        long deadline = Long.MAX_VALUE;

        if (UNRELIABLE) {
            deadline = System.currentTimeMillis() + EXIT_TIMEOUT;
        }

        synchronized (s) {
            while (!exitStageTwo && System.currentTimeMillis() < deadline) {
                try {
                    s.handleDelayedMessages();
                    s.wait(250);
                } catch (Exception e) {
                    // Ignore.
                }
            }
        }
    }

    public void closeSendPorts() {
        // If not closed, free ports. Otherwise, ports will be freed in leave
        // calls.
        while (true) {
            try {
                Victim v;

                synchronized (s) {
                    if (s.victims.size() == 0) {
                        break;
                    }

                    v = s.victims.remove(0);
                }

                if (v != null) {
                    commLogger.debug("SATIN '" + s.ident
                            + "': closing sendport to " + v.getIdent());
                    v.close();
                }

            } catch (Throwable e) {
                commLogger.warn("SATIN '" + s.ident
                        + "': port.close() throws exception", e);
            }
        }
    }

    public void closeReceivePort() {
        try {
            receivePort.close(10000);
        } catch (Throwable e) {
            commLogger.warn("SATIN '" + s.ident
                    + "': port.close() throws exception", e);
        }
    }

    public void end() {
        try {
            ibis.end();
        } catch (Throwable e) {
            commLogger.warn("SATIN '" + s.ident
                    + "': ibis.end() throws exception", e);
        }
    }

    public void waitForAllNodes() {
        commLogger.debug("SATIN '" + s.ident + "': pre barrier");

        int poolSize = ibis.registry().getPoolSize();

        synchronized (s) {
            while (s.victims.size() != poolSize - 1) {
                try {
                    s.wait();
                } catch (InterruptedException e) {
                    // Ignore.
                }
            }
            commLogger.debug("SATIN '" + s.ident
                    + "': barrier, everybody has joined");
        }

        barrier();

        commLogger.debug("SATIN '" + s.ident + "': post barrier");
    }

    public void handleExitReply(ReadMessage m) {

        SendPortIdentifier ident = m.origin();

        commLogger.debug("SATIN '" + s.ident + "': got exit ACK message from "
                + ident.ibisIdentifier());

        if (STATS) {
            try {
                Statistics stats = (Statistics) m.readObject();
                s.totalStats.add(stats);
            } catch (Exception e) {
                commLogger.warn("SATIN '" + s.ident
                        + "': Got Exception while reading stats: " + e, e);
            }
        }

        try {
            m.finish();
        } catch (Exception e) {
            /* ignore */
        }

        synchronized (s) {
            exitReplies++;
            s.notifyAll();
        }
    }

    public void handleExitMessage(IbisIdentifier ident) {
        commLogger.debug("SATIN '" + s.ident + "': got exit message from "
                + ident);

        synchronized (s) {
            s.exiting = true;
            s.notifyAll();
        }
    }

    public void handleExitStageTwoMessage(IbisIdentifier ident) {
        commLogger.debug("SATIN '" + s.ident + "': got exit2 message from "
                + ident);

        synchronized (s) {
            exitStageTwo = true;
            s.notifyAll();
        }
    }

    public void handleBarrierRequestMessage() {
        synchronized (s) {
            barrierRequests++;
            s.notifyAll();
        }
    }

    public void disableUpcallsForExit() {
        if (!CLOSED) {
            ibis.registry().disableEvents();
        }

        s.ft.disableConnectionUpcalls();
    }

    public void handleBarrierReply(IbisIdentifier sender) {
        commLogger.debug("SATIN '" + s.ident
                + "': got barrier reply message from " + sender);

        synchronized (s) {
            if (ASSERTS && gotBarrierReply) {
                s.assertFailed("Got barrier reply while I already got "
                        + "one.", new Exception());
            }
            gotBarrierReply = true;
            s.notifyAll();
        }
    }

    public static String opcodeToString(int opcode) {
        switch (opcode) {
        case EXIT:
            return "EXIT";
        case EXIT_REPLY:
            return "EXIT_REPLY";
        case BARRIER_REPLY:
            return "BARRIER_REPLY";
        case STEAL_REQUEST:
            return "STEAL_REQUEST";
        case STEAL_REPLY_FAILED:
            return "STEAL_REPLY_FAILED";
        case STEAL_REPLY_SUCCESS:
            return "STEAL_REPLY_SUCCESS";
        case ASYNC_STEAL_REQUEST:
            return "ASYNC_STEAL_REQUEST";
        case ASYNC_STEAL_REPLY_FAILED:
            return "ASYNC_STEAL_REPLY_FAILED";
        case ASYNC_STEAL_REPLY_SUCCESS:
            return "ASYNC_STEAL_REPLY_SUCCESS";
        case JOB_RESULT_NORMAL:
            return "JOB_RESULT_NORMAL";
        case JOB_RESULT_EXCEPTION:
            return "JOB_RESULT_EXCEPTION";
        case ABORT:
            return "ABORT";
        case BLOCKING_STEAL_REQUEST:
            return "BLOCKING_STEAL_REQUEST";
        case CRASH:
            return "CRASH";
        case ABORT_AND_STORE:
            return "ABORT_AND_STORE";
        case RESULT_REQUEST:
            return "RESULT_REQUEST";
        case STEAL_AND_TABLE_REQUEST:
            return "STEAL_AND_TABLE_REQUEST";
        case ASYNC_STEAL_AND_TABLE_REQUEST:
            return "ASYNC_STEAL_AND_TABLE_REQUEST";
        case STEAL_REPLY_FAILED_TABLE:
            return "STEAL_REPLY_FAILED_TABLE";
        case STEAL_REPLY_SUCCESS_TABLE:
            return "STEAL_REPLY_SUCCESS_TABLE";
        case ASYNC_STEAL_REPLY_FAILED_TABLE:
            return "ASYNC_STEAL_REPLY_FAILED_TABLE";
        case ASYNC_STEAL_REPLY_SUCCESS_TABLE:
            return "ASYNC_STEAL_REPLY_SUCCESS_TABLE";
        case RESULT_PUSH:
            return "RESULT_PUSH";
        case SO_INVOCATION:
            return "SO_INVOCATION";
        case SO_REQUEST:
            return "SO_REQUEST";
        case SO_TRANSFER:
            return "SO_TRANSFER";
        case EXIT_STAGE2:
            return "EXIT_STAGE2";
        case BARRIER_REQUEST:
            return "BARRIER_REQUEST";
        }

        throw new Error("unknown opcode in opcodeToString");
    }

    // FIXME send a resume after a crash

    public void pause() {
        paused = true;

        soBcastLogger.info("SATIN '" + s.ident + "': sending pause");
        Victim[] victims;
        synchronized (s) {
            victims = s.victims.victims();

        }
        for (int i = 0; i < victims.length; i++) {
            try {
                WriteMessage m = victims[i].newMessage();
                m.writeByte(PAUSE);
                victims[i].finish(m);
            } catch (IOException e) {
                commLogger.warn("SATIN '" + s.ident
                        + "': could not send pause message: " + e);
                // ignore
            }
        }
    }

    public void resume() {
        soBcastLogger.info("SATIN '" + s.ident + "': sending resume");

        Victim[] victims;
        synchronized (s) {
            victims = s.victims.victims();

        }
        for (int i = 0; i < victims.length; i++) {
            try {
                WriteMessage m = victims[i].newMessage();
                m.writeByte(RESUME);
                victims[i].finish(m);
            } catch (IOException e) {
                commLogger.info("SATIN '" + s.ident
                        + "': could not send pause message: " + e);
                // ignore
            }

        }
        paused = false;
    }

    void gotPause() {
        soBcastLogger.debug("SATIN '" + s.ident + "': got pause");

        synchronized (s) {
            paused = true;
            s.notifyAll();
        }
    }

    void gotResume() {
        soBcastLogger.debug("SATIN '" + s.ident + "': got resume");

        synchronized (s) {
            paused = false;
            s.notifyAll();
        }
    }
}
