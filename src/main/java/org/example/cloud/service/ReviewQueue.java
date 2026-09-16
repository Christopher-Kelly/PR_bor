package org.example.cloud.service;

import com.google.cloud.pubsub.v1.Publisher;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import com.google.protobuf.ByteString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
@Service
public class ReviewQueue {
    private final String projectId, topic;
    private Publisher publisher;

    public ReviewQueue(@Value("${gcp.project-id}") String projectId,
                       @Value("${pubsub.topic:pr-reviews}") String topic) {
        this.projectId = projectId;
        this.topic = topic;
    }

    private synchronized Publisher publisher() throws IOException {
        if (publisher == null) {
            publisher = Publisher.newBuilder(TopicName.of(projectId, topic)).build();
        }
        return publisher;
    }

    public void publish(String payload) throws Exception {
        PubsubMessage msg = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8(payload)).build();
        publisher().publish(msg).get();
    }
}