# File-System-Checker

csefsck will do the following:
1)	The DeviceID is correct
2)	All timesare in the past, nothing in the future
3)	Validate that the free block list is accurate this includes
a.	Making sure the free block list contains ALL of the free blocks
b.	Make sure than there are no files/directories stored on items listed in the free block list
4)	Each directory contains . and .. and their block numbers are correct
5)	Each directory’s link count matches the number of links in the filename_to_inode_dict
6)	If the data contained in a location pointer is an array, that indirect is one
7)	That the size is valid for the number of block pointers in the location array. The three possibilities are:
a.	size<blocksize if  indirect=0 and size>0
b.	size<blocksize*length of location array if indirect!=0
c.	size>blocksize*(length of location array-1) if indirect !=0
