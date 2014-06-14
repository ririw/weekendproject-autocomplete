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
