/* $Id: Spawnable.java 15353 2015-01-28 10:07:31Z ceriel $ */

package ibis.satin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The marker interface that indicates which methods of a class are spawnable by
 * the Satin divide-and-conquer environment. Use this interface to mark the
 * methods that may be spawned. The way to do this is to create an interface
 * that extends <code>ibis.satin.Spawnable</code> and specifies the methods that
 * may be spawned. The interface extends java.io.Serializable because the "this"
 * parameter is also sent across the network when work is transferred.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Spawnable {
    // just a marker interface
}
