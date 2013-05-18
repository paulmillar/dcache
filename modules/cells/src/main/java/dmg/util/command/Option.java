package dmg.util.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a field represents a command option.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Option
{
    /**
     * Name of the option.
     */
    String name();

    /**
     * Help string used to display the usage screen.
     *
     * <p>
     * If this value is empty, the option will not be displayed
     * in the usage screen.
     */
    String usage() default "";

    /**
     * Used on the usage screen as a meta variable to represent the
     * value of this option.
     */
    String metaVar() default "";

    /**
     * Used on the usage screen to describe the syntax of the value
     * of the option.
     */
    String valueSpec() default "";

    /**
     * Specify that the option is mandatory.
     */
    boolean required() default false;

    /**
     * The separator string used to split elements of array options.
     */
    String separator() default "";

    /**
     * Enumeration of allowed values.
     */
    String[] values() default {};

    /**
     * Category descriptor used to group options in help output.
     */
    String category() default "";

    /**
     * The name of the static method used to create an object of this
     * type.  This is used if the type is not primitive, not an enum and not a
     * String.  The method must accept a single String argument.
     */
    String factory() default "valueOf";
}
