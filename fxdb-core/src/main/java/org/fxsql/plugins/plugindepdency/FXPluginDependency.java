package org.fxsql.plugins.plugindepdency;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is used to mark a constructor parameter as a dependency.
 *
 * <p> Constraints </p>
 * <ul>
 *  <li> Class has to have Plugin annotation. </li>
 *  <li> Used for constructors only. </li>
 *  <li> Class has to have only one constructor.</li>
 * </ul>
 *
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface FXPluginDependency {
    String getName();
}
