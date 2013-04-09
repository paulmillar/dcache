package org.dcache.crypto;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateParsingException;
import java.util.Date;
import javax.security.auth.x500.X500Principal;
import java.security.cert.X509Certificate;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.bouncycastle.x509.extension.AuthorityKeyIdentifierStructure;
import org.bouncycastle.x509.extension.SubjectKeyIdentifierStructure;

import static org.dcache.crypto.CertGeneratorUtil.generateCertificate;
import static org.dcache.crypto.CertGeneratorUtil.configureForEEC;
import static org.dcache.crypto.BouncyCastleConstants.BOUNCYCASTLE_NAME;

/**
 * The signer is a class that can sign things
 */
public class CertificateAuthority
{
    public static final String DEFAULT_SIGNATURE_ALGORITHM =
            "SHA256WithRSAEncryption";

    /** Our CA's name */
    private static final String DEFAULT_DN =
            "DC=org, DC=dCache, OU=Unit-testing, CN=Simple CA";

    /** Prefix added to generated certificates */
    private static final String DEFAULT_SIGNING_DN_PREFIX =
            "DC=org, DC=dCache, OU=Unit-testing, CN=";

    public static final String DEFAULT_KEYPAIR_ALGORITHM = "RSA";

    /** Default size, in bits, of generated key-pairs */
    public static final int DEFAULT_KEYPAIR_SIZE = 1024;

    private static final String CA_EMAIL = "unit-test@dcache.org";

    private static final int CA_KEY_USAGE = KeyUsage.cRLSign |
            KeyUsage.keyCertSign;

    private static final int SERVER_KEY_USAGE = KeyUsage.dataEncipherment |
            KeyUsage.digitalSignature | KeyUsage.nonRepudiation |
            KeyUsage.keyEncipherment;
    
    private final X500Principal _caPrincipal;
    private final String _signingAlgorithm;
    private final KeyPairGenerator _kpg;
    private final PublicKey _publicKey;
    private final PrivateKey _privateKey;
    private final String _prefix;
    private final X509Certificate _certificate;

    public CertificateAuthority() throws NoSuchAlgorithmException,
            NoSuchProviderException
    {
        this(DEFAULT_DN, DEFAULT_SIGNING_DN_PREFIX);
    }

    public CertificateAuthority(String dn, String prefix)
            throws NoSuchAlgorithmException, NoSuchProviderException
    {
        this(dn, prefix, DEFAULT_SIGNATURE_ALGORITHM, DEFAULT_KEYPAIR_ALGORITHM,
                DEFAULT_KEYPAIR_SIZE);
    }


    public CertificateAuthority(String dn, String prefix,
            String signingAlgorithm, String keypairAlgorithm,
            int keypairSize) throws NoSuchAlgorithmException,
            NoSuchProviderException
    {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        _prefix = prefix;
        _caPrincipal = new X500Principal(dn);
        _signingAlgorithm = signingAlgorithm;
        _kpg = KeyPairGenerator.getInstance(keypairAlgorithm, BOUNCYCASTLE_NAME);
        _kpg.initialize(keypairSize, new SecureRandom());

        KeyPair kp = _kpg.generateKeyPair();
        _privateKey = kp.getPrivate();
        _publicKey = kp.getPublic();

        X509V3CertificateGenerator generator = getBasicGenerator();

        generator.addExtension(X509Extensions.KeyUsage, true,
                new KeyUsage(CA_KEY_USAGE));


        // self-signed certificate
        generator.setPublicKey(_publicKey);
        generator.setSubjectDN(_caPrincipal);

        generator.addExtension(X509Extensions.BasicConstraints, true,
                new BasicConstraints(true));
        generator.addExtension(X509Extensions.IssuerAlternativeName, false,
                new GeneralNames(new GeneralName(GeneralName.rfc822Name, CA_EMAIL)));


        _certificate = generateCertificate(_privateKey, generator);
    }

    
    public X509Certificate getCertificate()
    {
        return _certificate;
    }

    
    private X509V3CertificateGenerator getBasicGenerator()
    {
        X509V3CertificateGenerator certGen = new X509V3CertificateGenerator();

        certGen.setSerialNumber(BigInteger.valueOf(System.currentTimeMillis()));
        certGen.setIssuerDN(_caPrincipal);
        certGen.setNotBefore(new Date(System.currentTimeMillis() - 10000));
        certGen.setNotAfter(new Date(System.currentTimeMillis() + 10000));
        certGen.setSignatureAlgorithm(_signingAlgorithm);

        return certGen;
    }


    private X509V3CertificateGenerator getCaIssuedGenerator()
    {
        X509V3CertificateGenerator generator = getBasicGenerator();

        generator.addExtension(X509Extensions.BasicConstraints, true,
                new BasicConstraints(false));

        try {
            generator.addExtension(X509Extensions.AuthorityKeyIdentifier, false,
                        new AuthorityKeyIdentifierStructure(_certificate));
        } catch (CertificateParsingException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        return generator;
    }


    /**
     * Generate a public/private keypair and sign the public key to form
     * a certificate.
     * @param name The DNS name of the server.
     * @return A set of credentials that the server may use
     */
    public CertificatedKeyPair createServerCredentials(String name)
    {
        KeyPair kp = _kpg.generateKeyPair();

        X509V3CertificateGenerator generator = getCaIssuedGenerator();

        generator.addExtension(X509Extensions.KeyUsage, true,
                new KeyUsage(SERVER_KEY_USAGE));

        generator.setPublicKey(kp.getPublic());
        generator.setSubjectDN(new X500Principal(_prefix + name));

        generator.addExtension(X509Extensions.ExtendedKeyUsage, true,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));

        generator.addExtension(X509Extensions.IssuerAlternativeName, false,
                new GeneralNames(new GeneralName(GeneralName.dNSName, name)));

        try {
            generator.addExtension(X509Extensions.SubjectKeyIdentifier, false,
                    new SubjectKeyIdentifierStructure(kp.getPublic()));
        } catch (CertificateParsingException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        X509Certificate cert = generateCertificate(_privateKey, generator);

        return new CertificatedKeyPair(kp, cert);
    }


    /**
     * Generate credentials for a person (an End-Entity Certificate)
     * @param subjectDn
     * @return
     */
    public CertificatedKeyPair generateEEC(String name)
    {
        X509V3CertificateGenerator generator = getCaIssuedGenerator();

        KeyPair kp = _kpg.generateKeyPair();
        generator.setPublicKey(kp.getPublic());
        generator.setSubjectDN(new X500Principal(_prefix+name));

        configureForEEC(generator);

        try {
            generator.addExtension(X509Extensions.SubjectKeyIdentifier, false,
                    new SubjectKeyIdentifierStructure(kp.getPublic()));
        } catch (CertificateParsingException e) {
            throw new RuntimeException(e);
        }

        X509Certificate cert = generateCertificate(_privateKey, generator);
        return new CertificatedKeyPair(kp, cert);
    }


    public X500Principal getIssuingPrincipal()
    {
        return _caPrincipal;
    }
}