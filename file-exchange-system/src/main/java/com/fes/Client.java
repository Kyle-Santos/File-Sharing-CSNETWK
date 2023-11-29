package com.fes;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

public class Client {
    private static String clientDir =  "../../../../../ClientStorage/";
    public static void main(String[] args) {
        Scanner scan = new Scanner(System.in);
		String[] join;
        String username = "";
        String sServerAddress = "localhost";
        int nPort = 4020;
        boolean running = true;
        boolean registered = false;

		// while (true) {
        //     System.out.print("Enter command: ");
		// 	join = scan.nextLine().split(" ");

		// 	if (join[0].equals("/join")) {
		// 		if (join.length == 3) {
        //             sServerAddress = join[1];
        //             nPort = Integer.parseInt(join[2]);
        //             break;
		// 		} else {
        //             System.out.println("\nUsage: /join <server_ip_add> <port>\n");
        //         }

		// 	} else {
		// 		System.out.println("\nConnect to the server first with /join <server_ip_add> <port>\n");
		// 	}
		// }

        try {
            Socket clientSocket = new Socket(sServerAddress, nPort);
            System.out.println("\nConnected to server at " + clientSocket.getRemoteSocketAddress() + "\n");

            System.out.println("Connection to the File Exchange Server is successful!");

            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
            
            while (running) {
                System.out.print("\nEnter command: ");
                String input = scan.nextLine();
                String[] command = input.split(" ");
                String response;

                switch (command[0]) {
                    case "/leave":
                        running = false; // exit the loop
                        break; 
                    case "/register":
                        // check if command is valid
                        if (command.length == 2) {
                            // check if user already registered
                            if (registered) {
                                System.out.println("\nYou are already registered.");
                            } else {
                                writer.println(input);
                                response = reader.readLine();

                                System.out.println("\nUsername: " + command[1] + " " + response + ".");    

                                // check if username do not exist
                                if (!response.equals("exists")) {
                                    registered = true;
                                    username = command[1];
                                    System.out.println("\nWelcome " + command[1] + "!");   
                                }
                            }
                        } else {
                            System.out.println("\nUsage: /register <handle>");
                        }
                        break;
                    case "/store":
                        String fileName = command[1];
                        File fileToSend = new File(clientDir + username + "/" + fileName);
                        OutputStream outputStream = clientSocket.getOutputStream();
            
                        // Check if the file exists
                        if (!fileToSend.exists()) {
                            System.out.println("Error: File not found.");
                            break;
                        }
                        
                        // Send the file name to the server
                        writer.println(input);
            
                        // Create a FileInputStream to read the file
                        FileInputStream fileInputStream = new FileInputStream(fileToSend);
            
                        // Buffer for reading data
                        byte[] buffer = new byte[1024];
                        int bytesRead;
            
                        // Read data from the file and send to the server
                        while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                        LocalDateTime timestamp = LocalDateTime.now();
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

                        System.out.println(username + "<" + timestamp.format(formatter) + ">: Uploaded " + fileName);
                        break;
                    case "/dir":
                        if (registered) {
                            writer.println(input);

                            System.out.println("\n---------------------------\n" +
                                "     Server Directory:\n---------------------------");
                            
                            while (!(response = reader.readLine()).equals("END")) {
                                System.out.println(response); // Display each file name
                            }

                        } else {
                            System.out.println("\nRegister yourself first. Usage: /register <handle>");
                        }
                        break;         
                    case "/get":
                        break;
                    case "/?":
                        showCommands();
                        break;
                    default:
                        System.out.println("\nInvalid Command.");
                        break;
                }

                // Read and display the server's response
            }

            // Close resources, perform cleanup, etc.
            clientSocket.close();
            System.out.println("\nConnection closed. Thank you!");
            scan.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static void showCommands() {
		System.out.println("\nDescription => InputSyntax\n" + "--------------------------------\n" +
				"Connect to the server application => /join <server_ip_add> <port>\n" +
				"Disconnect to the server application => /leave\n" +
				"Register a unique handle or alias  => /register <handle>\n" +
				"Send file to server => /store <filename>\n" +
				"Request directory file list from a server => /dir\n" +
				"Fetch a file from a server => /get <filename>\n" +
				"Request command help to output all Input Syntax commands for references => /?\n\n");
	}
}
