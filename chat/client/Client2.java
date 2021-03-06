package chat.client;


import chat.Connection;
import chat.ConsoleHelper;
import chat.Message;
import chat.MessageType;

import java.io.IOException;
import java.net.Socket;

public class Client2 {
    protected Connection connection;
    private volatile boolean clientConnected;

    protected String getServerAddress() {
        ConsoleHelper.writeMessage("Enter server address:");
        return ConsoleHelper.readString();
    }

    protected int getServerPort() {
        ConsoleHelper.writeMessage("Enter server port:");
        return ConsoleHelper.readInt();
    }

    protected String getUserName() {
        ConsoleHelper.writeMessage("Enter your name:");
        return ConsoleHelper.readString();
    }

    public class SocketThread extends Thread {
        @Override
        public void run() {
            try {
                // Create a connection with the server
                connection = new Connection(new Socket(getServerAddress(), getServerPort()));

                clientHandshake();
                clientMainLoop();

            } catch (IOException | ClassNotFoundException e) {
                notifyConnectionStatusChanged(false);
            }
        }

        protected void clientHandshake() throws IOException, ClassNotFoundException {
            while (true) {
                Message message = connection.receive();

                if (message.getType() == MessageType.NAME_REQUEST) { // The server asked for the user's name
                    // Ask for the name to be entered from the console
                    String name = getUserName();
                    // Send the name to the server
                    connection.send(new Message(MessageType.USER_NAME, name));

                } else if (message.getType() == MessageType.NAME_ACCEPTED) { // The server accepted the user's name
                    // Inform the main thread that it can continue
                    notifyConnectionStatusChanged(true);
                    return;

                } else {
                    throw new IOException("Unexpected MessageType");
                }
            }
        }

        protected void clientMainLoop() throws IOException, ClassNotFoundException {
            // Loop for processing server messages
            while (true) {
                Message message = connection.receive();

                if (message.getType() == MessageType.TEXT) { // The server sent a message with text
                    processIncomingMessage(message.getData());
                } else if (MessageType.USER_ADDED == message.getType()) {
                    informAboutAddingNewUser(message.getData());
                } else if (MessageType.USER_REMOVED == message.getType()) {
                    informAboutDeletingNewUser(message.getData());
                } else {
                    throw new IOException("Unexpected MessageType");
                }
            }
        }

        protected void processIncomingMessage(String message) {
            // Display the text of the message on the console
            ConsoleHelper.writeMessage(message);
        }

        protected void informAboutAddingNewUser(String userName) {
            // Display a message that the participant has joined
            ConsoleHelper.writeMessage("Participant '" + userName + "' has joined the chat.");
        }

        protected void informAboutDeletingNewUser(String userName) {
            // Display a message that participant has left
            ConsoleHelper.writeMessage("Participant '" + userName + "' has left the chat.");
        }

        protected void notifyConnectionStatusChanged(boolean clientConnected) {
            Client2.this.clientConnected = clientConnected;
            synchronized (Client2.this) {
                Client2.this.notify();
            }
        }
    }

    protected SocketThread getSocketThread() {
        return new SocketThread();
    }

    protected void sendTextMessage(String text) {
        try {
            connection.send(new Message(MessageType.TEXT, text));
        } catch (IOException e) {
            ConsoleHelper.writeMessage("Unable to send message");
            clientConnected = false;
        }
    }

    protected boolean shouldSendTextFromConsole() {
        return true;
    }

    public void run() {
        SocketThread socketThread = getSocketThread();
        // Mark the thread as a daemon
        socketThread.setDaemon(true);
        socketThread.start();

        try {
            synchronized (this) {
                wait();
            }
        } catch (InterruptedException e) {
            ConsoleHelper.writeMessage("An error occurred while running the client.");
            return;
        }

        if (clientConnected)
            ConsoleHelper.writeMessage("A connection has been established. To exit, enter 'exit'.");
        else
            ConsoleHelper.writeMessage("An error occurred while running the client.");

        // Until the exit command is entered, read messages from the console and send them to the server
        while (clientConnected) {
            String text = ConsoleHelper.readString();
            if (text.equalsIgnoreCase("exit"))
                break;

            if (shouldSendTextFromConsole())
                sendTextMessage(text);
        }
    }

    public static void main(String[] args) {
        Client2 client = new Client2();
        client.run();
    }
}
