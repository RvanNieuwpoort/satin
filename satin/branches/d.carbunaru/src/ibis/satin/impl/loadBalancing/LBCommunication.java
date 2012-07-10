/*
 * Created on Apr 26, 2006 by rob
 */
package ibis.satin.impl.loadBalancing;

import ibis.ipl.IbisIdentifier;
import ibis.ipl.ReadMessage;
import ibis.ipl.SendPortIdentifier;
import ibis.ipl.WriteMessage;
import ibis.satin.impl.ClientThread;
import ibis.satin.impl.Config;
import ibis.satin.impl.Satin;
import ibis.satin.impl.communication.Communication;
import ibis.satin.impl.communication.Protocol;
import ibis.satin.impl.faultTolerance.GlobalResultTableValue;
import ibis.satin.impl.spawnSync.InvocationRecord;
import ibis.satin.impl.spawnSync.ReturnRecord;
import ibis.satin.impl.spawnSync.Stamp;
import ibis.util.Timer;

import java.io.IOException;
import java.io.InvalidClassException;
import java.io.NotSerializableException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

final class LBCommunication implements Config, Protocol {

    private final Satin s;
    private ClientThread ct;
    private LoadBalancing lb;

    protected LBCommunication(Satin s, LoadBalancing lb) {
        this.s = s;
        this.lb = lb;
    }

    /**
     * Daniela:
     *
     * @param ct
     * @param lb
     */
    protected LBCommunication(ClientThread ct, LoadBalancing lb) {
        this.ct = ct;
        this.s = ct.satin;
        this.lb = lb;
    }

    protected void sendStealRequest(Victim v, boolean synchronous,
            boolean blockUntilWorkIsAvailable) throws IOException {
        if (stealLogger.isDebugEnabled()) {
            stealLogger.debug("SATIN '" + s.ident
                    + (ct != null ? (" Thread " + ct.id) : " master")
                    + "': sending "
                    + (synchronous ? "SYNC" : "ASYNC") + "steal message to "
                    + v.getIdent());
        }

        WriteMessage writeMessage = v.newMessage();
        byte opcode = -1;

        if (synchronous) {
            if (blockUntilWorkIsAvailable) {
                opcode = Protocol.BLOCKING_STEAL_REQUEST;
            } else {
                if (!FT_NAIVE) {
                    synchronized (s) {
                        if (s.ft.getTable) {
                            opcode = Protocol.STEAL_AND_TABLE_REQUEST;
                        } else {
                            opcode = Protocol.STEAL_REQUEST;
                        }
                    }
                } else {
                    opcode = Protocol.STEAL_REQUEST;
                }
            }
        } else {
            if (!FT_NAIVE) {
                synchronized (s) {
                    if (s.clusterCoordinator && s.ft.getTable) {
                        opcode = Protocol.ASYNC_STEAL_AND_TABLE_REQUEST;
                    } else {
                        if (s.ft.getTable) {
                            if (grtLogger.isInfoEnabled()) {
                                grtLogger.info("SATIN '" + s.ident
                                        + ": EEEK sending async steal message "
                                        + "while waiting for table!!");
                            }
                        }
                        opcode = Protocol.ASYNC_STEAL_REQUEST;
                    }
                }
            } else {
                opcode = Protocol.ASYNC_STEAL_REQUEST;
            }
        }

        // Daniela: each thread registers itself to the Satin object
        // that it has sent a stealrequest, so that when the steal response
        // comes back, the Satin LB object knows to which thread to send it.

        synchronized (s) {
            if (s.waitingStealMap.containsKey(v.getIdent())) {
                List<Integer> list = s.waitingStealMap.get(v.getIdent());
                if (ct != null) {
                    list.add(ct.id);
                } else {
                    list.add(-1);
                }
            } else {
                List<Integer> list = new LinkedList<Integer>();
                if (ct != null) {
                    list.add(ct.id);
                } else {
                    list.add(-1);
                }
                s.waitingStealMap.put(v.getIdent(), list);
            }
        }


        try {
            writeMessage.writeByte(opcode);
            v.finish(writeMessage);
        } catch (IOException e) {
            writeMessage.finish(e);
            throw e;
        }
    }

    protected void handleJobResult(ReadMessage m, int opcode) {
        ReturnRecord rr = null;
        Stamp stamp = null;
        Throwable eek = null;
        Timer returnRecordReadTimer = null;
        boolean gotException = false;

        if (stealLogger.isInfoEnabled()) {
            stealLogger.info("SATIN '" + s.ident
                    + "': got job result message from "
                    + m.origin().ibisIdentifier());
        }

        // This upcall may run in parallel with other upcalls.
        // Therefore, we cannot directly use the timer in Satin.
        // Use our own local timer, and add the result to the global timer
        // later.

        returnRecordReadTimer = Timer.createTimer();
        returnRecordReadTimer.start();
        try {
            if (opcode == JOB_RESULT_NORMAL) {
                rr = (ReturnRecord) m.readObject();
                stamp = rr.getStamp();
                eek = rr.getEek();
            } else {
                eek = (Throwable) m.readObject();
                stamp = (Stamp) m.readObject();
            }
            // m.finish();
        } catch (Exception e) {
            spawnLogger.error("SATIN '" + s.ident
                    + "': got exception while reading job result: " + e
                    + opcode, e);
            gotException = true;
        } finally {
            returnRecordReadTimer.stop();
        }
        s.stats.returnRecordReadTimer.add(returnRecordReadTimer);

        if (gotException) {
            return;
        }

        if (stealLogger.isInfoEnabled()) {
            if (eek != null) {
                stealLogger.info("SATIN '" + s.ident
                        + "': handleJobResult: exception result: " + eek
                        + ", stamp = " + stamp, eek);
            } else {
                stealLogger.info("SATIN '" + s.ident
                        + "': handleJobResult: normal result, stamp = " + stamp);
            }
        }


        // Daniela
        int threadId = s.stampToThreadIdMap.get(stamp);
        synchronized (s.stampToThreadIdMap) {
            s.stampToThreadIdMap.remove(stamp);
        }

        if (threadId == -1) {
            lb.addJobResult(rr, eek, stamp);
        } else {
            s.clientThreads[threadId].lb.addJobResult(rr, eek, stamp);
        }
    }

    protected void sendResult(InvocationRecord r, ReturnRecord rr) {
        if (/*
                 * exiting ||
                 */r.alreadySentExceptionResult()) {
            return;
        }

        if (stealLogger.isInfoEnabled()) {
            stealLogger.info("SATIN '" + s.ident
                    + (ct != null ? (" Thread " + ct.id) : " master")
                    + "': sending job result to "
                    + r.getOwner() + ", exception = "
                    + (r.eek == null ? "null" : ("" + r.eek))
                    + ", stamp = " + r.getStamp());
        }

        Victim v = null;

        synchronized (s) {

            if (!FT_NAIVE && r.isOrphan()) {
                IbisIdentifier owner = s.ft.lookupOwner(r);
                if (ASSERTS && owner == null) {
                    grtLogger.error("SATIN '" + s.ident
                            + "': orphan not locked in the table");
                    System.exit(1); // Failed assertion
                }
                r.setOwner(owner);
                if (grtLogger.isInfoEnabled()) {
                    grtLogger.info("SATIN '" + s.ident
                            + "': storing an orphan");
                }
                s.ft.storeResult(r);
            }
            if (ct == null) {
                v = s.victims.getVictim(r.getOwner());
            } else {
                v = ct.victims.getVictim(r.getOwner());
            }
        }

        if (v == null) {
            //probably crashed..
            if (!FT_NAIVE && !r.isOrphan()) {
                synchronized (s) {
                    s.ft.storeResult(r);
                }
                if (grtLogger.isInfoEnabled()) {
                    grtLogger.info("SATIN '" + s.ident
                            + "': a job became an orphan??");
                }
            }
            return;
        }

        if (ct == null) {
            s.stats.returnRecordWriteTimer.start();
        } else {
            ct.stats.returnRecordWriteTimer.start();
        }
        WriteMessage writeMessage = null;
        try {
            writeMessage = v.newMessage();
            if (r.eek == null) {
                writeMessage.writeByte(Protocol.JOB_RESULT_NORMAL);
                writeMessage.writeObject(rr);
            } else {
                if (rr == null) {
                    r.setAlreadySentExceptionResult(true);
                }
                writeMessage.writeByte(Protocol.JOB_RESULT_EXCEPTION);
                writeMessage.writeObject(r.eek);
                writeMessage.writeObject(r.getStamp());
            }

            long cnt = v.finish(writeMessage);
            if (ct == null) {
                s.stats.returnRecordBytes += cnt;
            } else {
                ct.stats.returnRecordBytes += cnt;
            }

        } catch (IOException e) {
            if (writeMessage != null) {
                writeMessage.finish(e);
            }
            if (e instanceof NotSerializableException || e instanceof InvalidClassException) {
                ftLogger.warn("SATIN '" + s.ident
                        + "': Got exception while sending result of stolen job", e);
            } else if (ftLogger.isInfoEnabled()) {
                ftLogger.info("SATIN '" + s.ident
                        + "': Got exception while sending result of stolen job", e);
            }
        } finally {
            if (ct == null) {
                s.stats.returnRecordWriteTimer.stop();
            } else {
                ct.stats.returnRecordWriteTimer.stop();
            }
        }
    }

    protected void handleStealRequest(SendPortIdentifier ident, int opcode) {

        // This upcall may run in parallel with other upcalls.
        // Therefore, we cannot directly use the handleSteal timer in Satin.
        // Use our own local timer, and add the result to the global timer
        // later.
        // Not needed when steals are queued.

        Timer handleStealTimer = null;
        if (QUEUE_STEALS) {
            s.stats.handleStealTimer.start();
        } else {
            handleStealTimer = Timer.createTimer();
            handleStealTimer.start();
        }
        
        synchronized (s.stats) {
            s.stats.stealRequests++;
        }

        try {

            if (stealLogger.isDebugEnabled()) {
                stealLogger.debug("SATIN '" + s.ident + "': dealing with steal request from "
                        + ident.ibisIdentifier() + " opcode = "
                        + Communication.opcodeToString(opcode));
            }

            InvocationRecord result = null;
            Victim v = null;
            Map<Stamp, GlobalResultTableValue> table = null;

            synchronized (s) {
                v = s.victims.getVictim(ident.ibisIdentifier());
                if (v == null || s.deadIbises.contains(ident.ibisIdentifier())) {
                    //this message arrived after the crash of its sender was
                    // detected. Is this actually possible?
                    stealLogger.warn("SATIN '" + s.ident
                            + "': EEK!! got steal request from a dead ibis: "
                            + ident.ibisIdentifier());
                    return;
                }

                try {
                    result = lb.stealJobFromLocalQueue(ident,
                            opcode == BLOCKING_STEAL_REQUEST);
                } catch (IOException e) {
                    stealLogger.warn("SATIN '" + s.ident
                            + "': EEK!! got exception during steal request: "
                            + ident.ibisIdentifier());
                    return; // the stealing ibis died
                }

                if (!FT_NAIVE
                        && (opcode == STEAL_AND_TABLE_REQUEST || opcode == ASYNC_STEAL_AND_TABLE_REQUEST)) {
                    if (!s.ft.getTable) {
                        table = s.ft.getContents();
                    }
                }
            }

            if (result == null) {
                sendStealFailedMessage(ident, opcode, v, table);
                return;
            }

            // we stole a job
            sendStolenJobMessage(ident, opcode, v, result, table);
        } finally {
            if (QUEUE_STEALS) {
                s.stats.handleStealTimer.stop();
            } else {
                handleStealTimer.stop();
                s.stats.handleStealTimer.add(handleStealTimer);
            }
        }
    }

    // Here, the timing code is OK, the upcall cannot run in parallel
    // (readmessage is not finished).
    protected void handleReply(ReadMessage m, int opcode) {
        SendPortIdentifier ident = m.origin();
        InvocationRecord tmp = null;

        if (stealLogger.isDebugEnabled()) {
            stealLogger.debug("SATIN '" + s.ident
                    + "': got steal reply message from " + ident.ibisIdentifier()
                    + ": " + Communication.opcodeToString(opcode));
        }

        switch (opcode) {
            case STEAL_REPLY_SUCCESS_TABLE:
            case ASYNC_STEAL_REPLY_SUCCESS_TABLE:
                readAndAddTable(ident, m, opcode);
            // fall through
            case STEAL_REPLY_SUCCESS:
            case ASYNC_STEAL_REPLY_SUCCESS:
                try {
                    s.stats.invocationRecordReadTimer.start();
                    tmp = (InvocationRecord) m.readObject();

                    if (ASSERTS && tmp.aborted) {
                        stealLogger.warn("SATIN '" + s.ident
                                + ": stole aborted job!");
                    }
                } catch (Exception e) {
                    stealLogger.error("SATIN '" + s.ident
                            + "': Got Exception while reading steal " + "reply from "
                            + ident + ", opcode:" + opcode + ", exception: " + e, e);
                } finally {
                    s.stats.invocationRecordReadTimer.stop();
                }

                synchronized (s) {
                    if (s.deadIbises.contains(ident)) {
                        // this message arrived after the crash of its sender
                        // was detected. Is this actually possible?
                        stealLogger.error("SATIN '" + s.ident
                                + "': got reply from dead ibis??? Ignored");
                        break;
                    }
                }

                s.algorithm.stealReplyHandler(tmp, ident.ibisIdentifier(), opcode);
                break;

            case STEAL_REPLY_FAILED_TABLE:
            case ASYNC_STEAL_REPLY_FAILED_TABLE:
                readAndAddTable(ident, m, opcode);
            //fall through
            case STEAL_REPLY_FAILED:
            case ASYNC_STEAL_REPLY_FAILED:
                s.algorithm.stealReplyHandler(null, ident.ibisIdentifier(), opcode);
                break;
            default:
                stealLogger.error("INTERNAL ERROR, opcode = " + opcode);
                break;
        }
    }

    private void readAndAddTable(SendPortIdentifier ident, ReadMessage m, int opcode) {
        try {
            @SuppressWarnings("unchecked")
            Map<Stamp, GlobalResultTableValue> table = (Map<Stamp, GlobalResultTableValue>) m.readObject();
            if (table != null) {
                synchronized (s) {
                    s.ft.getTable = false;
                    s.ft.addContents(table);
                }
            }
        } catch (Exception e) {
            stealLogger.error("SATIN '" + s.ident
                    + "': Got Exception while reading steal " + "reply from "
                    + ident + ", opcode:" + +opcode + ", exception: " + e, e);
        }
    }

    private void sendStealFailedMessage(SendPortIdentifier ident, int opcode,
            Victim v, Map<Stamp, GlobalResultTableValue> table) {

        if (stealLogger.isDebugEnabled()) {
            if (opcode == ASYNC_STEAL_REQUEST) {
                stealLogger.debug("SATIN '" + s.ident
                        + "': sending FAILED back to " + ident.ibisIdentifier());
            }
            if (opcode == ASYNC_STEAL_AND_TABLE_REQUEST) {
                stealLogger.debug("SATIN '" + s.ident
                        + "': sending FAILED_TABLE back to "
                        + ident.ibisIdentifier());
            }
        }

        WriteMessage m = null;
        try {
            m = v.newMessage();
            if (opcode == STEAL_REQUEST || opcode == BLOCKING_STEAL_REQUEST) {
                m.writeByte(STEAL_REPLY_FAILED);
            } else if (opcode == ASYNC_STEAL_REQUEST) {
                m.writeByte(ASYNC_STEAL_REPLY_FAILED);
            } else if (opcode == STEAL_AND_TABLE_REQUEST) {
                if (table != null) {
                    m.writeByte(STEAL_REPLY_FAILED_TABLE);
                    m.writeObject(table);
                } else {
                    m.writeByte(STEAL_REPLY_FAILED);
                }
            } else if (opcode == ASYNC_STEAL_AND_TABLE_REQUEST) {
                if (table != null) {
                    m.writeByte(ASYNC_STEAL_REPLY_FAILED_TABLE);
                    m.writeObject(table);
                } else {
                    m.writeByte(ASYNC_STEAL_REPLY_FAILED);
                }
            } else {
                stealLogger.error("UNHANDLED opcode " + opcode
                        + " in handleStealRequest");
            }

            v.finish(m);

            if (stealLogger.isDebugEnabled()) {
                stealLogger.debug("SATIN '" + s.ident
                        + "': sending FAILED back to " + ident.ibisIdentifier()
                        + " DONE");
            }
        } catch (IOException e) {
            if (m != null) {
                m.finish(e);
            }
            stealLogger.warn("SATIN '" + s.ident
                    + "': trying to send FAILURE back, but got exception: " + e, e);
        }
    }

    private void sendStolenJobMessage(SendPortIdentifier ident, int opcode,
            Victim v, InvocationRecord result, Map<Stamp, GlobalResultTableValue> table) {
        if (ASSERTS && result.aborted) {
            stealLogger.warn("SATIN '" + s.ident
                    + ": trying to send aborted job!");
        }

        synchronized (s.stats) {
            s.stats.stolenJobs++;
        }

        if (stealLogger.isInfoEnabled()) {
            stealLogger.info("SATIN '" + s.ident
                    + "': sending SUCCESS and job #" + result.getStamp()
                    + " back to " + ident.ibisIdentifier());
        }

        WriteMessage m = null;
        try {
            m = v.newMessage();
            if (opcode == STEAL_REQUEST || opcode == BLOCKING_STEAL_REQUEST) {
                m.writeByte(STEAL_REPLY_SUCCESS);
            } else if (opcode == ASYNC_STEAL_REQUEST) {
                m.writeByte(ASYNC_STEAL_REPLY_SUCCESS);
            } else if (opcode == STEAL_AND_TABLE_REQUEST) {
                if (table != null) {
                    m.writeByte(STEAL_REPLY_SUCCESS_TABLE);
                    m.writeObject(table);
                } else {
                    stealLogger.warn("SATIN '" + s.ident
                            + "': EEK!! sending a job but not a table !?");
                }
            } else if (opcode == ASYNC_STEAL_AND_TABLE_REQUEST) {
                if (table != null) {
                    m.writeByte(ASYNC_STEAL_REPLY_SUCCESS_TABLE);
                    m.writeObject(table);
                } else {
                    stealLogger.warn("SATIN '" + s.ident
                            + "': EEK!! sending a job but not a table !?");
                }
            } else {
                stealLogger.error("UNHANDLED opcode " + opcode
                        + " in handleStealRequest");
                // System.exit(1);
            }

            Timer invocationRecordWriteTimer = Timer.createTimer();
            invocationRecordWriteTimer.start();
            m.writeObject(result);
            invocationRecordWriteTimer.stop();
            v.finish(m);
            s.stats.invocationRecordWriteTimer.add(invocationRecordWriteTimer);
        } catch (IOException e) {
            if (m != null) {
                m.finish(e); // TODO always use victim.finish
            }
            stealLogger.warn("SATIN '" + s.ident
                    + "': trying to send a job back, but got exception: " + e, e);
        }

        /*
         * If we don't use fault tolerance with the global result table, we can
         * set the object parameters to null, so the GC can clean them up --Rob
         */
        // No, this cannot be right: it must be possible to put the job back
        // onto the work queue, so the parameters cannot be cleared. --Ceriel
        // if (FT_NAIVE) {
        //    result.clearParams();
        // }
    }
}
