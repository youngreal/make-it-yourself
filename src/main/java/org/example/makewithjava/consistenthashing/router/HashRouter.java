package org.example.makewithjava.consistenthashing.router;

import org.example.makewithjava.consistenthashing.hash.Node;

public interface HashRouter<T extends Node> {
    T routeNode(String businessKey);
}
