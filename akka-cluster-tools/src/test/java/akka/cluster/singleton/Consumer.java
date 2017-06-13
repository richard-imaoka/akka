/**
 * Copyright (C) 2009-2017 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.cluster.singleton;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.cluster.singleton.TestSingletonMessages.*;

public class Consumer extends AbstractActor {

  private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

  ActorRef queue;
  ActorRef delegateTo;
  int      current = 0;
  boolean  stoppedBeforeUnregistration = true;

  public Consumer(ActorRef _queue, ActorRef _delegateTo) {
    queue = _queue;
    delegateTo = _delegateTo;
  }

  @Override
  public void preStart() {
    //queue.tell(TestSingletonMessages.registerConsumer(), getSelf());
  }

  @Override
  public void postStop() {
    if (stoppedBeforeUnregistration)
      log.warning("Stopped before unregistration");
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
      .match(Integer.class, n -> {
        if(n <= current)
          getContext().stop(self());
        else {
          current = n;
          delegateTo.tell(n, getSelf());
        }
      })
      .match(RegistrationOk.class, x ->
        delegateTo.tell(x, getSelf())
      )
      .match(UnexpectedRegistration.class, x ->
        delegateTo.tell(x, getSelf())
      )
      .match(GetCurrent.class, x ->
        getSender().tell(current, getSelf())
      )
      //#consumer-end
      .match(End.class, x ->
        queue.tell(UnregisterConsumer.class, getSelf())
      )
      .match(UnregistrationOk.class, x -> {
          stoppedBeforeUnregistration = false;
          getContext().stop(getSelf());
        }
      )
      .match(Ping.class, x ->
          getSender().tell(TestSingletonMessages.pong(), getSelf())
      )
      //#consumer-end
      .build();
  }
}