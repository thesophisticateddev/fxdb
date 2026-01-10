package org.fxsql.plugins.pluginEvents;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a method is a handler for events.<br>
 * Event handler methods are used to respond to events triggered within the plugin framework. <br>
 * These methods are automatically invoked when an event they are registered for occurs.
 * The method's parameters can be used to receive event-related data. <br>
 *
 * <p> Constraints </p>
 * <ul>
 *      <li> Class has to be annotated with Plugin </li>
 *      <li> Method must have one parameter type is Object</li>
 *  </ul>
 *
 *  To emit action PonderaMicrokernel.EVENT#emit
 *
 * Example:
 * <pre> <code>
 *
 *
 * {@literal @}Plugin(id= "com.ig.HttpRequestPlugin")
 * public class HttpRequestPlugin {
 *
 *     // this method acts as handler for PonderaMicrokernel.EVENT#emit
 *     {@literal @}HandleAction()
 *     public void handleSend(Object payload) {
 *
 *     }
 * }
 *
 * </code> </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FXPluginHandleEvent{

}
