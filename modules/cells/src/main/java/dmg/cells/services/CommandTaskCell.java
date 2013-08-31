package dmg.cells.services ;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import dmg.cells.nucleus.CellAdapter;
import dmg.cells.nucleus.CellMessage;
import dmg.cells.nucleus.CellMessageAnswerable;
import dmg.cells.nucleus.CellNucleus;
import dmg.cells.nucleus.CellPath;
import dmg.util.Args;
import dmg.util.CommandException;
import dmg.util.CommandInterpreter;
import dmg.util.CommandSyntaxException;

/**
 *
 * @author Patrick Fuhrmann patrick.fuhrmann@desy.de
 * @version 0.9, Aug 7, 2006
 *
 */

public class CommandTaskCell extends CellAdapter {

   private final static Logger _log =
       LoggerFactory.getLogger(CommandTaskCell.class);

   private String        _cellName;
   private Args          _args;
   private CellNucleus   _nucleus;
   private ClientHandler _clientHandler = new ClientHandler() ;
   private HashMap<String, CellCommandTaskCore> _cores   = new HashMap<>() ;
   private Map<String,ModuleInfo> _modules = new HashMap<>();

   public class CellCommandTaskCore  extends CommandInterpreter {
      private CellAdapter    _cell;
      private String         _name;
      private Args           _classArgs;
      private Args           _taskArgs;
      private ModuleInfo    _moduleInfo;
      private CellCommandTaskable _task;

      private CellCommandTaskCore( String name , ModuleInfo moduleInfo , Args args ){
         _name       = name ;
         _moduleInfo = moduleInfo ;
         _classArgs  = moduleInfo._args ;
         _taskArgs   = args ;
      }
      public void setCellCommandTaskable( CellCommandTaskable task ){
         addCommandListener(task);
         _task = task ;
      }
      public Args getModuleArgs(){ return _classArgs ; }
      public Args getTaskArgs(){ return _taskArgs ; }
      public CellCommandTaskable getTaskable(){ return _task ; }
      public String getName(){ return _name ; }

      public CellAdapter getParentCell(){ return CommandTaskCell.this ; }
      public void sendMessage( CellMessage message )
      {
          CommandTaskCell.this.sendMessage( message , true , true , _task , 999999999L) ;
      }
      public void sendMessage( CellMessage message , long timeout )
      {
          CommandTaskCell.this.sendMessage( message , true , true , _task , timeout) ;
      }
      public String getModuleName(){
         return _moduleInfo.getName() ;
      }
   }
   public interface CellCommandTaskable extends CellMessageAnswerable {
       public void timer() ;
       public void getInfo( PrintWriter pw ) ;
   }
   private class ModuleInfo {
      private Constructor<?> _constructor;
      private Args        _args;
      private String      _name;
      private ModuleInfo( String name , Constructor<?> constructor , Args args ){
         _constructor = constructor ;
         _args = args ;
         _name = name ;
      }
      public String getName(){return _name ; }
      public String toString(){
         return _name+" "+_constructor.getName();
      }
   }
   private class ClientInfo {

      private long   _time      = System.currentTimeMillis() ;
      private String _clientKey;
      private CellCommandTaskCore _session;

      private ClientInfo( String key ){
          _clientKey = key ;
      }
      public boolean isAttached(){
         return _session != null ;
      }
      public CellCommandTaskCore getCore(){
         return _session ;
      }
      public void setCore( CellCommandTaskCore core ){
         _session = core ;
      }
      public String toString(){
         return _clientKey+" = "+( _session == null ? "not attached" : _session.getName() ) ;
      }
      public String getClientKey(){
         return _clientKey ;
      }
      public void touch(){
         _time = System.currentTimeMillis() ;
      }
      public CellCommandTaskCore detach(){
         CellCommandTaskCore session = _session ;
         _session = null ;
         return session ;
      }
   }
   private class ClientHandler {

      private HashMap<String, ClientInfo> _clientHash      = new HashMap<>() ;
      private long    _maxSessionLogin = 10L * 60L * 1000L ;

      public Collection<ClientInfo> clients(){
         return _clientHash.values() ;
      }
      public ClientInfo getThisClient(){
         String     key  = getClientKey() ;
         ClientInfo info = _clientHash.get(key);
         if( info == null ) {
             _clientHash.put(key, info = new ClientInfo(key));
         }
         info.touch();
         return info ;
      }
      public CellCommandTaskCore detach(){
         return getThisClient().detach();
      }
      public CellCommandTaskCore detach( String clientKey ){
         ClientInfo info = _clientHash.get(clientKey);
         if( info == null ) {
             return null;
         }
         return info.detach();
      }
      public void setLogoutTimer( long interval ){
         _maxSessionLogin = interval ;
      }
      public long getLogoutTimer(){ return _maxSessionLogin ; }
      public void cleanUp(){
          long now = System.currentTimeMillis() ;
          for (Object client : new ArrayList<>( clients() )) {
              ClientInfo info = (ClientInfo) client;
              if ((now - info._time) > _maxSessionLogin) {
                  String key = info.getClientKey();
                  _log.info("Timer : " + key + " idle time exceeded");
                  _clientHash.remove(key);
              }
          }
      }
   }
   private class Scheduler implements Runnable {
       private long   _sleepInterval = 60L * 1000L ;
       private Thread _worker;
       private Scheduler()
       {
          _worker = _nucleus.newThread(this,"Scheduler");
       }

       private void start()
       {
           _worker.start();
       }

       @Override
       public void run(){
          _log.info("Scheduler worker started");
          while( ! Thread.interrupted() ){
             try{
                 Thread.sleep(_sleepInterval);
             }catch(InterruptedException ee ){
                 _log.info("Worker Thread interrupted");
                 break ;
             }
             try{
                doTiming();
             }catch(Throwable t ){
                 _log.warn("Problem in 'doTiming' : "+t, t);
             }
          }
       }
   }
   public CommandTaskCell( String cellName , String args ) throws Exception {
      super(cellName, args);
      _cellName = cellName ;
      _args     = getArgs() ;
      _nucleus  = getNucleus() ;
      useInterpreter( true ) ;
   }

   @Override
   public void start() throws Exception
   {
        super.start();
        new Scheduler().start();
   }

   private void doTiming(){
      _nucleus.updateWaitQueue();
       List<CellCommandTaskCore> cores = new ArrayList<>(_cores.values());
       for (CellCommandTaskCore core : cores) {
           try {
               core._task.timer();
           } catch (Throwable t) {
               _log.warn("Throwable in task : " + core
                       .getName() + " : " + t, t);
           }
       }
      _clientHandler.cleanUp();

   }
   private String getClientKey(){
      CellMessage commandMessage = getThisMessage() ;
      CellPath source = commandMessage.getSourcePath() ;
      return ""+source.getCellName()+"@"+source.getCellDomainName() ;
   }
   //
   public static final String hh_set_logout_time = "<timeInSeconds>";
   public String ac_set_logout_time_$_1( Args args ){
       long interval = Long.parseLong(args.argv(0)) * 1000L ;
       if( interval <= 0 ) {
           throw new
                   IllegalArgumentException("Time must be > 0");
       }

       _clientHandler.setLogoutTimer(interval);
       return "" ;
   }
   public static final String hh_ls_task = "[-l]" ;
   public String ac_ls_task( Args args ){
      ClientInfo client =_clientHandler.getThisClient() ;
      boolean  extended = args.hasOption("l") ;
      StringBuilder sb = new StringBuilder() ;
       for (Object value : _cores.values()) {
           CellCommandTaskCore core = (CellCommandTaskCore) value;
           sb.append(core.getName());
           if (extended) {
               sb.append(" ").
                       append(core._moduleInfo.getName()).
                       append(" {").append(core._task.toString()).append("}");
           }
           sb.append("\n");
       }
      return sb.toString() ;
   }
   public static final String hh_ls_module = "" ;
   public String ac_ls_module( Args args ){
      ClientInfo client = _clientHandler.getThisClient() ;

      StringBuilder sb = new StringBuilder() ;
       for (Map.Entry<String,ModuleInfo> entry : _modules.entrySet()) {
           sb.append(entry.getKey()).
                   append(" -> ").
                   append(entry.getValue()._constructor.getName()).
                   append("\n");
       }
      return sb.toString() ;
   }
   public static final String hh_ls_client = "" ;
   public String ac_ls_client( Args args ){

      ClientInfo client =_clientHandler.getThisClient() ;
      StringBuilder sb = new StringBuilder() ;
      String ourClientKey = client.getClientKey() ;
       for (Object o : _clientHandler.clients()) {
           ClientInfo info = (ClientInfo) o;
           String clientKey = info.getClientKey();
           sb.append(info.getClientKey()).
                   append(" ")
                   .append(ourClientKey.equals(clientKey) ? "*" : " ").
                   append(" [").
                   append((System.currentTimeMillis() - info._time) / 1000L)
                   .append("] -> ");
           if (!info.isAttached()) {
               sb.append("not attached\n");
           } else {
               sb.append(info.getCore().getName()).append("\n");
           }
       }
      return sb.toString() ;

   }
   public static final String hh_create_task = "<taskName> <moduleName>";
   public String ac_create_task_$_2( Args args ) throws Throwable {


      String taskName   = args.argv(0) ;
      String moduleName = args.argv(1) ;

      try{

          ClientInfo client = _clientHandler.getThisClient() ;
          if( client.isAttached() ) {
              throw new
                      IllegalArgumentException("Already attached to " + client
                      .getCore().getName());
          }

           CellCommandTaskCore core = _cores.get(taskName);
           if( core != null ) {
               throw new
                       IllegalArgumentException("Task already exists : " + taskName);
           }

           ModuleInfo moduleInfo = _modules.get( moduleName ) ;
           if( moduleInfo == null ) {
               throw new
                       NoSuchElementException("Module not found : " + moduleName);
           }

           core = new CellCommandTaskCore( taskName , moduleInfo , args ) ;

           Constructor<?> cons = moduleInfo._constructor ;

           Object obj = cons.newInstance(core) ;
           if( ! ( obj instanceof CellCommandTaskable ) ) {
               throw new
                       Exception("PANIC : module doesn't interface CellCommandTaskable");
           }

           core.setCellCommandTaskable( (CellCommandTaskable) obj );
           //
           // add instance
           //
           _cores.put( taskName , core ) ;
           //
           // attach
           //
           client.setCore( core ) ;

           return "Task <"+taskName+"> created and attached to (us) ["+client.getClientKey()+"]" ;

       }catch(InvocationTargetException ite ){
           Throwable cause = ite.getCause() ;
           _log.warn("Problem creating "+moduleName+" InvocationTargetException cause : "+cause, cause);
           if( cause != null ){
              throw cause ;
           }else{
              throw ite ;
           }

       }catch(Exception ee ){
           _log.warn("Problem creating "+moduleName+" "+ee, ee);
           throw ee ;
       }
   }
   public static final String hh_attach = "<sessionName>" ;
   public String ac_attach_$_1( Args args ){

      ClientInfo client = _clientHandler.getThisClient() ;
      if( client.isAttached() ) {
          throw new
                  IllegalArgumentException("Already attached to " + client
                  .getCore().getName());
      }

      String taskName = args.argv(0);
      CellCommandTaskCore core = _cores.get(taskName);
      if( core == null ) {
          throw new
                  NoSuchElementException("Task not found : " + taskName);
      }

      client.setCore(core);

      return  "Task <"+taskName+"> attached to (us) ["+client.getClientKey()+"]" ;
   }
   public static final String hh_detach = "[<clientKey>]" ;
   public String ac_detach_$_0_1( Args args ){

      if( args.argc() == 0 ){
         CellCommandTaskCore core = _clientHandler.detach() ;
         if( core == null ) {
             return "Wasn't attached";
         }
         return "Detached from : "+core.getName() ;
      }else{
         String clientKey = args.argv(0);
         CellCommandTaskCore core = _clientHandler.detach( clientKey ) ;
         if( core == null ) {
             return "Wasn't attached";
         }
         return "Detached from : "+core.getName() ;
      }


   }
   public static final String hh_do = "<module specific commands>" ;
   public Object ac_do_$_1_999( Args args ) throws Exception {
      return executeLocalCommand( args ) ;
   }
   public static final String hh_task = "<module specific commands>" ;
   public Serializable ac_task_$_1_999( Args args ) throws Exception {
      return executeLocalCommand( args ) ;
   }
   @Override
   public Serializable command( Args args )throws CommandException {
      Args copyArgs = new Args(args);
      try{
          return super.command(args) ;
      }catch(CommandSyntaxException ee ){
          //_log.warn("!!1 first shot failed : "+ee);
          return executeLocalCommand( copyArgs ) ;
      }
   }
   private Serializable executeLocalCommand( Args args ) throws CommandException {

      ClientInfo client = _clientHandler.getThisClient() ;
      if( ! client.isAttached() ) {
          throw new
                  IllegalArgumentException("Not attached");
      }

      CellCommandTaskCore core = client.getCore() ;

      Serializable obj = core.command( args ) ;
      if( obj == null ) {
          throw new
                  CommandException("Command returned null");
      }

      return obj ;

   }
   @Override
   public void getInfo( PrintWriter pw ){

      ClientInfo client = _clientHandler.getThisClient() ;

      pw.println("      Logout Time : "+(_clientHandler.getLogoutTimer()/1000L) + " seconds") ;
      pw.println("  Number of Tasks : "+_cores.size() ) ;
      pw.println("Number of Clients : "+_clientHandler.clients().size() ) ;
      pw.println("    Our Client Id : "+_clientHandler.getThisClient().getClientKey());
      CellCommandTaskCore core = client.getCore() ;
      pw.println("         Attached : "+( core == null ? "false" : core.getName() ) );
      if( core != null ){
         core._task.getInfo(pw);
      }
   }
   private Class<?>[] _classSignature = {
       CommandTaskCell.CellCommandTaskCore.class
   } ;
   public static final String hh_define_module = "<moduleName> <moduleClass>" ;
   public String ac_define_module_$_2( Args args )throws Exception {

       String moduleName = args.argv(0);
       String moduleClass = args.argv(1) ;

       Class<?>       mc  = Class.forName( moduleClass ) ;
       Constructor<?> mcc = mc.getConstructor( _classSignature ) ;

       _modules.put( moduleName , new ModuleInfo( moduleName , mcc , args ) ) ;

       return "" ;
   }
   public static final String hh_undefine_module = "<moduleName>" ;
   public String ac_undefine_module_$_1( Args args ){
       _modules.remove(args.argv(0));
       return "";
   }
   @Override
   public void messageArrived( CellMessage msg ){

   }
   /**
     * EXAMPLE
     */
   static public class ModuleExample implements CommandTaskCell.CellCommandTaskable {

      private CellAdapter _cell;
      private CommandTaskCell.CellCommandTaskCore _core;

      public ModuleExample( CommandTaskCell.CellCommandTaskCore core ){
          _cell = core.getParentCell() ;
          _core = core ;
          _log.info("Started : "+core.getName());
      }
      public static final String hh_send = "<destination> <message>" ;
      public String ac_send_$_2( Args args )
      {
          CellMessage msg = new CellMessage( new CellPath( args.argv(0) ) , args.argv(1) ) ;
          _core.sendMessage(msg);
          return "" ;
      }
      public static final String hh_test = "<whatever>" ;
      public String ac_test_$_0_99( Args args ){
          StringBuilder sb = new StringBuilder() ;
          sb.append(" Module Args : ").append( _core.getModuleArgs().toString() ).append("\n");
          sb.append("   Task Args : ").append( _core.getTaskArgs().toString() ).append("\n");
          sb.append("Command Args : ").append( args.toString()).append("\n");
          return sb.toString() ;
      }
      @Override
      public void answerArrived( CellMessage request , CellMessage answer ){
         _log.info("Answer arrived for task : "+_core.getName()+" : "+answer.getMessageObject().toString());
      }
      @Override
      public void exceptionArrived( CellMessage request , Exception   exception ){

      }
      @Override
      public void answerTimedOut( CellMessage request ){

      }
      @Override
      public void getInfo( PrintWriter pw ){
          pw.println(" Module Args : "+ _core.getModuleArgs() ) ;
          pw.println("   Task Args : "+ _core.getTaskArgs() ) ;
      }
      @Override
      public void timer(){
          //_log.info("Timer of "+_core.getName()+" triggered");
      }
      public String toString(){
         return "I'm "+_core.getName() ;
      }
   }


}
