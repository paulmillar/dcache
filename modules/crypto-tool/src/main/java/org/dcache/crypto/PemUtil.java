package org.dcache.crypto;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PEMWriter;

/**
 * Utility class for PEM related content.
 */
public class PemUtil
{
    public interface PemAcceptor
    {
        public void acceptX509Certificate(X509Certificate certificate);
        public void acceptContentInfo(ContentInfo ci);
        public void acceptKeyPair(KeyPair keyPair);
        public void acceptPublicKey(PublicKey publicKey);
        public void acceptX509CRL(X509CRL crl);
        public void acceptUnknownObject(Object object);
    }

    private static class RejectingPemAcceptor implements PemAcceptor
    {
        private final String _error;

        public RejectingPemAcceptor(String error)
        {
            _error = error;
        }

        @Override
        public void acceptX509Certificate(X509Certificate certificate)
        {
            throw new IllegalArgumentException(_error);
        }

        @Override
        public void acceptContentInfo(ContentInfo ci)
        {
            throw new IllegalArgumentException(_error);
        }

        @Override
        public void acceptKeyPair(KeyPair keyPair)
        {
            throw new IllegalArgumentException(_error);
        }

        @Override
        public void acceptPublicKey(PublicKey publicKey)
        {
            throw new IllegalArgumentException(_error);
        }

        @Override
        public void acceptX509CRL(X509CRL crl)
        {
            throw new IllegalArgumentException(_error);
        }

        @Override
        public void acceptUnknownObject(Object object)
        {
            throw new IllegalArgumentException("Unknown PEM object");
        }
    }

    private static class X509CertificatePemAcceptor extends RejectingPemAcceptor
    {
        private X509Certificate _certificate;

        public X509CertificatePemAcceptor()
        {
            super("not an X509 Certificate");
        }

        @Override
        public void acceptX509Certificate(X509Certificate certificate)
        {
            _certificate = certificate;
        }

        public X509Certificate get()
        {
            return _certificate;
        }
    }


    public static String toString(X509Certificate cert)
    {
        Writer w = new StringWriter();
        PEMWriter pw = new PEMWriter(w);
        try {
            pw.writeObject(cert);
            pw.flush();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        return w.toString();
    }

    
    public static X509Certificate parseAsX509Certificate(String data)
    {
        StringReader reader = new StringReader(data);
        try {
            return parseAsX509Certificate(reader);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    
    public static X509Certificate parseAsX509Certificate(Reader reader)
            throws IOException
    {
        X509CertificatePemAcceptor acceptor = new X509CertificatePemAcceptor();
        parsePem(acceptor, reader);
        return acceptor.get();
    }


    public static void parsePem(PemAcceptor acceptor, Reader reader)
            throws IOException
    {
        PEMReader pemReader = new PEMReader(reader);

        Object result = pemReader.readObject();

        if(result instanceof X509Certificate) {
            acceptor.acceptX509Certificate((X509Certificate) result);
        } else if(result instanceof ContentInfo) {
            acceptor.acceptContentInfo((ContentInfo) result);
        } else if(result instanceof KeyPair) {
            acceptor.acceptKeyPair((KeyPair) result);
        } else if(result instanceof PublicKey) {
            acceptor.acceptPublicKey((PublicKey) result);
        } else if(result instanceof X509CRL) {
            acceptor.acceptX509CRL((X509CRL) result);
        } else {
            acceptor.acceptUnknownObject(result);
        }
    }
}
