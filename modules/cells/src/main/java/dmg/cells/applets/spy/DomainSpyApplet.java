package dmg.cells.applets.spy ;

import java.applet.Applet;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Label;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


public class      DomainSpyApplet
       extends    Applet
       implements ActionListener,
                  Runnable           {


  private static final long serialVersionUID = -2434295270542744083L;
  private DomainConnection _connection;
  private String _host;
  private int    _port;
  private Thread _listen;

  public DomainSpyApplet( String host , int port ){
      _host = host ;
      _port = port ;

  }
  @Override
  public void init(){

     if( _host == null ){
         String dest = getParameter( "Galactica" ) ;
         if( dest == null ){
            System.err.println( "Galactica has to be set to host and port" ) ;
            System.exit(4);
         }
         try{
            int ind = dest.indexOf( ":" ) ;
            if( ind < 0 ){
               _host = dest ;
               _port = 22221 ;
            }else{
               _host = dest.substring( 0 , ind ) ;
               _port = Integer.parseInt( dest.substring( ind+1 ) ) ;
            }
         }catch( Exception e ){
            System.err.println( "Configuration failed : "+e.toString() ) ;
            System.exit(4);
         }
      }
      try{

         _connection = new DomainConnection( _host , _port ) ;
         System.out.println( "Connected ... " ) ;

      }catch( Exception e ){
         System.err.println( "Connection failed : "+e.toString() ) ;
         System.exit(4);
      }

      setLayout( new BorderLayout( ) ) ;
      Label label = new Label( "Domain Spy" , Label.CENTER ) ;
      label.setFont( new Font( "fixed" , Font.ITALIC | Font.BOLD , 24 ) ) ;

      add( label , "North" ) ;
      Panel panel = new DomainListPanel( _connection ) ;

      add( panel , "Center" ) ;


  }
  @Override
  public void actionPerformed( ActionEvent event ){
  }

  @Override
  public void start(){
//      System.out.println("Starting ... " ) ;
      setVisible( true ) ;
  }
  @Override
  public void run(){
  }
  @Override
  public void stop(){
//     System.out.println( "Applet stopping"  ) ;
  }
  @Override
  public void destroy(){
//     System.out.println( "Applet destroying"  ) ;
  }


}
