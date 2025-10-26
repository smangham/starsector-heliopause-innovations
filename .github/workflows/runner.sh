#!/bin/sh

### Edit
# Your mod name. The version number will be attached to this to form "My-Mod"
MOD_FOLDER_NAME="starsector-heliopause-innovations"

echo "Folder name will be $MOD_FOLDER_NAME"
###


chmod +x ./zipMod.sh
sh ./zipMod.sh "./../.." "$MOD_FOLDER_NAME"