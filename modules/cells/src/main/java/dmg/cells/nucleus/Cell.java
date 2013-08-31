
package  dmg.cells.nucleus ;
/**
  * Classes, implementing the Cell interface, are the basic
  * building blocks of the Cell Environment. The interface is
  * used to deliver Messages and Exceptions to the Cell, to
  * inform the Cell about a prepared removal of the Cell and
  * to get informations out of the Cell.
  * See <a href=guide/Guide-dmg.cells.nucleus>Guide to dmg.cells.nucleus</a>.
  *
  * @author Patrick Fuhrmann
  * @version 0.1, 15 Feb 1998

  */
public interface Cell {

   /**
    * Notification that the cell should do any activity that cannot safely be
    * done from the constructor.  For example, this can include delivering and
    * accepting messages and starting any background activity.
    *
    * The thread creating the cell is required to call start exactly once.
    * Any "setter" methods needed to configure the cell must be called before
    * calling start.  There is no explicit mechanism to mark such setters, but
    * the concept is equivalent to calling all @Required setters in Spring.
    *
    * If this method throws an exception then the cell is considered defunct
    * and no other methods of this class may not be used.  As a consequence,
    * the Cell class must take care that any background activity will terminate
    * promptly without any further interaction.
    */
   public void start() throws Exception;

   /**
     *  'getInfo' is frequently called by the Domain Kernel
     *  to obtain information out of the particular Cell.
     *  The Cell should return significant informations about
     *  the current status of the Cell.
     */
   public String getInfo() ;
   /**
     *  messageArrived is called by the kernel to deliver messages
     *  to the Cell. The message itself can be extracted out of the
     *  MessageEvent by the getMessage method. The very last event
     *  which is delivered by messageArrived will be a
     *  LastMessageEvent.
     */
   public void   messageArrived( MessageEvent me ) ;
   /**
     *  prepareRemoval is called by the kernel after a kill
     *  of the cell has been initialized. The KillEvent contains
     *  more informations about the initiater of the kill.
     *  After the prepareRemoval returns, the threadGroup of the
     *  cell is immediately stopped.
     *
     *  @param killEvent containing informations about the
     *                   initiater.
     *  @see  KillEvent
     */
   public void   prepareRemoval( KillEvent killEvent ) ;
   public void   exceptionArrived( ExceptionEvent ce ) ;

    CellVersion getCellVersion();
}
