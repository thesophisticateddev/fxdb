package org.fxsql.plugins.plugindepdency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is used to mark a class as a factory for providing dependency instances. <br>
 * Classes marked with this annotation should define methods annotated with {@link DependencyInstance} to provide dependency instances.
 *
 * Example:
 * <pre> <code>
 *
 * {@literal @}DependencyFactory
 * public class MyDependency {
 *     {@literal @}DependencyInstance()
 *     public String testIt() {
 *         return "Hello world";
 *     }
 * }
 *
 * </code> </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FXPluginDependencyFactory {
}
