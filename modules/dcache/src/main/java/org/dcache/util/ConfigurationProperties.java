package org.dcache.util;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.LineNumberReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.InvalidPropertiesFormatException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dmg.util.Formats;
import dmg.util.PropertiesBackedReplaceable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The ConfigurationProperties class represents a set of dCache
 * configuration properties.
 * <p>
 * Repeated declaration of the same property is considered an error
 * and will cause loading of configuration files to fail.
 * <p>
 * Properties may have zero or more annotations.  These annotations
 * are represented as a comma-separated list of annotation-labels
 * inside parentheses immediately before the property key. Valid
 * annotation labels are "deprecated", "obsolete", "forbidden"
 * and "not-for-services".  A property may have, at most, one annotation
 * from the set {deprecated, obsolete, forbidden}.
 * <p>
 * Annotations have the following semantics:
 * <ul>
 * <li><i>deprecated</i> indicates that a property is supported but that a
 * future version of dCache will likely remove that support.
 * <li><i>obsolete</i> indicates that a property is no longer supported and
 * that dCache will always behaves correctly without supporting this
 * property.
 * <li><i>forbidden</i> indicates that a property is no longer supported and
 * dCache does not always behave correctly without further configuration or
 * that support for some feature has been removed.
 * <li><i>not-for-services</i> indicates that a property has no effect if
 * the property is assigned a value within a service context.
 * </ul>
 * <p>
 * The intended behaviour of dCache when encountering sysadmin-supplied
 * property assignment of some annotated property is dependent on the
 * annotation(s).  If the property is annotated as deprecated and obsolete
 * then a warning is emitted and dCache continues to start up. If the user
 * assigns a value to a forbidden properties then dCache will refuse to start.
 * <p>
 * Annotation of a property only affects subsequent declarations.  It does not
 * affect any previous declarations of this property, nor does it generate any
 * errors when such properties are referenced in any way.
 * <p>
 * Annotations are inherited when several ConfigurationProperties instances are
 * chained. They are however not inherited through non-ConfigurationProperties
 * classes, eg
 * new ConfigurationProperties(new Properties(new ConfigurationProperties()))
 * will not preserve annotations.
 *
 * <p>
 * The following provides examples of valid annotated declarations:
 * <pre>
 *   (obsolete)dcache.property1 = some value
 *   (forbidden)dcache.property2 =
 *   (deprecated,no-for-services)dcache.property3 = default-value
 * </pre>
 *
 * @see Properties
 */
public class ConfigurationProperties
    extends Properties
{
    private static final long serialVersionUID = -5684848160314570455L;

    /**
     * The character that separates the prefix from the key for PREFIX-annotated
     * properties.
     */
    public static final String PREFIX_SEPARATOR = "!";

    private static final Set<Annotation> OBSOLETE_FORBIDDEN =
        EnumSet.of(Annotation.OBSOLETE, Annotation.FORBIDDEN);

    private static final Set<Annotation> VALIDATING_ANNOTATIONS =
        EnumSet.of(Annotation.ONE_OF, Annotation.ANY_OF);

    private final static Logger _log =
        LoggerFactory.getLogger(ConfigurationProperties.class);

    private final PropertiesBackedReplaceable _replaceable =
        new PropertiesBackedReplaceable(this);

    private final Map<String,AnnotatedKey> _annotatedKeys =
            new HashMap<>();
    private final UsageChecker _usageChecker;
    private final List<String> _prefixes = new ArrayList<>();

    private boolean _loading;
    private boolean _isService;
    private ProblemConsumer _problemConsumer = new DefaultProblemConsumer();
    private final Set<String> _validatingProperties = new HashSet<>();
    private final Map<String,String> _propertyContext = new HashMap<>();

    public ConfigurationProperties()
    {
        super();
        _usageChecker = new UniversalUsageChecker();
    }

    public ConfigurationProperties(Properties defaults)
    {
        this(defaults, new UniversalUsageChecker());
    }

    public ConfigurationProperties(Properties defaults, UsageChecker usageChecker)
    {
        super(defaults);

        if( defaults instanceof ConfigurationProperties) {
            ConfigurationProperties defaultConfig = (ConfigurationProperties) defaults;
            _problemConsumer = defaultConfig._problemConsumer;
            _prefixes.addAll(defaultConfig._prefixes);
            _validatingProperties.addAll(defaultConfig._validatingProperties);
        }
        _usageChecker = usageChecker;
    }

    public void setProblemConsumer(ProblemConsumer consumer)
    {
        _problemConsumer = consumer;
    }

    public ProblemConsumer getProblemConsumer()
    {
        return _problemConsumer;
    }

    public void setIsService(boolean isService)
    {
        _isService = isService;
    }

    public boolean hasDeclaredPrefix(String name)
    {
        for (String prefix : _prefixes) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }

    /**
     * @throws IllegalArgumentException during loading if a property
     * is defined multiple times.
     */
    @Override
    public synchronized void load(Reader reader) throws IOException
    {
        _loading = true;
        try {
            super.load(reader);
        } finally {
            _loading = false;
        }
    }

    /**
     * @throws IllegalArgumentException during loading if a property
     * is defined multiple times.
     */
    @Override
    public synchronized void load(InputStream in) throws IOException
    {
        _loading = true;
        try {
            super.load(in);
        } finally {
            _loading = false;
        }
    }

    /**
     * @throws IllegalArgumentException during loading if a property
     * is defined multiple times or the annotations are inappropriate.
     */
    @Override
    public synchronized void loadFromXML(InputStream in)
        throws IOException, InvalidPropertiesFormatException
    {
        _loading = true;
        try {
            super.loadFromXML(in);
        } finally {
            _loading = false;
        }
    }

    /**
     * Loads a Java properties file.
     */
    public void loadFile(File file)
        throws IOException
    {
        try (Reader reader = new FileReader(file)) {
            load(file.getName(), 0, reader);
        }
    }

    /**
     * Wrapper method that ensures error and warning messages have
     * the correct line number.
     * @param source a label describing where Reader is obtaining information
     * @param line Number of lines read so far
     * @param reader Source of the property information
     */
    public void load(String source, int line, Reader reader) throws IOException
    {
        LineNumberReader lnr = new LineNumberReader(reader);
        lnr.setLineNumber(line);
        load(source, lnr);
    }

    /**
     * Wrapper method that ensures error and warning messages have
     * the correct line number.
     * @param source a label describing where Reader is obtaining information
     * @param reader Source of the property information
     */
    public void load(String source, LineNumberReader reader) throws IOException
    {
        _problemConsumer.setFilename(source);
        _problemConsumer.setLineNumberReader(reader);
        try {
            load(new ConfigurationParserAwareReader(reader));
        } finally {
            _problemConsumer.setFilename(null);
        }
    }

    /**
     * @throws IllegalArgumentException during loading if key is
     * already defined.
     */
    @Override
    public synchronized Object put(Object rawKey, Object value)
    {
        checkNotNull(rawKey, "A property key must not be null");
        checkNotNull(value, "A property value must not be null");

        AnnotatedKey key = new AnnotatedKey(rawKey, value);
        String name = key.getPropertyName();
        if (_loading && containsKey(name)) {
            _problemConsumer.error(name + " is already defined");
            return null;
        }

        if (key.hasAnnotation(Annotation.PREFIX)) {
            _prefixes.add(key.getPropertyName() + PREFIX_SEPARATOR);
        }

        checkIsAllowed(key, (String) value);

        if (key.hasAnnotations()) {
            putAnnotatedKey(key);
        }

        AnnotatedKey declaringKey = getAnnotatedKey(name);
        if (declaringKey != null && declaringKey.hasAnyOf(VALIDATING_ANNOTATIONS)) {
            _propertyContext.put(name, _problemConsumer.getContext());
        }

        return key.hasAnyOf(OBSOLETE_FORBIDDEN) ? null : super.put(name, ((String)value).trim());
    }

    protected void checkIsAllowed(AnnotatedKey key, String value)
    {
        String name = key.getPropertyName();
        AnnotatedKey existingKey = getAnnotatedKey(name);
        if (existingKey != null) {
            checkKeyValid(existingKey, key);
        } else if (name.indexOf('/') > -1) {
            _problemConsumer.error(
                    "Property " + name + " is a scoped property. Scoped properties are no longer supported.");
        } else if (!_usageChecker.isStandardProperty(defaults, name)) {
            // TODO: It would be nice if we could check whether the property is actually
            // used, ie if it appears as part of the value of a standard property. To do this
            // we need to implement a multi-pass parser and that means rewriting
            // the entire property checking logic.
            _problemConsumer.info("Property " + name + " is not a standard property");
        }
    }

    private void checkKeyValid(AnnotatedKey existingKey, AnnotatedKey key)
    {
        String name = key.getPropertyName();

        if (existingKey.hasAnnotations() && key.hasAnnotations()) {
            _problemConsumer.error("Property " + name + ": " +
                    "remove \"" + key.getAnnotationDeclaration() + "\"; " +
                    "annotated assignments are not allowed");
        }

        if (existingKey.hasAnyOf(EnumSet.of(Annotation.IMMUTABLE,
                Annotation.PREFIX, Annotation.FORBIDDEN))) {
            _problemConsumer.error(messageFor(existingKey));
        }

        if ((_isService && existingKey.hasAnnotation(Annotation.NOT_FOR_SERVICES)) ||
            existingKey.hasAnyOf(EnumSet.of(Annotation.OBSOLETE, Annotation.DEPRECATED))) {
            _problemConsumer.warning(messageFor(existingKey));
        }
    }


    /**
     * Define the binary relationship property A hasSynonym property B
     * as true iff either:
     * <ul>
     * <li>If there exists precisely one non-deprecated property with a simple reference to
     * property A; e.g.
     * <pre>
     *     property.B = ${property.A}
     *     property.A = some default value
     * </pre>
     * <li>If there exists precisely one deprecated property that hasSynonym property B
     * with a simple reference to property A; e.g.
     * <pre>
     *     property.B = ${property.C}
     *     (deprecated)property.C = ${property.A}
     *     property.A = some default value
     * </pre>
     * <p>
     * This method returns the name of a property that is the subject of
     * hasSynonym relationship (property B) with the supplied property
     * (as property A), or null if no such property exists.
     */
    private String findSynonymOf(String propertyName)
    {
        String synonym = null;
        String simpleReference = "${" + propertyName + "}";

        for (String name : stringPropertyNames()) {
            String value = getProperty(name);
            if (value.equals(simpleReference)) {
                if (synonym != null) {
                    return null;
                }
                synonym = name;
            }
        }

        AnnotatedKey key = getAnnotatedKey(synonym);
        if (key != null && key.hasAnnotation(Annotation.DEPRECATED)) {
            synonym = findSynonymOf(synonym);
        }

        return synonym;
    }

    private String messageFor(AnnotatedKey key)
    {
        String name = key.getPropertyName();

        StringBuilder sb = new StringBuilder();
        sb.append("Property ").append(name).append(": ");

        if (key.hasAnnotation(Annotation.IMMUTABLE)) {
            sb.append("may not be adjusted as it is marked 'immutable'");
        } else if (key.hasAnnotation(Annotation.PREFIX)) {
            sb.append("may not be adjusted as it is marked 'prefix'");
        } else if (key.hasAnnotation(Annotation.FORBIDDEN)) {
            sb.append("may not be adjusted; ");
            sb.append(key.hasError() ? key.getError() : "this property no longer affects dCache");
        } else if (key.hasAnnotation(Annotation.OBSOLETE)) {
            sb.append("please remove this assignment; ");
            sb.append(key.hasError() ? key.getError() : "it has no effect");
        } else if(key.hasAnnotation(Annotation.DEPRECATED)) {
            String synonym = findSynonymOf(name);
            if (synonym != null) {
                sb.append("use \"").append(synonym).append("\" instead");
            } else {
                sb.append("please review configuration");
            }
            sb.append("; support for ").append(name).append(" will be removed in the future");
        } else if (key.hasAnnotation(Annotation.NOT_FOR_SERVICES)) {
            sb.append("consider moving to a domain scope; it has no effect here");
        } else {
            sb.append("has an unknown problem");
        }

        return sb.toString();
    }

    @Override
    public synchronized Enumeration<?> propertyNames()
    {
        return Collections.enumeration(stringPropertyNames());
    }

    public String replaceKeywords(String s)
    {
        return Formats.replaceKeywords(s, _replaceable);
    }

    public String getValue(String name)
    {
        String value = getProperty(name);
        return (value == null) ? null : replaceKeywords(value);
    }

    @Nullable
    public AnnotatedKey getAnnotatedKey(String name)
    {
        AnnotatedKey key = _annotatedKeys.get(name);
        if (key == null && defaults instanceof ConfigurationProperties) {
            key = ((ConfigurationProperties) defaults).getAnnotatedKey(name);
        }
        return key;
    }

    private void putAnnotatedKey(AnnotatedKey key)
    {
        _annotatedKeys.put(key.getPropertyName(), key);

        if (key.hasAnyOf(VALIDATING_ANNOTATIONS)) {
            _validatingProperties.add(key.getPropertyName());
        }
    }

    /**
     * This method validates the configuration data.  It should be run once
     * after all properties are loaded.
     *
     * The context object should be some mutable Multimap implementation
     * (e.g., HashMultimap).  No thread safety is needed and it should be
     * initially empty.  If this method is called on two different
     * ConfigurationProperties instances with some common defaults then the
     * same context object should be presented.
     */
    public void validate(Multimap<String,String> context)
    {
        validate(this, new HashMap<>(), new HashSet<>(), context);
    }

    private void validate(ConfigurationProperties target,
            Map<String,String> namesToCheck, Set<String> namesToSkip,
            Multimap<String,String> reported)
    {
        keySet().stream().map(Object::toString).
                filter(_validatingProperties::contains).
                filter(n -> !namesToCheck.containsKey(n)).
                forEach(n -> {if (reported.containsEntry(n, _propertyContext.get(n))) {
                        namesToSkip.add(n);
                    } else {
                        namesToCheck.put(n, _propertyContext.get(n));
                    }
                });

        if (defaults instanceof ConfigurationProperties) {
            ((ConfigurationProperties)defaults).validate(target, namesToCheck, namesToSkip, reported);
        }

        namesToCheck.entrySet().stream().filter(e -> _annotatedKeys.containsKey(e.getKey())
                    && _annotatedKeys.get(e.getKey()).hasAnnotation(Annotation.ONE_OF)).
                filter(e -> !namesToSkip.contains(e.getKey())).
                forEach(e -> {String value = target.getValue(e.getKey());
                        AnnotatedKey key = _annotatedKeys.get(e.getKey());
                        String oneOfParameter = key.getParameter(Annotation.ONE_OF);
                        Set<String> validValues = ImmutableSet.copyOf(oneOfParameter.split("\\|"));
                        if(!validValues.contains(value)) {
                            String validValuesList = "\"" + Joiner.on("\", \"").join(validValues) + "\"";
                            _problemConsumer.setContext(e.getValue());
                            _problemConsumer.error("Property " + key.getPropertyName() +
                                    ": \"" + value +
                                    "\" is not a valid value.  Must be one of "
                                    + validValuesList);
                            _problemConsumer.setContext(null);
                            reported.put(e.getKey(), e.getValue());
                        }});

        namesToCheck.entrySet().stream().filter(e -> _annotatedKeys.containsKey(e.getKey())
                    && _annotatedKeys.get(e.getKey()).hasAnnotation(Annotation.ANY_OF)).
                filter(e -> !namesToSkip.contains(e.getKey())).
                forEach(e -> {String value = target.getValue(e.getKey());
                        AnnotatedKey key = _annotatedKeys.get(e.getKey());
                        String anyOfParameter = key.getParameter(Annotation.ANY_OF);
                        Set<String> values = Sets.newHashSet(Splitter.on(',')
                                .omitEmptyStrings().trimResults().split(value));
                        Set<String> validValues =
                                ImmutableSet.copyOf(anyOfParameter.split("\\|"));
                        values.removeAll(validValues);
                        if (!values.isEmpty()) {
                            String validValuesList = "\""
                                    + Joiner.on("\", \"").join(validValues) + "\"";
                            _problemConsumer.setContext(e.getValue());
                            _problemConsumer.error("Property " + key.getPropertyName()
                                    + ": \"" + value
                                    + "\" is not a valid value. Must be a comma separated list of "
                                    + validValuesList);
                            _problemConsumer.setContext(null);
                            reported.put(e.getKey(), e.getValue());
                        }});
    }

    /**
     * A class for parsing and storing a set of annotations associated with
     * some specific property declaration's key in addition to a potential
     * custom error message.
     *
     * Annotations take the form of a comma-separated list of keywords
     * within parentheses that immediately precede the property name;
     *
     * If a property is annotated as forbidden then the property value is taken
     * as a custom error message to report.  If the value is empty then a default
     * error message is used instead.
     */
    public static class AnnotatedKey
    {
        private static final String RE_ATTRIBUTE = "[^),]+";
        private static final String RE_SEPARATOR = ",";
        private static final String RE_ANNOTATION_DECLARATION =
            "(\\((" + RE_ATTRIBUTE + "(?:" + RE_SEPARATOR + RE_ATTRIBUTE + ")*)\\))";
        private static final String RE_KEY_DECLARATION =
            RE_ANNOTATION_DECLARATION + "(.*)";

        private static final Pattern PATTERN_KEY_DECLARATION = Pattern.compile(RE_KEY_DECLARATION);
        private static final Pattern PATTERN_SEPARATOR = Pattern.compile(RE_SEPARATOR);

        private static final Set<Annotation> FORBIDDEN_OBSOLETE_DEPRECATED =
            EnumSet.of(Annotation.FORBIDDEN, Annotation.OBSOLETE, Annotation.DEPRECATED);

        private static final Set<Annotation> FORBIDDEN_OBSOLETE =
            EnumSet.of(Annotation.FORBIDDEN, Annotation.OBSOLETE);

        private final String _name;
        private final String _annotationDeclaration;
        private final Map<Annotation,String> _annotations =
                new EnumMap<>(Annotation.class);
        private final String _error;

        public AnnotatedKey(Object propertyKey, Object propertyValue)
        {
            String key = propertyKey.toString();
            Matcher m = PATTERN_KEY_DECLARATION.matcher(key);
            if(m.matches()) {
                _annotationDeclaration = m.group(1);

                for(String annotation : PATTERN_SEPARATOR.split(m.group(2))) {
                    addAnnotation(annotation);
                }

                _name = m.group(3);

                if(countDeclaredAnnotationsFrom(FORBIDDEN_OBSOLETE_DEPRECATED) > 1) {
                    throw new IllegalArgumentException("At most one of forbidden, obsolete " +
                            "and deprecated may be specified.");
                }
            } else {
                _annotationDeclaration = "";
                _name = key;
            }

            _error = hasAnyOf(FORBIDDEN_OBSOLETE) ? propertyValue.toString() : "";
        }

        /**
         * Process an individual attribute declaration.  An annotation has
         * one or more attributes.  Each attribute has the form:
         * <pre>&lt;label>['?'&lt;parameter>]</pre>
         */
        private void addAnnotation(String declaration)
        {
            int idx = declaration.indexOf('?');
            String label = (idx != -1) ? declaration.substring(0, idx) :
                    declaration;
            Annotation annotation = Annotation.forLabel(label);

            checkArgument(!annotation.isParameterRequired() || idx != -1,
                    "Annotation " + label + " declared without parameter");
            checkArgument(annotation.isParameterRequired() || idx == -1,
                    "Annotation " + label + " declared with parameter");

            if(annotation.isParameterRequired()) {
                String parameter = declaration.substring(idx+1,
                        declaration.length());
                _annotations.put(annotation, parameter);
            } else {
                _annotations.put(annotation, null);
            }
        }

        private int countDeclaredAnnotationsFrom(Set<Annotation> items) {
            Collection<Annotation> a = EnumSet.copyOf(items);
            a.retainAll(_annotations.keySet());
            return a.size();
        }

        public boolean hasAnnotation(Annotation annotation) {
            return _annotations.keySet().contains(annotation);
        }

        public final boolean hasAnyOf(Set<Annotation> annotations) {
            return countDeclaredAnnotationsFrom(annotations) > 0;
        }

        public boolean hasAnnotations() {
            return !_annotations.isEmpty();
        }

        public String getAnnotationDeclaration() {
            return _annotationDeclaration;
        }

        public String getPropertyName() {
            return _name;
        }

        public String getError() {
            return _error;
        }

        public boolean hasError() {
            return !_error.isEmpty();
        }

        public String getParameter(Annotation annotation) {
            String parameter = _annotations.get(annotation);

            if(parameter == null) {
                throw new IllegalArgumentException("No such annotation or " +
                        "annotation given without parameter: " + annotation);
            }

            return parameter;
        }
    }

    /**
     *  This enum represents a property key annotation.  Each annotation has
     *  an associated label that is present as a comma-separated list within
     *  parentheses.
     */
    public enum Annotation
    {
        FORBIDDEN("forbidden"),
        OBSOLETE("obsolete"),
        ONE_OF("one-of", true),
        DEPRECATED("deprecated"),
        NOT_FOR_SERVICES("not-for-services"),
        IMMUTABLE("immutable"),
        ANY_OF("any-of", true),
        PREFIX("prefix");

        private static final Map<String,Annotation> ANNOTATION_LABELS =
                new HashMap<>();

        private final String _label;
        private final boolean _isParameterRequired;

        static {
            for( Annotation annotation : Annotation.values()) {
                ANNOTATION_LABELS.put(annotation._label, annotation);
            }
        }

        public static Annotation forLabel(String label)
        {
            checkArgument(ANNOTATION_LABELS.containsKey(label),
                    "Unknown annotation: " + label);
            return ANNOTATION_LABELS.get(label);
        }

        Annotation(String label)
        {
            this(label, false);
        }

        Annotation(String label, boolean isParameterRequired)
        {
            _label = label;
            _isParameterRequired = isParameterRequired;
        }

        public boolean isParameterRequired()
        {
            return _isParameterRequired;
        }
    }


    /**
     * A class that implement this interface, when registered, will accept
     * responsibility for handling the warnings and errors produced when
     * parsing dCache configuration.  These methods may throw an exception,
     * to terminate parsing; however, code using a ProblemsAware class must
     * not assume that this will happen.
     */
    public interface ProblemConsumer {
        public void setFilename(String name);
        public void setLineNumberReader(LineNumberReader reader);
        public String getContext();
        public void setContext(String context);
        public void error(String message);
        public void warning(String message);
        public void info(String message);
    }

    /**
     * This class provides the default behaviour if no problem
     * consumer is registered: warnings are logged and errors
     * result in an IllegalArgumentException being thrown.
     */
    public static class DefaultProblemConsumer implements ProblemConsumer
    {
        private String _filename;
        private LineNumberReader _reader;
        private String _context;

        protected String addContextTo(String message)
        {
            String context = getContext();
            return context == null ? message : (context + ": " + message);
        }

        @Override
        public String getContext()
        {
            if (_context != null) {
                return _context;
            }

            if( _filename == null || _reader == null) {
                return null;
            }

            return _filename + ":" + _reader.getLineNumber();
        }

        @Override
        public void setContext(String context)
        {
            _context = context;
        }

        @Override
        public void error(String message)
        {
            throw new IllegalArgumentException(addContextTo(message));
        }

        @Override
        public void warning(String message)
        {
            _log.warn(addContextTo(message));
        }

        @Override
        public void info(String message)
        {
            _log.info(addContextTo(message));
        }

        @Override
        public void setFilename(String name)
        {
            _filename = name;
            _context = null;
        }

        @Override
        public void setLineNumberReader(LineNumberReader reader)
        {
            _reader = reader;
            _context = null;
        }
    }


    /**
     * This reader wraps a BufferedReader and extends the basic Reader class
     * so that it compensates for Configuration.read behaviour.  The perser's
     * behaviour results in unreliable line numbers being reported if
     * LineNumberReader is used directly.  This is due to two reasons:
     * <p>
     * First, the load method uses an internal buffer to read as much as
     * possible from the reader.  It is very likely that this will include
     * many lines, advancing the LineNumberReader so the line number count
     * will be unreliable.  The put method, when reporting a problem, will
     * very likely use a line number greater than that of the line where the
     * problem is located.
     * <p>
     * Second, when finished parsing a line, if the parsing has exhausted
     * the available data then the parser will always fetch more data.  This
     * is needed if the line ends with a backslash ('\'), but the parser does
     * this unconditionally if the buffer is exhausted.  This behaviour
     * results in an out-by-one error in the line numbers, except when reading
     * the last line.
     * <p>
     * To counter the first problem, this class replies with exactly one line
     * for each read request.  For the second problem, this class injects
     * a empty line in between each real line-read, provided the previous
     * line didn't end with a backslash.  These empty lines do not cause the
     * line number to increase but prevent the out-by-one error.
     * <p>
     * NB. In case it isn't obvious: this class is nothing more than an ugly
     * hack.  The correct solution is to write a replacement parser.
     */
    public static class ConfigurationParserAwareReader extends Reader
    {
        final private BufferedReader _inner;
        private boolean _shouldInjectBlankLine;
        private String _remaining = "";

        public ConfigurationParserAwareReader(BufferedReader reader)
        {
            _inner = reader;
        }

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException
        {
            String data = getDataForParser();
            if(data == null) {
                return -1;
            }

            int count = Math.min(len, data.length());
            System.arraycopy(data.toCharArray(), 0, cbuf, off, count);

            _remaining = data.substring(count);

            if(_remaining.isEmpty()) {
                if (_shouldInjectBlankLine){
                    _shouldInjectBlankLine = false;
                } else {
                    _shouldInjectBlankLine = !data.endsWith("\\\n");
                }
            }

            return count;
        }

        private String getDataForParser() throws IOException
        {
            if( !_remaining.isEmpty()) {
                return _remaining;
            }

            if(_shouldInjectBlankLine) {
                return "\n";
            }

            String data = _inner.readLine();

            return data == null ? null : data + "\n";
        }

        @Override
        public void close() throws IOException {
            _inner.close();
        }
    }

    public interface UsageChecker
    {
        boolean isStandardProperty(Properties defaults, String name);
    }

    public static class UniversalUsageChecker implements UsageChecker
    {
        @Override
        public boolean isStandardProperty(Properties defaults, String name)
        {
            return true;
        }
    }
}
