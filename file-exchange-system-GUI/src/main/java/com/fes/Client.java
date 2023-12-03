package com.fes;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Client {
    private static final String clientDir =  "ClientStorage/";

    // thread attributes
    private volatile boolean pauseThread = false;
    private final Object pauseLock = new Object();
    private BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();

    private String username = "";
    private javax.swing.JTextArea messageTA;
    private String response;

    private Socket serverSocket;
    private BufferedReader reader;
    private PrintWriter writer;
    
    public Client(String sServerAddress, int nPort) {
        try {
            this.serverSocket = new Socket(sServerAddress, nPort);
            this.reader = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
            this.writer = new PrintWriter(serverSocket.getOutputStream(), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void startMessagingThread() {
        // Start a separate thread to handle incoming messages from the server
        String regex = "^[a-zA-Z0-9() ]+: .*";
        Thread receiveThread = new Thread(() -> {
            while (!Thread.interrupted()) {
                response = readResponse();
                if (response == null) {
                    break; // exit the loop if the server closes the connection
                }

                if (!response.startsWith("Error:") && response.matches(regex)) {
                    messageTA.append("\n" + response + "\n");
                } else if (response.equals("END")) {
                    // Notify the waiting thread (if any) that a response is available
                    synchronized (messageQueue) {
                        messageQueue.notify();
                    }
                } else {
                    messageQueue.add(response);
                }

                // Check for the pause condition
                synchronized (pauseLock) {
                    while (pauseThread) {
                        try {
                            pauseLock.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }

            }
        });
        receiveThread.start();
    }

    public void broadcast(String message) {
        writer.println("/broadcast " + this.username + ": " + message);
    }

    public void unicast(String username, String message) {
        writer.println("/unicast " + username + " " + this.username + ": " + message);
    }

    public String login(String input) {
        writer.println("/login " + input);
        response = readResponse();
        if (response.contains("logged in")) {
            username = input;
        }
        System.out.println("\n" + response);
        return response;
    }

    public String register(String input) {
        try {
            writer.println("/register " + input);
            response = reader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("\n" + response);   

        return response;
    }

    public String leave() {
        try {
            // Close resources, perform cleanup, etc.
            serverSocket.close();
            System.out.println("\nConnection closed. Thank you!");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "\nConnection closed. Thank you!";
    }

    public String dir(String input) {
        String list = "\n---------------------------\n" +
        "     Server Directory:\n---------------------------";

        writer.println(input);

        System.out.println("\n---------------------------\n" +
            "     Server Directory:\n---------------------------");
        
        waitForResponse();

        while ((response = messageQueue.poll()) != null) {
            list += "\n" + response;
        }

        return list + "\n";
    }

    public String get(String input) {
        String fileName1 = input;
        
        pauseReceiveThread();

        try {
            writer.println("/get " + input);
            response = readResponse();

            if (response.startsWith("Error")) {
                System.out.println("\n" + response);
                return "\n" + response + "\n";
            } else {
                File receievedFile = new File(clientDir + username + "/" + fileName1);
                FileOutputStream fileOutputStream = new FileOutputStream(receievedFile);
                DataInputStream dataIn = new DataInputStream(serverSocket.getInputStream());
                
                // get file size
                long fileSize = dataIn.readLong();

                // Buffer for reading data
                byte[] buffer = new byte[1024];
                long totalBytesRead = 0;
                int bytesRead;

                // Read data from the server and save it to the file
                while (fileSize > totalBytesRead && (bytesRead = dataIn.read(buffer)) != -1) {
                    fileOutputStream.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                }

                System.out.println("\nFile receieved from server: " + fileName1);
                fileOutputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        messageQueue.clear();
        resumeReceiveThread();

        return "\nFile receieved from server: " + fileName1 + "\n";
    }

    public String store(String input) {
        String fileName = input;
        LocalDateTime timestamp = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        try {
            File fileToSend = new File(clientDir + username + "/" + fileName);
            DataOutputStream outputStream = new DataOutputStream(serverSocket.getOutputStream());
            long fileSize = fileToSend.length();
            // Check if the file exists
            if (!fileToSend.exists()) {
                System.out.println("Error: File not found.");
                return "\nError: File not found.\n";
            }
            
            // Send the file name to the server
            writer.println("/store " + fileSize + " " + input);

            // Create a FileInputStream to read the file
            FileInputStream fileInputStream = new FileInputStream(fileToSend);

            // Buffer for reading data
            byte[] buffer = new byte[1024];
            int bytesRead;

            // Read data from the file and send to the server
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            System.out.println("\n" + username + "<" + timestamp.format(formatter) + ">: Uploaded " + fileName);
            fileInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }  

        return "\n" + username + "<" + timestamp.format(formatter) + ">: Uploaded " + fileName + "\n";
    }

    public String showCommands() {
		return "\nDescription => InputSyntax\n" + "--------------------------------\n" +
                "Log in to the server => /login <username>\n" +
				"Connect to the server application => /join <server_ip_add> <port>\n" +
				"Disconnect to the server application => /leave\n" +
				"Register a unique handle or alias  => /register <handle>\n" +
				"Send file to server => /store <filename>\n" +
				"Request directory file list from a server => /dir\n" +
				"Fetch a file from a server => /get <filename>\n" +
				"Request command help to output all Input Syntax commands for references => /?\n";
	}

    private String readResponse() {
        try {
            return reader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    // Method to wait for a response to be added to the messageQueue
    private void waitForResponse() {
        synchronized (messageQueue) {
            try {
                // Wait for a response to be added to the messageQueue
                messageQueue.wait();
            } catch (InterruptedException e) {
                // Handle the InterruptedException
                e.printStackTrace();
            }
        }
    }

    public void pauseReceiveThread() {
        pauseThread = true;
    }

    public void resumeReceiveThread() {
        synchronized (pauseLock) {
            pauseThread = false;
            pauseLock.notify();
        }
    }

    public ArrayList<String> getConnectedUsers() {
        ArrayList<String> usernames = new ArrayList<>();

        writer.println("/getusernames " + this.username);

        waitForResponse();

        while ((response = messageQueue.poll()) != null) {
            usernames.add(response);
        }

        return usernames;
    }

    public String getUsername() {
        return this.username;
    }

    public Socket getServerSocket() {
        return this.serverSocket;
    }

    public void setMessageTA(javax.swing.JTextArea messageTA) {
        this.messageTA = messageTA;
    }
}
