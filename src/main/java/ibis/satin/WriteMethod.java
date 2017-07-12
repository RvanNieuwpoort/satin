/* $Id$ */

package ibis.satin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This interface is a marker interface for methods that change the value of a
 * Satin shared object. A shared object that has methods that change the value
 * of the object must mark these methods as write methods. This is accomplished
 * by putting these methods in an interface that extends this marker interface.
 * The shared object class must implement this interface.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface WriteMethod {
    /* just a marker interface, no methods */
}
