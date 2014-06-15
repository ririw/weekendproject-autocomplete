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
  * Edit: It turns out that the in-memory version, accessible at aa00f9518967198c1a9997fbadec2309ba5fdc8b, can work
    with 16GB of memory quite happily! So I'm changing this reqirement from 4GB to 16GB.
    This runs at a comfortable 399774.73 queries per second on my machine (which has 16GB ram).

## Algorithm
 There will actually be two algorithms. The first is a prefix one, which suggests the next most probably suggestions given a
string. The second kicks in when the first has no suggestions, and suggests words based on the existing bag of words in the
search. By removing the ordering requirement, the second should be able to handle a lot of possibilities, while the first 
should be a lot more helpful, because it'll suggest very salient things. I think.

# Bug Log
 This is a log of all the mistakes I made along the way. They've settled on a format:
 
    <Date> - <Type> - <Git Hash where bug was last seen>: <Explanation>
 
 The types are:
  
  * D - design mistake
  * U - Unexpected situation
  * O - Optimization needed
  * L - Language error
  
### The list!

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
  * Sat Jun 14 17:55:28 EST 2014 - D - c691600a732f1743b03b29d7db0ab4fe13d91049: I had the searches in the AOLSearchSource
    object as values, when they're probably better as functions that produce new values. This is so that the programmer
    can use the close() method in a "better" way.
  * Sat Jun 14 19:59:06 EST 2014 - D - bef5e1646bd4de32a57a48011bb8039e81e43d8d: I forgot to make my queries a separate
    class, instead keeping them as bare Array[String]s. This might have lead to bugs down the line.
  * Sat Jun 14 19:59:06 EST 2014 - D - d65e0ffcb9d7b68f474a9a09b921fe543613013b: A large refactoring of the BucComputation
    class that splits it into two cases. The first is for when the DataSet builds refinements in a way where it
    counts up the items anyway, in which case the implementor may only return refinements with above or equal to minSup.
    The other case is for when the implementor does not need to do this, and may return the refinements in any order
    they choose. This took a couple of refactorings, it was still broken in 976ca61843a4a160f646eec666e028de351642d7
    and a198c872df222e54ac993e59a22c83f0aeb9df2e, but they are different approaches to the problem
  * Sat Jun 14 21:39:16 EST 2014 - U - 079f3847a4e32c46e6d920fcc58649285da92915: I just noticed, while documenting
    my code, that my filter method had a mistake. It previously worked by zipping the query array and the search
    array, then checking all of the strings were the same. The problem with this is that if the search is shorter 
    than the query, it could slip through. Eg:
        
        Query:  Build a website
        Search: Build a
        Zipped: (Build, Build), (a, a)
    
    The solution is simple: just make the search is at least as long as the query.
  * Sun Jun 15 12:01:17 EST 2014 - U - 9204accc628afcc9739c3ad306c3ec5b012ad8d6: I didn't realize that input streams 
    were so thoroughly un-resettable, so I discovered I couldn't iterate over the data set more than once. This was
    not picked up in earlier tests because they never checked for things twice. I've updated the tests and now I
    use a function yielding org.apache.commons.io.input.AutoCloseInputStream as the argument to AOLSearchSource, 
    which simplifies the AOLSearchSource and also solves a bunch of these problems. Although it still isn't quite 
    satisfactory, because it requires the user to fully traverse an iterator before closing it, which still may
    cause file pointer leaks
  * Sun Jun 15 15:38:26 EST 2014 - O - 96a852e61184ee63c8ae260996e3bd1624d9ec2f: As I suspected before, the sequential 
    scan approach is too slow. Instead I've created a CountingTrie implementation that may be fast enough. It counts
    things by their prefixes. But the downside of this is that it may blow out the memory when it is used with the 
    much larger production dataset.
  * Sun Jun 15 18:36:04 EST 2014 - U - aa00f9518967198c1a9997fbadec2309ba5fdc8b: In the previous BUC trie implementation
    the user could not request the count of an empty key. This was not covered in testing because it seemed like
    a silly situation. There are three ways I could have caught this earlier
        
    1. Enforce that they key not be empty with the type system, then I would have found my error
    2. Write a test that checks for empty keys, which might have made me think more about them
    3. The best option would have been to not write the bug in the first place, and have realized
       that this would need a count later on.
       
  * Sun Jun 15 18:58:54 EST 2014 - L - aa00f9518967198c1a9997fbadec2309ba5fdc8b: All this time I've been using Arrays
    to represent searches. It turns out that Scala won't do a structural comparison on an Array, only object equality.
    This meant that *none* of my in autocomplete.buc.BucComputation.apply would work, because they'd never do a 
    structural comparison. The fix was simple - just replace the Array in SearchSourceQuery with a List, which does
    do a structural compare (and also matches the TrieCounter, and also perhaps better matches the general structure
    of the problem). Again, java vs scala and identity vs structural comparison was a problem :(

# Discoveries
## Memory use profiling
Is the best. Until commit a4cb562abb6ece54c2b49abaaba3f1548a08e150, I was using a priority queue to queue queries.
This was ok, but it meant creating huge numbers of scala.math.Ordering.Ops. By switching to a normal Queue, and doing
away with the ordering, I cut down quite noticably on GC time.
    

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
