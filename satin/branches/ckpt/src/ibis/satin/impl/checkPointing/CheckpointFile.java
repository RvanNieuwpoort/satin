/*
  - De private functies die doen een operatie van source naar destination, 
    waarna  destination de nieuwe fileWriter is.
  - De public tegenhangers kopieren de file naar een tijdelijke kopie, en 
    doen de operatie daarna van de tijdelijke kopie naar de originele file, 
    zodat de checkpointFile ongewijzigd blijft
  - init zorgt dat uiteindelijk de originele checkpointfile gebruikt wordt
*/
package ibis.satin.impl.checkPointing;

import ibis.ipl.IbisIdentifier;
import ibis.satin.impl.faultTolerance.GlobalResultTable;
import ibis.satin.impl.spawnSync.ReturnRecord;
import ibis.satin.impl.spawnSync.Stamp;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.util.ArrayList;
import java.util.Set;

import org.gridlab.gat.GAT;
import org.gridlab.gat.GATContext;
import org.gridlab.gat.URI;
import org.gridlab.gat.io.File;
import org.gridlab.gat.io.FileInputStream;
import org.gridlab.gat.io.FileOutputStream;

public class CheckpointFile {

    private String filename = null;
    private FileOutputStream fileStream = null;
    private ObjectOutputStream fileWriter = null;
    private ArrayList<Stamp> checkpoints = new ArrayList<Stamp>();
    private long maxFileSize = 0;
    private boolean stopWriting = true;

    public CheckpointFile(String filename){
	this(filename, 0);
    }

    public CheckpointFile(String filename, long maxFileSize){
	this.filename = filename;
	this.maxFileSize = maxFileSize;
    }

    // when a global result table is passed, this function will insert
    // the results into the grt, and return the number of read checkpoints
    public int init(GlobalResultTable grt){
	GATContext context = new GATContext();
	try {
	    File f = GAT.createFile(context, new URI(filename));
	    if (f.exists()){
		if (maxFileSize > 0 && f.length() > maxFileSize){
		    restore(filename, filename + "_TMP", grt);
		    compress(filename + "_TMP", filename);
		    delete(filename + "_TMP");
		} else {
		    move(filename, filename + "_TMP");
		    restore(filename + "_TMP", filename, grt);
		    delete(filename + "_TMP");
		}
	    } else {
		open(filename);
	    }
	} catch (Exception e){
	    System.out.println("init calling fatal");
	    fatal(e);
	    return checkpoints.size();
	}
	stopWriting = false;
	return checkpoints.size();
    }

    /**
     *  Writes newCheckpoints to the checkpointFile and checks the filesize
     *  of the checkpointfile for compression afterwards. Every exception
     *  is fatal and results in no write-possibilities in the future 
     *  whatsoever
     **/
    public void write(ArrayList<Checkpoint> newCheckpoints){
	if (stopWriting){
	    return;
	}
	if (fileStream == null || fileWriter == null){
	    return;
	}

	int i = 0;
	try {
	    while(newCheckpoints.size() > 0){	
		Checkpoint cp = newCheckpoints.remove(0);
		fileWriter.writeObject(cp);
		checkpoints.add(cp.rr.getStamp());
		i++;
	    }
	} catch (IOException e){
	    System.out.println("iox while writing checkpoints: " + e);
	} 
	try {
	    GATContext context = new GATContext();
	    if (maxFileSize > 0 && GAT.createFile(context, 
		new URI(filename)).length() > maxFileSize){
		compress();
		if (GAT.createFile(context, new URI(filename)).length() > 
		    maxFileSize){
		    System.out.println("compression resulted in too big file. Checkpointing aborted");
		    stopWriting = true;
		}
	    }
	} catch (Exception e){
	    System.out.println("write calling fatal");
	    fatal(e);
	}
    }

    /**
     * Retrieves all the checkpoints which belonged to 'id' from the
     * checkpoint-file and stores them in 'grt'.
     * if id == null, all checkpoints belonging to any node will be stored
     * in 'grt.'
     */    
    public int read(Set<IbisIdentifier> id, GlobalResultTable grt) {
        int result = 0;
	FileInputStream tempInputFile = null;
	ObjectInputStream tempReader = null;
        try {
            GATContext context = new GATContext();
            tempInputFile = GAT.createFileInputStream(context,
			    new URI(filename));     
	    tempReader = new ObjectInputStream(tempInputFile);

            while (tempInputFile.available() > 0){
                Checkpoint cp = (Checkpoint) tempReader.readObject();
                if (id == null || id.contains(cp.sender)) {
                    ReturnRecord record = cp.rr;
                    synchronized (this){
                        grt.storeResult(record);
                    }
                    result++;
                }
            }
        } catch (Exception e){
            System.out.println("[CheckpointFile|read] exception: " + e);
        }

	try {
            tempInputFile.close();
            tempReader.close();
	} catch (Exception e){}
	
	return result;
    }

    /**
     * Tries to compress the checkpointfile. Every exception is fatal 
     * and results in no write-possibilities in the future whatsoever
     **/
    public void compress(){
	if (fileStream == null || fileWriter == null){
	    return;
	}

	try {
	    close();
	    move(filename, filename + "_TMP");
	    compress(filename + "_TMP", filename);
	    delete(filename + "_TMP");
	} catch (Exception e){
	    System.out.println("compress calling fatal");
	    fatal(e);
	}
    }

    /**
     * Tries to restore the old checkpointFile. Every Exception is fatal
     * and leads to no write-possibiliets in the future whatsoever
     **/
    public void restore(GlobalResultTable grt){
	if (fileStream == null || fileWriter == null){
	    return;
	}
	try {
	    close();
	    move(filename, filename + "_TMP");
	    restore(filename + "_TMP", filename, grt);
	    delete(filename + "_TMP");
	} catch (Exception e){
	    System.out.println("restore calling fatal");
	    fatal(e);
	}
    }

    /**
     * First finds all the checkpoints which don't have any parents which
     * are also available, and copies only these checkpoints to dest.
     **/
    private void compress(String source, String dest) throws Exception{
	// find out  which checkpoints are needed

	int i = 0;
	while (i < checkpoints.size()) {
	    int j = i + 1;
	    Stamp stamp1 = checkpoints.get(i);
	    if (stamp1 == null) {
	        checkpoints.remove(i);
	        continue;
	    }
	    while (j < checkpoints.size()) {
		Stamp stamp2 = checkpoints.get(j);
		// stamp2 can be removed if it is null, equal to stamp1,
		// or a descendent of stamp1.
		if (stamp2 == null || stamp2.equals(stamp1)
		        || stamp2.isDescendentOf(stamp1)) {
		    checkpoints.remove(j);
		    continue;
		}
		// stamp1 can be removed if it is a descendent of stamp2.
		if (stamp1.isDescendentOf(stamp2)) {
		    checkpoints.remove(i);
		    i--;
		    break;
		}
		j++;
	    }
	    i++;
	}
	// write these checkpoints to the file 'dest'
	FileOutputStream tempOutputFile = null;;
	ObjectOutputStream tempWriter = null;
	FileInputStream tempInputFile = null;
	ObjectInputStream tempReader = null;

	GATContext context = new GATContext();
	tempOutputFile = GAT.createFileOutputStream(context, 
						    new URI(dest), false);
	tempWriter = new ObjectOutputStream(tempOutputFile);
	tempInputFile = GAT.createFileInputStream(context, 
						  new URI(source));
	tempReader = new ObjectInputStream(tempInputFile);
	
	i = 0;
	while (tempInputFile.available() > 0){
	    Checkpoint cp = (Checkpoint) tempReader.readObject();
	    if (cp.rr.getStamp().stampEquals(checkpoints.get(i))) {
		tempWriter.writeObject(cp);
	    }
	    i++;
	}

	try {
	    fileStream.close();
	    fileWriter.close();
	    tempInputFile.close();
	    tempReader.close();
	} catch (Exception e){}

	// make 'dest' the checkpointFile
	fileStream = tempOutputFile;
	fileWriter = tempWriter;
    }

    /**
     * Copies all the objects from the old checkpointfile to the new copy
     * until a StreamCorruptedException occurs. Everything that was
     * written after this points is lost.
     **/
    private void restore(String source, String dest, GlobalResultTable grt) 
	throws Exception{
	FileOutputStream tempOutputFile = null;
	ObjectOutputStream tempWriter = null;
	FileInputStream tempInputFile = null;
	ObjectInputStream tempReader = null;
	try {
	    GATContext context = new GATContext();
            tempOutputFile = GAT.createFileOutputStream(context, 
							new URI(dest), false);
            tempWriter = new ObjectOutputStream(tempOutputFile);
            tempInputFile = GAT.createFileInputStream(context,
						      new URI(source));
            tempReader = new ObjectInputStream(tempInputFile);

            checkpoints = new ArrayList<Stamp>();

	    while (tempInputFile.available() > 0) {

		Checkpoint cp = (Checkpoint) tempReader.readObject();
		tempWriter.writeObject(cp);
		tempWriter.flush();
		if (grt != null){
		    grt.storeResult(cp.rr);
		    // propagate updates after every 1000 updates
		    try {
			if (checkpoints.size() % 1000 == 999){
			    long begin = System.currentTimeMillis();
			    grt.sendUpdates();
			    long end = System.currentTimeMillis();
			    System.out.println("update took " + (end - begin) +
					       " ms");
			}
		    } catch (Exception e){
			// nullpointerException if !GRT_MESSAGE_COMBINING
		    }
		}
		checkpoints.add(cp.rr.getStamp());
	    }
	} catch (StreamCorruptedException e){
	    System.out.println("restored " + checkpoints.size() + 
			       " from a corrupted checkpoint file");
	} catch (EOFException e){
	    System.out.println("restored " + checkpoints.size() + 
			       " from a corrupted checkpoint file");
	}
	try {
	    fileStream.close();
	    fileWriter.close();
	    tempInputFile.close();
	    tempReader.close();
	} catch (Exception e){}

	fileStream = tempOutputFile;
	fileWriter = tempWriter;
    }

    /**
     * Tries to delete the file filename
     **/
    private void delete(String filename){
	try {
	    GATContext context = new GATContext();
	    GAT.createFile(context, new URI(filename)).delete();
	} catch (Exception e){}
    }

    /**
     * Moves the file source to dest
     **/
    private void move(String source, String dest) throws Exception{
	GATContext context = new GATContext();
	GAT.createFile(context, new URI(source)).move(new URI(dest));
    }

    /**
     * Tries to open filename, so that it will be used for future
     * write-operations
     **/
    private void open(String filename) throws Exception{
	GATContext context = new GATContext();
	fileStream = GAT.createFileOutputStream(context, new URI(filename), 
						false);
	fileWriter = new ObjectOutputStream(fileStream);
    }

    /**
     * Prints Exception e, closes all streams and deletes all temporary
     * files. Cosequently, all future write-operations will fail.
     * The read-operations might still succeed if fatal wasn't called
     * on a moment the original file (i.e. filename) didn't exist
     **/
    private void fatal(Exception e){
	System.out.println("CheckpointFile: Fatal Exception: " + e);
	close();
	delete(filename + "_TMP");
	fileStream = null;
	fileWriter = null;
	stopWriting = true;
    }

    public void close(){
	try {
	    fileWriter.close();
	    fileStream.close();
	} catch (Exception e){}
    }
}
