/*
  Ilya Nemtsov
  CSC 656 Project 2 Sys2
  Professor William Hsu
  04/26/2018

  Instructions how to compile and run
    1. to compile the sys2:
      a. navigate in terminal to the directory where sys2.java file is located
      b. type in terminal: javac sys2.java
      c. the previous command (b) will create sys2.class file

    2. to run the sys2:
      a. type in terminal: java sys2 <path to trace file: gcc.xac or sjeng.xac> [size of the cache] [set-associativity] [-v ic1 ic2]
        i. [size of the cache] is a positive integer that is a power of 2 bytes
        j. [set-associativity] is a positive integer that is a power of 2 bytes
        k. [-v ic1 ic2] is optional argument to enable a verbose mode, ic1 and ic2  - are positive integers
                that represent starting and ending points of output

     example:
      javac sys2.java
      java sys2 ~whsu/csc656/Traces/S18/P1/gcc.xac 4 2 -v 0 100
 */

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class sys2 {

  private boolean isVerboseModeOn;
  private BufferedReader source;
  private String line;
  private int cacheSize;
  private int setAssociativity;
  private int ic1, ic2;
  private int blockSize = 16;
  private int missPenaltyCycles = 80;
  private String[][][] kWayCache;
  private int validBitIndex = 1;
  private int dirtyBitIndex = 2;
  private int tagIndex = 3;
  private int lastUsed = 4;
  private int order = 0;
  private String caseNumber = "";
  private String hitOrMiss = "";

  private int numOfLoads, numOfStores, numOfDataReadMisses, numOfDataWriteMisses, numOfDataMisses,
          numOfDirtyDataReadMisses, numOfDirtyDataWriteMisses, numOfBytesReadFromMem, numOfBytesWrittenToMem,
          totalAccessTimeForReads, totalAccessTimeForWrites, numOfDataAccesses;

  //constructor that initialize the system two
  public sys2( String fileName, String cacheSize, String setAssociativity, boolean isVerboseModeOn, String ic1, String ic2 ) {

    this.isVerboseModeOn = isVerboseModeOn;
    this.cacheSize = Integer.parseInt( cacheSize );
    this.setAssociativity = Integer.parseInt( setAssociativity );
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
    int numOfBlocks = (cacheSize * 1024) / (blockSize * setAssociativity);

    kWayCache = new String[numOfBlocks][setAssociativity][5];
    for( int i = 0; i < kWayCache.length; i++ ) {
      for( int j = 0; j < setAssociativity; j++ ) {
        kWayCache[i][j][validBitIndex] = "0";
        kWayCache[i][j][dirtyBitIndex] = "0";
      }
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
      int indexOfAddress = calculateIndex( currDataAddress );
      String tagOfAddress = calculateTag( currDataAddress );
      int blockID = -1;

      //find the case number
      caseNumber = getCaseNumber( indexOfAddress, tagOfAddress );

      //find the block id
      if( caseNumber.equals( "1" ) ) {
        blockID = findBlockId( indexOfAddress, tagOfAddress );
      }
      if( caseNumber.equals( "2a" ) ) {
        blockID = (containsEmptyBlock( indexOfAddress ) ? findEmptyBlockId( indexOfAddress ) : findOldestIdUsed( indexOfAddress ));
      }
      if( caseNumber.equals( "2b" ) ) {
        blockID = findOldestIdUsed( indexOfAddress );
      }

      String curValidBit = kWayCache[indexOfAddress][blockID][validBitIndex];
      String curCacheTag = kWayCache[indexOfAddress][blockID][tagIndex];
      String lastUsedField = kWayCache[indexOfAddress][blockID][lastUsed];
      String curDirtyBit = kWayCache[indexOfAddress][blockID][dirtyBitIndex];

      //each access falls under one of these cases
      if( tokens[7].equals( "L" ) ) {
        numOfLoads++; //reads
        //cache hit
        if( caseNumber.equals( "1" ) ) {
          hitOrMiss = "1";
          totalAccessTimeForReads++;
          kWayCache[indexOfAddress][blockID][lastUsed] = String.valueOf( order );
        }
        //clean cache miss: block is empty or contains block that is clean
        if( caseNumber.equals( "2a" ) ) {
          hitOrMiss = "0";
          numOfDataReadMisses++;
          numOfDataMisses++;
          totalAccessTimeForReads += (1 + missPenaltyCycles);
          numOfBytesReadFromMem += blockSize;
          setNewValues( indexOfAddress, blockID, "0", "1", tagOfAddress, String.valueOf( order ) );
        }
        //dirty cache miss
        if( caseNumber.equals( "2b" ) ) {
          hitOrMiss = "0";
          numOfDataReadMisses++;
          numOfDataMisses++;
          numOfDirtyDataReadMisses++;
          numOfBytesReadFromMem += blockSize;
          numOfBytesWrittenToMem += blockSize;
          totalAccessTimeForReads += (1 + 2 * missPenaltyCycles);
          setNewValues( indexOfAddress, blockID, "0", "1", tagOfAddress, String.valueOf( order ) );
        }
      }else {
        numOfStores++; //writes
        //cache hit
        if( caseNumber.equals( "1" ) ) {
          hitOrMiss = "1";
          totalAccessTimeForWrites++;
          kWayCache[indexOfAddress][blockID][lastUsed] = String.valueOf( order );
          kWayCache[indexOfAddress][blockID][dirtyBitIndex] = "1";
        }          //clean cache miss: block is empty or contains block that is clean
        if( caseNumber.equals( "2a" ) ) {
          hitOrMiss = "0";
          numOfDataWriteMisses++;
          numOfDataMisses++;
          numOfBytesReadFromMem += blockSize;
          totalAccessTimeForWrites += (1 + missPenaltyCycles);
          setNewValues( indexOfAddress, blockID, "1", "1", tagOfAddress, String.valueOf( order ) );
        }
        //dirty cache miss
        if( caseNumber.equals( "2b" ) ) {
          hitOrMiss = "0";
          numOfDataWriteMisses++;
          numOfDataMisses++;
          numOfBytesWrittenToMem += blockSize;
          numOfBytesReadFromMem += blockSize;
          totalAccessTimeForWrites += (1 + 2 * missPenaltyCycles);
          numOfDirtyDataWriteMisses++;
          setNewValues( indexOfAddress, blockID, "1", "1", tagOfAddress, String.valueOf( order ) );
        }
      }
      //if verbose is turned on it prints verbose outputs from/to in sequential order(range starts from 0 to # of blocks -1)
      if( isVerboseModeOn && (ic1 <= order && ic2 >= order) ) {
        System.out.printf( "%-10s %-15s %-5s %-15s %-5s %-5s %-10s %-15s %-5s %-5s %-5s", order, currDataAddress, intToHex( indexOfAddress ),
                binToHex( tagOfAddress ), curValidBit, String.valueOf( blockID ), lastUsedField == null ? "0" : Integer.parseInt( lastUsedField ),
                binToHex( (curCacheTag == null ? "0" : curCacheTag) ), curDirtyBit, hitOrMiss, caseNumber );
        System.out.println();
      }
      order++;
    }
  }

  //sets new values to particular block
  private void setNewValues( int index, int id, String dBit, String vBit, String newTag, String lu ) {
    kWayCache[index][id][dirtyBitIndex] = dBit;
    kWayCache[index][id][validBitIndex] = vBit;
    kWayCache[index][id][tagIndex] = newTag;
    kWayCache[index][id][lastUsed] = lu;
  }

  //converts binary to hexadecimal
  private String binToHex( String num ) {
    return Long.toString( Long.parseLong( num, 2 ), 16 );
  }

  //converts from integer to Hexadecimal
  private String intToHex( int num ) {
    return Long.toString( num, 16 );
  }

  //find the oldest block id for a given index
  private int findOldestIdUsed( int index ) {
    int oldestStamp = Integer.parseInt( kWayCache[index][0][lastUsed] );
    int id = 0;
    for( int i = 0; i < setAssociativity; i++ ) {
      if( Integer.parseInt( kWayCache[index][i][lastUsed] ) < oldestStamp ) {
        oldestStamp = Integer.parseInt( kWayCache[index][i][lastUsed] );
        id = i;
      }
    }
    return id;
  }

  //finds the first empty block for a given index and returns its id
  private int findEmptyBlockId( int index ) {
    for( int i = 0; i < setAssociativity; i++ ) {
      if( kWayCache[index][i][validBitIndex].equals( "0" ) ) {
        return i;
      }
    }
    return -1;
  }

  //finds bloc id for the given index and tag
  private int findBlockId( int index, String tag ) {
    for( int i = 0; i < setAssociativity; i++ ) {
      if( kWayCache[index][i][validBitIndex].equals( "1" ) && kWayCache[index][i][tagIndex].equals( tag ) ) {
        return i;
      }
    }
    return -1;
  }

  //checks if a cache has an empty block for a given index
  private boolean containsEmptyBlock( int index ) {
    boolean found = false;
    for( int i = 0; i < setAssociativity; i++ ) {
      if( kWayCache[index][i][validBitIndex].equals( "0" ) ) {
        found = true;
        break;
      }
    }
    return found;
  }

  //checks if a tag is found under given index
  private boolean isFound( int index, String tag ) {
    for( int i = 0; i < setAssociativity; i++ ) {
      if( kWayCache[index][i][validBitIndex].equals( "1" ) && kWayCache[index][i][tagIndex].equals( tag ) ) {
        return true;
      }
    }
    return false;
  }

  //helps to find the case number based on index and tag
  private String getCaseNumber( int index, String tag ) {
    String cn = "";
    if( isFound( index, tag ) ) {
      cn = "1";
    }else if( (containsEmptyBlock( index ) || kWayCache[index][findOldestIdUsed( index )][dirtyBitIndex].equals( "0" )) ) {
      cn = "2a";
    }else if( kWayCache[index][findOldestIdUsed( index )][dirtyBitIndex].equals( "1" ) ) {
      cn = "2b";
    }
    return cn;
  }

  //calculates the index from the address
  private int calculateIndex( String address ) {

    String binNumber = getAddressInBinary( address );
    int offsetNumOfBits = getLogNum( blockSize );
    int indexNumOfBits = getLogNum( (cacheSize * 1024) / (blockSize * setAssociativity) );
    return Integer.parseInt( (binNumber.substring( (binNumber.length() - indexNumOfBits - offsetNumOfBits), (binNumber.length() - offsetNumOfBits) )), 2 );
  }

  //calculates the tag from the address
  private String calculateTag( String address ) {

    String binNumber = getAddressInBinary( address );
    int offsetNumOfBits = getLogNum( blockSize );
    int indexNumOfBits = getLogNum( cacheSize * 1024 / (blockSize * setAssociativity) );
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
    System.out.println( setAssociativity + "-way, writeback, size = " + cacheSize + "KB" );
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
    if( lengthOfArg > 3 ) {
      if( !checkArgNum( args[1] ) || !checkArgNum( args[2] ) ) {
        System.out.println( "Cache size or set-associativity is not correct. It should be positive and a power of two." );
      }else if( lengthOfArg != 6 || !args[3].equals( "-v" ) || !checkIfPositiveDigit( args[4] ) || !checkIfPositiveDigit( args[5] ) ) {
        System.out.println( "The argument(s) for verbose mode is not correct or too many or less arguments." );
      }else {
        isVerbose = true;
        new sys2( args[0], args[1], args[2], isVerbose, args[4], args[5] );
      }
    }else if( lengthOfArg <= 2 ) {
      System.out.println( "Please provide arguments(1:trace file 2:cache size 3:set-associativity 4(Optional verbose mode):\"-v ic1 ic2\"" );
    }else {
      if( !checkIfPositiveDigit( args[1] ) || !checkIfPositiveDigit( args[2] ) || !checkArgNum( args[1] ) || !checkArgNum( args[2] ) ) {
        System.out.println( "Cache size or set-associativity is not correct. It should be positive and a power of two." );
      }else {
        new sys2( args[0], args[1], args[2], isVerbose, "0", "0" );
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

  //checks if a number is a power of two
  private static boolean checkArgNum( String num ) {
    int number = Integer.parseInt( num );
    return ((number > 0) && (number & (number - 1)) == 0);
  }
}
