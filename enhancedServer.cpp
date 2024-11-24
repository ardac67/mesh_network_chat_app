// EnhancedServer.cpp
#include <winsock2.h>
#include <ws2tcpip.h>
#include <iostream>
#include <string>
#include <thread>
#include <chrono>
#include <vector>
#include <mutex>
#include <atomic>
#include "json.hpp" // Include the JSON library

#pragma comment(lib, "ws2_32.lib") // Link with Winsock library

using json = nlohmann::json;

// Structure to hold server socket information
struct ServerInfo {
    SOCKET socket;
    int port;
};

// Structure to hold connected client information
struct ClientInfo {
    SOCKET socket;
    std::thread thread;
    std::atomic<bool> isActive;
};

// Global variables
std::vector<ClientInfo*> clients; // List of connected clients
std::mutex clientsMutex;          // Mutex to protect the clients list
std::atomic<bool> serverRunning(true); // Flag to control server shutdown

// Function to get the local IP address
std::string getLocalIPAddress() {
    char hostname[256];
    if (gethostname(hostname, sizeof(hostname)) == SOCKET_ERROR) {
        std::cerr << "gethostname failed: " << WSAGetLastError() << std::endl;
        return "";
    }

    struct addrinfo hints = { 0 }, *res = NULL;
    hints.ai_family = AF_INET;
    hints.ai_socktype = SOCK_STREAM; // Or SOCK_DGRAM
    hints.ai_flags = AI_PASSIVE;

    if (getaddrinfo(hostname, NULL, &hints, &res) != 0) {
        std::cerr << "getaddrinfo failed: " << WSAGetLastError() << std::endl;
        return "";
    }

    char localIP[INET_ADDRSTRLEN];
    struct sockaddr_in* sockaddr_ipv4 = (struct sockaddr_in*)res->ai_addr;
    inet_ntop(AF_INET, &(sockaddr_ipv4->sin_addr), localIP, INET_ADDRSTRLEN);

    freeaddrinfo(res);

    return std::string(localIP);
}

// Function to create and set up a server socket
bool createServerSocket(ServerInfo& serverInfo) {
    serverInfo.socket = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (serverInfo.socket == INVALID_SOCKET) {
        std::cerr << "Server socket creation failed: " << WSAGetLastError() << std::endl;
        return false;
    }

    // Allow address reuse
    int opt = 1;
    if (setsockopt(serverInfo.socket, SOL_SOCKET, SO_REUSEADDR, (char*)&opt, sizeof(opt)) == SOCKET_ERROR) {
        std::cerr << "setsockopt failed: " << WSAGetLastError() << std::endl;
        closesocket(serverInfo.socket);
        return false;
    }

    sockaddr_in serverAddr = { 0 };
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_addr.s_addr = INADDR_ANY; // Bind to all interfaces
    serverAddr.sin_port = htons(serverInfo.port);

    if (bind(serverInfo.socket, (SOCKADDR*)&serverAddr, sizeof(serverAddr)) == SOCKET_ERROR) {
        std::cerr << "Bind failed on port " << serverInfo.port << ": " << WSAGetLastError() << std::endl;
        closesocket(serverInfo.socket);
        return false;
    }

    if (listen(serverInfo.socket, SOMAXCONN) == SOCKET_ERROR) {
        std::cerr << "Listen failed on port " << serverInfo.port << ": " << WSAGetLastError() << std::endl;
        closesocket(serverInfo.socket);
        return false;
    }

    std::cout << "Server socket created and listening on port " << serverInfo.port << std::endl;
    return true;
}

// Function to handle communication with a connected client
void clientHandler(ClientInfo* client) {
    SOCKET clientSocket = client->socket;
    int port;

    // Determine which port the client is connected to
    sockaddr_in clientAddr;
    int clientAddrSize = sizeof(clientAddr);
    if (getpeername(clientSocket, (sockaddr*)&clientAddr, &clientAddrSize) == 0) {
        port = ntohs(clientAddr.sin_port);
    } else {
        std::cerr << "getpeername failed: " << WSAGetLastError() << std::endl;
        port = -1; // Unknown port
    }

    // Send a welcome JSON message to the client
    json welcomeMsg;
    welcomeMsg["message"] = "Welcome! You are connected to the server.";
    std::string welcomeStr = welcomeMsg.dump() + "\n"; // Add newline for client parsing

    int sendResult = send(clientSocket, welcomeStr.c_str(), (int)welcomeStr.length(), 0);
    if (sendResult == SOCKET_ERROR) {
        std::cerr << "Send failed on port " << port << ": " << WSAGetLastError() << std::endl;
        closesocket(clientSocket);
        client->isActive = false;
        return;
    }

    // Start a thread to send periodic JSON messages to the client
    std::thread periodicThread([client, port]() {
        while (client->isActive) {
            // Create a JSON message
            json periodicMsg;
            periodicMsg["message"] = "Hello from server!";
            periodicMsg["timestamp"] = std::chrono::system_clock::to_time_t(std::chrono::system_clock::now());
            std::string periodicStr = periodicMsg.dump() + "\n"; // Add newline for client parsing

            // Send the periodic message
            int sendResult = send(client->socket, periodicStr.c_str(), (int)periodicStr.length(), 0);
            if (sendResult == SOCKET_ERROR) {
                std::cerr << "Periodic send failed on port " << port << ": " << WSAGetLastError() << std::endl;
                client->isActive = false;
                break;
            }

            std::cout << "Sent periodic message to client on port " << port << std::endl;

            std::cout << periodicMsg << std::endl;

            // Wait for 10 seconds before sending the next message
            std::this_thread::sleep_for(std::chrono::seconds(10));
        }

        // Close the client socket if periodic sending fails
        closesocket(client->socket);
        std::cout << "Client disconnected from port " << port << " (Periodic sender)" << std::endl;
    });

    periodicThread.detach();

    // Receive data from the client
    char recvbuf[512];
    int recvResult;
    while (client->isActive && (recvResult = recv(clientSocket, recvbuf, sizeof(recvbuf) - 1, 0)) > 0) {
        recvbuf[recvResult] = '\0'; // Null-terminate the received data
        std::cout << "Received from client on port " << port << ": " << recvbuf << std::endl;

        // Parse the received JSON message
        try {
            json receivedJson = json::parse(std::string(recvbuf));

            // Handle the received JSON message as needed
            // For example, respond with an acknowledgment
            json responseJson;
            responseJson["status"] = "Message received.";
            std::string responseStr = responseJson.dump() + "\n"; // Add newline for client parsing

            sendResult = send(clientSocket, responseStr.c_str(), (int)responseStr.length(), 0);
            if (sendResult == SOCKET_ERROR) {
                std::cerr << "Send failed on port " << port << ": " << WSAGetLastError() << std::endl;
                break;
            }
        }
        catch (json::parse_error& e) {
            std::cerr << "JSON parse error on port " << port << ": " << e.what() << std::endl;
            // Optionally, send an error message back to the client
            json errorJson;
            errorJson["error"] = "Invalid JSON format.";
            std::string errorStr = errorJson.dump() + "\n";
            send(clientSocket, errorStr.c_str(), (int)errorStr.length(), 0);
        }
    }

    if (recvResult == 0) {
        std::cout << "Connection closing on port " << port << std::endl;
    }
    else if (recvResult == SOCKET_ERROR) {
        std::cerr << "Recv failed on port " << port << ": " << WSAGetLastError() << std::endl;
    }

    // Mark the client as inactive and close the socket
    client->isActive = false;
    closesocket(clientSocket);
    std::cout << "Client disconnected from port " << port << std::endl;
}

// Function to accept incoming client connections
void acceptConnections(ServerInfo serverInfo) {
    SOCKET clientSocket;
    sockaddr_in clientAddr;
    int clientAddrSize = sizeof(clientAddr);

    while (serverRunning) {
        clientSocket = accept(serverInfo.socket, (SOCKADDR*)&clientAddr, &clientAddrSize);
        if (clientSocket == INVALID_SOCKET) {
            std::cerr << "Accept failed on port " << serverInfo.port << ": " << WSAGetLastError() << std::endl;
            continue;
        }

        // Create a new ClientInfo object
        ClientInfo* client = new ClientInfo();
        client->socket = clientSocket;
        client->isActive = true;

        // Add the client to the global clients list
        {
            std::lock_guard<std::mutex> lock(clientsMutex);
            clients.push_back(client);
        }

        // Start a thread to handle communication with the client
        client->thread = std::thread(clientHandler, client);
        client->thread.detach(); // Detach the thread to allow independent execution

        std::cout << "Client connected on port " << serverInfo.port << std::endl;
    }
}

// Function to send broadcast messages (JSON-formatted) via UDP
void sendBroadcast(const std::vector<ServerInfo>& servers) {
    SOCKET sockfd;
    struct sockaddr_in broadcastAddr;
    const char* broadcastIP = "255.255.255.255";  // Broadcast IP address
    const int broadcastPort = 8888;               // Broadcast port
    int broadcastPermission = 1;
    int sendResult;

    // Create a UDP socket for broadcasting
    if ((sockfd = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)) == INVALID_SOCKET) {
        std::cerr << "Broadcast socket creation failed: " << WSAGetLastError() << std::endl;
        return;
    }

    // Set socket options to allow broadcast
    if (setsockopt(sockfd, SOL_SOCKET, SO_BROADCAST,
        (char*)&broadcastPermission, sizeof(broadcastPermission)) == SOCKET_ERROR) {
        std::cerr << "Setting broadcast option failed: " << WSAGetLastError() << std::endl;
        closesocket(sockfd);
        return;
    }

    // Set up the broadcast address struct
    memset(&broadcastAddr, 0, sizeof(broadcastAddr));       // Zero out structure
    broadcastAddr.sin_family = AF_INET;                     // Internet address family
    broadcastAddr.sin_addr.s_addr = inet_addr(broadcastIP); // Broadcast IP address
    broadcastAddr.sin_port = htons(broadcastPort);          // Broadcast port

    std::string localIP = getLocalIPAddress();
    if (localIP.empty()) {
        std::cerr << "Unable to get local IP address." << std::endl;
        closesocket(sockfd);
        return;
    }

    std::cout << "Starting to send broadcast messages..." << std::endl;

    while (serverRunning) {
        // Create a JSON message with the IP and ports of all servers
        json jsonMsg;
        jsonMsg["ip"] = localIP;
        jsonMsg["ports"] = json::array();
        for (const auto& server : servers) {
            jsonMsg["ports"].push_back(server.port);
        }

        std::string message = jsonMsg.dump(); // Serialize JSON to string

        // Send the broadcast message
        sendResult = sendto(sockfd, message.c_str(), (int)message.length(), 0,
            (struct sockaddr*)&broadcastAddr, sizeof(broadcastAddr));

        if (sendResult == SOCKET_ERROR) {
            std::cerr << "Broadcast send failed: " << WSAGetLastError() << std::endl;
        }
        else {
            std::cout << "Broadcast message sent: " << message << std::endl;
        }

        // Wait for 5 seconds before sending the next broadcast
        std::this_thread::sleep_for(std::chrono::seconds(5));
    }

    // Close the broadcast socket
    closesocket(sockfd);
}

// Function to clean up all client connections
void cleanupClients() {
    std::lock_guard<std::mutex> lock(clientsMutex);
    for (auto& client : clients) {
        if (client->isActive) {
            client->isActive = false;
            closesocket(client->socket);
        }
        if (client->thread.joinable()) {
            client->thread.join();
        }
        delete client;
    }
    clients.clear();
}

int main() {
    WSADATA wsaData;
    int iResult;

    // Initialize Winsock
    iResult = WSAStartup(MAKEWORD(2, 2), &wsaData);
    if (iResult != 0) {
        std::cerr << "WSAStartup failed: " << iResult << std::endl;
        return 1;
    }

    // Define the server ports
    std::vector<ServerInfo> servers = {
        { INVALID_SOCKET, 8080 },
        { INVALID_SOCKET, 8081 },
        { INVALID_SOCKET, 8082 },
        { INVALID_SOCKET, 8083 }
    };

    // Create server sockets
    for (auto& server : servers) {
        if (!createServerSocket(server)) {
            std::cerr << "Failed to create server on port " << server.port << std::endl;
            // Clean up and exit if any server fails to start
            for (auto& srv : servers) {
                if (srv.socket != INVALID_SOCKET) {
                    closesocket(srv.socket);
                }
            }
            WSACleanup();
            return 1;
        }
    }

    // Start threads to accept connections on each server
    std::vector<std::thread> serverThreads;
    for (const auto& server : servers) {
        serverThreads.emplace_back(acceptConnections, server);
    }

    // Start broadcasting in a separate thread
    std::thread broadcastThread(sendBroadcast, servers);

    std::cout << "Press Enter to shut down the server..." << std::endl;
    std::cin.get(); // Wait for user input to terminate

    // Initiate server shutdown
    serverRunning = false;

    // Close all server sockets to unblock accept()
    for (auto& server : servers) {
        closesocket(server.socket);
    }

    // Wait for server threads to finish
    for (auto& thread : serverThreads) {
        if (thread.joinable()) {
            thread.join();
        }
    }

    // Wait for broadcast thread to finish
    if (broadcastThread.joinable()) {
        broadcastThread.join();
    }

    // Clean up all client connections
    cleanupClients();

    WSACleanup();
    std::cout << "Server shutdown gracefully." << std::endl;
    return 0;
}
//g++ -o EnhancedServer.exe EnhancedServer.cpp -lws2_32 -pthread
