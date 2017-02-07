package unimelb.ds.project1;

/**
 * This runs the server application
 * 
 * @author Sewwandi Perera
 *
 */
public class ChatServerApplication {

	/**
	 * Start the server application.
	 * 
	 * @param config
	 */
	public void start(Config config) {
		ServerData serverData = ServerData.getInstance();

		// Store server details
		for (Server server : config.readConfigFile()) {
			if (server.getId().equals(config.getServerId())) {
				serverData.setMyData(server);
			} else {
				serverData.addOtherServer(server);
			}
		}

		// Create Main-hall chat room
		ChatRoom mainhall = new ChatRoom(GlobalConstants.MAIN_HALL, "");
		serverData.addChatRoom(mainhall);

		// start threads to receive massages from other severs and clients
		Thread coordinationListner = new CoordinationListner(serverData.getMyData().getCoordinationPort());
		coordinationListner.setName("Coordination Listner Thread");
		Thread clientCommunicationListner = new ClientListner(serverData.getMyData().getClientPort());
		clientCommunicationListner.setName("Client Listner Thread");
		coordinationListner.start();
		clientCommunicationListner.start();
	}
}
