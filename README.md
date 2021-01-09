# Battlecode Players

This is my copy of the scaffold repository. Here's most of what I've implemented so far:

- Units have (random) target locations instead of wandering around inefficiently, and avoid units in the way but don't make use of passability yet
- Messaging infrastructure, which works pretty well
- Half-decent bidding algorithm: increment bid every time we lose, and don't bid above 10% of our influence
- Units find and tell each other about map edges and enemy ECs
- Slanderers run from muckrakers, and muckrakers run toward slanderers (neither does it very well, but it's something)
- Politicians run towards the enemy EC if they know about it, and blow things up if possible

While a lot of the specific code will be replaced, the infrastructure seems to work well and is probably worth keeping.

### Project Structure

- `README.md`
    This file.
- `build.gradle`
    The Gradle build file used to build and run players.
- `src/`
    Player source code.
- `test/`
    Player test code.
- `client/`
    Contains the client. The proper executable can be found in this folder (don't move this!)
- `build/`
    Contains compiled player code and other artifacts of the build process. Can be safely ignored.
- `matches/`
    The output folder for match files.
- `maps/`
    The default folder for custom maps.
- `gradlew`, `gradlew.bat`
    The Unix (OS X/Linux) and Windows versions, respectively, of the Gradle wrapper. These are nifty scripts that you can execute in a terminal to run the Gradle build tasks of this project. If you aren't planning to do command line development, these can be safely ignored.
- `gradle/`
    Contains files used by the Gradle wrapper scripts. Can be safely ignored.


### Useful Commands

- `./gradlew run`
    Runs a game with the settings in gradle.properties
- `./gradlew update`
    Update to the newest version! Run every so often
