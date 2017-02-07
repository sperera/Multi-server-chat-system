package unimelb.ds.project1;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * This thread listen to the incoming connections from other servers.
 * 
 * @author Sewwandi Perera
 *
 */
public class CoordinationListner extends Thread {

	/**
	 * Coordination port number
	 */
	private int coordinationPort;

	/**
	 * Constructor
	 * 
	 * @param port
	 */
	public CoordinationListner(int port) {
		this.coordinationPort = port;
	}

	@Override
	public void run() {
		ServerSocket serverSocket = null;
		try {
			serverSocket = new ServerSocket(coordinationPort);
			System.out.println(Thread.currentThread().getName() + ": Server is listening in coordination port "
					+ coordinationPort + ".");
			int threadCount = 0;
			while (true) {
				threadCount++;
				Socket socket = serverSocket.accept();
				CoordinationWorker worker = new CoordinationWorker(socket);
				worker.setName("Coordination" + threadCount);
				worker.start();
			}
		} catch (IOException e) {
			System.err.println(Thread.currentThread().getName() + ": Error while listening to coordination port "
					+ e.getMessage());
		} finally {
			try {
				serverSocket.close();
			} catch (IOException e) {
				System.err.println(e.getMessage());
			}
		}
	}
}
