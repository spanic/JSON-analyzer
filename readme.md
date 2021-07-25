# What is it?

This is a super-simple single-class JSON analyzer tool, that I've used for one of my tasks at work.

# What can it do?
* **remove all the keys** listed in the specified JSON file from the other JSON files passed as an arguments;
* **find unique entries** in both of the 2 files, like disjunctive union (or symmetric difference, as you wish);

# How to build & run it?
* clone this repo;
* execute `mvn package`
* run as `java -jar JsonAnalyzer-1.0-SNAPSHOT.jar` with the following way of setting the arguments:
  * for **compare**: `-compare C:\en-eComm.json C:\en-ePOS.json`;
  * for **cleanup**: `-cleanup C:\keys-to-exclude.json C:\en-eComm.json`;

# Disclaimer

Of course, I know that there are a lot of things to fix & refactor, my main goal was just to create a working prototype 
as soon as possible.
