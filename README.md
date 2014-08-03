MileageTracker
==============

Introduction
Welcome to my first application for the Android OS!

MileageTracker is an application I originally wrote on my two week break from work at the end of 2009, and have been tinkering with here and there from then on. I've decided to share my application with the world, and have uploaded the code to Google Code!

Details
==============
If you haven't already checked it out, head on over to the Android Market and search for 'MileageTracker'. My application is basically a front-end for a database which records your actual average miles-per-gallon, as well as compares the 'actual' number (miles traveled/number of gallons consumed) against the number most modern automobiles can give (FYI: my car shows a number that's consistently 10% better than the real number!!).

I've incorporated the [achartengine library](http://code.google.com/p/achartengine) to generate charts based on the data the user enters (shows the difference between your car's average number and the one calculated by my application, as well as MPG over time, and gas price over time).

Please feel free to give me any feedback or suggestions for future enhancements. I've written the application to a point where it suites my purpose, and I'm out of ideas! I'd love to expand a bit more on the base application!

Current Limitations of Multi-Vehicle profiles
==============
1. ~~Cannot change the name of the vehicles~~ added in revision b71fac
2. ~~Cannot export all vehicle's data to single CSV file, in a single operation~~ fixed
3. ~~Gas-station auto-completion is vehicle dependent (i.e. gas stations entered for 'Car1' will not show up as completion options for 'Car2' or 'Car3')~~ fixed
4. ~~Cannot move a record from one vehicle to another. At this time, this will require manually creating a new entry for the destination vehicle, and entering the data.~~ added in revision b71fac
5. Clearing data will clear from all vehicles (not just the current vehicle)

Coming Features
==============
* Add new charts (one new chart in release v1.5)
* Add preferences to select which charts to display (added in v1.5)
* Allow user to rename Vehicle names (added in v2.0, revision b71fac)


Thanks, Trevor
