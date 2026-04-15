package org.example.makewithjava.consistenthashing.hash;

public class ServerNode implements Node{
    private final String name;

    public ServerNode(String name) {
        this.name = name;
    }

    @Override
    public String getKey() {
        return this.name;
    }
}
