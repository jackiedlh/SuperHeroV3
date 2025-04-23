package com.example.superheroproxy.config;

import org.apache.kafka.common.serialization.Serializer;
import com.google.protobuf.Message;

public class ProtoSerializer implements Serializer<Message> {
    @Override
    public byte[] serialize(String topic, Message data) {
        if (data == null) {
            return null;
        }
        return data.toByteArray();
    }
} 