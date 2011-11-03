/* $Id$ */

package ibis.satin.impl;

import ibis.ipl.IbisProperties;
import ibis.util.TypedProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Constants for the configuration of Satin. This interface is public because it
 * is also used in code generated by the Satin frontend.
 */

public interface Config {

    static final TypedProperties properties = new TypedProperties(
            IbisProperties.getDefaultProperties());

    static final String PROPERTY_PREFIX = "satin.";

    static final String s_asserts = PROPERTY_PREFIX + "asserts";

    static final String s_queue_steals = PROPERTY_PREFIX + "queueSteals";

    static final String s_closed = PROPERTY_PREFIX + "closed";

    static final String s_client = PROPERTY_PREFIX + "client";

    static final String s_unreliable = PROPERTY_PREFIX + "unreliable";

    static final String s_close_connections = PROPERTY_PREFIX
            + "closeConnections";

    static final String s_max_connections = PROPERTY_PREFIX + "maxConnections";

    static final String s_connections_on_demand = PROPERTY_PREFIX
            + "connectionsOnDemand";

    static final String s_keep_intra_connections = PROPERTY_PREFIX
            + "keepIntraConnections";

    static final String s_throttle_steals = PROPERTY_PREFIX + "throttleSteals";

    static final String s_max_steal_throttle = PROPERTY_PREFIX
            + "maxStealThrottle";

    static final String s_stats = PROPERTY_PREFIX + "stats";

    static final String s_detailed_stats = PROPERTY_PREFIX + "detailedStats";

    static final String s_alg = PROPERTY_PREFIX + "alg";

    static final String s_dump = PROPERTY_PREFIX + "dump";

    static final String s_in_latency = PROPERTY_PREFIX + "messagesInLatency";
    
    static final String s_so = PROPERTY_PREFIX + "so";

    static final String s_so_delay = PROPERTY_PREFIX + "so.delay";

    static final String s_so_size = PROPERTY_PREFIX + "so.size";

    static final String s_so_lrmc = PROPERTY_PREFIX + "so.lrmc";

    static final String s_so_wait_time = PROPERTY_PREFIX + "so.waitTime";

    static final String s_ft_naive = PROPERTY_PREFIX + "ft.naive";

    static final String s_ft_connectTimeout = PROPERTY_PREFIX
            + "ft.connectTimeout";

    static final String s_masterhost = PROPERTY_PREFIX + "masterHost";

    static final String s_delete_time = PROPERTY_PREFIX + "deleteTime";

    static final String s_steal_wait_timeout = PROPERTY_PREFIX
            + "stealWaitTimeout";

    static final String s_delete_cluster_time = PROPERTY_PREFIX
            + "deleteClusterTime";

    static final String s_kill_time = PROPERTY_PREFIX + "killTime";

    static final String s_cpt = PROPERTY_PREFIX + "cpt";
    
    static final String s_cpt_push = PROPERTY_PREFIX + "cpt.push";
    
    static final String s_cpt_interval = PROPERTY_PREFIX + "cpt.interval";
    
    static final String s_cpt_first = PROPERTY_PREFIX + "cpt.first";
    
    static final String s_cpt_file = PROPERTY_PREFIX + "cpt.file";
    
    static final String s_cpt_cluster = PROPERTY_PREFIX + "cpt.cluster";
        
    static final String s_cpt_maxFileSize = PROPERTY_PREFIX + "cpt.maxfilesize";
    
    static final String s_cpt_quit = PROPERTY_PREFIX + "cpt.quit";

    static final String[] sysprops = { s_stats, s_queue_steals,
            s_detailed_stats, s_client, s_closed, s_unreliable, s_asserts,
            s_ft_naive, s_ft_connectTimeout, s_masterhost, s_in_latency,
            s_delete_time, s_delete_cluster_time, s_kill_time, s_dump, s_so,
            s_so_delay, s_so_size, s_alg, s_so_lrmc, s_close_connections,
            s_max_connections, s_so_wait_time, s_steal_wait_timeout,
            s_connections_on_demand, s_keep_intra_connections,
            s_throttle_steals, s_max_steal_throttle,
            s_cpt, s_cpt_push, s_cpt_interval, s_cpt_first, s_cpt_file,
            s_cpt_maxFileSize, s_cpt_quit, s_cpt_cluster};

    /** Enable or disable asserts. */
    static final boolean ASSERTS = properties.getBooleanProperty(s_asserts,
            true);

    /** True if the node should dump its datastructures during shutdown. */
    static final boolean DUMP = properties.getBooleanProperty(s_dump, false);

    /** Enable this if Satin should print statistics at the end. */
    static final boolean STATS = properties.getBooleanProperty(s_stats, true);

    /** Enable this if Satin should print statistics per machine at the end. */
    static final boolean DETAILED_STATS = properties.getBooleanProperty(
            s_detailed_stats, false);

    /** When set, this instance cannot be master. */
    static final boolean CLIENT = properties
            .getBooleanProperty(s_client, false);

    /**
     * Enable this if satin should run with a closed world: no nodes can join or
     * leave.
     */
    static final boolean CLOSED = properties
            .getBooleanProperty(s_closed, false);

    /**
     * Enable this if satin should use a lesser consistency model for
     * join/leaves and elections. This breaks lrmc as well as the master
     * election, so the master must be specified, and lrmc must be disable.
     */
    static final boolean UNRELIABLE = properties
            .getBooleanProperty(s_unreliable, false);

    /** Determines master hostname. */
    static final String MASTER_HOST = properties.getProperty(s_masterhost);

    /** Determines which load-balancing algorithm is used. */
    static final String SUPPLIED_ALG = properties.getProperty(s_alg);

    /**
     * Fault tolerance with restarting crashed jobs, but without the global
     * result table.
     */
    static final boolean FT_NAIVE = properties.getBooleanProperty(s_ft_naive,
            false);

    /** When set, checkpointing code is enabled. */
    public static final boolean CHECKPOINTING = properties.getBooleanProperty(
            s_cpt, false);
    
    /**
     * When set, every node decides for itself when checkpoints are sent to
     * the coordinator.
     */
    public static final boolean CHECKPOINT_PUSH = properties.getBooleanProperty(
            s_cpt_push, true);

    /** Checkpointing interval, in milliseconds. */
    public static final int CHECKPOINT_INTERVAL
            = properties.getIntProperty(s_cpt_interval, 60000);
    
    /** Time to wait before first checkpoint, in milliseconds. */
    public static final int CHECKPOINT_FIRST
            = properties.getIntProperty(s_cpt_first, CHECKPOINT_INTERVAL);
    
    /** URI of the checkpoint file. */
    public static final String CHECKPOINT_FILE
            = properties.getProperty(s_cpt_file, "checkpoints.txt");
    
    /** When set, this node is a candidate to become coordinator. */
    public static final boolean CHECKPOINT_CLUSTER
            = properties.getBooleanProperty(s_cpt_cluster, true);
    
    /** If the checkpoint file becomes larger than this, compress (unless 0). */
    public static final int CHECKPOINT_MAXFILESIZE
            = properties.getIntProperty(s_cpt_maxFileSize, 0);
    
    public static final int CHECKPOINT_QUITTIME
            = properties.getIntProperty(s_cpt_quit, 0);
    
    public static final int COORDINATOR_QUIT_DELAY_TIME = 10000;

    /** Enable or disable an optimization for handling delayed messages. */
    static final boolean HANDLE_MESSAGES_IN_LATENCY = properties
            .getBooleanProperty(s_in_latency, false);

    /**
     * Timeout for connecting to other nodes. Properties are always specified in
     * seconds, but this variable contains millis.
     */
    public static final long CONNECT_TIMEOUT = properties.getLongProperty(
            s_ft_connectTimeout, 60) * 1000L;

    /**
     * Timeout in seconds for waiting on a steal reply from another node.
     * Properties are always specified in seconds, but this variable contains
     * millis.
     */
    public static final long STEAL_WAIT_TIMEOUT = properties.getLongProperty(
            s_steal_wait_timeout, (CONNECT_TIMEOUT / 1000L) * 2 + 1) * 1000L;

    /** Enable/disable shared objects. */
    static final boolean SO_ENABLED = properties.getBooleanProperty(s_so, true);
    
    /**
     * Maximum time that messages may be buffered for message combining. If > 0,
     * it is used for combining shared objects invocations. setting this to 0
     * disables message combining.
     */
    static final int SO_MAX_INVOCATION_DELAY = properties.getIntProperty(
            s_so_delay, 0);

    /**
     * The maximum message size if message combining is used for SO Invocations.
     */
    static final int SO_MAX_MESSAGE_SIZE = properties.getIntProperty(s_so_size,
            64 * 1024);

    /** Wait time before requesting a shared object. */
    static final int SO_WAIT_FOR_UPDATES_TIME = properties.getIntProperty(
            s_so_wait_time, 60) * 1000;

    /** Enable or disable label routing multicast for shared objects . */
    static final boolean LABEL_ROUTING_MCAST = properties.getBooleanProperty(
            s_so_lrmc, true);

    /** Used in automatic ft tests */
    static final int DELETE_TIME = properties.getIntProperty(s_delete_time, 0);

    /** Used in automatic ft tests */
    static final int DELETE_CLUSTER_TIME = properties.getIntProperty(
            s_delete_cluster_time, 0);

    /** Used in automatic ft tests */
    static final int KILL_TIME = properties.getIntProperty(s_kill_time, 0);

    /**
     * Enable or disable using a seperate queue for work steal requests to avoid
     * thread creation.
     */
    static final boolean QUEUE_STEALS = properties.getBooleanProperty(
            s_queue_steals, true);

    /** Close connections after use. Used for scalability. */
    static final boolean CLOSE_CONNECTIONS = properties.getBooleanProperty(
            s_close_connections, false);

    /** When using CLOSE_CONNECTIONS, keep open MAX_CONNECTIONS connections. */
    static final int MAX_CONNECTIONS = properties.getIntProperty(
            s_max_connections, 64);

    /** Setup connections as we need them. Used for scalability. */
    static final boolean CONNECTIONS_ON_DEMAND = properties.getBooleanProperty(
            s_connections_on_demand, true);

    /** Do not steal as fast as we can, but use exponential backoff. */
    static final boolean THROTTLE_STEALS = properties.getBooleanProperty(
            s_throttle_steals, false);

    /** the maximal time to sleep after a failed steal attempt in milliseconds */
    static final int MAX_STEAL_THROTTLE = properties.getIntProperty(
            s_max_steal_throttle, 256);

    /**
     * When CLOSE_CONNECTIONS is set, keep intra-cluster connections. When set,
     * MAX_CONNECTIONS is ignored.
     */
    static final boolean KEEP_INTRA_CONNECTIONS = properties
            .getBooleanProperty(s_keep_intra_connections, true);

    /** Logger for communication. */
    public static final Logger commLogger = LoggerFactory
            .getLogger("ibis.satin.comm");

    /** Logger for connections. */
    public static final Logger connLogger = LoggerFactory
            .getLogger("ibis.satin.conn");

    /** Logger for job stealing. */
    public static final Logger stealLogger = LoggerFactory
            .getLogger("ibis.satin.steal");

    /** Logger for spawns. */
    public static final Logger spawnLogger = LoggerFactory
            .getLogger("ibis.satin.spawn");

    /** Logger for inlets. */
    public static final Logger inletLogger = LoggerFactory
            .getLogger("ibis.satin.inlet");

    /** Logger for aborts. */
    public static final Logger abortLogger = LoggerFactory
            .getLogger("ibis.satin.abort");

    /** Logger for fault tolerance. */
    public static final Logger ftLogger = LoggerFactory
            .getLogger("ibis.satin.ft");

    /** Logger for the global result table. */
    public static final Logger grtLogger = LoggerFactory
            .getLogger("ibis.satin.ft.grt");

    /** Logger for shared objects. */
    public static final Logger soLogger = LoggerFactory
            .getLogger("ibis.satin.so");

    /** Logger for shared objects broadcasts. */
    public static final Logger soBcastLogger = LoggerFactory
            .getLogger("ibis.satin.so.bcast");

    /** Generic logger. */
    public static final Logger mainLogger = LoggerFactory
            .getLogger("ibis.satin");
}
