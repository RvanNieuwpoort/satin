/* $Id$ */
package ibis.satin.impl.spawnSync;

import ibis.ipl.IbisIdentifier;
import ibis.satin.impl.ClientThread;
import ibis.satin.impl.Config;
import ibis.satin.impl.Satin;
import java.util.Random;

/**
 * A vector of invocation records.
 */
public final class IRVector implements Config {

    private InvocationRecord[] l = new InvocationRecord[500];
    private int count = 0;
    private Satin satin;
    private ClientThread clientThread;

    public IRVector(Satin s) {
        this.satin = s;
    }

    /**
     * Daniela:
     *
     * @param clientThread
     */
    public IRVector(ClientThread clientThread) {
        this.clientThread = clientThread;
        this.satin = clientThread.satin;
    }

    public void add(InvocationRecord r) {
        if (ASSERTS) {
            Satin.assertLocked(satin);
        }

        if (count >= l.length) {
            InvocationRecord[] nl = new InvocationRecord[l.length * 2];
            System.arraycopy(l, 0, nl, 0, l.length);
            l = nl;
        }

        l[count] = r;
        count++;
    }

    public int size() {
        if (ASSERTS) {
            Satin.assertLocked(satin);
        }
        return count;
    }

    public InvocationRecord remove(Stamp stamp) {
        InvocationRecord res = null;

        if (ASSERTS) {
            Satin.assertLocked(satin);
        }

        for (int i = 0; i < count; i++) {
            if (l[i].getStamp().stampEquals(stamp)) {
                res = l[i];
                count--;
                l[i] = l[count];
                l[count] = null;
                return res;
            }
        }

        // Sometimes (in case of crashes or aborts), we try to remove
        // non-existent elements. This is not a problem, just return null.
        spawnLogger.debug("IRVector: removing non-existent elt: " + stamp);
        return null;
    }

    public InvocationRecord remove(InvocationRecord r) {
        if (ASSERTS) {
            Satin.assertLocked(satin);
        }

        for (int i = count - 1; i >= 0; i--) {
            if (l[i].equals(r)) {
                InvocationRecord res = l[i];
                count--;
                l[i] = l[count];
                l[count] = null;
                return res;
            }
        }

        // Sometimes (in case of crashes or aborts), we try to remove
        // non-existent elements. This is not a problem, just return null.
        spawnLogger.debug("IRVector: removing non-existent elt: "
                + r.getStamp());
        return null;
    }

    public void killChildrenOf(Stamp targetStamp, boolean store, int threadId) {
        if (ASSERTS) {
            Satin.assertLocked(satin);
        }

        for (int i = 0; i < count; i++) {
            InvocationRecord curr = l[i];
            synchronized (curr) {
                if (curr.aborted) {
                    continue; // already handled.
                }

                if ((curr.getParent() != null && curr.getParent().aborted)
                        || curr.isDescendentOf(targetStamp)) {
                    curr.aborted = true;
                    if (abortLogger.isDebugEnabled()) {
                        abortLogger.debug("found stolen child: " + curr.getStamp()
                                + ", it depends on " + targetStamp);
                    }
                    if (curr.getStealer() != null && !curr.getStealer().equals(satin.ident)) {
                        curr.decrSpawnCounter();
                    }
                    
                    if (clientThread == null) {
                        satin.stats.abortedJobs++;
                        satin.stats.abortMessages++;
                    } else {
                        clientThread.stats.abortedJobs++;
                        clientThread.stats.abortMessages++;
                    }
                    // Curr is removed, but not put back in cache.
                    // this is OK. Moreover, it might have children,
                    // so we should keep it alive.
                    // cleanup is done inside the spawner itself.
                    removeIndex(i);
                    i--;
                    if (store) {
                        satin.ft.sendAbortAndStoreMessage(curr);
                    } else {
                        satin.aborts.sendAbortMessage(curr);
                    }
                }

            }
        }
    }

    public void killChildrenOf(Stamp targetStamp, boolean store) {
        if (ASSERTS) {
            Satin.assertLocked(satin);
        }

        for (int i = 0; i < count; i++) {
            InvocationRecord curr = l[i];
            synchronized (curr) {
                if (curr.aborted) {
                    continue; // already handled.
                }

                if ((curr.getParent() != null && curr.getParent().aborted)
                        || curr.isDescendentOf(targetStamp)) {
                    curr.aborted = true;
                    if (abortLogger.isDebugEnabled()) {
                        abortLogger.debug("found stolen child: " + curr.getStamp()
                                + ", it depends on " + targetStamp);
                    }
                    curr.decrSpawnCounter();
                    if (clientThread == null) {
                        satin.stats.abortedJobs++;
                        satin.stats.abortMessages++;
                    } else {
                        clientThread.stats.abortedJobs++;
                        clientThread.stats.abortMessages++;
                    }
                    // Curr is removed, but not put back in cache.
                    // this is OK. Moreover, it might have children,
                    // so we should keep it alive.
                    // cleanup is done inside the spawner itself.
                    removeIndex(i);
                    i--;
                    if (store) {
                        satin.ft.sendAbortAndStoreMessage(curr);
                    } else {
                        satin.aborts.sendAbortMessage(curr);
                    }
                }

            }
        }
    }

    // Abort every job that was spawned on targetOwner
    // or is a child of a job spawned on targetOwner.
    public void killAndStoreSubtreeOf(IbisIdentifier targetOwner) {
        if (ASSERTS) {
            Satin.assertLocked(satin);
        }

        for (int i = 0; i < count; i++) {
            InvocationRecord curr = l[i];
            synchronized (curr) {
                if ((curr.getParent() != null && curr.getParent().aborted)
                        || curr.isDescendentOf(targetOwner)
                        || curr.getOwner().equals(targetOwner)) {
                    //this shouldnt happen, actually
                    curr.aborted = true;
                    if (abortLogger.isDebugEnabled()) {
                        abortLogger.debug("found stolen child: " + curr.getStamp()
                                + ", it depends on " + targetOwner);
                    }
                    curr.decrSpawnCounter();
                    if (clientThread == null) {
                        satin.stats.abortedJobs++;
                        satin.stats.abortMessages++;
                    } else {
                        clientThread.stats.abortedJobs++;
                        clientThread.stats.abortMessages++;
                    }
                    removeIndex(i);
                    i--;
                    satin.ft.sendAbortAndStoreMessage(curr);
                }
            }
        }
    }

    // Abort every job that was spawned on targetOwner
    // or is a child of a job spawned on targetOwner.
    public void killSubtreeOf(IbisIdentifier targetOwner) {
        if (ASSERTS) {
            Satin.assertLocked(satin);
        }

        for (int i = 0; i < count; i++) {
            InvocationRecord curr = l[i];
            synchronized (curr) {
                if ((curr.getParent() != null && curr.getParent().aborted)
                        || curr.isDescendentOf(targetOwner)
                        || curr.getOwner().equals(targetOwner)) {
                    //this shouldnt happen, actually
                    curr.aborted = true;
                    if (abortLogger.isDebugEnabled()) {
                        abortLogger.debug("found stolen child: " + curr.getStamp()
                                + ", it depends on " + targetOwner);
                    }
                    curr.decrSpawnCounter();
                    if (clientThread == null) {
                        satin.stats.abortedJobs++;
                        satin.stats.abortMessages++;
                    } else {
                        clientThread.stats.abortedJobs++;
                        clientThread.stats.abortMessages++;
                    }
                    removeIndex(i);
                    i--;
                    satin.ft.sendAbortMessage(curr);
                }
            }
        }
    }

    public void killAll() {
        if (ASSERTS) {
            Satin.assertLocked(satin);
        }

        for (int i = 0; i < count; i++) {
            InvocationRecord curr = l[i];
            synchronized (curr) {
                curr.aborted = true;
                curr.decrSpawnCounter();
                removeIndex(i);
                i--;
            }
        }
    }

    public InvocationRecord removeIndex(int i) {
        if (ASSERTS) {
            Satin.assertLocked(satin);
        }
        if (i >= count) {
            return null;
        }

        InvocationRecord res = l[i];
        count--;
        l[i] = l[count];
        l[count] = null;
        return res;
    }

    /**
     * Used for fault tolerance. Remove all the jobs stolen by targetOwner and
     * put them back in the taskQueue.
     */
    public void redoStolenBy(IbisIdentifier crashedIbis) {
        Satin.assertLocked(satin);

        for (int i = count - 1; i >= 0; i--) {
            if (crashedIbis.equals(l[i].getStealer())) {
                if (ftLogger.isDebugEnabled()) {
                    ftLogger.debug("Found a job to restart: " + l[i].getStamp());
                }
                l[i].setReDone(true);
                l[i].setStealer(null);

                int n = satin.clientThreads.length;
                Random r = new Random();
                int j;
                if (satin.isMaster()) {
                    n++;//satin.q.addToTail(l[i]);
                    j = r.nextInt(n) - 1;
                } else {
                    j = r.nextInt(n);
                }

                if (j == -1) {
                    satin.q.addToTail(l[i]);
                } else {
                    satin.clientThreads[j].q.addToTail(l[i]);
                }

                //satin.stats.restartedJobs++;
                count--;
                l[i] = l[count];
            }
        }
    }

    public void print(java.io.PrintStream out) {
        Satin.assertLocked(satin);

        out.println("=IRVector " + satin.ident + ":=============");
        for (int i = 0; i < count; i++) {
            out.println("outjobs [" + i + "] = " + l[i] + ","
                    + l[i].getStealer());
        }
        out.println("end of IRVector: " + satin.ident + "=");
    }

    public InvocationRecord first() {
        return l[0];
    }
}
