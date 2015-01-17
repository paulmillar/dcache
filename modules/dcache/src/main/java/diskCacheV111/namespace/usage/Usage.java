package diskCacheV111.namespace.usage;

import com.google.common.collect.ImmutableMap;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * An composite that records potentially multiple different types of
 * usages of some common selection criteria.
 */
public class Usage implements Serializable
{
    private static final long serialVersionUID = 1L;

    private final Map<Type,Record> _usage = new HashMap();

    public void add(Type type, Record additional)
    {
        Record existing = _usage.get(type);
        if (existing == null) {
            _usage.put(type, additional);
        } else {
            ImmutableMap.Builder<String,Long> builder = ImmutableMap.builder();

            existing.getPhysicalUsage().entrySet().stream().forEach(e -> {
                Long additionalUsage = additional.getPhysicalUsage().get(e.getKey());
                if (additionalUsage != null) {
                    builder.put(e.getKey(), e.getValue() + additionalUsage);
                } else {
                    builder.put(e);
                }
            });

            additional.getPhysicalUsage().entrySet().stream().
                    filter(e -> !existing.getPhysicalUsage().containsKey(e.getKey())).
                    forEach(builder::put);

            _usage.put(type, new Record(
                    existing.getLogicalUsage() + additional.getLogicalUsage(),
                    existing.getFileCount() + additional.getFileCount(),
                    builder.build()));
        }
    }

    public Record get(Type type)
    {
        return _usage.get(type);
    }

    public boolean has(Type type)
    {
        return _usage.containsKey(type);
    }
}
