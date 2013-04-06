package org.dcache.gplazma.plugins;

import diskCacheV111.namespace.NameSpaceProvider;
import diskCacheV111.util.CacheException;
import diskCacheV111.util.FileNotFoundCacheException;
import diskCacheV111.util.FsPath;
import diskCacheV111.util.PnfsId;
import java.security.Principal;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.security.auth.Subject;
import org.dcache.auth.PrincipalSetMaker;
import static org.dcache.auth.PrincipalSetMaker.aSetOfPrincipals;
import org.dcache.auth.attributes.HomeDirectory;
import org.dcache.auth.attributes.ReadOnly;
import org.dcache.gplazma.AuthenticationException;
import org.dcache.namespace.FileType;
import org.dcache.vehicles.FileAttributes;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Matchers;
import org.mockito.Mockito;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.dcache.gplazma.plugins.HomedirPluginTests.EntryBuilder.directory;
import static org.dcache.gplazma.plugins.HomedirPluginTests.EntryBuilder.file;
import org.hamcrest.Matcher;
import org.mockito.exceptions.verification.NeverWantedButInvoked;

/**
 *  Tests for the homedir plugin
 */
public class HomedirPluginTests
{
    Set<Object> _attributes;
    Set<Principal> _principals;
    Properties _properties;
    NameSpaceProvider _namespace;

    @Before
    public void setup()
    {
        _attributes = new HashSet<>();
        _principals = new HashSet<>();
        _properties = new Properties();
    }


    @Test(expected=AuthenticationException.class)
    public void shouldFailIfNoReadOnlyAttribute() throws AuthenticationException
    {
        given(aNamespace().thatIsEmpty());
        given(configuration().
                with("gplazma.homedir.choose-if-undefined.enabled", "true").
                with("gplazma.homedir.choose-if-undefined.path", "/home").
                with("gplazma.homedir.create-if-absent.enabled", "true").
                with("gplazma.homedir.create-if-absent.permissions", "755"));
        given(aSetOfPrincipals().thatIsEmpty());
        given(aSetOfAttributes().thatIsEmpty());

        whenPluginSessionCalled();
    }


    @Test(expected=AuthenticationException.class)
    public void shouldFailIfMultipleReadOnlyAttributes()
            throws AuthenticationException
    {
        given(aNamespace().thatIsEmpty());
        given(configuration().
                with("gplazma.homedir.choose-if-undefined.enabled", "true").
                with("gplazma.homedir.choose-if-undefined.path", "/home").
                with("gplazma.homedir.create-if-absent.enabled", "true").
                with("gplazma.homedir.create-if-absent.permissions", "755"));
        given(aSetOfPrincipals().thatIsEmpty());
        given(aSetOfAttributes().withReadOnly(false).withReadOnly(true));

        whenPluginSessionCalled();
    }


    @Test
    public void shouldAlwaysAcceptReadOnlyUser() throws AuthenticationException
    {
        given(aNamespace().thatIsEmpty());
        given(configuration().
                with("gplazma.homedir.choose-if-undefined.enabled", "true").
                with("gplazma.homedir.choose-if-undefined.path", "/home").
                with("gplazma.homedir.create-if-absent.enabled", "true").
                with("gplazma.homedir.create-if-absent.permissions", "755"));
        given(aSetOfPrincipals().thatIsEmpty());
        given(aSetOfAttributes().withReadOnly(true));

        whenPluginSessionCalled();

        assertThat(_namespace, hasCreatedNoEntries());
    }


    @Test(expected=AuthenticationException.class)
    public void shouldFailIfMissingHomeDirAttributeAndNotConfiguredToBuildIt()
            throws AuthenticationException
    {
        given(aNamespace().thatIsEmpty());
        given(configuration().
                with("gplazma.homedir.choose-if-undefined.enabled", "false").
                with("gplazma.homedir.choose-if-undefined.path", "/home").
                with("gplazma.homedir.create-if-absent.enabled", "true").
                with("gplazma.homedir.create-if-absent.permissions", "755"));
        given(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000));
        given(aSetOfAttributes().withReadOnly(false));

        whenPluginSessionCalled();
    }


    @Test(expected=AuthenticationException.class)
    public void shouldFailIfMissingHomeDirAttributeAndNotUsernamePrincipal()
            throws AuthenticationException
    {
        given(aNamespace().thatIsEmpty());
        given(configuration().
                with("gplazma.homedir.choose-if-undefined.enabled", "true").
                with("gplazma.homedir.choose-if-undefined.path", "/home").
                with("gplazma.homedir.create-if-absent.enabled", "true").
                with("gplazma.homedir.create-if-absent.permissions", "755"));
        given(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000));
        given(aSetOfAttributes().withReadOnly(false));

        whenPluginSessionCalled();
    }


    @Test
    public void shouldAcceptExistingHomeDir() throws AuthenticationException
    {
        given(aNamespace().
                with(directory("/home/paul").uid(1000).gid(1000).mode(0755)));
        given(configuration().
                with("gplazma.homedir.choose-if-undefined.enabled", "true").
                with("gplazma.homedir.choose-if-undefined.path", "/home").
                with("gplazma.homedir.create-if-absent.enabled", "true").
                with("gplazma.homedir.create-if-absent.permissions", "755"));
        given(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000));
        given(aSetOfAttributes().withHomeDir("/home/paul").withReadOnly(false));

        whenPluginSessionCalled();
    }


    @Test
    public void shouldAcceptExistingHomeDirBuildFromUsername()
            throws AuthenticationException
    {
        given(aNamespace().
                with(directory("/home/paul").uid(1000).gid(1000).mode(0755)));
        given(configuration().
                with("gplazma.homedir.choose-if-undefined.enabled", "true").
                with("gplazma.homedir.choose-if-undefined.path", "/home").
                with("gplazma.homedir.create-if-absent.enabled", "true").
                with("gplazma.homedir.create-if-absent.permissions", "755"));
        given(aSetOfPrincipals().
                withUid(1000).
                withPrimaryGid(1000).
                withUsername("paul"));
        given(aSetOfAttributes().withReadOnly(false));

        whenPluginSessionCalled();

        assertThat(_attributes, hasHomeDirectory("/home/paul"));
        assertThat(_namespace, hasCreatedNoEntries());
    }


    @Test(expected=AuthenticationException.class)
    public void shouldFailLoginIfMultipleHomeDir()
            throws AuthenticationException
    {
        given(aNamespace().thatIsEmpty());
        given(configuration().
                with("gplazma.homedir.choose-if-undefined.enabled", "true").
                with("gplazma.homedir.choose-if-undefined.path", "/home").
                with("gplazma.homedir.create-if-absent.enabled", "true").
                with("gplazma.homedir.create-if-absent.permissions", "755"));
        given(aSetOfAttributes().
                withHomeDir("/home/paul").
                withHomeDir("/home2/paul").
                withReadOnly(false));

        whenPluginSessionCalled();
    }


    @Test(expected=AuthenticationException.class)
    public void shouldFailLoginIfHomeDirExistsAsFile()
            throws AuthenticationException
    {
        given(aNamespace().
                with(file("/home/paul").uid(1000).gid(1000).mode(0644)));
        given(configuration().
                with("gplazma.homedir.choose-if-undefined.enabled", "true").
                with("gplazma.homedir.choose-if-undefined.path", "/home").
                with("gplazma.homedir.create-if-absent.enabled", "true").
                with("gplazma.homedir.create-if-absent.permissions", "755"));
        given(aSetOfAttributes().withHomeDir("/home/paul").withReadOnly(false));

        whenPluginSessionCalled();
    }


    @Test
    public void shouldCreateDirIfNotExisting() throws AuthenticationException
    {
        given(aNamespace().thatIsEmpty());
        given(configuration().
                with("gplazma.homedir.choose-if-undefined.enabled", "true").
                with("gplazma.homedir.choose-if-undefined.path", "/home").
                with("gplazma.homedir.create-if-absent.enabled", "true").
                with("gplazma.homedir.create-if-absent.permissions", "755"));
        given(aSetOfAttributes().withHomeDir("/home/paul").withReadOnly(false));
        given(aSetOfPrincipals().withUid(1000).withPrimaryGid(2000));

        whenPluginSessionCalled();

        assertThat(_namespace, hasCreated().aDirectory("/home/paul").
                withUid(1000).withGid(2000).withMode(0755));
    }


    @Test(expected=AuthenticationException.class)
    public void shouldFailIfCreatingDirectoryWithNoUid()
            throws AuthenticationException
    {
        given(aNamespace().thatIsEmpty());
        given(configuration().
                with("gplazma.homedir.choose-if-undefined.enabled", "true").
                with("gplazma.homedir.choose-if-undefined.path", "/home").
                with("gplazma.homedir.create-if-absent.enabled", "true").
                with("gplazma.homedir.create-if-absent.permissions", "755"));
        given(aSetOfAttributes().withHomeDir("/home/paul").withReadOnly(false));
        given(aSetOfPrincipals().withPrimaryGid(2000));

        whenPluginSessionCalled();
    }


    @Test(expected=AuthenticationException.class)
    public void shouldFailIfCreatingDirectoryWithTwoUids()
            throws AuthenticationException
    {
        given(aNamespace().thatIsEmpty());
        given(configuration().
                with("gplazma.homedir.choose-if-undefined.enabled", "true").
                with("gplazma.homedir.choose-if-undefined.path", "/home").
                with("gplazma.homedir.create-if-absent.enabled", "true").
                with("gplazma.homedir.create-if-absent.permissions", "755"));
        given(aSetOfAttributes().withHomeDir("/home/paul").withReadOnly(false));
        given(aSetOfPrincipals().
                withUid(1000).
                withUid(2000).
                withPrimaryGid(2000));

        whenPluginSessionCalled();
    }


    @Test(expected=AuthenticationException.class)
    public void shouldFailIfCreatingDirectoryWithNoGid()
            throws AuthenticationException
    {
        given(aNamespace().thatIsEmpty());
        given(configuration().
                with("gplazma.homedir.choose-if-undefined.enabled", "true").
                with("gplazma.homedir.choose-if-undefined.path", "/home").
                with("gplazma.homedir.create-if-absent.enabled", "true").
                with("gplazma.homedir.create-if-absent.permissions", "755"));
        given(aSetOfAttributes().withHomeDir("/home/paul").withReadOnly(false));
        given(aSetOfPrincipals().withUid(1000));

        whenPluginSessionCalled();
    }


    @Test(expected=AuthenticationException.class)
    public void shouldFailIfCreatingDirectoryWithTwoPrimaryGids()
            throws AuthenticationException
    {
        given(aNamespace().thatIsEmpty());
        given(configuration().
                with("gplazma.homedir.choose-if-undefined.enabled", "true").
                with("gplazma.homedir.choose-if-undefined.path", "/home").
                with("gplazma.homedir.create-if-absent.enabled", "true").
                with("gplazma.homedir.create-if-absent.permissions", "755"));
        given(aSetOfAttributes().withHomeDir("/home/paul").withReadOnly(false));
        given(aSetOfPrincipals().
                withUid(1000).
                withPrimaryGid(1000).
                withPrimaryGid(2000));

        whenPluginSessionCalled();
    }



    @Test(expected=AuthenticationException.class)
    public void shouldFailIfBuildIfNotDefinedFalse()
            throws AuthenticationException
    {
        given(aNamespace().
                with(directory("/home/paul").uid(1000).gid(1000).mode(0755)));
        given(configuration().
                with("gplazma.homedir.choose-if-undefined.enabled", "false").
                with("gplazma.homedir.choose-if-undefined.path", "/home").
                with("gplazma.homedir.create-if-absent.enabled", "true").
                with("gplazma.homedir.create-if-absent.permissions", "755"));
        given(aSetOfAttributes().withReadOnly(false));
        given(aSetOfPrincipals().
                withUsername("paul").
                withUid(1000).
                withPrimaryGid(2000));

        whenPluginSessionCalled();
    }


    @Test
    public void shouldAddHomeDirAttrAndCreateDir()
            throws AuthenticationException
    {
        given(aNamespace().thatIsEmpty());
        given(configuration().
                with("gplazma.homedir.choose-if-undefined.enabled", "true").
                with("gplazma.homedir.choose-if-undefined.path", "/home").
                with("gplazma.homedir.create-if-absent.enabled", "true").
                with("gplazma.homedir.create-if-absent.permissions", "755"));
        given(aSetOfAttributes().withReadOnly(false));
        given(aSetOfPrincipals().
                withUsername("paul").
                withUid(1000).
                withPrimaryGid(2000));

        whenPluginSessionCalled();

        assertThat(_attributes, hasHomeDirectory("/home/paul"));
        assertThat(_namespace, hasCreated().aDirectory("/home/paul").
                withUid(1000).withGid(2000).withMode(0755));
    }

    private AttributeBuilder aSetOfAttributes()
    {
        return new AttributeBuilder();
    }

    private PropertyBuilder configuration()
    {
        return new PropertyBuilder();
    }

    private void given(AttributeBuilder attributes)
    {
        _attributes.clear();
        _attributes.addAll(attributes.build());
    }

    private void given(PropertyBuilder properties)
    {
        _properties = properties.build();
    }

    private void given(MockedNameSpaceBuilder namespace)
    {
        _namespace = namespace.build();
    }

    private void given(PrincipalSetMaker principals)
    {
        _principals = principals.build();
    }

    private void whenPluginSessionCalled() throws AuthenticationException
    {
        HomedirPlugin plugin = new HomedirPlugin(_properties);
        plugin.setNamespace(_namespace);
        plugin.session(_principals, _attributes);
    }

    /**
     * Attribute set builder using a fluent interface
     */
    private static class AttributeBuilder
    {
        Set<Object> _attributes = new HashSet<>();

        public AttributeBuilder withHomeDir(String dir)
        {
            _attributes.add(new HomeDirectory(dir));
            return this;
        }

        public AttributeBuilder withReadOnly(boolean isReadOnly)
        {
            _attributes.add(new ReadOnly(isReadOnly));
            return this;
        }

        public AttributeBuilder thatIsEmpty()
        {
            _attributes.clear();
            return this;
        }

        public Set<Object> build()
        {
            return Collections.unmodifiableSet(_attributes);
        }
    }

    /**
     * Property builder using a fluent interface
     */
    private static class PropertyBuilder
    {
        Properties _properties = new Properties();

        public PropertyBuilder with(String key, String value)
        {
            _properties.setProperty(key, value);
            return this;
        }

        public Properties build()
        {
            return _properties;
        }
    }

    public MockedNameSpaceBuilder aNamespace()
    {
        return new MockedNameSpaceBuilder();
    }


    /**
     * This class provides a builder that uses a fluent interface to allow
     * configuration of a limited mocked NameSpaceProvider.  It may be
     * programmed to behave as a real NameSpaceProvider would when
     * holding the specified information.
     *
     * The namespace acts more like a keyed object store than a true namespace:
     * a "file" or "directory" can exist independently of the parent directory.
     * Nevertheless, it is possible to simulate the behaviour or a true
     * namespace by correct population of entries.
     *
     * The mocked namespace supports two method: getFileAttributes and
     * pathToPnfsid.  Both methods provide no security: they ignore the
     * Subject argument and act as if the supplied subject was root.  The
     * getFileAttributes returns all configured attributes rather than the
     * requested set.
     *
     * If a call is made to getFileAttributes with an unknown PnfsId or to
     * pathToPnfsid with an unknown path then the appropriate exception is
     * thrown.
     */
    public static class MockedNameSpaceBuilder
    {
        private static final CacheException NOT_FOUND_EXCEPTION =
                new FileNotFoundCacheException("no such file or directory");

        private final NameSpaceProvider _namespace = mock(NameSpaceProvider.class);
        private final Map<FsPath,PnfsId> _pathToId = new HashMap<>();
        private final Map<PnfsId,FileAttributes> _idToType = new HashMap<>();

        private int _idCounter;

        public MockedNameSpaceBuilder with(EntryBuilder entry)
        {
            FsPath path = entry.getPath();
            FileAttributes attr = entry.getAttributes();
            PnfsId id = createEntry(path);
            _pathToId.put(path, id);
            _idToType.put(id, attr);
            return this;
        }

        public MockedNameSpaceBuilder thatIsEmpty()
        {
            _pathToId.clear();
            _idToType.clear();
            return this;
        }

        private PnfsId createEntry(FsPath path)
        {
            PnfsId id = newId();
            _pathToId.put(path, id);
            return id;
        }

        private PnfsId newId()
        {
            String id = String.format("%036x", _idCounter++);
            return new PnfsId(id);
        }

        public NameSpaceProvider build()
        {
            try {
                for(Map.Entry<PnfsId,FileAttributes> entry :
                        _idToType.entrySet()) {
                    when(_namespace.getFileAttributes(
                            Matchers.any(Subject.class),
                            Matchers.eq(entry.getKey()),
                            Matchers.any(EnumSet.class))).
                            thenReturn(entry.getValue());
                }

                for(Map.Entry<FsPath,PnfsId> entry : _pathToId.entrySet()) {
                    String path = entry.getKey().toString();
                    PnfsId id = entry.getValue();
                    when(_namespace.pathToPnfsid(Matchers.any(Subject.class),
                            Matchers.eq(path), Matchers.anyBoolean())).
                            thenReturn(id);
                }

                when(_namespace.getFileAttributes(Matchers.any(Subject.class),
                        Matchers.argThat(new IsUnknownPnfs()),
                        Matchers.any(EnumSet.class))).
                        thenThrow(NOT_FOUND_EXCEPTION);

                when(_namespace.pathToPnfsid(Matchers.any(Subject.class),
                        Matchers.argThat(new IsUnknownPath()),
                        Matchers.anyBoolean())).
                        thenThrow(NOT_FOUND_EXCEPTION);
            } catch (CacheException e) {
                throw new RuntimeException(e);
            }

            when(_namespace.toString()).thenReturn("mocked namespace");

            return _namespace;
        }

        /**
         * A class used to direct Mockito's behaviour when the argument to
         * a mocked method is an unknown PnfsId.
         */
        private class IsUnknownPnfs extends ArgumentMatcher<PnfsId>
        {
            @Override
            public boolean matches(Object argument)
            {
                if(!(argument instanceof PnfsId)) {
                    return true;
                }

                return !_idToType.containsKey((PnfsId) argument);
            }
        }

        /**
         * A class used to direct Mockito's behaviour when the argument to
         * a mocked method is an unknown path.
         */
        private class IsUnknownPath extends ArgumentMatcher<String>
        {
            @Override
            public boolean matches(Object argument)
            {
                if(!(argument instanceof String)) {
                    return true;
                }

                FsPath path = new FsPath((String)argument);
                return !_pathToId.containsKey(path);
            }
        }
    }


    /**
     * Fluent class for building a namespace entry (file or directory).
     */
    public static class EntryBuilder
    {
        private final FsPath _path;
        private final FileAttributes _attributes = new FileAttributes();

        public static EntryBuilder file(String path)
        {
            return new EntryBuilder(FileType.REGULAR, path);
        }

        public static EntryBuilder directory(String path)
        {
            return new EntryBuilder(FileType.DIR, path);
        }

        public EntryBuilder(FileType type, String path)
        {
            _attributes.setFileType(type);
            _path = new FsPath(path);
        }

        public EntryBuilder uid(int uid)
        {
            _attributes.setOwner(uid);
            return this;
        }

        public EntryBuilder gid(int gid)
        {
            _attributes.setGroup(gid);
            return this;
        }

        public EntryBuilder mode(int permission)
        {
            _attributes.setMode(permission);
            return this;
        }

        public FsPath getPath()
        {
            return _path;
        }

        public FileAttributes getAttributes()
        {
            return _attributes;
        }
    }

    public HasCreatedEntry hasCreated()
    {
        return new HasCreatedEntry();
    }

    /**
     * This class allows the assertion that a particular namespace entry
     * has been created.  It uses the fluent interface to describe the kind
     * of entry who's creation is being asserted.
     */
    public class HasCreatedEntry extends BaseMatcher<NameSpaceProvider>
    {
        private FsPath _path;
        private FileType _type;
        private int _uid;
        private int _gid;
        private int _mode;

        public HasCreatedEntry aDirectory(String path)
        {
            _path = new FsPath(path);
            _type = FileType.DIR;
            return this;
        }

        public HasCreatedEntry aFile(String path)
        {
            _path = new FsPath(path);
            _type = FileType.REGULAR;
            return this;
        }

        public HasCreatedEntry withUid(int uid)
        {
            _uid = uid;
            return this;
        }

        public HasCreatedEntry withGid(int gid)
        {
            _gid = gid;
            return this;
        }

        public HasCreatedEntry withMode(int mode)
        {
            _mode = mode;
            return this;
        }

        @Override
        public void describeTo(Description d)
        {
            d.appendText("a namespace in which a ");
            d.appendText(_type == FileType.DIR ? "directory" : "file");
            d.appendText(" ");
            d.appendValue(_path);
            d.appendText(" was created.");
        }

        @Override
        public boolean matches(Object o)
        {
            if(!(o instanceof NameSpaceProvider)) {
                return false;
            }

            NameSpaceProvider namespace = (NameSpaceProvider) o;
            try {
                Mockito.verify(namespace).createEntry(
                        Matchers.any(Subject.class),
                        Mockito.eq(_path.toString()),
                        Mockito.eq(_uid),
                        Mockito.eq(_gid),
                        Mockito.eq(_mode),
                        Mockito.eq(_type == FileType.DIR));
            } catch (CacheException e) {
                throw new RuntimeException(e);
            }

            return true;
        }
    }

    public NoEntryCreated hasCreatedNoEntries()
    {
        return new NoEntryCreated();
    }

    /**
     * Verify that a mocked namespace has not been instructed to create any
     * entries.
     */
    public class NoEntryCreated extends BaseMatcher<NameSpaceProvider>
    {
        @Override
        public boolean matches(Object o)
        {
            if(!(o instanceof NameSpaceProvider)) {
                return false;
            }

            NameSpaceProvider namespace = (NameSpaceProvider) o;
            try {
                Mockito.verify(namespace, Mockito.never()).
                        createEntry(Matchers.any(Subject.class),
                        Mockito.anyString(),
                        Mockito.anyInt(),
                        Mockito.anyInt(),
                        Mockito.anyInt(),
                        Mockito.anyBoolean());
            } catch (CacheException e) {
                throw new RuntimeException(e);
            } catch (NeverWantedButInvoked e) {
                return false;
            }

            return true;
        }

        @Override
        public void describeTo(Description d)
        {
            d.appendText("a namespace in which nothing was created");
        }
    }

    private static Matcher<Iterable<? super HomeDirectory>>
            hasHomeDirectory(String directory)
    {
        return hasItem(new HomeDirectory(directory));
    }
}
