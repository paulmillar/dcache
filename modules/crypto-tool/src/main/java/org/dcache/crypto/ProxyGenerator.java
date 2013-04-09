package org.dcache.crypto;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Date;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.bouncycastle.x509.extension.AuthorityKeyIdentifierStructure;
import org.bouncycastle.x509.extension.SubjectKeyIdentifierStructure;

import static org.dcache.crypto.CertGeneratorUtil.configureForEEC;
import static org.dcache.crypto.CertGeneratorUtil.generateCertificate;
import static org.dcache.crypto.BouncyCastleConstants.BOUNCYCASTLE_NAME;

/**
 *  An instance of this class will generate proxy certificates
 */
public class ProxyGenerator
{
    private final KeyPairGenerator _kpg;

    private final String _signingAlgorithm;

    /**
     * Create ProxyGenerator using the same default settings as
     * CertificateAuthority
     */
    public ProxyGenerator()
            throws NoSuchAlgorithmException, NoSuchProviderException
    {
        this(CertificateAuthority.DEFAULT_SIGNATURE_ALGORITHM,
                CertificateAuthority.DEFAULT_KEYPAIR_ALGORITHM,
                CertificateAuthority.DEFAULT_KEYPAIR_SIZE);
    }


    /**
     * Create a ProxyGenerator using specified algorithm and key size.
     */
    public ProxyGenerator(String signingAlgorithm, String keyAlgorithm,
            int keySize) throws NoSuchAlgorithmException, NoSuchProviderException
    {
        _signingAlgorithm = signingAlgorithm;
        _kpg = KeyPairGenerator.getInstance(keyAlgorithm, BOUNCYCASTLE_NAME);        
        _kpg.initialize(keySize, new SecureRandom());
    }

    
    public synchronized CertificatedKeyPair generateProxy(CertificatedKeyPair ckp)
    {
        X509V3CertificateGenerator generator = new X509V3CertificateGenerator();

        generator.setSignatureAlgorithm(_signingAlgorithm);
        generator.setSerialNumber(BigInteger.valueOf(System.currentTimeMillis()));
        generator.setIssuerDN(ckp.getCertificate().getSubjectX500Principal());
        generator.setNotBefore(new Date());
        generator.setNotAfter(new Date(System.currentTimeMillis() + 10000));

        configureForEEC(generator);

        String dn = ckp.getCertificate().getIssuerX500Principal().toString();
        generator.setSubjectDN(new X500Principal(dn + ", CN=proxy"));

        KeyPair kp = _kpg.generateKeyPair();
        generator.setPublicKey(kp.getPublic());

        try {
            generator.addExtension(X509Extensions.AuthorityKeyIdentifier, false,
                    new AuthorityKeyIdentifierStructure(ckp.getCertificate()));
            generator.addExtension(X509Extensions.SubjectKeyIdentifier, false,
                    new SubjectKeyIdentifierStructure(kp.getPublic()));
        } catch (CertificateParsingException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        X509Certificate proxyCertificate = generateCertificate(ckp.getPrivateKey(),
            generator);

        return new CertificatedKeyPair(kp, proxyCertificate);
    }
}
