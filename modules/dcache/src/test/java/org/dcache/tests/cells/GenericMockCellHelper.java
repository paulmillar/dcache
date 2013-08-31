package org.dcache.tests.cells;

import com.google.common.base.Throwables;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import diskCacheV111.vehicles.Message;

import dmg.cells.nucleus.CellAdapter;
import dmg.cells.nucleus.CellMessage;
import dmg.cells.nucleus.CellMessageAnswerable;
import dmg.cells.nucleus.CellNucleus;
import dmg.cells.nucleus.CellPath;
import dmg.cells.nucleus.NoRouteToCellException;
import dmg.cells.nucleus.SerializationException;

public class GenericMockCellHelper extends CellAdapterHelper {

    private static class MessageEnvelope {

        private final Message _message;
        private final boolean _isPersistent;

        MessageEnvelope(Message message, boolean isPersistent) {
            _message = message;
            _isPersistent = isPersistent;
        }

        Message getMessage() {
            return _message;
        }

        boolean isPersistent() {
            return _isPersistent;
        }
    }
    private static final Map<CellPath, Map<String, List<MessageEnvelope>>> _messageQueue = new HashMap<>();
    private final static Map<String, Map<Class<?>, MessageAction>> _messageActions = new HashMap<>();
    private final CellNucleus _nucleus;

    public GenericMockCellHelper(String name, String args) {
        super(name, args);
        _nucleus = new NucleusHelper(this, name + "-fake");

        try {
            start();
        } catch (Exception e) {
            Throwables.propagate(e);
        }
    }

    @Override
    public CellMessage sendAndWait(CellMessage msg, long millisecs) throws NoRouteToCellException, InterruptedException {

        Object messageObject = msg.getMessageObject();

        if (messageObject instanceof Message) {


            CellPath destinationPath = msg.getDestinationPath();

            Map<String, List<MessageEnvelope>> messagesByType = _messageQueue.get(destinationPath);
            if (messagesByType == null) {
                return null;
            }

            String messageType = messageObject.getClass().getName();
            List<MessageEnvelope> messages = messagesByType.get(messageType);

            if (messages == null || messages.isEmpty()) {
                return null;
            }

            MessageEnvelope messageEnvelope = messages.get(0);
            if (!messageEnvelope.isPersistent()) {
                messages.remove(0);
            }

            sendMessage(msg);

            Message message = messageEnvelope.getMessage();
            message.setReply();
            msg.setMessageObject(message);

            return msg;
        }

        return null;
    }

    @Override
    public void sendMessage(CellMessage msg, CellMessageAnswerable callback, long timeout) throws SerializationException {

        Map<String, List<MessageEnvelope>> messages = _messageQueue.get(msg.getDestinationPath());
        List<MessageEnvelope> envelopes = messages.get(msg.getMessageObject().getClass().getName());
        MessageEnvelope m = envelopes.get(0);
        if(!m.isPersistent()) {
            envelopes.remove(0);
        }
        callback.answerArrived(msg, new CellMessage(msg.getDestinationPath(), m.getMessage()));
    }

    @Override
    public void sendMessage(CellMessage msg) throws NoRouteToCellException {

        String destinations = msg.getDestinationPath().getCellName();

        Map<Class<?>, MessageAction> actions = _messageActions.get(destinations);
        if (actions != null) {
            // there is something pre-defined
            MessageAction action = actions.get(msg.getMessageObject().getClass());
            if (action != null) {
                msg.revertDirection();
                action.messageArraved(msg);
            }
        }

    }

    /**
     *
     * same as <i>prepareMessage( cellPath, message, false);</i>
     *
     * @param cellPath
     * @param message
     */
    public static void prepareMessage(CellPath cellPath, Message message) {
        prepareMessage(cellPath, message, false);
    }

    /**
     * create pre-defined reply from a cell.
     *
     * @param cellPath
     * @param message
     * @param isPesistent remove message from reply list if false
     */
    public static void prepareMessage(CellPath cellPath, Message message, boolean isPesistent) {

        Map<String, List<MessageEnvelope>> messagesByType = _messageQueue.get(cellPath);

        if (messagesByType == null) {
            messagesByType = new HashMap<>();
            _messageQueue.put(cellPath, messagesByType);
        }

        String messageType = message.getClass().getName();
        List<MessageEnvelope> messages = messagesByType.get(messageType);
        if (messages == null) {
            messages = new ArrayList<>();
            messagesByType.put(messageType, messages);
        }
        messages.add(new MessageEnvelope(message, isPesistent));

    }


    /*
     * Fake nucleus
     */
    @Override
    public CellNucleus getNucleus() {
        return _nucleus;

    }

    public static class NucleusHelper extends CellNucleus {

        public final CellAdapter _cell;

        public NucleusHelper(CellAdapter cell, String name) {
            super(cell, name, "Generic");
            _cell = cell;
        }

        @Override
        public void sendMessage(CellMessage msg, boolean local, boolean remote,
                CellMessageAnswerable callback, long timeout) {

            CellMessage reply;
            try {
                reply = _cell.sendAndWait(msg, timeout);
                if (reply != null) {
                    callback.answerArrived(msg, reply);
                }
            } catch (NoRouteToCellException | InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }
    }

    public static void registerAction(String cellName, Class<?> messageClass, MessageAction action) {


        Map<Class<?>, MessageAction> actions = _messageActions.get(cellName);
        if (actions == null) {
            actions = new HashMap<>();
            _messageActions.put(cellName, actions);
        }

        actions.put(messageClass, action);

    }

    public interface MessageAction {

        public void messageArraved(CellMessage message);
    }

    public static void clean() {
        _messageActions.clear();
        _messageQueue.clear();
    }
}
