package  dmg.cells.services ;

import dmg.cells.network.GNLCell;
import dmg.cells.network.RetryTunnel;
import dmg.cells.nucleus.SystemCell;

//           new GNLCell( "telnet" , "dmg.cells.services.TelnetShell "+args[1] ) ;
//           new ClientBootstrap( "init" , args[1] + " "+args[2] ) ;

/**
  *
  *
  * @author Patrick Fuhrmann
  * @version 0.1, 15 Feb 1998
  */
public class SmallestDomain {

  public static void main( String [] args ) {

     if( args.length < 2 ){
        System.out.println( "USAGE : <domainName> <telnetListenPort> [<tunnelListenPort>]" ) ;
        System.out.println( "USAGE : <domainName> <regyHost> <regyPort>" ) ;
        System.exit(1);
      }
      try{
         new  SystemCell( args[0] ).start();

         if( args.length < 3 ){
             new TelnetLoginManager( "tlm" , args[1] ).start();
         }else{
             try{
                new Integer( args[2] ) ;
                new TelnetLoginManager( "tlm" , args[1] ).start();
                new GNLCell( "t0" , "dmg.cells.network.RetryTunnel "+args[2] ).start();
             }catch( Exception ee ){
                new RetryTunnel( "t1" , args[1]+" "+args[2] ).start();
             }
         }
      }catch( Exception e ){
          e.printStackTrace() ;
          System.exit(4);
      }

  }

}

