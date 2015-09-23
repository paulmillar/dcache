package org.dcache.macaroons;

import com.github.nitram509.jmacaroons.MacaroonsBuilder;
import com.google.common.base.Throwables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import javax.security.auth.Subject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

import dmg.cells.nucleus.AbstractCellComponent;
import dmg.cells.nucleus.CellMessage;
import dmg.cells.nucleus.CellMessageReceiver;

import org.dcache.auth.Subjects;
import org.dcache.macaroons.model.Macaroon;

import static com.github.nitram509.jmacaroons.MacaroonsConstants.MACAROON_SUGGESTED_SECRET_LENGTH;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.primitives.Longs.asList;


/**
 * Handle macaroon-related messages.
 */
public class MacaroonService extends AbstractCellComponent implements CellMessageReceiver
{
    private static final Logger LOG  = LoggerFactory.getLogger(MacaroonService.class);

    /** Size of the identifier string, in characters. */
    private static final int ID_LENGTH = 8;

    private final SecureRandom _random = new SecureRandom();

    private Duration _maximumLifetime;
    private MacaroonDao _dao;

    public void setMaximumLifetime(long value)
    {
        checkArgument(value >= 0);
        _maximumLifetime = value > 0 ? Duration.ofMillis(value) : null;
    }

    @Required
    public void setDao(MacaroonDao dao)
    {
        _dao = dao;
    }

    public void messageArrived(CellMessage envelope, MacaroonRequestMessage message) throws NoSuchElementException
    {
        LOG.trace("Received macaroon request message");

        byte[] secret = new byte[MACAROON_SUGGESTED_SECRET_LENGTH];
        _random.nextBytes(secret);

        // The ID is used only to identify the secret.  Apart from avoiding
        // collisions, any value may be used.
        byte[] digest = getSHA256Digest().digest(secret);
        String id = Base64.getMimeEncoder().withoutPadding().encodeToString(Arrays.copyOf(digest, (ID_LENGTH*3)/4));

        Instant termination = null;

        MacaroonsBuilder builder = new MacaroonsBuilder(message.getPath(), secret, id);

        Stream<String> caveats = message.getCaveats().stream();
        if (_maximumLifetime != null) {
            termination = Instant.now().plus(_maximumLifetime);
            caveats = Stream.concat(caveats, Stream.of("time < " + termination));
        }
        caveats.forEach(builder::add_first_party_caveat);

        Subject subject = message.getSubject();
        Macaroon m = new Macaroon();
        m.setCreated(new Date());
        m.setGids(asList(Subjects.getGids(subject)));
        m.setUid(Subjects.getUid(subject));
        m.setId(id);
        m.setPnfsid(message.getPnfsId().toString());
        m.setSecret(secret);
        if (termination != null) {
            m.setTermination(Date.from(termination));
        }
        _dao.storeMacaroon(m);

        String macaroon = builder.getMacaroon().serialize();
        message.setMacaroon(macaroon);

        if (message.getReplyRequired()) {
            envelope.revertDirection();
            sendMessage(envelope);
        }
    }

    private static MessageDigest getSHA256Digest()
    {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // Should never happen
            throw Throwables.propagate(e);
        }
    }
}
