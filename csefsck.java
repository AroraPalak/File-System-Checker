/**
 * 
 */
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.Queue;

enum Type {
	ATime, MTime, CTIME, CurrDir, ParDir, LinkCount, Indirect, Location
}

class Directory {

	int loc;
	int parent;

	Directory(int Location, int par) {
		loc = Location;
		parent = par;
	}
}

public class csefsck {

	final static int DEVICEID = 20;
	final static int MAXBLOCK = 10000;
	final static int MAXPOINTERS = 400;
	final static int BLOCKSIZE = 4096;
	static boolean[] isUsedList = new boolean[MAXBLOCK];

	// Initialize File System Checker
	public static void InitializeFSC(String path) {
		System.out.println("\nBeginning the system scan."
				+ "This process will take some time.");

		// Method to run all 7 checks of this this file system checker
		run_all_Checkes(path);
	}

	public static void run_all_Checkes(String path) {

		//Check for DeviceID
		System.out.println("\nCheck 1 : Checking Device ID\n");
		scanSuperBlock(path, 1);
		System.out
				.println("Check for DeviceID is complete.Proceeding to next Check.");

		// Check for timestamps
		System.out.println("\nCheck 2 : Checking timestamps\n");
		scanSuperBlock(path, 2);
		scanAll(path, 2);
		System.out.println("All timestamps are correct.");
		System.out
				.println("Check for timestamps is complete.Proceeding to next Check.");

		// Check for free block list
		System.out.println("\nCheck 3 : Checking free block lists\n");

		if (checkFreeBlocklist(path)) {
			System.out.println("Check for free block list is complete.Proceeding to next Check.");
		}

		//Check for current and parent directory blocknos
		System.out.println("\nCheck 4 : Checking "
				+ "current and parent block in all directories \n");
		scanAll(path, 4);
		System.out.println("Check for current and parent block is complete.Proceeding to next Check.");

		//Check for LinkCount
		System.out.println("\nCheck 5 : Checking LinkCount of directories");
		scanAll(path, 5);
		System.out.println("\nCheck for LinkCount is complete.Proceeding to next Check.");

		//Check for Indexblock of files
		System.out.println("\nCheck 6 : Checking index block of files\n");
		scanAll(path, 6);
		System.out.println("Check for index block is complete.Proceeding to next Check.");

		//Check for Size of file
		System.out.println("\nCheck 7 : Checking size of files\n");
		scanAll(path, 7);
		System.out.println("Size of all files is correct. No further defragmentation is not possible");

		System.out.println("\nThe system has been scanned successfully");
	}

	// Utility method to test if a string can parse Integer
	static public boolean isInteger(String input) {
		try {
			Integer.parseInt(input);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	// This will scan SuperBlock for DeviceID and Creation time
	public static void scanSuperBlock(String path, int checkno) {

		String line = null;
		BufferedReader br = null;
		int devID = 0;
		try {
			br = new BufferedReader(new FileReader(path+ "/fusedata.0"));
			line = br.readLine();
			StringTokenizer st = new StringTokenizer(line, ",");
			String creationTimeBlock = st.nextToken(); // creation time
			st.nextToken();
			String devIdBlock = st.nextToken(); // Device ID
			String tokens[] = devIdBlock.split(":");
			devID = Integer.parseInt(tokens[1].trim());
			br.close();

			if (checkno == 1) {

				if (checkDeviceID(devID)) {

					System.out.println("Device ID is correct.");
				} else {

					System.out.println("Device ID is incorrect."+ "This is not the desired FILE SYSTEM");
					System.out.println("Exiting File System Checker....");
					System.exit(0);
				}
			} else if (checkno == 2) {

				if (checkTime(creationTimeBlock)) {

					System.out.println("Creation time of superblock is "+ "correct." + "Proceeding to other directories");

				} else {
					System.out.println("Creation time of superblock is "+ "incorrect." + "Correcting the time.....");

					updateSuperBlockTime(path, line);
					System.out.println("Time corrected.Proceeding to other directories");

				}
			}

		} catch (FileNotFoundException ex) {

			System.out.println("\n Unable to open file fusedata.0'");
		} catch (IOException ex) {

			System.out.println("\n Error reading file fusedata.0 ");
		}

	}

	// Check if deviceID is 20. If not, return false.
	public static boolean checkDeviceID(int devID) {
		if (devID == DEVICEID)
			return true;
		else
			return false;
	}

	// This method will check if the time Checked is
	// greater or leass than the current time.
	public static boolean checkTime(String timeBlock) {

		long timeVal = 0;
		String token[] = timeBlock.split(":");
		timeVal = Long.parseLong(token[1].trim());
		if (timeVal < System.currentTimeMillis()) {
			return true;
		} else {
			return false;
		}
	}

	// This method updates Super Block Creation Time to current time in seconds
	public static void updateSuperBlockTime(String path, String fileline) {

		StringTokenizer st = new StringTokenizer(fileline, ",");
		String createTimeBlock = st.nextToken().trim(); // creation time
		String mountIDBlock = st.nextToken().trim(); // number of mounts
		String devIDBlock = st.nextToken().trim();
		String freeStartBlock = st.nextToken().trim();
		String freeEndBlock = st.nextToken().trim();
		String rootBlock = st.nextToken().trim();
		String maxBlock = st.nextToken().trim();
		FileWriter fstream;
		long creatTime = 0;
		String tokens[] = createTimeBlock.split(":");
		creatTime = Long.parseLong(tokens[1].trim());
		creatTime = System.currentTimeMillis() / 1000;

		try {
			fstream = new FileWriter(path + "/fusedata.0");
			BufferedWriter fb = new BufferedWriter(fstream);
			fb.write("{creationTime: " + creatTime + "," + mountIDBlock + ","
					+ devIDBlock + "," + freeStartBlock + "," + freeEndBlock
					+ "," + rootBlock + "," + maxBlock);
			fb.newLine();
			fb.close();
		} catch (IOException e) {
		
			e.printStackTrace();
		}
	}

	/*
	 * This method checks if free block list is accurate. Display errors of
	 * inaccurate free blocks
	 */
	public static boolean checkFreeBlocklist(String path) {

		BufferedReader br = null;
		String line = null;
		int blockno = 0;
		TreeSet<Integer> hs = new TreeSet<Integer>();
		int firstfile = (MAXBLOCK / 400) + 2;
		boolean errorflag = false;
		int j = 0;

		try {
			isUsedList[0] = true;
			for (int i = 1; i <= MAXBLOCK / MAXPOINTERS; i++) {

				br = new BufferedReader(new FileReader(path
						+ "/fusedata." + i));
				line = br.readLine();
				StringTokenizer st = new StringTokenizer(line, ",");
				while (st.moreTokens()) {
					blockno = Integer.parseInt(st.nextToken().trim()
							.substring(0));
					hs.add(blockno);
				}
				isUsedList[i] = true;
				br.close();
			}
			scanAll(path, 3);
			for (j = firstfile; j < MAXBLOCK; j++) {

				if (!isUsedList[j] && !hs.contains(j)) {
					System.out.println("ERROR: Freeblock " + j
							+ " is not present in the freeblock "
							+ (int) (Math.floor(j / 400) + 1));
					hs.add(j);
					errorflag = true;
				} else if (isUsedList[j] && hs.contains(j)) {
					System.out.println("ERROR: " + "Block " + j
							+ " is not empty but present in the freeblock list"
							+ (int) (Math.floor(j / 400) + 1));
					hs.remove(j);// update FreeBlock source to remove referenced
									// block
					errorflag = true;
				}
			}
			if (errorflag) {
				System.out.println("\nCorrecting free blocklist....");
				updatefreeblist(path);
				System.out.println("Free blocklist corrected.");
				return true;
			} else {
				System.out.println("No errors found in free blocklist.");
				return true;
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	/*
	 * This method updates Free block list everytime the block is freed or for
	 * check 2
	 */

	public static void updatefreeblist(String path) {

		FileWriter fstream;
		BufferedWriter fb;
		int loopCounter = (MAXBLOCK / 400) + 2;
		int firstFreeBlockNo = loopCounter;
		int j = 0;

		try {
			for (int i = 1; i <= MAXBLOCK / MAXPOINTERS; i++) {
				fstream = new FileWriter(path + "/fusedata." + i);
				fb = new BufferedWriter(fstream);
				// First block is a special case
				if (i == 1) {
					for (j = loopCounter; j < MAXPOINTERS; j++) {
						if (!isUsedList[firstFreeBlockNo]) {
							if (j == MAXPOINTERS - 1) {
								fb.write(firstFreeBlockNo + "");
							} else {
								fb.write(firstFreeBlockNo + ",");
							}
						}
						firstFreeBlockNo++;
					}
					fb.close();
				} else {
					for (j = 0; j < MAXPOINTERS; j++) {
						if (!isUsedList[firstFreeBlockNo]) {
							if (j == MAXPOINTERS - 1) {
								fb.write(firstFreeBlockNo + "");
							} else {
								fb.write(firstFreeBlockNo + ",");
							}
						}
						firstFreeBlockNo++;
					}
					fb.close();
				}

			}

		} catch (IOException e) {
		
			e.printStackTrace();
		}
	}

	/*
	 * This method is called for
	 * every check to scan Directory and Files and perform according to the
	 * check required.
	 */
	public static void scanAll(String path, int checkno) {

		int rootblock = (MAXBLOCK / 400) + 1;
		BufferedReader br = null;
		LinkedList<Directory> dirqueue = new LinkedList<Directory>();
		String line = null;
		String nextBlock = null;
		String type = null;
		int Location = 0;
		int calcLinkCount = 0;
		try {
			Directory dir = new Directory(rootblock, rootblock);
			dirqueue.add(dir);
			while (!dirqueue.isEmpty()) {
				Directory d = dirqueue.remove();
				if (checkno == 3) {
					isUsedList[d.loc] = true;
				}
				br = new BufferedReader(new FileReader(path
						+ "/fusedata." + d.loc));
				line = br.readLine();
				StringTokenizer st = new StringTokenizer(line, ",");
				st.nextToken(); // Size, not required
				st.nextToken(); // UI,not required
				st.nextToken(); // GID, not required
				st.nextToken(); // Mode,not required
				String ATimeBlock = st.nextToken().trim();
				String ctimeBlock = st.nextToken().trim();
				String MTimeBlock = st.nextToken().trim();
				String LinkCountBlock = st.nextToken().trim();
				String finBlock = st.nextToken().trim();
				boolean firstchild = true;
				calcLinkCount = 0;
				while (st.moreTokens()) {
					StringTokenizer stInner = null;
					if (firstchild) {
						nextBlock = finBlock;
						stInner = new StringTokenizer(nextBlock, ":");
						stInner.nextToken();
						type = stInner.nextToken().trim().substring(1);
						firstchild = false;
					} else {
						nextBlock = st.nextToken();
						stInner = new StringTokenizer(nextBlock, ":");
						type = stInner.nextToken().trim().substring(0);
					}
					calcLinkCount++;
					String name = stInner.nextToken();
					if (st.countTokens() == 0) {
						Location = Integer.parseInt(stInner.nextToken("}").substring(1));
					} else {
						Location = Integer.parseInt(stInner.nextToken());
					}
					if (type.equals("d")) {

						/*
						 * If the Type is directory, scan the directory for
						 * checks 2,3,4 and 5
						 */
						ScanDir(path, br, name, d, checkno,Location, line, dirqueue);
					} else if (type.equals("f")) {

						/*
						 * If the Type is Files, scan the file for checks 2,3,6
						 * and 7
						 */
						scanFiles(path, d, checkno, Location, line);
					}

				}
				// For timestamps
				if (checkno == 2 && type.equals("d")) {

					if (!checkTime(ATimeBlock)) {
						System.out.println("\nAccess time of directory "
								+ d.loc + " is incorrect."
								+ "\nCorrecting the time");
						br.close();
						br = new BufferedReader(new FileReader(path+ "/fusedata." + d.loc));
						line = br.readLine();
						if (updateDirectory(path, line, d.loc, 0, Type.ATime)) {
							System.out.println("Access time corrected");
						}

					}
					if (!checkTime(ctimeBlock)) {

						System.out.println("\nCreation time of directory "
								+ d.loc + " is incorrect."
								+ "\nCorrecting the time.");
						br.close();
						br = new BufferedReader(new FileReader(path+ "/fusedata." + d.loc));
						line = br.readLine();
						if (updateDirectory(path, line, d.loc, 0, Type.CTIME)) {
							System.out.println("Creation time corrected.");
						}

					}
					if (!checkTime(MTimeBlock)) {
						System.out.println("\nModification time of directory "
								+ d.loc + " is incorrect."
								+ "Correcting the time.....");
						br.close();
						br = new BufferedReader(new FileReader(path+ "/fusedata." + d.loc));
						line = br.readLine();
						if (updateDirectory(path, line, d.loc, 0, Type.MTime)) {
							System.out.println("Modification time corrected");

						}
					}
				}
				// For Check 5 - LinkCount of Directories
				if (type.equals("d") && checkno == 5) {
					String tokens[] = LinkCountBlock.split(":");
					int dirLinkCount = Integer.parseInt(tokens[1].trim());
					if (dirLinkCount != calcLinkCount) {
						System.out.println("\nLinkCount of block no " + d.loc+ " is incorrect");
						System.out.println("Correcting LinkCount.....");
						if (updateDirectory(path, line, d.loc, calcLinkCount,Type.LinkCount)) {
							System.out.println("LinkCount corrected.");
						}
					}
				}
				br.close();
			}
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/* This method will scan each specific directory for check no 4 */
	public static void ScanDir(String path, BufferedReader br,
			String name, Directory d, int checkno, int Location, String line,
			Queue<Directory> dirqueue) {

		try {
			switch (name) {
			case ".": // For Current Directory
				if (d.loc != Location && checkno == 4) {
					System.out.println("Current directory of " + d.loc+ " is incorrectly stored as " + Location);
					System.out.println("Correcting data.....");
					if (updateDirectory(path + "inner", line, d.loc, d.loc,Type.CurrDir)) {
						System.out.println("Data corrected.\n");
					}

				}
				Thread.sleep(1000);
				break;
			case "..": // For Parent Directory
				if (d.parent != Location && checkno == 4) {
					System.out.println("Parent directory of " + d.loc+ " is incorrectly stored as " + Location);
					System.out.println("Correcting data.....");
					if (updateDirectory(path + "outer", line, d.loc, d.parent,
							Type.ParDir)) {
						System.out.println("Data corrected.\n");
					}
				}
				break;
			default:
				Directory dirchild = new Directory(Location, d.loc);
				dirqueue.add(dirchild);
				break;

			}
		} catch (Exception e) {

			e.printStackTrace();
		}

	}

	/*
	 * This method will Scan all files. Used for check 2, 3, 6 and 7
	 */
	public static void scanFiles(String path, Directory d, int checkno,
			int Location, String line) {

		BufferedReader fileReader = null;
		BufferedReader br = null;
		String filedata = null;
		int[] iarray = new int[MAXPOINTERS];
		try {
			br = new BufferedReader(new FileReader(path + "/fusedata."+ Location));
			filedata = br.readLine();
			StringTokenizer fileScanner = new StringTokenizer(filedata, ",");
			fileScanner.nextToken(); // Size, not required
			fileScanner.nextToken(); // UID, not required
			fileScanner.nextToken(); // GID, not required
			fileScanner.nextToken(); // Mode,not required
			fileScanner.nextToken(); // LinkCount Block, not required
			String fATimeBlock = fileScanner.nextToken();
			String fctimeBlock = fileScanner.nextToken();
			String fMTimeBlock = fileScanner.nextToken();
			String indexBlock = fileScanner.nextToken();
			if (checkno == 3) {
				isUsedList[Location] = true;
			}
			// ----For TIMESTAMPS
			if (checkno == 2) {
				if (!checkTime(fATimeBlock)) {
					System.out.println("\nAccess time of file " + Location+ " is incorrect." + "Correcting the time.....");
					br.close();
					br = new BufferedReader(new FileReader(path+ "/fusedata." + Location));
					filedata = br.readLine();
					if (updateFile(path, filedata, Location, 0, Type.ATime)) {
						System.out.println("Access Time corrected");
					}
				}
				if (!checkTime(fctimeBlock)) {
					System.out.println("\nCreation time of file " + Location+ " is incorrect." + "Correcting the time.....");
					br.close();
					br = new BufferedReader(new FileReader(path+ "/fusedata." + Location));
					filedata = br.readLine();
					if (updateFile(path, filedata, Location, 0, Type.CTIME)) {
						System.out.println("Creation Time corrected");
					}

				}
				if (!checkTime(fMTimeBlock)) {
					System.out.println("\nModification time of file "
							+ Location + " is incorrect."
							+ "Correcting the time.....");
					br.close();
					br = new BufferedReader(new FileReader(path+ "/fusedata." + Location));
					filedata = br.readLine();
					if (updateFile(path, filedata, Location, 0, Type.MTime)) {
						System.out.println("Modification time corrected");
					}

				}

			} else if (checkno == 3) { // ----For Freeblock list
				String tokens[] = indexBlock.split(":");
				int fLocation = Integer.parseInt(tokens[2].split("}")[0]);
				isUsedList[fLocation] = true;
				fileReader = new BufferedReader(new FileReader(path+ "/fusedata." + fLocation));
				String indexdata = fileReader.readLine();
				StringTokenizer arrayScanner = new StringTokenizer(indexdata,",");
				int i = 0;
				while (arrayScanner.moreTokens()) {
					String nextToken = arrayScanner.nextToken().trim();
					if (isInteger(nextToken)) {
						iarray[i] = Integer.parseInt(nextToken);
						isUsedList[iarray[i]] = true;
						i++;
					}
				}
			} else if (checkno == 6) { // ---For Indirect Block
				String tokens[] = indexBlock.split(":");
				String Token1 = tokens[1].trim().substring(0, 1);
				int Indirect = Integer.parseInt(Token1);
				int fLocation = Integer.parseInt(tokens[2].split("}")[0]);
				fileReader = new BufferedReader(new FileReader(path+ "/fusedata." + fLocation));
				String indexdata = fileReader.readLine();
				StringTokenizer arrayScanner = new StringTokenizer(indexdata,",");
				int i = 0;
				int countTokens = 0;
				while (arrayScanner.moreTokens()) {
					String nextToken = arrayScanner.nextToken().trim();
					if (isInteger(nextToken)) {
						iarray[i++] = Integer.parseInt(nextToken);
						countTokens++;
					}
				}
				/*
				 * CASE 1: If data contained in Location pointer is array but
				 * Indirect block is 0
				 */
				if (countTokens > 1 && Indirect == 0) {
					System.out.println("Indirect of block " + Location+ " is incorrect as 0");
					System.out.println("Changing Indirect to 1.");
					if (updateFile(path, filedata, Location, 1, Type.Indirect)) {
						System.out.println("Indirect block changed.");
					}

				}
				/*
				 * CASE 2: If data contained in Location pointer is a single
				 * digit but Indirect is 1. The Indirect should be changed to 0
				 * and Location should directly point to file data The Indirect
				 * file should be then added to free block list
				 */
				else if (Indirect == 1 && countTokens == 1) {

					System.out.println("Indirect of block " + Location+ " is incorrect as 1");
					// No use of Indirecting.Can directly point to File data
					System.out.println("Changing Indirect to 0 and "+ "Location to actual file data.");
					if (updateFile(path, filedata, Location, iarray[0],Type.Location)) {
						System.out.println("Indirect and Location changed.");
						// The Indirect block can be now added to free block
						// list
						System.out.println("Updating free block list....");
						isUsedList[fLocation] = false;
						updatefreeblist(path);
						System.out.println("Free block list updated with currently freed block");
					}
				}
				fileReader.close();

			}
			// ----For File size
			else if (checkno == 7) {
				long combinedSize = 0;
				String tokens[] = indexBlock.split(":");
				String Token1 = tokens[1].trim().substring(0, 1);
				int Indirect = Integer.parseInt(Token1);
				int fLocation = Integer.parseInt(tokens[2].split("}")[0]);
				fileReader = new BufferedReader(new FileReader(path+ "/fusedata." + fLocation));
				String indexdata = fileReader.readLine();
				if (Indirect == 0) {
					Path p = Paths.get(path + "/fusedata." + fLocation);
					byte[] data = Files.readAllBytes(p);
					if (data.length > BLOCKSIZE) {
						System.out.println("\nThe size of the file "
										+ fLocation
										+ " is greater than the blocksize but Indirect is 0");
					}
				} else if (Indirect == 1) {

					StringTokenizer arrayScanner = new StringTokenizer(indexdata, ",");
					int i = 0;
					while (arrayScanner.moreTokens()) {
						String nextToken = arrayScanner.nextToken().trim();
						if (isInteger(nextToken)) {
							System.out.println("nextToken" + nextToken);
							iarray[i++] = Integer.parseInt(nextToken);
						}
					}
					LinkedList<byte[]> fileData = new LinkedList<byte[]>();
					for (int j = 0; j < i; j++) {
						Path p = Paths.get(path + "/fusedata." + iarray[j]);
						byte[] data = Files.readAllBytes(p);
						fileData.add(data);
						combinedSize += data.length;

					}
					if (combinedSize < BLOCKSIZE * iarray.length) {
						if (combinedSize <= BLOCKSIZE * (iarray.length - 1)) {

							byte[] compArr = new byte[BLOCKSIZE * iarray.length
									- 1];
							int comIndex = 0;
							for (byte[] bAr : fileData) {
								for (byte b : bAr) {
									compArr[comIndex++] = b;
								}
							}
							int x = 0;
							int fileNum = 0;
							int bytesRemaining = comIndex;
							FileWriter fInd = new FileWriter(path
									+ "/fusedata." + fLocation);
							BufferedWriter bInd = new BufferedWriter(fInd);
							while (x < comIndex) {
								FileOutputStream fos = new FileOutputStream(
										path + "/fusedata." + iarray[fileNum]);
								int bytesToCopy = 0;
								if (bytesRemaining > MAXBLOCK) {
									bytesToCopy = MAXBLOCK;
									bytesRemaining = bytesRemaining - MAXBLOCK;
								} else {
									bytesToCopy = bytesRemaining;
								}
								fos.write(Arrays.copyOfRange(compArr, x,
										bytesToCopy));
								System.out.println("x : " + x);
								System.out.println("iarray : "+ iarray[fileNum]);
								System.out.println("Filenum : " + fileNum);
								x = x + MAXBLOCK;
								bInd.write(iarray[fileNum]+",");
								fileNum++;
								fos.close();
							}

							bInd.close();

						}
					}
				}
			}

			br.close();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public static boolean updateDirectory(String path, String fileline,
			int loc, int correctVal, Type t) {

		StringTokenizer stUpdate = new StringTokenizer(fileline, ",");
		String sizeBlock = stUpdate.nextToken().trim();
		String uidBlock = stUpdate.nextToken().trim();
		String gidBlock = stUpdate.nextToken().trim();
		String modeBlock = stUpdate.nextToken().trim();
		String ATimeBlock = stUpdate.nextToken().trim();
		String ctimeBlock = stUpdate.nextToken().trim();
		String MTimeBlock = stUpdate.nextToken().trim();
		String LinkCountBlock = stUpdate.nextToken().trim();
		String finBlock = stUpdate.nextToken().trim();
		FileWriter fstream;
		BufferedWriter fb;
		long timeVal = 0;
		boolean firstchild = true;
		String nextBlock = null;
		String finText = null;
		StringBuilder appendString = new StringBuilder();
		int Location = 0;

		try {
			fstream = new FileWriter(path + "/fusedata." + loc);
			fb = new BufferedWriter(fstream);
			if (t == Type.CurrDir || t == Type.ParDir) {
				while (stUpdate.moreTokens()) {
					StringTokenizer stInner = null;
					if (firstchild) {
						nextBlock = finBlock;
						stInner = new StringTokenizer(nextBlock, ":");
						finText = stInner.nextToken().trim();
						stInner.nextToken();
					} else {
						nextBlock = stUpdate.nextToken().trim();
						stInner = new StringTokenizer(nextBlock, ":");
						stInner.nextToken();
					}
					String name = stInner.nextToken();
					if (stUpdate.countTokens() == 0) {
						Location = Integer.parseInt(stInner.nextToken("}").substring(1));
					} else {
						Location = Integer.parseInt(stInner.nextToken());
					}
					Location = correctVal;
					String StringLeft = null;
					if (name.equals(".") && t == Type.CurrDir) {
						if (firstchild) {
							while (stUpdate.moreTokens()) {
								StringLeft = stUpdate.nextToken().trim();
							}
							fb.write(sizeBlock + "," + uidBlock + ","
									+ gidBlock + "," + modeBlock + ","
									+ ATimeBlock + "," + ctimeBlock + ","
									+ MTimeBlock + "," + LinkCountBlock + ","
									+ finText + ":{d:" + name + ":" + Location
									+ "," + StringLeft);
							fb.newLine();
							fb.close();
							return true;
						} else {
							while (stUpdate.moreTokens()) {
								StringLeft = stUpdate.nextToken().trim();
							}
							fb.write(sizeBlock + "," + uidBlock + ","
									+ gidBlock + "," + modeBlock + ","
									+ ATimeBlock + "," + ctimeBlock + ","
									+ MTimeBlock + "," + LinkCountBlock + ","
									+ appendString + "d:" + name + ":"
									+ Location + "," + StringLeft);
							fb.newLine();
							fb.close();
							return true;
						}
					}
					if (name.equals("..") && t == Type.ParDir) {

						if (firstchild) {
							while (stUpdate.moreTokens()) {
								StringLeft = stUpdate.nextToken().trim();
							}
							fb.write(sizeBlock + "," + uidBlock + ","
									+ gidBlock + "," + modeBlock + ","
									+ ATimeBlock + "," + ctimeBlock + ","
									+ MTimeBlock + "," + LinkCountBlock + ","
									+ finText + ":{d:" + name + ":" + Location
									+ "," + StringLeft);
							fb.newLine();
							fb.close();
							return true;
						} else {
							while (stUpdate.moreTokens()) {
								StringLeft = stUpdate.nextToken().trim();
							}
							fb.write(sizeBlock + "," + uidBlock + ","
									+ gidBlock + "," + modeBlock + ","
									+ ATimeBlock + "," + ctimeBlock + ","
									+ MTimeBlock + "," + LinkCountBlock + ","
									+ appendString + "d:" + name + ":"
									+ Location + "," + StringLeft);
							fb.newLine();
							fb.close();
							return true;
						}

					}
					firstchild = false;
					appendString.append(nextBlock);
					appendString.append(",");
				}

			} else if (t == Type.LinkCount) {

				StringBuilder StringLeft = new StringBuilder();
				while (stUpdate.moreTokens()) {
					StringLeft.append(stUpdate.nextToken().trim());
					if (!(stUpdate.countTokens() == 0))
						StringLeft.append(",");
				}
				String token[] = LinkCountBlock.split(":");
				String LinkCountText = token[0];
				int LinkCount = Integer.parseInt(token[1].trim());
				LinkCount = correctVal;

				fb.write(sizeBlock + "," + uidBlock + "," + gidBlock + ","
						+ modeBlock + "," + ATimeBlock + "," + ctimeBlock + ","
						+ MTimeBlock + "," + LinkCountText + ":" + LinkCount
						+ "," + finBlock + "," + StringLeft);
				fb.newLine();
				fb.close();
				return true;

			} else if (t == Type.ATime) {
				StringBuilder StringLeft = new StringBuilder();
				while (stUpdate.moreTokens()) {
					StringLeft.append(stUpdate.nextToken().trim());
					if (!(stUpdate.countTokens() == 0))
						StringLeft.append(",");
				}
				String token[] = ATimeBlock.split(":");
				timeVal = Long.parseLong(token[1]);
				timeVal = System.currentTimeMillis() / 1000;
				fb.write(sizeBlock + "," + uidBlock + "," + gidBlock + ","
						+ modeBlock + "," + token[0] + ":" + timeVal + ","
						+ ctimeBlock + "," + MTimeBlock + "," + LinkCountBlock
						+ "," + finBlock + "," + StringLeft);
				fb.newLine();
				fb.close();
				return true;

			} else if (t == Type.CTIME) {
				StringBuilder StringLeft = new StringBuilder();
				while (stUpdate.moreTokens()) {
					StringLeft.append(stUpdate.nextToken().trim());
					if (!(stUpdate.countTokens() == 0))
						StringLeft.append(",");
				}
				String token[] = ctimeBlock.split(":");
				timeVal = Long.parseLong(token[1]);
				timeVal = System.currentTimeMillis() / 1000;
				fb.write(sizeBlock + "," + uidBlock + "," + gidBlock + ","
						+ modeBlock + "," + ATimeBlock + "," + token[0] + ":"
						+ timeVal + "," + MTimeBlock + "," + LinkCountBlock
						+ "," + finBlock + "," + StringLeft);
				fb.newLine();
				fb.close();
				return true;

			} else {
				StringBuilder StringLeft = new StringBuilder();
				while (stUpdate.moreTokens()) {
					StringLeft.append(stUpdate.nextToken().trim());
					if (!(stUpdate.countTokens() == 0))
						StringLeft.append(",");
				}
				String token[] = MTimeBlock.split(":");
				timeVal = Long.parseLong(token[1]);
				timeVal = System.currentTimeMillis() / 1000;
				fb.write(sizeBlock + "," + uidBlock + "," + gidBlock + ","
						+ modeBlock + "," + ATimeBlock + "," + ctimeBlock + ","
						+ token[0] + ":" + timeVal + "," + LinkCountBlock + ","
						+ finBlock + "," + StringLeft);
				fb.newLine();
				fb.close();
				return true;

			}
			fb.newLine();
			fb.close();
		} catch (IOException e) {
		
			e.printStackTrace();
		}

		return false;
	}

	public static boolean updateFile(String path, String fileline, int loc,
			int correctVal, Type t) {

		StringTokenizer st = new StringTokenizer(fileline, ",");
		String fsizeBlock = st.nextToken().trim();
		String fuidBlock = st.nextToken().trim();
		String fgidBlock = st.nextToken().trim();
		String fmodeBlock = st.nextToken().trim();
		String fLinkCountBlock = st.nextToken().trim();
		String fATimeBlock = st.nextToken().trim();
		String fctimeBlock = st.nextToken().trim();
		String fMTimeBlock = st.nextToken().trim();
		String indexBlock = st.nextToken().trim();
		FileWriter fstream;
		BufferedWriter fb;
		long timeVal = 0;

		try {
			fstream = new FileWriter(path + "/fusedata." + loc);
			fb = new BufferedWriter(fstream);
			if (t == Type.Indirect) {
				String tokens[] = indexBlock.split(":");
				String Token1 = tokens[1].trim().substring(0, 1);
				String locText = tokens[1].trim().substring(1).trim();
				int Indirect = Integer.parseInt(Token1);
				Indirect = correctVal;
				fb.write(fsizeBlock + "," + fuidBlock + "," + fgidBlock + ","
						+ fmodeBlock + "," + fLinkCountBlock + ","
						+ fATimeBlock + "," + fctimeBlock + "," + fMTimeBlock
						+ "," + tokens[0] + ":" + Indirect + " " + locText
						+ ":" + tokens[2]);
				fb.newLine();
				fb.close();
				return true;

			} else if (t == Type.Location) {
				String tokens[] = indexBlock.split(":");
				String Token1 = tokens[1].trim().substring(0, 1);
				String locText = tokens[1].trim().substring(1).trim();
				String locWithoutSpace[] = tokens[2].split("}");
				int Indirect = Integer.parseInt(Token1);
				int oldLocation = Integer.parseInt(locWithoutSpace[0].substring(0));
				Indirect = 0;
				oldLocation = correctVal;
				fb.write(fsizeBlock + "," + fuidBlock + "," + fgidBlock + ","
						+ fmodeBlock + "," + fLinkCountBlock + ","
						+ fATimeBlock + "," + fctimeBlock + "," + fMTimeBlock
						+ "," + tokens[0] + ":" + Indirect + " " + locText
						+ ":" + oldLocation + "}");
				fb.newLine();
				fb.close();
				return true;

			} else if (t == Type.ATime) {
				String token[] = fATimeBlock.split(":");
				timeVal = Long.parseLong(token[1]);
				timeVal = System.currentTimeMillis() / 1000;
				fb.write(fsizeBlock + "," + fuidBlock + "," + fgidBlock + ","
						+ fmodeBlock + "," + fLinkCountBlock + "," + token[0]
						+ ":" + timeVal + "," + fctimeBlock + "," + fMTimeBlock
						+ "," + indexBlock);
				fb.newLine();
				fb.close();
				return true;

			} else if (t == Type.CTIME) {
				String token[] = fctimeBlock.split(":");
				timeVal = Long.parseLong(token[1]);
				timeVal = System.currentTimeMillis() / 1000;
				fb.write(fsizeBlock + "," + fuidBlock + "," + fgidBlock + ","
						+ fmodeBlock + "," + fLinkCountBlock + ","
						+ fATimeBlock + "," + token[0] + ":" + timeVal + ","
						+ fMTimeBlock + "," + indexBlock);
				fb.newLine();
				fb.close();
				return true;

			} else if (t == Type.MTime) {
				String token[] = fMTimeBlock.split(":");
				timeVal = Long.parseLong(token[1]);
				timeVal = System.currentTimeMillis() / 1000;
				fb.write(fsizeBlock + "," + fuidBlock + "," + fgidBlock + ","
						+ fmodeBlock + "," + fLinkCountBlock + ","
						+ fATimeBlock + "," + fctimeBlock + "," + token[0]
						+ ":" + timeVal + "," + indexBlock);
				fb.newLine();
				fb.close();
				return true;

			}
		} catch (IOException e) {C:\Users\Chutku's PC\Desktop\Filesystem
			e.printStackTrace();
		}
		return false;
	}


	public static void main(String[] args) throws IOException {

		// Checking if the File System exists
		try {
			File f = new File("C:/Users/Palak's PC/Desktop/Filesystem/fusedata.0");
			String path = "C:/Users/Palak's PC/Desktop/Filesystem";
			if (!f.exists()) {
				System.out.println("\nIncorrect path: NO FILE SYSTEM FOUND");
				System.out.println("\nfsck died");
				System.exit(0);
			}
			// Initiate File System Checker
			InitializeFSC(path);

		}catch (Exception e) {
			e.printStackTrace();
		}

	}

}

// Took some reference from http://www.javatpoint.com