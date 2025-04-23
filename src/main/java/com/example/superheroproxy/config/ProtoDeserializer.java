package com.example.superheroproxy.config;

import org.apache.kafka.common.serialization.Deserializer;
import com.google.protobuf.Message;
import com.google.protobuf.InvalidProtocolBufferException;
import com.example.superheroproxy.proto.HeroUpdate;

public class ProtoDeserializer implements Deserializer<Message> {
    @Override
    public Message deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            return HeroUpdate.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException("Error deserializing Protocol Buffer message", e);
        }
    }
} 