package com.fes;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileExchangeServer {
    private static Map<String, Socket> user_socket = new HashMap<>();
    private static List<Socket> clientSockets = new ArrayList<>();
    private static int connectedClients = 0;
    private static final String serverDir =  "../../../../../ServerStorage";
    private static final String clientDir =  "../../../../../ClientStorage/";
    private static final String listUsers =  "../../../../../users.txt";
    private static boolean serverRunning = true;
    
    public static void main(String[] args) {
        int nPort = Integer.parseInt(args[0]);
        ServerSocket serverSocket;

        // load past users to registered users
        File userstxt = new File(listUsers);

        // Check if the file exists
        if (!userstxt.exists()) {
            try {
                userstxt.createNewFile();
                System.out.println("File created: " + userstxt.getAbsolutePath() + "\n");
            } catch (IOException e) {
                System.err.println("Error creating file: " + e.getMessage());
                return; // or handle the case accordingly
            }
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(listUsers))) {
            String user;

            // Read lines from the file
            while ((user = reader.readLine()) != null) {
                user_socket.put(user, null);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
		
		try {
            serverSocket = new ServerSocket(nPort);

            while (serverRunning) {
                System.out.println("Server: Listening on port " + args[0] + "...\n");
                
                Socket clientSocket = serverSocket.accept();
                clientSockets.add(clientSocket);
                connectedClients++;

                System.out.println("Server: New client connected: " + clientSocket.getRemoteSocketAddress());
                System.out.println();

                Thread clientThread = new Thread(() -> {
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                        PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
                        DataOutputStream dataOut = new DataOutputStream(clientSocket.getOutputStream());
                        DataInputStream dataIn = new DataInputStream(clientSocket.getInputStream());

                        String command;
                        while ((command = reader.readLine()) != null) {
                            // Broadcast the message to all other clients (excluding the sender)
                            processCommand(command, dataIn, dataOut, writer, clientSocket);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            clientSocket.close();
                            clientSockets.remove(clientSocket);
                            connectedClients--;

                            if (connectedClients == 0) {
                                try (BufferedWriter usersWriter = new BufferedWriter(new FileWriter(listUsers))) {
                                    for (String user : user_socket.keySet()) {
                                        usersWriter.write(user);
                                        usersWriter.newLine();
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                                // Close the server after all clients have disconnected
                                serverSocket.close();

                                serverRunning = false;
                                System.out.println("Server: Shutting down...");
                            } else {
                                System.out.println("Server: Client disconnected.\n");
                            }
                        } catch (IOException e) {
                            e.printStackTrace();   
                        }
                    }
                });
                clientThread.start();
            }
        } catch (SocketException e) {
            // Ignore SocketException when the server socket is closed intentionally
        } catch (IOException e) {
            e.printStackTrace();
            // System.out.println("\nServer closed.");
        }
    }

    private static void processCommand(String command, DataInputStream dataIn, DataOutputStream dataOut, PrintWriter writer, Socket socket) throws IOException {
        // Add your logic to process the command and return the result
        System.out.println("Server: Received command - " + command + "\n");
        String[] tokens = command.split(" ");
        String cmd = tokens[0];

        switch (cmd.toLowerCase()) {
            case "/getusernames":
                for (String user : user_socket.keySet()) {
                    if (!user.equals(tokens[1]) && user_socket.get(user) != null) {
                        writer.println(user);
                    }
                }
                writer.println("END");
                break;
            case "/broadcast":
                broadcastMessage(command.split("\\s", 2)[1]);
                break;
            case "/unicast":
                String[] inputs = command.split("\\s", 3);
                unicastMessage(inputs[1], inputs[2]);
                break;
            case "/login":
                if (user_socket.containsKey(tokens[1])) {
                    writer.println("Welcome " + tokens[1] + " you are logged in!");
                    user_socket.put(tokens[1], socket);
                } else {
                    writer.println("Username: " + tokens[1] + " do not exist. Register first, usage: /register <handle>");
                }
                break;
            case "/register":
                if (user_socket.containsKey(tokens[1])) {
                    writer.println("Error: Registration failed. Handle or alias already exists.");
                } else {
                    user_socket.put(tokens[1], null);
                    new File(clientDir + tokens[1]).mkdir();
                    writer.println("Welcome " + tokens[1] + "!"); 
                }
                break;
            case "/store":
                storeFile(dataIn, Long.parseLong(tokens[1]), tokens[2]);
                break;
            case "/get":
                if (new File(serverDir + "/" + tokens[1]).exists()) {
                    writer.println();
                    writer.println("File fetched successfully");
                    getFile(dataOut, tokens[1]);
                } else {
                    writer.println("Error: File not found in the server.");
                }
                break;
            case "/dir":
                listFiles(writer);
                break;   
        }
    }

    // broadcast message
    private static void broadcastMessage(String message) {
        for (Socket clientSocket : clientSockets) {
            try {
                // add if kung di kasama yung sender
                PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
                writer.println("(Broadcast) " + message);
            } catch (IOException e) {
                // Handle exceptions, e.g., remove the disconnected client from the list
                e.printStackTrace();
            }
        }
    }

    public static void unicastMessage(String targetUsername, String message) {
        // Get the target socket based on the target username
        Socket targetSocket = user_socket.get(targetUsername);

        try {
            // Create a PrintWriter for the target socket
            PrintWriter targetWriter = new PrintWriter(targetSocket.getOutputStream(), true);

            // Send the unicast message
            targetWriter.println(message);
        } catch (IOException e) {
            // Handle the IOException
            e.printStackTrace();
        }
    }

    // store file to the server
    private static void storeFile(DataInputStream clientData, long fileSize, String filename) throws IOException {
        File file = new File(serverDir + "/" + filename); // Save in a server_storage directory
        FileOutputStream fileOutputStream = new FileOutputStream(file);
        byte[] buffer = new byte[1024];
        int bytesRead;
        long totalBytesRead = 0;

        while (fileSize > totalBytesRead && (bytesRead = clientData.read(buffer)) != -1) {
            fileOutputStream.write(buffer, 0, bytesRead);
            totalBytesRead += bytesRead;
        }
        fileOutputStream.close();
    }

    // get file from the server
    private static void getFile(DataOutputStream clientData, String filename) throws IOException {
        File file = new File(serverDir + "/" + filename);
        FileInputStream fileInputStream = new FileInputStream(file);
        Long fileSize = file.length();

        clientData.writeLong(fileSize);
        clientData.flush();

        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = fileInputStream.read(buffer)) != -1) {
            clientData.write(buffer, 0, bytesRead);
        }

        System.out.println("Server: File sent successfully\n");
        fileInputStream.close();
    }

    // list files of the server dir
    private static void listFiles(PrintWriter clientOut) {
        File folder = new File(serverDir);
        File[] listOfFiles = folder.listFiles();
        for (File file : listOfFiles) {
            if (file.isFile()) {
                clientOut.println(file.getName());
            }
        }
        clientOut.println("END"); // Indicates the end of the file list
    }
}

