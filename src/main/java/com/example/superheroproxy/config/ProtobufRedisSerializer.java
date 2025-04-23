package com.example.superheroproxy.config;

import com.example.superheroproxy.proto.Hero;
import com.google.protobuf.Message;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class ProtobufRedisSerializer implements RedisSerializer<Object> {

    @Override
    public byte[] serialize(Object value) throws SerializationException {
        if (value == null) {
            return new byte[0];
        }

        try {
            if (value instanceof Message) {
                return new CacheValueWrapper(value.getClass().getName(), ((Message) value).toByteArray()).toByteArray();
            } else {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeObject(value);
                return bos.toByteArray();
            }
        } catch (Exception e) {
            throw new SerializationException("Could not serialize value", e);
        }
    }

    @Override
    public Object deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(bis);
            Object obj = ois.readObject();

            if (obj instanceof CacheValueWrapper) {
                CacheValueWrapper wrapper = (CacheValueWrapper) obj;
                if (wrapper.getType().equals(Hero.class.getName())) {
                    return Hero.parseFrom(wrapper.getData());
                }
                // Add more type handling here if needed
            }
            return obj;
        } catch (Exception e) {
            throw new SerializationException("Could not deserialize value", e);
        }
    }
} 