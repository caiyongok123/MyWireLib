/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.test;

import org.java_websocket.WebSocket;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.framing.Framedata;
import org.java_websocket.framing.FramedataImpl1;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class WebSocketEchoServer extends WebSocketServer {

    public static final int PORT = 8083;

    private boolean enabled = true;

    public WebSocketEchoServer() {
        this(PORT);
    }

    public WebSocketEchoServer(int port) {
        super(new InetSocketAddress(port));

        start();
    }

    @Override
    public void onOpen(org.java_websocket.WebSocket conn, ClientHandshake handshake) {
    }

    @Override
    public void onWebsocketPong(WebSocket conn, Framedata f) {
        super.onWebsocketPong(conn, f);
        String msg = new String(f.getPayloadData().array());
        System.out.println("got pong: " + msg);

        if (enabled) conn.send("pong: " + msg);
    }

    @Override
    public void onClose(org.java_websocket.WebSocket conn, int code, String reason, boolean remote) {
    }

    @Override
    public void onMessage(org.java_websocket.WebSocket conn, String message) {
        System.out.println("onMessage: " + message);
        if (message.equals("enable")) {
            enabled = true;
        } else if (message.equals("disable")) {
            enabled = false;
        } else if (message.equals("restart")) {
            restart(conn);
        }

        if (enabled) conn.send(message);

        if (message.startsWith("ping: ")) {
            sendPing(conn, message.substring("ping: ".length()));
        } else if (message.startsWith("closeAfter ")) {
            closeAfter(conn, Long.parseLong(message.substring("closeAfter ".length())));
        }
    }

    private void restart(WebSocket conn) {
        conn.close();
        try {
            stop();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        new WebSocketEchoServer();
    }

    private void sendPing(WebSocket conn, String msg) {
        System.out.println("sending ping: " + msg);
        FramedataImpl1 frame = new FramedataImpl1(Framedata.Opcode.PING);
        frame.setFin(true);
        try {
            frame.setPayload(ByteBuffer.wrap(msg.getBytes()));
        } catch (InvalidDataException e) {
            e.printStackTrace();
        }
        conn.sendFrame(frame);
    }

    private void closeAfter(final WebSocket conn, final long delayInMillis) {
        System.out.println("will close connection in " + delayInMillis + " milliseconds...");
        Thread closeLater = new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(delayInMillis);
                    System.out.println("closing connection.");
                    restart(conn);
                } catch(InterruptedException e) {
                    System.out.println("Interrupted!");
                    Thread.currentThread().interrupt();
                }
            }
        });
        closeLater.start();
    }

    @Override
    public void onMessage(org.java_websocket.WebSocket conn, ByteBuffer message) {
        byte[] bytes = new byte[message.limit()];
        message.get(bytes);
        System.out.println("onByteMessage: " + new String(bytes));

        if (enabled) conn.send(bytes);
    }

    @Override
    public void onError(org.java_websocket.WebSocket conn, Exception ex) {
        ex.printStackTrace(System.err);
    }

    public static void main(String[] args) {
        if (args.length == 0) new WebSocketEchoServer();
        else new WebSocketEchoServer(Integer.parseInt(args[0]));
    }
}
