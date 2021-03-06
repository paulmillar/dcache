// $ID$

package diskCacheV111.movers ;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class VspDataOutputStream extends DataOutputStream {

      private static final int IOCMD_WRITE         = 1 ;
      private static final int IOCMD_READ          = 2 ;
      private static final int IOCMD_SEEK          = 3 ;
      private static final int IOCMD_CLOSE         = 4 ;
      private static final int IOCMD_INTERRUPT     = 5 ;
      private static final int IOCMD_ACK           = 6 ;
      private static final int IOCMD_FIN           = 7 ;
      private static final int IOCMD_DATA          = 8 ;
      private static final int IOCMD_LOCATE        = 9 ;
      private static final int IOCMD_STATUS        = 10 ;
      private static final int IOCMD_SEEK_AND_READ  = 11 ;
      private static final int IOCMD_SEEK_AND_WRITE = 11 ;
      private static final int IOCMD_SEEK_SET      = 0 ;
      private static final int IOCMD_SEEK_CURRENT  = 1 ;
      private static final int IOCMD_SEEK_END      = 2 ;
      public VspDataOutputStream( OutputStream out ){
         super( out ) ;
      }
      public void writeCmdData( byte [] data , int offset , int size ) throws IOException {
          writeInt(4);
          writeInt(IOCMD_DATA) ;
          writeInt(size) ;
          write( data , offset , size ) ;
          writeInt(-1);
          flush() ;
      }
      public void writeCmdSeek( long offset , int whence ) throws IOException {
          writeInt(16) ;
          writeInt( IOCMD_SEEK ) ;
          writeLong( offset ) ;
          writeInt(whence) ;
          flush() ;
      }
      public void writeCmdLocate() throws IOException {
          writeInt(4) ;
          writeInt(IOCMD_LOCATE) ;
          flush() ;
      }
      public void writeCmdWrite() throws IOException {
          writeInt(4) ;
          writeInt(IOCMD_WRITE) ;
          flush() ;
      }
      public void writeCmdRead( long size ) throws IOException {
          writeInt(12) ;
          writeInt(IOCMD_READ) ;
          writeLong(size) ;
          flush() ;
      }
      public void writeCmdSeekAndRead( long offset ,
                                       int  whence ,
                                       long size ) throws IOException {
          writeInt(24) ;
          writeInt(IOCMD_SEEK_AND_READ) ;
          writeLong(offset) ;
          writeInt(whence) ;
          writeLong(size) ;
          flush() ;
      }
      public void writeCmdClose() throws IOException {
          writeInt(4) ;
          writeInt(IOCMD_CLOSE) ;
          flush() ;
      }
      public void writeACK(int command) throws IOException {
         writeInt(12) ;
         writeInt(IOCMD_ACK) ;
         writeInt(command) ;
         writeInt(0) ;
         flush() ;
      }
      public void writeACK( int command , int returnCode , String message)
             throws IOException {
         ByteArrayOutputStream baos = new ByteArrayOutputStream() ;
         DataOutputStream dos = new DataOutputStream(baos) ;
         dos.writeUTF(message) ;
         dos.flush() ;
         dos.close() ;
         byte [] msgBytes = baos.toByteArray() ;
         int len = 4 + 4 + 4 + msgBytes.length ;
//         len = ( (len-1) / 8 + 1 ) * 8 ;
         writeInt(len) ;
         writeInt(IOCMD_ACK) ;
         writeInt(command) ;
         writeInt(returnCode) ;
         write(msgBytes,0,msgBytes.length) ;
         flush() ;
      }
      public void writeACK( long location , long size ) throws IOException {
         writeInt(4+4+4+8+8) ;
         writeInt(IOCMD_ACK) ;
         writeInt(IOCMD_LOCATE) ;
         writeInt(0) ;
         writeLong(location) ;
         writeLong(size) ;
         flush() ;
      }
      public void writeACK( long location ) throws IOException {
         writeInt(4+4+4+8) ;
         writeInt(IOCMD_ACK) ;
         writeInt(IOCMD_SEEK) ;
         writeInt(0) ;
         writeLong(location) ;
         flush() ;
      }
      public void writeFIN(int command) throws IOException {
         writeInt(12) ;
         writeInt(IOCMD_FIN) ;
         writeInt(command) ;
         writeInt(0) ;
         flush() ;
      }
      public void writeFIN( int command , int returnCode , String message)
             throws IOException {
         ByteArrayOutputStream baos = new ByteArrayOutputStream() ;
         DataOutputStream dos = new DataOutputStream(baos) ;
         dos.writeUTF(message) ;
         dos.flush() ;
         dos.close() ;
         byte [] msgBytes = baos.toByteArray() ;
         int len = 4 + 4 + 4 + msgBytes.length ;
//         len = ( (len-1) / 8 + 1 ) * 8 ;
         writeInt(len) ;
         writeInt(IOCMD_FIN) ;
         writeInt(command) ;
         writeInt(returnCode) ;
         write(msgBytes,0,msgBytes.length) ;
         flush() ;
      }
      public void writeDATA_HEADER() throws IOException {
         writeInt(4) ;
         writeInt(IOCMD_DATA) ;
         flush() ;
      }
      public void writeDATA_TRAILER() throws IOException {
         writeInt(-1) ;
         flush() ;
      }
      public void writeDATA_BLOCK( byte [] data , int offset , int size )
             throws IOException{

           writeInt( size ) ;
           write( data , offset , size ) ;
           flush() ;
      }
   }

