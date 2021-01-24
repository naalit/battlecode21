# A small Python script that makes a copy of a bot, renaming the package appropriately, so you can test changes against the old version.
# Call like this: `python botsnapshot.py starfleet starfleet2` to copy src/starfleet to src/starfleet2

import shutil
import sys
import os

botname = sys.argv[1]
botname2 = sys.argv[2]
path = "src/" + botname2
print("Creating snapshot of bot", botname, "at path", path)
spath = "src/" + botname

if os.path.isdir(path):
    confirm = input("Path already exists. Remove and overwrite (y/N)? ")
    if confirm != "y" and confirm != "yes" and confirm != "Y":
        sys.exit(1)
    else:
        shutil.rmtree(path)

os.mkdir(path)
for file in os.listdir(spath):
    fpath = spath + "/" + file
    srcfile = open(fpath, "r")
    contents = srcfile.read()
    srcfile.close()

    contents2 = contents.replace(botname, botname2)
    fpath2 = path + "/" + file
    dstfile = open(fpath2, "w+")
    dstfile.write(contents2)
    dstfile.close()

    print("Copied file", fpath, "to", fpath2)

print("Snapshot complete!")
