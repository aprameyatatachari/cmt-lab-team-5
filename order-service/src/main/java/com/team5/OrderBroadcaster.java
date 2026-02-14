package com.team5;

import com.google.gson.Gson;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;

/**
 * WebSocket server for broadcasting order updates to connected UI clients.
 * Listens on port 8080 and pushes real-time order data as JSON.
 */
public class OrderBroadcaster extends WebSocketServer {
    private final Gson gson = new Gson();

    public OrderBroadcaster(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("UI Connected: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        // We don't expect messages from the UI in this lab
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("UI Disconnected: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("WebSocket error: " + ex.getMessage());
    }

    @Override
    public void onStart() {
        System.out.println("WebSocket Server started on port " + getPort());
    }

    /**
     * Broadcast order to all connected UI clients
     */
    public void broadcastOrder(Order order) {
        String json = gson.toJson(order);
        broadcast(json);
        System.out.println("Broadcasted order to UI: " + order.getClOrdID());
    }
}
