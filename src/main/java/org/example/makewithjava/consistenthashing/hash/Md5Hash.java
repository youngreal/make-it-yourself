package org.example.makewithjava.consistenthashing.hash;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Md5Hash implements HashAlgorithm{
    @Override
    public long hash(String key) {
        byte[] md5s;
        try {
            md5s = MessageDigest.getInstance("MD5").digest(key.getBytes());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        return ByteBuffer.wrap(md5s).getLong();
    }
}
