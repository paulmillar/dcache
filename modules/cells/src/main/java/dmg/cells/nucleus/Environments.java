package dmg.cells.nucleus;

import com.google.common.base.Optional;

import java.util.Map;
import java.util.Properties;

import dmg.util.Formats;

/**
 * Utility class for working with a Cell's environment
 */
public class Environments
{

    private Environments()
    {
        // prevent instantiation
    }

    public static Properties toProperties(final Map<String,Object> env)
    {
        Properties properties = new Properties();

        env.entrySet().forEach(e -> {
            properties.put(e.getKey(), expand(e.getValue(), env));
        });

        return properties;
    }

    public static String getValue(final Map<String,Object> env, String name)
    {
        Object value = env.get(name);

        if (value == null) {
            throw new IllegalArgumentException("'" + name + "' is not set");
        }

        if (!(value instanceof String)) {
            throw new IllegalArgumentException("Invalid value of '" + name +
                    "': " + value);
        }

        return expand(value, env);
    }

    private static String expand(Object in, final Map<String,Object> env)
    {
        return Formats.replaceKeywords(String.valueOf(in), n -> Optional.fromNullable(env.get(n)).
                transform(Object::toString).transform(String::trim).orNull());
    }
}
