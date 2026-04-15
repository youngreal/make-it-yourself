package org.example.makewithjava.consistenthashing.router;

import org.example.makewithjava.consistenthashing.hash.HashAlgorithm;
import org.example.makewithjava.consistenthashing.hash.Node;

import java.util.List;

public class SimpleHashRouter<T extends Node> implements HashRouter<T>{

    private final HashAlgorithm hashAlgorithm;
    private final List<T> nodes;

    public SimpleHashRouter(HashAlgorithm hashAlgorithm, List<T> nodes) {
        this.hashAlgorithm = hashAlgorithm;
        this.nodes = nodes;
    }

    public void addNode(T node){
        this.nodes.add(node);
    }

    public void removeNode(T node){
        this.nodes.remove(node);
    }

    @Override
    public T routeNode(String businessKey) {
        long hash = hashAlgorithm.hash(businessKey);
        int index = (int) Long.remainderUnsigned(hash, nodes.size());
        return nodes.get(index);
    }
}
