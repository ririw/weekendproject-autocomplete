# Search engine style autocomplete.   
## Requirements

 * Only take a weekend or two's work.
 * Fast
 * Websockety
 * Accurate
 * Big data
 * Quality code
 * Tests

## Actual requirements
 * A text field where the user may type and receive suggestions
 * The suggestions should be based on the AOL search dataset.
 * That dataset is not included, because licencing and whatever
 * The suggestions should be nice and quick. One server should be able to (ideally) handle about 1K requests per second
 * The suggestions should run with only 4GB of ram, so we need to be careful not to include too many.

## Algorithm
 There will actually be two algorithms. The first is a prefix one, which suggests the next most probably suggestions given a
string. The second kicks in when the first has no suggestions, and suggests words based on the existing bag of words in the
search. By removing the ordering requirement, the second should be able to handle a lot of possibilities, while the first 
should be a lot more helpful, because it'll suggest very salient things. I think.

## Bug Log
  * Sat Jun 14 16:15:25 EST 2014 - misunderstood the string split method. I split on the '\t' character, but this can
    fail, when a string such as "a\t\b\t\t" comes along, it'll split to ["a", "b"] rather than ["a", "b", ""], which
    meant that my parsing code wouldn't work in some cases. My solution was twofold. Firstly, rather than condition on
    exactly 5 fields, I conditioned on more than two. Secondly, rather than return an Option, I return Either, so I
    may track failures when they happen. 
  * Sat Jun 14 17:43:53 EST 2014 - I originally had a single string for a search. I changed this to an Array[String],
    because that makes way more sense when it comes to querying. Not sure how I could have anticipated this one though...
  * Sat Jun 14 17:43:53 EST 2014 - Not quite a bug, but I'm worried about my current version (git sha version: 
    afd1a9d1075a30266cfbd0b918dba63f188b21f2) of the SearchSourceDataSet. It uses a sequential scan for every query.
    This needs updating to use some sort of index. But the problem is that if I use a prefix index or something, but 
    then I may need BUC for that as well!
    

# Acknowledgements
This was based on the spray easter eggs template from typesafe activator. That shit is like magic.
## Spray Easter Eggs Project

This project provides a starting point for your own _spray-routing_
and _web sockets_ endeavours.

Follow these steps to get started:

1. Git-clone this repository.

        $ git clone git://github.com/cuali/SprayEasterEggs.git my-project

2. Change directory into your clone:

        $ cd my-project

3. Launch SBT:

        $ sbt

4. Compile everything and run all tests:

        > test

5. Start the application:

        > re-start

6. Browse to http://localhost:9692/
6a. Call your friends or colleagues to access your server.
6b. Open more tabs in your browser to http://localhost:9692/hide

7. Stop the application:

        > re-stop

8. Learn more at http://www.spray.io/

9. Start hacking on `src/main/scala/cua/li/reactive/ReactiveSystem.scala`
