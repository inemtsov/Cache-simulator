/*
  Ilya Nemtsov

  Instructions how to compile and run
    1. to compile the sys1:
      a. navigate in terminal to the directory where sys1.java file is located
      b. type in terminal: javac sys1.java
      c. the previous command (b) will create sys1.class file

    2. to run the sys1:
      a. type in terminal: java sys1 <path to trace file: gcc.xac or sjeng.xac> [size of the cache] [-v ic1 ic2]
        i. [size of the cache] is a positive integer that is a power of 2 bytes
        j. [-v ic1 ic2] is optional arguments to enable a verbose mode, ic1 and ic2  - are positive integers
                that represent starting and ending points of output

     example:
      javac sys1.java
      java sys1 ~whsu/csc656/Traces/S18/P1/gcc.xac 4 -v 0 100
 */

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class sys1 {

  private boolean isVerboseModeOn;
  private BufferedReader source;
  private String line;
  private int cacheSize;
  private int ic1, ic2;
  private int blockSize = 16;
  private int missPenaltyCycles = 80;
  private String[][] directMappedCache;
  private int validBitIndex = 0;
  private int dirtyBitIndex = 1;
  private int tagIndex = 2;
  private int order = 0;
  private String caseNumber = "";
  private String hitOrMiss = "";

  private int numOfLoads, numOfStores, numOfDataReadMisses, numOfDataWriteMisses, numOfDataMisses,
          numOfDirtyDataReadMisses, numOfDirtyDataWriteMisses, numOfBytesReadFromMem, numOfBytesWrittenToMem,
          totalAccessTimeForReads, totalAccessTimeForWrites, numOfDataAccesses;

  //constructor that initialize the system one
  public sys1( String fileName, String cacheSize, boolean isVerboseModeOn, String ic1, String ic2 ) {
    this.isVerboseModeOn = isVerboseModeOn;
    this.cacheSize = Integer.parseInt( cacheSize );
    this.ic1 = Integer.parseInt( ic1 );
    this.ic2 = Integer.parseInt( ic2 );

    try {

      initCacheSystem( this.cacheSize );

      //open trace file
      source = new BufferedReader( new FileReader( fileName ) );

      load();

      //print the measurements
      printOutput();
    }catch( IOException e ) {
      source = null;
    }
  }

  //initialize cache system and assign all valid bits and dirty bits to 0
  private void initCacheSystem( int cacheSize ) {
    int numOfBlocks = (cacheSize * 1024) / blockSize;

    directMappedCache = new String[numOfBlocks][3];
    for( int i = 0; i < directMappedCache.length; i++ ) {
      directMappedCache[i][validBitIndex] = "0";
      directMappedCache[i][dirtyBitIndex] = "0";
    }
  }

  //parse the line and extracts needed information for measurements
  private void parseLine( String currentLine ) {

    //splits the line based on spaces
    String[] tokens = currentLine.split( "\\s+" );
    //process only loads and stores
    if( tokens[7].equals( "L" ) || tokens[7].equals( "S" ) ) {
      numOfDataAccesses++;
      String currDataAddress = tokens[9];
      int indexOfAddress = calculateIndex( tokens[9] );
      String tagOfAddress = calculateTag( tokens[9] );
      String curCacheTag = directMappedCache[indexOfAddress][tagIndex];
      String curValidBit = directMappedCache[indexOfAddress][validBitIndex];
      String curDirtyBitIndex = directMappedCache[indexOfAddress][dirtyBitIndex];

      //find the case number
      caseNumber = getCaseNumber( indexOfAddress, tagOfAddress );

      //each access falls under one of these cases
      if( tokens[7].equals( "L" ) ) {
        numOfLoads++; //reads

        //cache hit
        if( caseNumber.equals( "1" ) ) {
          hitOrMiss = "1";
          totalAccessTimeForReads++;
        }
        //clean cache miss: block is empty or contains block that is clean
        else if( caseNumber.equals( "2a" ) ) {
          hitOrMiss = "0";
          numOfDataReadMisses++;
          numOfDataMisses++;
          totalAccessTimeForReads += (1 + missPenaltyCycles);
          numOfBytesReadFromMem += blockSize;
          setNewValues( indexOfAddress, "0", "1", tagOfAddress );
        }
        //dirty cache miss
        else if( caseNumber.equals( "2b" ) ) {
          hitOrMiss = "0";
          numOfDataReadMisses++;
          numOfDataMisses++;
          numOfDirtyDataReadMisses++;
          numOfBytesReadFromMem += blockSize;
          numOfBytesWrittenToMem += blockSize;
          totalAccessTimeForReads += (1 + 2 * missPenaltyCycles);
          setNewValues( indexOfAddress, "0", "1", tagOfAddress );
        }
      }else {
        numOfStores++; //writes
        //cache hit
        if( caseNumber.equals( "1" ) ) {
          hitOrMiss = "1";
          totalAccessTimeForWrites++;
          directMappedCache[indexOfAddress][dirtyBitIndex] = "1";
        }
        //clean cache miss: block is empty or contains block that is clean
        else if( caseNumber.equals( "2a" ) ) {
          hitOrMiss = "0";
          numOfDataWriteMisses++;
          numOfDataMisses++;
          numOfBytesReadFromMem += blockSize;
          totalAccessTimeForWrites += (1 + missPenaltyCycles);
          setNewValues( indexOfAddress, "1", "1", tagOfAddress );
        }
        //dirty cache miss
        else if( caseNumber.equals( "2b" ) ) {
          hitOrMiss = "0";
          numOfDataWriteMisses++;
          numOfDirtyDataWriteMisses++;
          numOfDataMisses++;
          numOfBytesWrittenToMem += blockSize;
          numOfBytesReadFromMem += blockSize;
          totalAccessTimeForWrites += (1 + 2 * missPenaltyCycles);
          setNewValues( indexOfAddress, "1", "1", tagOfAddress );
        }
      }
      //if verbose is turned on it prints verbose outputs from/to in sequential order(range starts from 0 to # of blocks -1)
      if( isVerboseModeOn && (ic1 <= order && ic2 >= order) )

      {
        System.out.printf( "%-10s %-15s %-5s %-10s %-5s %-10s %-5s %-5s %-5s", order, currDataAddress, intToHex( indexOfAddress ),
                binToHex( tagOfAddress ), curValidBit, binToHex( (curCacheTag == null ? "0" : curCacheTag) ),
                curDirtyBitIndex, hitOrMiss, caseNumber );
        System.out.println();
      }

      order++;
    }

  }

  //sets new values to particular block
  private void setNewValues( int index, String dBit, String vBit, String newTag ) {
    directMappedCache[index][dirtyBitIndex] = dBit;
    directMappedCache[index][validBitIndex] = vBit;
    directMappedCache[index][tagIndex] = newTag;
  }

  //converts from binary to hexadecimal
  private String binToHex( String num ) {
    return Long.toString( Long.parseLong( num, 2 ), 16 );
  }

  //converts from integer to Hexadecimal
  private String intToHex( int num ) {
    return Long.toString( num, 16 );
  }

  //helps to find the case number based on index and tag
  private String getCaseNumber( int index, String tag ) {
    String cn = "";
    if( directMappedCache[index][validBitIndex].equals( "1" ) && directMappedCache[index][tagIndex].equals( tag ) ) {
      cn = "1";
    }else if( directMappedCache[index][validBitIndex].equals( "0" ) ||
            (!directMappedCache[index][tagIndex].equals( tag ) && (directMappedCache[index][dirtyBitIndex].equals( "0" ))) ) {
      cn = "2a";
    }else if( !directMappedCache[index][tagIndex].equals( tag ) && directMappedCache[index][dirtyBitIndex].equals( "1" ) ) {
      cn = "2b";
    }
    return cn;
  }

  //calculated the index from the address
  private int calculateIndex( String address ) {

    String binNumber = getAddressInBinary( address );
    int offsetNumOfBits = getLogNum( blockSize );
    int indexNumOfBits = getLogNum( (cacheSize * 1024) / blockSize );
    return Integer.parseInt( (binNumber.substring( (binNumber.length() - indexNumOfBits - offsetNumOfBits), (binNumber.length() - offsetNumOfBits) )), 2 );
  }

  //calculates the tag from the address
  private String calculateTag( String address ) {

    String binNumber = getAddressInBinary( address );
    int offsetNumOfBits = getLogNum( blockSize );
    int indexNumOfBits = getLogNum( cacheSize * 1024 / blockSize );
    return binNumber.substring( 0, (binNumber.length() - indexNumOfBits - offsetNumOfBits) );
  }

  //converts address into binary
  private String getAddressInBinary( String address ) {
    String binNumber = Long.toBinaryString( Long.parseLong( address, 16 ) );
    for( int i = binNumber.length(); i < 64; i++ ) {
      binNumber = "0" + binNumber;
    }
    return binNumber;
  }

  //calculate the log based of a given number
  private int getLogNum( int num ) {
    return ( int ) (Math.log( num ) / Math.log( 2 ));
  }

  private void load() {
    while( hasMoreLines() ) {
      parseLine( getLine() );
    }
  }

  //checks if there are more lines to parse
  private boolean hasMoreLines() {
    line = nextLine();
    return line == null ? false : true;
  }

  //reads the next line
  private String nextLine() {
    try {
      return source.readLine();
    }catch( Exception e ) {
      return null;
    }
  }

  //return the current line
  private String getLine() {
    return line;
  }

  //print the measurements
  private void printOutput() {
    System.out.println( "direct-mapped, writeback, size = " + cacheSize + "KB" );
    System.out.println( "Number of data reads........................: " + numOfLoads );
    System.out.println( "Number of data writes.......................: " + numOfStores );
    System.out.println( "Number of data accesses.....................: " + numOfDataAccesses );
    System.out.println( "Number of total data read misses............: " + numOfDataReadMisses );
    System.out.println( "Number of total data write misses...........: " + numOfDataWriteMisses );
    System.out.println( "Number of data misses.......................: " + numOfDataMisses );
    System.out.println( "Number of dirty data read misses............: " + numOfDirtyDataReadMisses );
    System.out.println( "Number of dirty write misses................: " + numOfDirtyDataWriteMisses );
    System.out.println( "Number of bytes read from memory............: " + numOfBytesReadFromMem );
    System.out.println( "Number of bytes written to memory...........: " + numOfBytesWrittenToMem );
    System.out.println( "The total access time in cycles for reads...: " + totalAccessTimeForReads );
    System.out.println( "The total access time in cycles for writes..: " + totalAccessTimeForWrites );
    System.out.println( "The overall data cache miss rate............: " + ( float ) (numOfDataReadMisses + numOfDataWriteMisses) / numOfDataAccesses );
  }

  //the main method
  public static void main( String args[] ) {
    int lengthOfArg = args.length;
    boolean isVerbose = false;

    //check the length of arguments and their correctness
    if( lengthOfArg > 2 ) {
      if( !checkCacheSize( args[1] ) ) {
        System.out.println( "Cache size is not correct. It should be positive and a power of two." );
      }else if( lengthOfArg != 5 || !args[2].equals( "-v" ) || !checkIfPositiveDigit( args[3] ) || !checkIfPositiveDigit( args[4] ) ) {
        System.out.println( "The argument(s) for verbose mode is not correct or too many or less arguments." );
      }else {
        isVerbose = true;
        new sys1( args[0], args[1], isVerbose, args[3], args[4] );
      }
    }else if( lengthOfArg <= 1 ) {
      System.out.println( "Please provide arguments(1:trace file 2:cache size 3(Optional verbose mode):\"-v ic1 ic2\"" );
    }else {
      if( !checkIfPositiveDigit( args[1] ) || !checkCacheSize( args[1] ) ) {
        System.out.println( "Cache size is not correct. It should be positive and a power of two." );
      }else {
        new sys1( args[0], args[1], isVerbose, "0", "0" );
      }
    }
  }

  //check if number is a positive digit
  private static boolean checkIfPositiveDigit( String num ) {
    try {
      return Character.isDigit( num.charAt( 0 ) ) && Integer.parseInt( num ) >= 0;
    }catch( NumberFormatException ex ) {
      return false;
    }
  }

  //check if cache size is a power of two
  private static boolean checkCacheSize( String num ) {
    int number = Integer.parseInt( num );
    return ((number > 0) && (number & (number - 1)) == 0);
  }
}
