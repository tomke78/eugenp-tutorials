package com.baeldung.jnats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.nats.client.Message;
import io.nats.client.Subscription;

/**
 * All the tests in this class require that a NATS server be running on localhost at the default port.
 * See {@link <a href="https://docs.nats.io/nats-server/installation">Installing a NATS Server</a>}.
 */
public class NatsClientLiveTest {

    public static final int TIMEOUT_MILLIS = 200;

    private NatsClient connectClient() throws IOException, InterruptedException {
        return new NatsClient(NatsClient.createConnection("nats://localhost:4222"));
    }

    @Test
    public void whenMessagesAreExchangedViaPublish_thenResponsesMustBeReceivedWithSecondarySubscription() throws Exception {
        try (NatsClient client = connectClient()) {
            Subscription replySideSubscription = client.subscribeSync("requestSubject");
            Subscription publishSideSubscription = client.subscribeSync("replyToSubject");
            client.publishMessageWithReply("requestSubject", "replyToSubject", "hello there");

            Message message = replySideSubscription.nextMessage(TIMEOUT_MILLIS);
            assertNotNull(message, "No message!");
            assertEquals("hello there", new String(message.getData()));
            client.publishMessage(message.getReplyTo(), "hello back");

            message = publishSideSubscription.nextMessage(TIMEOUT_MILLIS);
            assertNotNull(message, "No message!");
            assertEquals("hello back", new String(message.getData()));
        }
    }

    @Test
    public void whenMessagesAreExchangedViaRequest_thenResponsesMustBeReceivedDirectly() throws Exception {
        try (NatsClient client = connectClient()) {
            Subscription replySideSubscription = client.subscribeSync("requestSubject");

            CompletableFuture<Message> future = client.request("requestSubject", "hello there");

            Message message = replySideSubscription.nextMessage(TIMEOUT_MILLIS);
            assertNotNull(message, "No message!");
            assertEquals("hello there", new String(message.getData()));
            client.publishMessage(message.getReplyTo(), "hello back");

            message = future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertNotNull(message, "No message!");
            assertEquals("hello back", new String(message.getData()));
        }
    }

    @Test
    public void whenMatchingWildCardsAreUsedInSubscriptions_thenSubscriptionsMustReceiveAllMatchingMessages() throws Exception {
        try (NatsClient client = connectClient()) {
            Subscription fooStarSubscription = client.subscribeSync("foo.*");

            client.publishMessage("foo.star", "hello foo star");

            Message message = fooStarSubscription.nextMessage(TIMEOUT_MILLIS);
            assertNotNull(message, "No message!");
            assertEquals("hello foo star", new String(message.getData()));

            Subscription fooGreaterSubscription = client.subscribeSync("foo.>");
            client.publishMessage("foo.greater.than", "hello foo greater");

            message = fooGreaterSubscription.nextMessage(TIMEOUT_MILLIS);
            assertNotNull(message, "No message!");
            assertEquals("hello foo greater", new String(message.getData()));
        }
    }

    @Test
    public void whenNonMatchingWildCardsAreUsedInSubscriptions_thenSubscriptionsMustNotReceiveNonMatchingMessages() throws Exception {
        try (NatsClient client = connectClient()) {
            Subscription starSubscription = client.subscribeSync("foo.*");

            client.publishMessage("foo.bar.plop", "hello there");

            Message message = starSubscription.nextMessage(TIMEOUT_MILLIS);
            assertNull(message, "Got message!");

            Subscription greaterSubscription = client.subscribeSync("foo.>");
            client.publishMessage("foo.bar.plop", "hello there");

            message = greaterSubscription.nextMessage(TIMEOUT_MILLIS);
            assertNotNull(message, "No message!");
            assertEquals("hello there", new String(message.getData()));
        }
    }

    @Test
    public void whenSubscribingWithQueueGroups_thenOnlyOneSubscriberInTheGroupShouldReceiveEachMessage() throws Exception {
        try (NatsClient client = connectClient()) {
            Subscription qSub1 = client.subscribeSyncInQueueGroup("foo.bar.requests", "myQueue");
            Subscription qSub2 = client.subscribeSyncInQueueGroup("foo.bar.requests", "myQueue");

            client.publishMessage("foo.bar.requests", "foobar");

            List<Message> messages = new ArrayList<>();

            Message message = qSub2.nextMessage(TIMEOUT_MILLIS);
            if (message != null) {
                messages.add(message);
            }

            message = qSub1.nextMessage(TIMEOUT_MILLIS);
            if (message != null) {
                messages.add(message);
            }

            assertEquals(1, messages.size());
        }
    }
}
