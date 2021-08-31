package com.gluonhq.elita;

import com.google.common.primitives.Bytes;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.whispersystems.websocket.messages.InvalidMessageException;
import org.whispersystems.websocket.messages.WebSocketMessage;
import org.whispersystems.websocket.messages.WebSocketMessageFactory;
import org.whispersystems.websocket.messages.WebSocketRequestMessage;
import org.whispersystems.websocket.messages.WebSocketResponseMessage;
import org.whispersystems.websocket.messages.protobuf.ProtobufWebSocketMessageFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@WebSocket(maxTextMessageSize = 64 * 1024)
public class WebSocketInterface {
// heavily inspired by https://github.com/signalapp/WebSocket-Resources/blob/master/sample-client/src/main/java/org/whispersystems/websocket/client/WebSocketInterface.java
    private final WebSocketMessageFactory factory = new ProtobufWebSocketMessageFactory();

    private Listener listener;
    private Session session;
    private final CountDownLatch latch;

    public WebSocketInterface() {
        this.latch = new CountDownLatch(1);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
        listener.attached(this);
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        listener.onClosed();
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        this.session = session;
        listener.onConnected();
        // we should be connected now, and listener is notified.
        this.latch.countDown();
    }
    
    /**
     * Block until this websocket is connected, or return after s seconds
     * @param s If this is a positive value, it indicates the maximum time we 
     * want to wait on an onConnect event. 
     * A value of `0` will use the default value, a negative value will wait forever
     * @throws java.lang.InterruptedException in case we are interrupted before 
     * connection happens or timeout expires.
     */
    public void waitUntilConnected(int s) throws InterruptedException {
        if (s < 0) {
            this.latch.await();
        } else {
            if (s == 0) {s = 10;}
            this.latch.await(s, TimeUnit.SECONDS);
        }
    }

    @OnWebSocketMessage
    public void onMessage(byte[] buffer, int offset, int length) {
        System.out.println("[JVDBG] onMessage");
        try {
            WebSocketMessage message = factory.parseMessage(buffer, offset, length);

            if (message.getType() == WebSocketMessage.Type.REQUEST_MESSAGE) {
                listener.onReceivedRequest(message.getRequestMessage());
            } else if (message.getType() == WebSocketMessage.Type.RESPONSE_MESSAGE) {
                listener.onReceivedResponse(message.getResponseMessage());
            } else {
                System.out.println("Received websocket message of unknown type: " + message.getType());
            }

        } catch (InvalidMessageException e) {
            e.printStackTrace();
        }
    }

    public void sendRequest(long id, String verb, String path) throws IOException {
        sendRequest (id, verb, path, new LinkedList<String>(), null);
    }

    public void sendRequest(long id, String verb, String path, List<String> headers, byte[] body) throws IOException {
        System.err.println("SendRequest, id = "+id+", verb = "+verb+", path = " +path+", headers = "+headers+", bodysize = "+(body == null ? 0 : body.length));
        WebSocketMessage message = 
                factory.createRequest(Optional.of(id), verb, path, headers, Optional.ofNullable(body));
        System.err.println("BYTES = "+Bytes.asList(message.toByteArray()));
        session.getRemote().sendBytes(ByteBuffer.wrap(message.toByteArray()));
    }

    public void sendResponse(long id, int code, String message, byte[] body) throws IOException {
        WebSocketMessage response = factory.createResponse(id, code, message, new LinkedList<String>(), Optional.ofNullable(body));
        session.getRemote().sendBytes(ByteBuffer.wrap(response.toByteArray()));
    }

    public interface Listener {

        public void attached(WebSocketInterface parent);
        
        public void onReceivedRequest(WebSocketRequestMessage requestMessage);

        public void onReceivedResponse(WebSocketResponseMessage responseMessage);

        public void onClosed();

        public void onConnected();
    }
}
