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
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class FileExchangeServer {
    private static List<Socket> clientSockets = new ArrayList<>();
    private static List<String> registeredUsers = new ArrayList<>();
    private static int connectedClients = 0;
    private static String serverDir =  "../../../../../ServerStorage";
    
    public static void main(String[] args) {
        int nPort = Integer.parseInt(args[0]);
        ServerSocket serverSocket;
		
		try
		{
			serverSocket = new ServerSocket(nPort);

            while (true) {
                System.out.println("Server: Listening on port " + args[0] + "...");
                System.out.println();
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
                            System.out.println(command);
                            // Broadcast the message to all other clients (excluding the sender)
                            String result = processCommand(command, dataIn, dataOut, writer);
                            writer.println(result);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            clientSocket.close();
                            clientSockets.remove(clientSocket);
                            connectedClients--;

                            if (connectedClients == 0) {
                                // Close the server after all clients have disconnected
                                serverSocket.close();
                                System.out.println("Server: Shutting down...");
                            } else {
                                System.out.println("Server: Client disconnected.");
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });

                clientThread.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String processCommand(String command, DataInputStream dataIn, DataOutputStream dataOut, PrintWriter writer) throws IOException {
        // Add your logic to process the command and return the result
        System.out.println("Server: Received command - " + command);
        String[] tokens = command.split(" ");
        String cmd = tokens[0];

        switch (cmd.toLowerCase()) {
            case "/register":
                if (registeredUsers.contains(tokens[1])) {
                    return "exists";
                } else {
                    registeredUsers.add(tokens[1]);
                    return "registered"; 
                }
            case "/store":
                if (tokens.length > 1) {
                    storeFile(dataIn, tokens[1]);
                    return "\nFile stored successfully";
                }
                break;
            case "/fetch":
                if (tokens.length > 1) {
                    fetchFile(dataOut, tokens[1]);
                    return "\nFile fetched successfully";
                }
                break;
            case "/dir":
                listFiles(writer);
                return "\nFile list sent";
        }
        return "Invalid command";
    }


    // Inside the server's client-handling thread
    private static void storeFile(DataInputStream clientData, String filename) throws IOException {
        File file = new File(serverDir + "/" + filename); // Save in a server_storage directory
        FileOutputStream fileOutputStream = new FileOutputStream(file);
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = clientData.read(buffer)) != -1) {
            fileOutputStream.write(buffer, 0, bytesRead);
        }
        fileOutputStream.close();
    }

    // Inside the server's client-handling thread
    private static void fetchFile(DataOutputStream clientData, String filename) throws IOException {
        File file = new File(serverDir + "/" + filename);
        FileInputStream fileInputStream = new FileInputStream(file);
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = fileInputStream.read(buffer)) != -1) {
            clientData.write(buffer, 0, bytesRead);
        }
        fileInputStream.close();
    }

    // Inside listFiles method on the server
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

