package unimelb.ds.project1;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * This singleton class holds all server data.
 * 
 * @author Sewwandi Perera
 *
 */
public class ServerData {
	/**
	 * Singleton instance of the class
	 */
	private static ServerData instance = new ServerData();

	/**
	 * Thread safe queue of all my clients
	 */
	private Queue<String> myClients;

	/**
	 * Locked client identities with the server identity which sent the lock
	 * request
	 */
	private Map<String, String> lockedClients;

	/**
	 * Chat rooms with their IDs
	 */
	private Map<String, ChatRoom> myChatRooms;

	/**
	 * Locked room identifiers with the server identity which sent the lock
	 * request
	 */
	private Map<String, String> lockedRoomIds;

	/**
	 * Chat rooms owned by other servers with their belonging server id
	 */
	private Map<String, String> othersChatRooms;

	/**
	 * Information about other servers
	 */
	private Map<String, Server> otherServers;

	/**
	 * Information of this server
	 */
	private Server myData;

	/**
	 * Private constructor of the singleton class
	 */
	private ServerData() {
		myClients = new ConcurrentLinkedQueue<String>();
		myChatRooms = new ConcurrentHashMap<String, ChatRoom>();
		othersChatRooms = new ConcurrentHashMap<String, String>();
		otherServers = new ConcurrentHashMap<String, Server>();
		lockedClients = new ConcurrentHashMap<String, String>();
		lockedRoomIds = new ConcurrentHashMap<String, String>();
	}

	/**
	 * Get singleton instance of the class.
	 * 
	 * @return
	 */
	public static ServerData getInstance() {
		return instance;
	}

	/**
	 * Add another coordinating server
	 * 
	 * @param server
	 */
	public void addOtherServer(Server server) {
		otherServers.put(server.getId(), server);
		othersChatRooms.put(GlobalConstants.MAIN_HALL_PREFIX + server.getId(), server.getId());
	}

	/**
	 * Get all other servers in the system
	 * 
	 * @return
	 */
	public List<Server> getOtherServers() {
		return new ArrayList<Server>(otherServers.values());
	}

	/**
	 * Release room id
	 * 
	 * @param roomId
	 * @param requestedServer
	 * @param successful
	 */
	public synchronized void releaseRoomId(String roomId, String requestedServer, boolean successful) {
		if (lockedRoomIds.containsKey(roomId) && lockedRoomIds.get(roomId).equals(requestedServer)) {
			if (successful) {
				othersChatRooms.put(roomId, requestedServer);
			}
			lockedRoomIds.remove(roomId);
		}
	}

	/**
	 * Add a client to a chat room
	 * 
	 * @param formerRoom
	 * @param newRoom
	 * @param clientId
	 * @param clientThread
	 * @return 
	 */
	public synchronized boolean addClientToNewChatRoom(String formerRoom, String newRoom, String clientId,
			ClientWorker clientThread) {
		boolean out = true;
		if (myChatRooms.containsKey(newRoom)){
			myChatRooms.get(newRoom).addMember(clientId, clientThread);
		} else {
			out = false;
		}
		if (myChatRooms.containsKey(formerRoom)) {
			myChatRooms.get(formerRoom).ifContainsRemoveMember(clientId);
		}
		return out;
	}

	/**
	 * Add a new chat room belong to this server.
	 * 
	 * @param roomId
	 * @param ownerId
	 * @param ownerThread
	 */
	public synchronized void addMyNewChatRoom(String roomId, String ownerId, ClientWorker ownerThread) {
		if (lockedRoomIds.containsKey(roomId) && lockedRoomIds.get(roomId).equals(myData.getId())) {
			// remove owner from other chat rooms
			for (ChatRoom room : myChatRooms.values()) {
				room.ifContainsRemoveMember(ownerId);
			}

			// add new chat room and add the member to the chat room
			ChatRoom chatRoom = new ChatRoom(roomId, ownerId);
			chatRoom.addMember(ownerId, ownerThread);
			myChatRooms.put(roomId, chatRoom);

			// remove locked room id
			lockedRoomIds.remove(roomId);
		}
	}

	/**
	 * Lock a chat room identifier
	 * 
	 * @param chatroomid
	 * @param serverId
	 * @return
	 */
	public synchronized boolean lockChatRoom(String chatroomid, String serverId) {
		// || othersChatRooms.containsKey(chatroomid)
		if (myChatRooms.keySet().contains(chatroomid) || lockedRoomIds.keySet().contains(chatroomid)) {
			return false;
		}
		lockedRoomIds.put(chatroomid, serverId);
		return true;
	}

	/**
	 * Get all chat room names belong to any server.
	 * 
	 * @return
	 */
	public synchronized List<String> getAllChatRoomNames() {
		List<String> allChatRooms = new ArrayList<String>(othersChatRooms.keySet());
		allChatRooms.addAll(myChatRooms.keySet());
		return allChatRooms;
	}

	/**
	 * Add new chat room
	 * 
	 * @param room
	 */
	public synchronized void addChatRoom(ChatRoom room) {
		myChatRooms.put(room.getId(), room);
	}

	/**
	 * Release client identifier
	 * 
	 * @param clientId
	 * @param serverId
	 */
	public synchronized void releaseClientId(String clientId, String serverId) {
		if (lockedClients.containsKey(clientId)) {
			String storedServerId = lockedClients.get(clientId);
			if (storedServerId.equals(serverId)) {
				lockedClients.remove(clientId);
			}
		}
	}

	/**
	 * Lock identifier.
	 * 
	 * @param clientId
	 * @param serverId
	 * @return
	 */
	public synchronized boolean lockIdentity(String clientId, String serverId) {
		if (myClients.contains(clientId) || lockedClients.containsKey(clientId)) {
			return false;
		} else {
			lockedClients.put(clientId, serverId);
			return true;
		}
	}
	
	public synchronized ChatRoom addMemberToChatRoom(String roomId, String clientId, ClientWorker worker){
		try {
			ChatRoom room = getChatRoom(roomId);
			room.addMember(clientId, worker);
			return room;
		} catch(Exception e){
			ChatRoom room = getChatRoom(GlobalConstants.MAIN_HALL);
			room.addMember(clientId, worker);
			return room;
		}
	}

	/**
	 * Add new client
	 * 
	 * @param clientId
	 * @return
	 */
	public synchronized boolean addNewClient(String clientId) {
		if (myClients.contains(clientId)) {
			return false;
		} else {
			myClients.add(clientId);
			if (lockedClients.containsKey(clientId) && lockedClients.get(clientId).equals(myData.getId())) {
				lockedClients.remove(clientId);
			}
			System.out.println("[ Added a new client " + clientId + " ]");
			return true;
		}
	}

	/**
	 * Check whether the client is an owner of a chat room.
	 * 
	 * @param clientId
	 * @return
	 */
	public synchronized boolean isClientOwner(String clientId) {
		for (ChatRoom chatRoom : myChatRooms.values()) {
			if (chatRoom.getOwnerId().equals(clientId)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Check whether the chat room is available in any server.
	 * 
	 * @param id
	 * @return
	 */
	public synchronized boolean isChatRoomAvailable(String id) {
		return myChatRooms.containsKey(id) || othersChatRooms.containsKey(id);
	}

	/**
	 * Get the server belong the chat room.
	 * 
	 * @param chatroomId
	 * @return
	 */
	public synchronized Server getTheServerBelongChatRoom(String chatroomId) {
		if (myChatRooms.containsKey(chatroomId)) {
			return myData;
		} else if (othersChatRooms.containsKey(chatroomId)) {
			return otherServers.get(othersChatRooms.get(chatroomId));
		}
		return null;
	}

	/**
	 * Get the chat room given by the identifier.
	 * 
	 * @param id
	 * @return
	 */
	public synchronized ChatRoom getChatRoom(String id) {
		if (myChatRooms.containsKey(id)) {
			return myChatRooms.get(id);
		}
		return null;
	}

	/**
	 * Delete chat room
	 * 
	 * @param roomId
	 * @param ownerQuit
	 */
	public synchronized void deleteChatRoom(String roomId, boolean ownerQuit) {
		ChatRoom mainhall = getChatRoom(GlobalConstants.MAIN_HALL);
		this.getChatRoom(roomId).prepareToDelete(mainhall, ownerQuit);
		this.myChatRooms.remove(roomId);
	}
	
	/**
	 * Delete others chat room
	 * 
	 * @param roomId
	 * @param ownerQuit
	 */
	public synchronized void deleteOthersChatRoom(String roomId, String serverId) {
		if (othersChatRooms.containsKey(roomId) && othersChatRooms.get(roomId).equals(serverId)){
			othersChatRooms.remove(roomId);
		}
	}

	/**
	 * Remove a client belong to this server.
	 * 
	 * @param clientId
	 */
	public synchronized void removeMyClient(String clientId) {
		if (myClients.contains(clientId)) {
			myClients.remove(clientId);
		}
	}

	/**
	 * Get this server information.
	 * 
	 * @return
	 */
	public Server getMyData() {
		return myData;
	}

	/**
	 * Set this server information.
	 * 
	 * @param myData
	 */
	public void setMyData(Server myData) {
		this.myData = myData;
	}

	/**
	 * Print data.
	 */
	public synchronized void printData() {
		System.out.println("=================================");
		System.out.println("===All my chat rooms: " + myChatRooms.keySet());
		System.out.println("===All otherservers chat rooms" + othersChatRooms.keySet());
		System.out.println("===All locked chat rooms: " + lockedRoomIds.keySet());
		System.out.println("===All my clients: " + myClients);
		System.out.println("===All locked clients: " + lockedClients.keySet());
		System.out.println("=================================");
	}
}
