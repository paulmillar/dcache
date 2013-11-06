package dmg.cells.nucleus ;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import dmg.util.Args;
import dmg.util.AuthorizedString;
import dmg.util.CommandException;
import dmg.util.CommandRequestable;
import dmg.util.DomainInterruptHandler;
import dmg.util.Gate;
import dmg.util.logback.FilterShell;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
  *
  *
  * @author Patrick Fuhrmann
  * @version 0.1, 15 Feb 1998
  */
public class      SystemCell
    extends    CellAdapter
    implements Runnable, Thread.UncaughtExceptionHandler
{
    private final static Logger _log = LoggerFactory.getLogger(SystemCell.class);

    /* Released on OOM to increase the chance that the shutdown succeeds.
     */
    private byte[] _oomSafetyBuffer = new byte[2 << 20];

   private final CellShell   _cellShell ;
   private final CellNucleus _nucleus ;
   private int  _packetsReceived,
                _packetsAnswered,
                _packetsForwarded,
                _packetsReplied,
                _exceptionCounter;
   private DomainInterruptHandler _interruptHandler;
   private Thread                 _interruptThread;
   private long                   _interruptTimer   = 2000 ;
   private final Runtime                _runtime = Runtime.getRuntime() ;
   private final Gate                   _shutdownLock = new Gate(false);
   private AtomicBoolean _hasShutdownStarted = new AtomicBoolean();
   private AtomicBoolean _hasEmergencyShutdownStarted = new AtomicBoolean();

   private class TheKiller extends Thread {
      @Override
      public void run(){
         _log.info("Running shutdown sequence");
         kill() ;
         _log.info("Kill done, waiting for shutdown lock");
         _shutdownLock.check() ;
         _log.info("Killer done");
      }
   }
   public SystemCell( String cellDomainName  ){
       super( cellDomainName ) ;

       _nucleus   = getNucleus() ;
       _cellShell = new CellShell( getNucleus() ) ;
       _cellShell.addCommandListener(this);
       _cellShell.addCommandListener(new LogbackShell());
       _cellShell.addCommandListener(new FilterShell(_nucleus.getLoggingThresholds()));
       useInterpreter( false ) ;

       _runtime.addShutdownHook( new TheKiller() ) ;

       Thread.setDefaultUncaughtExceptionHandler(this);
//       setPrintoutLevel(0xff);

//      addCellEventListener() ;
   }

   //
   // interface from Cell
   //
   public String toString(){
      long fm = _runtime.freeMemory() ;
      long tm = _runtime.totalMemory() ;
      return  getCellDomainName()+
              ":IOrec="+_packetsReceived+
              ";IOexc="+_exceptionCounter+
              ";MEM="+(tm-fm) ;
   }
   public int  enableInterrupts( String handlerName ){
      Class<? extends DomainInterruptHandler> handlerClass;
      try{
          handlerClass = Class.forName(handlerName).asSubclass(DomainInterruptHandler.class);
      }catch( ClassNotFoundException cnfe ){
          _log.warn( "Couldn't install interrupt handler ("+
                handlerName+") : "+cnfe ) ;
         return -1 ;
      }
      try{
          _interruptHandler = handlerClass.newInstance();
      }catch( Exception ee ){
          _log.warn( "Couldn't install interrupt handler ("+
                handlerName+") : "+ee ) ;
          return -2 ;
      }
      _interruptThread = _nucleus.newThread( this ) ;
      _interruptThread.start() ;
      return 0 ;
   }

    public static final String hh_get_hostname = "# returns the hostname of the " +
            "computer this domain is running at";

    public String ac_get_hostname_$_0(Args args) {
        try {
            return InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException ex) {
            return "localhost";
        }
    }

   @Override
   public void run(){
       while( true ){
          try{
             Thread.sleep( _interruptTimer ) ;
             if( _interruptHandler.interruptPending() ) {
                 break;
             }
          }catch( InterruptedException ie ){
             _log.info( "Interrupt loop was interrupted" ) ;
             break ;
          }
       }
       _log.info( "Interrupt loop stopped (shutting down system now)" ) ;
       kill() ;
   }
    private void shutdownSystem()
    {
        List<String> names = _nucleus.getCellNames();
        List<String> nonSystem = new ArrayList<>(names.size());
        List<String> system = new ArrayList<>(names.size());

        for (String name: names) {
            CellInfo info = _nucleus.getCellInfo(name);
            if (info == null) {
                continue;
            }
            String cellName = info.getCellName();
            if (cellName.equals("System")) {
                // Don't kill the system cell
            } else if (info.getCellType().equals("System")) {
                system.add(cellName);
            } else {
                nonSystem.add(cellName);
            }
        }

        _log.info("Will try to shutdown non-system cells {}", nonSystem);
        shutdownCells(nonSystem, 3000);

        _log.info("Will try to shutdown remaining cells {}", system);
        shutdownCells(system, 5000);
    }

    /**
     * Shuts down named cells. The method will block until the cells
     * are dead or until a timeout has occurred.
     *
     * @param cells List of names of cells to kill.
     * @param timeout Time in milliseconds to wait for a cell to die.
     */
    private void shutdownCells(List<String> cells, long timeout)
    {
       for (String cellName: cells) {
           try {
               _nucleus.kill(cellName);
           } catch (IllegalArgumentException e) {
               _log.info("Problem killing : {} -> {}",
                         cellName, e.getMessage());
           }
       }

       for (String cellName: cells) {
           try {
               if (_nucleus.join(cellName, timeout)) {
                   _log.info("Killed {}", cellName);
               } else {
                   _log.warn("Timeout waiting for {}", cellName);
                   _nucleus.listThreadGroupOf(cellName);
                   break;
               }
           } catch (InterruptedException e) {
               _log.warn("Problem killing : {} -> {}",
                         cellName, e.getMessage());
               break;
           }
       }
    }

    @Override
    public void cleanUp()
    {
        shutdownSystem();
        _log.info("Opening shutdown lock");
       _shutdownLock.open();
       System.exit(0);
    }

    @Override
    public void getInfo(PrintWriter pw)
    {
        pw.append(" CellDomainName   = ").println(getCellDomainName());
        pw.format(" I/O rcv=%d;asw=%d;frw=%d;rpy=%d;exc=%d\n",
                  _packetsReceived, _packetsAnswered, _packetsForwarded,
                  _packetsReplied, _exceptionCounter);
        long fm = _runtime.freeMemory();
        long tm = _runtime.totalMemory();

        pw.format(" Memory : tot=%d;free=%d;used=%d\n", tm, fm, tm - fm);
        pw.println(" Cells (Threads)");
        for (String name: _nucleus.getCellNames()) {
            pw.append(" ").append(name).append("(");
            Thread[] threads = _nucleus.getThreads(name);
            if (threads != null) {
                boolean first = true;
                for (Thread thread: threads) {
                    pw.print(thread.getName());
                    if (first) {
                        first = false;
                    } else {
                        pw.print(",");
                    }
                }
            }
            pw.println(")");
        }
    }

   @Override
   public void messageToForward( CellMessage msg ){
        msg.nextDestination() ;
        try{
           sendMessage( msg ) ;
           _packetsForwarded ++ ;
        }catch( Exception eee ){
           _exceptionCounter ++ ;
        }
   }

   @Override
   public void messageArrived( CellMessage msg ){
        _log.info( "Message arrived : "+msg ) ;
        _packetsReceived ++ ;
        if( msg.isReply() ){
            _log.warn("Seems to a bounce : "+msg);
            return ;
        }
        Object obj  = msg.getMessageObject() ;
        Serializable reply = null; // dummy value needed for Java, not used.
        boolean processed = false;

        if(obj instanceof String) {
           String command = (String) obj;
           if(command.length() < 1) {
               return;
           }
           _log.info( "Command : "+command ) ;
           reply = _cellShell.objectCommand2( command ) ;
           processed = true;
        }else if( obj instanceof AuthorizedString ){
           AuthorizedString as = (AuthorizedString)obj ;
           String command = as.toString() ;
           if( command.length() < 1 ) {
               return;
           }
           _log.info( "Command(p="+as.getAuthorizedPrincipal()+") : "+command ) ;
           reply = _cellShell.objectCommand2( command ) ;
           processed = true;
        }else if( obj instanceof CommandRequestable ){
           CommandRequestable request = (CommandRequestable)obj ;
           try{
               _log.info( "Command : "+request.getRequestCommand() ) ;
              reply = _cellShell.command( request ) ;
           }catch( CommandException cee ){
              reply = cee ;
           }
           processed = true;
        }

        if(processed) {
            _log.debug("Reply : {}", reply);
            _packetsAnswered++;
        }

        msg.revertDirection();

        try {
            if (processed && reply instanceof Reply) {
                ((Reply)reply).deliver(this, msg);
            } else {
                if(processed) {
                    msg.setMessageObject(reply);
                }
                sendMessage(msg);
                _log.debug("Sending : {}", msg);
            }
            _packetsReplied++;
        }catch( Exception e ){
            _exceptionCounter ++ ;
        }
   }

    @Override
    public void uncaughtException(Thread t, Throwable e)
    {
        /* In case of fatal errors we shut down. The wrapper script
         * will restart the domain. Notice that there is no guarantee
         * that the fatal error will not reoccur during shutdown and
         * in that case the shutdown may fail.
         *
         * Should a second VirtualMachineError be suffered then an emergency
         * shutdown timer is started, exiting the JVM after 10 seconds.
         * Although this emergency stop is bad, continuing to run in an
         * unpredictable state is worse.
         */
        if (e instanceof VirtualMachineError) {
            if (!_hasShutdownStarted.getAndSet(true)) {
                _oomSafetyBuffer = null;
                _log.error("Fatal JVM error", e);
                _log.error("Trying to shutdown dCache cleanly...");
                kill();
            } else {
                _log.error("Another fatal JVM error", e);

                if (!_isEmergencyShutdownStarted.getAndSet(true)) {
                    _log.error("Now uncertain if the clean shutdown " +
                            "will happen; emergency stop in 10 seconds");
                    // NB. we cannot do t.setDaemon(true) as this throws
                    //     IllegalThreadStateException
                    while (true) {
                        try {
                            Thread.sleep(SECONDS.toMillis(10));
                            _log.error("Emergency stop NOW");
                            System.exit(1);
                        } catch (InterruptedException ie) {
                            _log.error("Shutdown process appears to be " +
                                    "continuing; waiting another 10 seconds");
                        }
                    }
                }
            }
        } else {
            _log.error("Uncaught exception in thread " + t.getName(), e);
        }
    }
}
