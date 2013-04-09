package org.dcache.auth;

import com.google.common.collect.Sets;
import org.globus.gsi.jaas.GlobusPrincipal;

import java.security.Principal;
import java.util.Collections;
import java.util.Set;

import org.dcache.auth.FQANPrincipal;
import org.dcache.auth.UidPrincipal;


/**
 * The PrincipalSetMaker is a class that allows code to easily build a
 * Set of principals using the fluent interface.  The final build method
 * provides the set of principals.
 *
 * An example of the intended use of this class is:
 * {@code
 * import static org.dcache.gplazma.plugins.PrincipalSetMaker.aSetOfPrincipals;
 *
 * // ...
 *
 * Set<Principal> principals = aSetOfPrincipals().
 *         withUid(200).
 *         withDn("/O=ACME/CN=Wile E Coyote").
 *         build();
 * }
 */
public class PrincipalSetMaker
{
    private final Set<Principal> _principals = Sets.newHashSet();
    private final Set<Principal> _unmodifiableView =
            Collections.unmodifiableSet(_principals);

    public static PrincipalSetMaker aSetOfPrincipals()
    {
        return new PrincipalSetMaker();
    }

    /**
     * Add a UID Principal to the set.
     * @param uid the id to add
     */
    public PrincipalSetMaker withUid(int uid)
    {
        _principals.add(new UidPrincipal(uid));
        return this;
    }

    /**
     * Add a DN to the set of principals.  The DN should use the OpenSSL
     * format; for example "/O=ACME/CN=Wile E Coyote".
     * @param dn the DN in OpenSSL format.
     */
    public PrincipalSetMaker withDn(String dn)
    {
        _principals.add(new GlobusPrincipal(dn));
        return this;
    }

    /**
     * Add a primary FQAN to the set.  The primary FQAN is the first FQAN
     * in the attribute certificate from a VOMS server.
     * @param fqan the FQAN to add
     */
    public PrincipalSetMaker withPrimaryFqan(String fqan)
    {
        _principals.add(new FQANPrincipal(fqan, true));
        return this;
    }

    /**
     * Add a non-primary FQAN to the set.  The primary FQAN is the first
     * FQAN in the attribute certificate from a VOMS server.
     * @param fqan the FQAN to add
     */
    public PrincipalSetMaker withFqan(String name)
    {
        _principals.add(new FQANPrincipal(name));
        return this;
    }

    /**
     * Clears the set of principals.
     */
    public PrincipalSetMaker thatIsEmpty()
    {
        _principals.clear();
        return this;
    }

    /**
     * Add a primary gid principal.  No checks are made whether adding this
     * principal invalidates the set of Principals by, for example, resulting
     * in a set of principals that has more than one primary gid.
     */
    public PrincipalSetMaker withPrimaryGid(long gid)
    {
        _principals.add(new GidPrincipal(gid, true));
        return this;
    }


    /**
     * Add a username to the set of principals.
     */
    public PrincipalSetMaker withUsername(String username)
    {
        _principals.add(new UserNamePrincipal(username));
        return this;
    }

    /**
     * Provide a unmodifiable view of the set of principals.
     */
    public Set<Principal> build()
    {
        return _unmodifiableView;
    }
}
