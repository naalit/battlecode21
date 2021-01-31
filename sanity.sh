#!/bin/bash

echo "Running $1 vs $2"

for i in Corridor Arena quadrants Branches CringyAsF circle Andromeda Bog Chevron CrownJewels ExesAndOhs FiveOfHearts Gridlock quadrants Illusion NotAPuzzle Rainbow SlowMusic Snowflake BadSnowflake FindYourWay GetShrekt Goldfish HexesAndOhms MainCampus Punctuation Radial SeaFloor Sediment Smile SpaceInvaders Surprised VideoGames ManyCorridors maptestsmall Licc CrossStitch
do
  echo "Running map $i"
  ./gradlew run -PteamA=$1 -PteamB=$2 -Pmaps=$i -PprofilerEnabled=false | grep wins
  ./gradlew run -PteamA=$2 -PteamB=$1 -Pmaps=$i -PprofilerEnabled=false | grep wins
done
