package org.dcache.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

import org.dcache.util.PortRange;

/**
 * Class to handle accepting incoming connections and verifying the
 * client against some challenge.
 */
public class ProtocolConnectionPool extends Thread {

    private static Logger _logSocketIO = LoggerFactory.getLogger("logger.dev.org.dcache.io.socket");
    private final ServerSocketChannel _serverSocketChannel;
    private final Map<Object, SocketChannel> _acceptedSockets = new HashMap<>();
    private final ChallengeReader _challengeReader;
    private boolean _stop;

    /**
     * Create a new ProtocolConnectionPool using the specified PortRange.
     * The {@link ChallengeReader} is used to associate connections with clients.
     */
    ProtocolConnectionPool(PortRange portRange, int receiveBufferSize,
                           ChallengeReader challengeReader) throws IOException
    {
        super("ProtocolConnectionPool");
        _challengeReader = challengeReader;
        _serverSocketChannel = ServerSocketChannel.open();
        if (receiveBufferSize > 0) {
            _serverSocketChannel.socket().setReceiveBufferSize(receiveBufferSize);
        }

        portRange.bind(_serverSocketChannel.socket());
        if (_logSocketIO.isDebugEnabled()) {
            _logSocketIO.debug("Socket BIND local = " + _serverSocketChannel.socket().getInetAddress() + ":" + _serverSocketChannel.socket().getLocalPort());
        }

    }

    /**
     * Get a {@link SocketChannel} identified by <code>chllenge</code>. The
     * caller will block until client is connected and challenge exchange is done.
     *
     * @param challenge
     * @return {@link SocketChannel} connected to client
     * @throws InterruptedException if current thread was interrupted
     */
    public SocketChannel getSocket(Object challenge) throws InterruptedException {

        synchronized (_acceptedSockets) {

            while (_acceptedSockets.isEmpty() || !_acceptedSockets.containsKey(challenge)) {
                _acceptedSockets.wait();
            }
            return  _acceptedSockets.remove(challenge);
        }
    }

    /**
     * Get TCP port number used by this connection pool.
     * @return port number
     */
    public int getLocalPort() {
        return _serverSocketChannel.socket().getLocalPort();
    }

    @Override
    public void run() {

        while (!_stop) {

            try {

                SocketChannel newSocketChannel = _serverSocketChannel.accept();
                if (_logSocketIO.isDebugEnabled()) {
                    _logSocketIO.debug("Socket OPEN (ACCEPT) remote = " + newSocketChannel.socket().getInetAddress() + ":" + newSocketChannel.socket().getPort() +
                            " local = " + newSocketChannel.socket().getLocalAddress() + ":" + newSocketChannel.socket().getLocalPort());
                }
                Object challenge = _challengeReader.getChallenge(newSocketChannel);

                if (challenge == null) {
                    // Unable to read challenge....skip connection
                    if (_logSocketIO.isDebugEnabled()) {
                        _logSocketIO.debug("Socket CLOSE (no challenge) remote = " + newSocketChannel.socket().getInetAddress() + ":" + newSocketChannel.socket().getPort() +
                                " local = " + newSocketChannel.socket().getLocalAddress() + ":" + newSocketChannel.socket().getLocalPort());
                    }
                    newSocketChannel.close();
                    continue;
                }

                synchronized (_acceptedSockets) {
                    _acceptedSockets.put(challenge, newSocketChannel);
                    _acceptedSockets.notifyAll();
                    Thread.yield();
                }

            } catch (IOException e) {
                _logSocketIO.error("Accept loop", e);
                _stop = true;
                try {
                    _logSocketIO.debug("Socket SHUTDOWN local = {}:{}",
                            _serverSocketChannel.socket().getInetAddress(),
                            _serverSocketChannel.socket().getLocalPort());
                    _serverSocketChannel.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
