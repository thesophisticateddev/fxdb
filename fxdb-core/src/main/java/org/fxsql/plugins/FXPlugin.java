package org.fxsql.plugins;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Each class marked with this annotation will be identified as plugin within the "PonderaAssembly"
 * framework and managed by it.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FXPlugin {
    String id();
}
