package dmg.cells.nucleus ;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandlerFactory;
import java.util.Dictionary;
import java.util.Map;
import java.util.Properties;

import static org.dcache.util.Exceptions.Behaviour.RETURNS_RUNTIMEEXCEPTION;
import static org.dcache.util.Exceptions.unwrapInvocationTargetException;

public class CellUrl {

   private final static Logger _log =
       LoggerFactory.getLogger(CellUrl.class);

   private                CellGlue  _glue;
   private URLStreamHandlerFactory _others;

   public CellUrl( CellGlue glue ){

        Properties p = System.getProperties();
        String s = p.getProperty("java.protocol.handler.pkgs");
        if(s != null)
        {
            s= s+"|dmg.cells.nucleus.protocols";
        }
        else
        {
            s="dmg.cells.nucleus.protocols";
        }

        p.setProperty("java.protocol.handler.pkgs",s);
        System.setProperties(p);
   }

   public static class DomainUrlConnection extends URLConnection
   {
       private String      _protocol;
       private CellNucleus _nucleus;
       private Map<String,Object>  _environment;
       public DomainUrlConnection( URL url , String protocol ){
          super( url ) ;
          _protocol = protocol ;
       }

       @Override
       public void connect(){

           _log.info( "DomainUrlConnection : Connect called" ) ;
       }
       public void setNucleus( CellNucleus nucleus ){
          _nucleus = nucleus ;
       }

       public void setEnvironment(Map<String,Object> environment ){
          _environment = environment ;
       }
       @Override
       public InputStream getInputStream() throws IOException {
          if( _nucleus == null ) {
              throw new IOException("Nucleus not defined");
          }

          throw new IOException( "getInputStream not supported on : "+_protocol ) ;
          /*
          if( _protocol.equals( "context" ) ){

          }else if( _protocol.equals( "env" ) ){

          }else if( _protocol.equals( "cell" ) ){

          }else
             throw new IOException( "Protocol not supported : "+_protocol ) ;
          */
       }
       public Reader getReader() throws IOException {
          if( _nucleus == null ) {
              throw new IOException("Nucleus not defined");
          }

           switch (_protocol) {
           case "context":
               if (url.getHost().equals("")) {
                   String filePart = url.getFile();
                   filePart = (filePart.length() > 0) &&
                           (filePart.charAt(0) == '/') ?
                           filePart.substring(1) :
                           filePart;
                   return
                           _nucleus.getDomainContextReader(filePart);
               } else {
                   return getRemoteContextReader(_nucleus, url);
               }
           case "env":
               if (_environment == null) {
                   throw new IOException("Nucleus not defined");
               }

               String filePart = url.getFile();
               filePart = (filePart.length() > 0) &&
                       (filePart.charAt(0) == '/') ?
                       filePart.substring(1) :
                       filePart;
               return getDictionaryReader(_environment, filePart);
           case "cell":
               return getRemoteCellReader(_nucleus, url);
           default:
               throw new IOException("Protocol not supported : " + _protocol);
           }
       }
       @Override
       public String getContentType(){ return "text/context" ; }
       public String toString(){ return "DomainUrlConnection of : "+_protocol ; }
       //
       // helpers
       //
       private Reader getRemoteCellReader( CellNucleus nucleus , URL url )
               throws IOException  {

          Object o = getRemoteData( nucleus ,
                                    url.getHost() ,
                                    url.getFile().substring(1) ,
                                    4000 ) ;

          if( o instanceof Exception ) {
              throw new IOException(o.toString());
          }

          return new StringReader( o.toString() ) ;
       }
       private Reader getRemoteContextReader( CellNucleus nucleus , URL url )
               throws IOException {

          Object o = getRemoteData( nucleus ,
                                    "System@"+url.getHost() ,
                                    "show context "+url.getFile().substring(1) ,
                                    4000 ) ;

          if( o instanceof Exception ) {
              throw new IOException(o.toString());
          }

          return new StringReader( o.toString() ) ;
       }
       private Object getRemoteData( CellNucleus nucleus ,
                                     String path ,
                                     String command ,
                                     long timeout    )
               throws IOException {

         CellMessage answer;
         try{
            answer = nucleus.sendAndWait(
                           new CellMessage( new CellPath( path ) ,
                                            command ) ,
                           timeout
                                         ) ;
         }catch( Exception e ){
            throw new IOException( "sendAndWait : "+e.toString() ) ;
         }
         if( answer == null ) {
             throw new IOException("Request timed out");
         }

         return answer.getMessageObject() ;

       }
       private Reader getDictionaryReader( Map<String,Object> env , String name )
               throws IOException {
          Object o ;
          if( ( o = env.get( name ) ) == null ) {
              throw new IOException("Not found : " + name);
          }

          return new StringReader( o.toString() ) ;
       }
       private Reader getDictionaryReaderx( Cell cell , String name )
               throws IOException {

          Class<? extends Cell> cellClass = cell.getClass() ;
          _log.info("DomainUrlConnection : Cell Class is : "+cellClass ) ;
          Class<?>[] argsClasses = new Class<?>[0] ;
          try{
             Method method = cellClass.getDeclaredMethod(
                                "getEnvironmentDictionary" ,
                                 argsClasses                  ) ;
             Object [] args = new Object[0] ;

             Dictionary<String,Object> dir = (Dictionary<String,Object>) method.invoke( cell , args ) ;

             Object o = dir.get( name ) ;
             if( o == null ) {
                 throw new IOException("Not found : " + name);
             }

             return new StringReader( o.toString() ) ;

          }catch( Exception e ){
              Exception cause = unwrapInvocationTargetException(e,
                      RETURNS_RUNTIMEEXCEPTION);
              throw new IOException("Problem: " + cause, cause);
          }

       }
   }
   public static void main(String args[]) throws Exception
   {
       new CellUrl(null);
       System.out.println("checking the creation of context url");
       URL url1 = new URL("context://localhost:1111//sfs");
       System.out.println("checking the creation of cell url");
       URL url2 = new URL("cell://localhost:1111//sfs");
       System.out.println("checking the creation of env url");
       URL url3 = new URL("env://localhost:1111//sfs");
       System.out.println("done");
   }
}
