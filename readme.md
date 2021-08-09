# What is it?

This is a super-simple single-class JSON analyzer tool, that I've used for one of my tasks at work.

# What can it do?
* **sort and align keys** from the source JSON file;
* **remove all the keys** listed in the specified JSON file from the other JSON files passed as an arguments;
* **find unique entries** in both of the 2 files, like disjunctive union (or symmetric difference, as you wish);
* **merge** JSON files into one, with alphabetically-sorted unique keys;
* **find inclusions** of the keys from the source JSON file in the other named key sets (JSON files)

# How to build & run it?
* clone this repo;
* execute `mvn package` in the project home directory (requires maven and JDK 8);
  * *or download the latest stable build from the [Releases](https://github.com/spanic/JSON-analyzer/releases) page*
* run as `java -jar JsonAnalyzer-1.X.jar` with one of the following parameters:
  * for **sorting and aligning**: `-sort C:\source.json`;
  * for **finding uniques**: `-compare C:\first.json C:\second.json`;
  * for **cleanup**: `-cleanup C:\keys-to-exclude.json C:\first.json C:\second.json ...`;
  * for **merging**: `-merge C:\first.json C:\second.json ...`
  * for **finding inclusions**: `-find C:\source.json first=C:\first.json second=C:\second.json ...`

# Disclaimer

Of course, I know that there are a lot of things to fix & refactor, but this is just a dev. tool, my main goal was just to create a working prototype 
as soon as possible.

