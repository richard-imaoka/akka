# Actor Systems

Actors are objects which encapsulate state and behavior, they communicate
exclusively by exchanging messages which are placed into the recipient’s
mailbox. In a sense, actors are the most stringent form of object-oriented
programming, but it serves better to view them as persons: while modeling a
solution with actors, envision a group of people and assign sub-tasks to them,
arrange their functions into an organizational structure and think about how to
escalate failure (all with the benefit of not actually dealing with people,
which means that we need not concern ourselves with their emotional state or
moral issues). The result can then serve as a mental scaffolding for building
the software implementation.

@@@ note

An ActorSystem is a heavyweight structure that will allocate 1…N Threads,
so create one per logical application.

@@@

## Hierarchical Structure

Like in an economic organization, actors naturally form hierarchies. One actor,
which is to oversee a certain function in the program might want to split up
its task into smaller, more manageable pieces. For this purpose it starts child
actors which it supervises. While the details of supervision are explained
@ref:[here](supervision.md), we shall concentrate on the underlying concepts in
this section. The only prerequisite is to know that each actor has exactly one
supervisor, which is the actor that created it.

The quintessential feature of actor systems is that tasks are split up and
delegated until they become small enough to be handled in one piece. In doing
so, not only is the task itself clearly structured, but the resulting actors
can be reasoned about in terms of which messages they should process, how they
should react normally and how failure should be handled. If one actor does not
have the means for dealing with a certain situation, it sends a corresponding
failure message to its supervisor, asking for help. The recursive structure
then allows to handle failure at the right level.

Compare this to layered software design which easily devolves into defensive
programming with the aim of not leaking any failure out: if the problem is
communicated to the right person, a better solution can be found than if
trying to keep everything “under the carpet”.

Now, the difficulty in designing such a system is how to decide who should
supervise what. There is of course no single best solution, but there are a few
guidelines which might be helpful:

 * If one actor manages the work another actor is doing, e.g. by passing on
sub-tasks, then the manager should supervise the child. The reason is that
the manager knows which kind of failures are expected and how to handle
them.
 * If one actor carries very important data (i.e. its state shall not be lost
if avoidable), this actor should source out any possibly dangerous sub-tasks
to children it supervises and handle failures of these children as
appropriate. Depending on the nature of the requests, it may be best to
create a new child for each request, which simplifies state management for
collecting the replies. This is known as the “Error Kernel Pattern” from
Erlang.
 * If one actor depends on another actor for carrying out its duty, it should
watch that other actor’s liveness and act upon receiving a termination
notice. This is different from supervision, as the watching party has no
influence on the supervisor strategy, and it should be noted that a
functional dependency alone is not a criterion for deciding where to place a
certain child actor in the hierarchy.

There are of course always exceptions to these rules, but no matter whether you
follow the rules or break them, you should always have a reason.

## Configuration Container

The actor system as a collaborating ensemble of actors is the natural unit for
managing shared facilities like scheduling services, configuration, logging,
etc. Several actor systems with different configuration may co-exist within the
same JVM without problems, there is no global shared state within Akka itself.
Couple this with the transparent communication between actor systems—within one
node or across a network connection—to see that actor systems themselves can be
used as building blocks in a functional hierarchy.

## Actor Best Practices

 1. Actors should be like nice co-workers: do their job efficiently without
bothering everyone else needlessly and avoid hogging resources. Translated
to programming this means to process events and generate responses (or more
requests) in an event-driven manner. Actors should not block (i.e. passively
wait while occupying a Thread) on some external entity—which might be a
lock, a network socket, etc.—unless it is unavoidable; in the latter case
see below.
 2. Do not pass mutable objects between actors. In order to ensure that, prefer
immutable messages. If the encapsulation of actors is broken by exposing
their mutable state to the outside, you are back in normal Java concurrency
land with all the drawbacks.
 3. Actors are made to be containers for behavior and state, embracing this
means to not routinely send behavior within messages (which may be tempting
using Scala closures). One of the risks is to accidentally share mutable
state between actors, and this violation of the actor model unfortunately
breaks all the properties which make programming in actors such a nice
experience.
 4. Top-level actors are the innermost part of your Error Kernel, so create them
sparingly and prefer truly hierarchical systems. This has benefits with
respect to fault-handling (both considering the granularity of configuration
and the performance) and it also reduces the strain on the guardian actor,
which is a single point of contention if over-used.

## Blocking Needs Careful Management

In some cases it is unavoidable to do blocking operations, i.e. to put a thread
to sleep for an indeterminate time, waiting for an external event to occur.
Examples are legacy RDBMS drivers or messaging APIs, and the underlying reason
is typically that (network) I/O occurs under the covers. 

@@snip [BlockingDispatcherSample.scala](../../../../test/scala/docs/actor/BlockingDispatcherSample.scala) { #blocking-in-actor }

When facing this, you
may be tempted to just wrap the blocking call inside a `Future` and work
with that instead, but this strategy is too simple: you are quite likely to
find bottlenecks or run out of memory or threads when the application runs
under increased load.

@@snip [BlockingDispatcherSample.scalaa](../../../../test/scala/docs/actor/BlockingDispatcherSample.scala) { #blocking-in-future }


### Problem

The key here is this line:

```scala
implicit val executionContext: ExecutionContext = context.dispatcher
```

Using `context.dispatcher` as the dispatcher on which the blocking Future
executes can be a problem - the same dispatcher is used for actor's processing,
unless you @ref:[set up a separate dispatcher for the actor](../dispatchers.md).

If all of the available threads are blocked, then all the actors on the same dispatchers will starve for threads and
they cannot process any more incoming message.

@@@ note

Blocking APIs should also be avoided if possible. Try to find or build Reactive APIs,
such that blocking is minimised, or moved over to dedicated dispatchers.

Often when integrating with existing libraries or systems it is not possible to
avoid blocking APIs. The following solution explains how to handle blocking
operations properly.

Note that the same hints apply to managing blocking operations anywhere in Akka,
including in Actors etc.

@@@

### Problem example: blocking the default dispatcher

Let's set up an application with the above `BlockingFutureActor` and the following `PrintActor`. 

@@snip [BlockingDispatcherSample.scala](../../../../test/scala/docs/actor/BlockingDispatcherSample.scala) { #print-actor }

@@snip [BlockingDispatcherSample.scala](../../../../test/scala/docs/actor/BlockingDispatcherSample.scala) { #blocking-main }

Here the app is sending 100 messages to `BlockingFutureActor` and `PrintActor` and large numbers
of akka.actor.default-dispatcher threads are handling requests. When you run run the above code,
you will likely to see the entire application gets stuck somewhere like this:

```
>　PrintActor: 44
>　PrintActor: 45
```

(i.e.) (Ignoring `println`'s latency) `PrintActor` is non-blocking but it cannot process all the 100 messages asap.

In the thread state diagrams below the colours have the following meaning:

 * Turquoise - Sleeping state
 * Orange - Waiting state
 * Green - Runnable state

The thread information was recorded using the YourKit profiler, however any good JVM profiler 
has this feature (including the free and bundled with the Oracle JDK VisualVM, as well as Oracle Flight Recorder). 

The orange portion of the thread shows that it is idle. Idle threads are fine -
they're ready to accept new work. However, large amounts of Turquoise (sleeping) threads are very bad!

![dispatcher-behaviour-on-good-code.png](../../images/dispatcher-behaviour-on-bad-code.png)

The app is exposed to the load of `i: Int` messages which will block these threads. 
For example "`default-akka.default-dispatcher2,3,4`"
are going into the blocking state, after having been idle. It can be observed
that the number of new threads increases, "`default-akka.actor.default-dispatcher 18,19,20,...`" 
however they go to sleep state immediately, thus wasting the
resources.

The number of such new threads depends on the default dispatcher configuration,
but it will likely not exceed 50. Since many requests are being processed, the entire
thread pool is starved. The blocking operations dominate threads in the dispatcher 
such that it has no thread available to handle other requests.

In essence, the `Thread.sleep` operation has dominated all threads and caused anything 
executing on the default dispatcher to starve for resources (including any actor
that you have not configured an explicit dispatcher for).

## Solution: Dedicated dispatcher for blocking operations

In `application.conf`, the dispatcher dedicated to blocking behaviour should
be configured as follows:

```conf
my-blocking-dispatcher {
  type = Dispatcher
  executor = "thread-pool-executor"
  thread-pool-executor {
    // or in Akka 2.4.2+
    fixed-pool-size = 16
  }
  throughput = 100
}
```

There are many dispatcher options available which can be found in @ref[Dispatchers](../dispatchers.md).

Here `thread-pool-executor` is used, which has a hardcoded limit of threads. It keeps a set number of threads
available that allow for safe isolation of the blocking operations. The size settings should depend on the app's
functionality and the number of cores the server has.

Whenever blocking has to be done, use the above configured dispatcher
instead of the default one:

@@snip [BlockingDispatcherSample.scala](../../../../test/scala/docs/actor/BlockingDispatcherSample.scala) { #separate-dispatcher }

The thread pool behaviour is shown in the figure.

![dispatcher-behaviour-on-good-code.png](../../images/dispatcher-behaviour-on-good-code.png)

the normal requests are easily handled by the default dispatcher - the
green lines, which represent the actual execution.

When blocking operations are issued, the `my-blocking-dispatcher`
starts up to the number of configured threads. It handles sleeping. After
a certain period of nothing happening to the threads, it shuts them down.

If another bunch of operations have to be done, the pool will start new
threads that will take care of putting them into sleep state, but the
threads are not wasted.

In this case, the throughput of other actors was not impacted -
they were still served on the default dispatcher.

This is the recommended way of dealing with any kind of blocking in reactive
applications. It is referred to as "bulkheading" or "isolating" the bad behaving
parts of an app. In this case, bad behaviour of blocking operations.

## Available solutions to blocking operations

The non-exhaustive list of adequate solutions to the “blocking problem”
includes the following suggestions, where the 3rd one was what we described in the previous section:

 * Do the blocking call within an actor (or a set of actors managed by a router
@ref:[router](../routing.md),  making sure to
configure a thread pool which is either dedicated for this purpose or
sufficiently sized.
 * Do the blocking call within a `Future`, ensuring an upper bound on
the number of such calls at any point in time (submitting an unbounded
number of tasks of this nature will exhaust your memory or thread limits).
 * Do the blocking call within a `Future`, providing a thread pool with
an upper limit on the number of threads which is appropriate for the
hardware on which the application runs.
 * Dedicate a single thread to manage a set of blocking resources (e.g. a NIO
selector driving multiple channels) and dispatch events as they occur as
actor messages.

The first possibility is especially well-suited for resources which are
single-threaded in nature, like database handles which traditionally can only
execute one outstanding query at a time and use internal synchronization to
ensure this. A common pattern is to create a router for N actors, each of which
wraps a single DB connection and handles queries as sent to the router. The
number N must then be tuned for maximum throughput, which will vary depending
on which DBMS is deployed on what hardware.

@@@ note

Configuring thread pools is a task best delegated to Akka, simply configure
in the `application.conf` and instantiate through an
@ref:[`ActorSystem`](../dispatchers.md#dispatcher-lookup)

@@@

## What you should not concern yourself with

An actor system manages the resources it is configured to use in order to run
the actors which it contains. There may be millions of actors within one such
system, after all the mantra is to view them as abundant and they weigh in at
an overhead of only roughly 300 bytes per instance. Naturally, the exact order
in which messages are processed in large systems is not controllable by the
application author, but this is also not intended. Take a step back and relax
while Akka does the heavy lifting under the hood.

## Terminating ActorSystem

When you know everything is done for your application, you can call the
`terminate` method of `ActorSystem`. That will stop the guardian
actor, which in turn will recursively stop all its child actors, the system
guardian.

If you want to execute some operations while terminating `ActorSystem`,
look at @ref:[`CoordinatedShutdown`](../actors.md#coordinated-shutdown).
