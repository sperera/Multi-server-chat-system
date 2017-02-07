package unimelb.ds.project1;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * This thread listens for new client connections
 * 
 * @author Sewwandi Perera
 *
 */
public class ClientListner extends Thread {

	private int clientPort;

	public ClientListner(int port) {
		this.clientPort = port;
	}

	@Override
	public void run() {
		ServerSocket clientSocket = null;
		try {
			clientSocket = new ServerSocket(clientPort);
			System.out.println(
					Thread.currentThread().getName() + ": Server is listening in client port " + clientPort + ".");
			int threadCount = 0;
			while (true) {
				threadCount++;
				Socket socket = clientSocket.accept();
				System.out.println(Thread.currentThread().getName() + ":Connected with client.");
				ClientWorker worker = new ClientWorker(socket);
				worker.setName("ClientThread" + threadCount);
				worker.start();
			}
		} catch (IOException e) {
			System.err.println(
					Thread.currentThread().getName() + ":Error while listening to client port " + e.getMessage());
		} finally {
			try {
				if (clientSocket != null) {
					clientSocket.close();
				}
			} catch (IOException e) {
			}
		}

	}

}
