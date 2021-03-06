package dmg.util.db ;

public class DbGLockTest {
   private DbGLock _lock;
   private class LockThread implements Runnable {
      private Thread _thread;
      private int    _flags;
      private LockThread( int flags ){
         _flags  = flags ;
         _thread = new Thread( this ) ;
         _thread.start() ;
      }
      @Override
      public void run(){
         try{
           if( _flags == 1 ){
               System.out.println(""+_thread+" : trying to get READ lock" ) ;
               _lock.open( DbGLock.READ_LOCK ) ;
               System.out.println(""+_thread+" : could get READ lock" ) ;
               _thread.sleep(2000) ;
           }else if( _flags == 2 ){
               System.out.println(""+_thread+" : trying to get WRITE lock" ) ;
               _lock.open( DbGLock.WRITE_LOCK ) ;
               System.out.println(""+_thread+" : could get WRITE lock" ) ;
               _thread.sleep(2000) ;
           }else if ( _flags == 3 ){
               System.out.println(""+_thread+" : trying to get READ* lock" ) ;
               _lock.open( DbGLock.READ_LOCK ) ;
               System.out.println(""+_thread+" : could get READ* lock" ) ;
               _thread.sleep(1000) ;
               System.out.println(""+_thread+" : trying to get WRITE* lock" ) ;
               _lock.open( DbGLock.WRITE_LOCK ) ;
               System.out.println(""+_thread+" : could get WRITE* lock" ) ;
               _thread.sleep(1000) ;
               System.out.println(""+_thread+" : releasing lock" ) ;
               _lock.close() ;
           }
            System.out.println(""+_thread+" : releasing lock" ) ;
            _lock.close() ;
            System.out.println(""+_thread+" : Done" ) ;
         }catch( Exception ee ){
            System.out.println(""+_thread+" : "+ee ) ;
         }
      }

   }

   public DbGLockTest()
   {
       _lock = new DbGLock() ;

       for( int i = 0 ; i < 2000  ; i++ ){
          int m = i % 4 ;
          if( m == 0 ){
             new LockThread( 2 ) ;
          }else if( m == 1 ){
             new LockThread( 3 ) ;
          }else{
             new LockThread( 1 ) ;
          }
//          try{ Thread.sleep(1000) ; }
//          catch( Exception e){}
          System.err.println( _lock.toString() ) ;
       }

   }

   public static void main( String [] args )
   {

      new DbGLockTest() ;
   }
}
