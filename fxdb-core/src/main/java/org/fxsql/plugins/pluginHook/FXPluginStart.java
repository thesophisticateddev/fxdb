package org.fxsql.plugins.pluginHook;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation, when applied to a method, designates it as a plugin start hook within the "PonderaAssembly" framework. <br>
 * Plugin start hooks are methods that will be automatically triggered when a plugin starts its lifecycle. <br>
 * These methods provide an entry point for executing initialization tasks.
 *
 * <p> Order of execution in plugin class </p>
 * <ul>
 * <li> initialize dependencies </li>
 * <li> run constructor </li>
 * <li> initialize extension points</li>
 * <li> run @Start method</li>
 *</ul>
 *
 *
 * Class has to be declared as plugin for this annotation to work.
 * <br>
 * If a class has multiple methods marked with this annotation, they will be executed in the order they are declared.
 *
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FXPluginStart {
}
