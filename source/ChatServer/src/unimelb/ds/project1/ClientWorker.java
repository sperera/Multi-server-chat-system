package unimelb.ds.project1;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import unimelb.ds.project1.GlobalConstants.MessageTag;
import unimelb.ds.project1.GlobalConstants.MessageType;

/**
 * This class represents the thread that listen to a particular client and it
 * process the messages itself. Instances of this class should be created per
 * client connection.
 * 
 * @author Sewwandi Perera
 *
 */
public class ClientWorker extends Thread {
	/**
	 * TCP socket to communicate with the client
	 */
	private Socket socket;

	/**
	 * Input stream
	 */
	private BufferedReader in;

	/**
	 * Output stream
	 */
	private BufferedWriter out;

	/**
	 * Reference to the singleton instance of server data.
	 */
	private ServerData serverData;

	/**
	 * Identity of the client
	 */
	private String myIdentity;

	/**
	 * Identity of the chat room, to which the client currently belong
	 */
	private String myChatRoom;

	/**
	 * This lock is used to block client from receiving chat messages in special
	 * cases like client moving from one chat room to another.
	 */
	private boolean clientOutBufferBlocked = false;

	/**
	 * The exit flag is used to indicate the server that it should stop
	 * listening to the client anymore.
	 */
	private boolean exit = false;
	
	/**
	 * This flag is used to indicate that client quits because he is moving to
	 * another server.
	 */
	private boolean clientMovingToAnotherServer = false;

	/**
	 * Constructor
	 * 
	 * @param socket
	 *            the TCP socket that should be used to communicate with the
	 *            client
	 */
	public ClientWorker(Socket socket) {
		try {
			// assign socket and create input and output streams
			this.socket = socket;
			in = new BufferedReader(new InputStreamReader(this.socket.getInputStream(), "UTF-8"));
			out = new BufferedWriter(new OutputStreamWriter(this.socket.getOutputStream(), "UTF-8"));
			serverData = ServerData.getInstance();
		} catch (IOException e) {
			System.err.println(
					Thread.currentThread().getName() + ": Error while creating client worker: " + e.getMessage());
			// Close the socket
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException ioe) {
				}
			}
		} catch (Exception e) {
			System.err.println(
					Thread.currentThread().getName() + ": Error while creating client worker: " + e.getMessage());
			// Close the socket
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException ioe) {
				}
			}
		}
	}

	/**
	 * Run method of {@link ClientWorker}. Listen for all incoming messages from
	 * the client and process them.
	 */
	@Override
	public void run() {
		try {
			String clientMsg = null;
			while ((clientMsg = in.readLine()) != null) {
				processMessage(clientMsg);
				if (exit) {
					break;
				}
			}
		} catch (SocketException e) {
			System.err.println(e.getMessage());
		} catch (IOException e) {
			System.err.println(e.getMessage());
		} catch (Exception e) {
			System.err.println(e.getMessage());
		} finally {
			// remove the client completely from the system
			handleQuit();
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException e) {
					System.err.println(e.getMessage());
					e.printStackTrace();
				}
			}
			System.out.println(Thread.currentThread().getName() + ": end of thread!");
		}
	}

	/**
	 * Process incoming messages from the client
	 * 
	 * @param messageString
	 *            JSON string of message
	 */
	private void processMessage(String messageString) {
		System.out.println(Thread.currentThread().getName() + ": received a	message: " + messageString);
		try {
			JSONParser parser = new JSONParser();
			JSONObject jsonObject = (JSONObject) parser.parse(messageString);
			MessageType type = MessageType.valueOf((String) jsonObject.get(MessageTag.type.name()));
			switch (type) {
			case newidentity:
				handleNewIdentityMessage(jsonObject, parser);
				break;
			case list:
				handleListMessage();
				break;
			case who:
				handleWhoMessage();
				break;
			case createroom:
				handleCreateRoomMessage(jsonObject, parser);
				break;
			case join:
				handleJoinRoomMessage(jsonObject);
				break;
			case movejoin:
				handleMoveJoin(jsonObject, parser);
				break;
			case deleteroom:
				handleDeleteRoom(jsonObject);
				break;
			case message:
				handleMessage(jsonObject);
				break;
			case quit:
				this.exit = true;
				break;
			default:
				break;
			}
		} catch (ParseException e) {
			System.err.println(Thread.currentThread().getName() + ": Error while processing message from client: "
					+ e.getMessage());
		}
		serverData.printData();
	}

	/**
	 * Handle client quit.
	 */
	private void handleQuit() {
		if (myChatRoom != null) {
			// if client is the owner of the chat room, delete it
			if (serverData.getChatRoom(myChatRoom).getOwnerId().equals(myIdentity)) {
				serverData.deleteChatRoom(myChatRoom, true);
				// notify other servers
				sendCoordinationMessage(createDeleteRoomNotification(serverData.getMyData().getId(), myChatRoom));
			}
	
			// else remove client and send the server change message to group
			// members
			else {
				if (!clientMovingToAnotherServer) {
					serverData.getChatRoom(myChatRoom).sendMessage(createRoomChangeMessage(myChatRoom, "", myIdentity),
							false);
				}
				serverData.getChatRoom(myChatRoom).ifContainsRemoveMember(myIdentity);
			}
		}

		// finally remove member from list
		if (myIdentity != null) {
			serverData.removeMyClient(myIdentity);
		}
	}

	/**
	 * Handle chat messages
	 * 
	 * @param jsonObject
	 */
	private void handleMessage(JSONObject jsonObject) {
		// read message
		String content = (String) jsonObject.get(MessageTag.content.name());

		// send message to users
		serverData.getChatRoom(myChatRoom).sendMessage(createBroadcastChatMessage(myIdentity, content), true);
	}

	/**
	 * Handle delete room
	 * 
	 * @param jsonObject
	 */
	private void handleDeleteRoom(JSONObject jsonObject) {
		// read data from the message
		String roomId = (String) jsonObject.get(MessageTag.roomid.name());

		// check if client is not the owner of the chat room or whether chat
		// room exists
		if (serverData.getChatRoom(roomId) == null || !serverData.getChatRoom(roomId).getOwnerId().equals(myIdentity)) {
			sendMessage(createDeleteRoomResponse(roomId, "false"), false);
			return;
		}

		// move members in the room to Main hall and delete the room. Meanwhile
		// notify all users
		serverData.deleteChatRoom(roomId, false);
		
		// notify other servers
		sendCoordinationMessage(createDeleteRoomNotification(serverData.getMyData().getId(), roomId));
		
		// send client response
		sendMessage(createDeleteRoomResponse(roomId, "true"), false);
	}

	/**
	 * Handle move join
	 * 
	 * @param jsonObject
	 */
	private void handleMoveJoin(JSONObject jsonObject, JSONParser parser) {
		// read data from the message
		String roomId = (String) jsonObject.get(MessageTag.roomid.name());
		String clientId = (String) jsonObject.get(MessageTag.identity.name());
		String formerRoom = (String) jsonObject.get(MessageTag.former.name());

		// check if either the identity is used by my clients or
		// currently I have locked the identity.
		boolean clientIdInUse = !serverData.lockIdentity(clientId, serverData.getMyData().getId());

		// check if the identity is already used by other servers
		boolean sentLockMessage = false;
		if (!clientIdInUse) {
			// send lock message
			sentLockMessage = true;
			List<String> lockIdentityResponses = sendCoordinationMessageAndGetReply(
					createLockIdentity(serverData.getMyData().getId(), clientId));

			// read responses from other servers
			for (String response : lockIdentityResponses) {
				JSONObject lockIdentityResponse;
				try {
					lockIdentityResponse = (JSONObject) parser.parse(response);
					if (((String) lockIdentityResponse.get(MessageTag.locked.name())).equals("false")) {
						clientIdInUse = true;
						break;
					}
				} catch (ParseException e) {
				}
			}
		}

		// store client information and send acknowledgement to client
		if (!clientIdInUse) {
			clientOutBufferBlocked = true;
			// select a room
			ChatRoom room = serverData.addMemberToChatRoom(roomId, clientId, this);
			room.addMember(clientId, this);
			this.myIdentity = clientId;
			this.myChatRoom = room.getId();
			
			// send response to the client
			sendMessage(createServerChangeMessage(serverData.getMyData().getId(), "true"), true);
			clientOutBufferBlocked = false;

			// broadcast room change massage to all members in the room
			serverData.getChatRoom(myChatRoom).sendMessage(createRoomChangeMessage(formerRoom, myChatRoom, clientId),
					false);
		}

		// release the lock
		if (sentLockMessage) {
			sendCoordinationMessage(createReleaseIdentityMessage(serverData.getMyData().getId(), clientId));
		}
		if (clientIdInUse) {
			sendMessage(createServerChangeMessage(serverData.getMyData().getId(), "false"), true);
		}
		serverData.releaseClientId(clientId, serverData.getMyData().getId());
	}

	/**
	 * Handle join room
	 * 
	 * @param jsonObject
	 */
	private void handleJoinRoomMessage(JSONObject jsonObject) {
		// read room id
		String roomid = (String) jsonObject.get(MessageTag.roomid.name());

		// validate the request (client is the owner of the current room or
		// non-existent chat room)
		boolean notValid = serverData.getChatRoom(myChatRoom).getOwnerId().equals(myIdentity)
				|| !serverData.isChatRoomAvailable(roomid) || myChatRoom.equals(roomid);
		if (notValid) {
			sendMessage(createRoomChangeMessage(myChatRoom, myChatRoom, myIdentity), false);
			return;
		}

		// handle if the chat room belong to this server
		Server server = serverData.getTheServerBelongChatRoom(roomid);
		if (server.getId().equals(serverData.getMyData().getId())) {
			// create room change message
			String formerRoom = myChatRoom;
			JSONObject roomchange = createRoomChangeMessage(formerRoom, roomid, myIdentity);

			// place the client in the new chat room
			this.clientOutBufferBlocked = true;
			boolean success = serverData.addClientToNewChatRoom(myChatRoom, roomid, myIdentity, this);
			if (!success){
				sendMessage(createRoomChangeMessage(myChatRoom, myChatRoom, myIdentity), false);
				return;
			}
			myChatRoom = roomid;

			// broadcast room change message to new group
			serverData.getChatRoom(roomid).sendMessage(roomchange, false);

			// send room change message to the client
			sendMessage(roomchange, true);
			this.clientOutBufferBlocked = false;

			// broadcast room change message to former group
			serverData.getChatRoom(formerRoom).sendMessage(roomchange, false);

			return;
		}

		// handle if chat room belong to another server
		else {
			// send the route to the client
			sendMessage(createRouteMessage(roomid, server.getIp(), server.getClientPort()), false);

			// remove client
			serverData.getChatRoom(myChatRoom).ifContainsRemoveMember(myIdentity);
			serverData.removeMyClient(myIdentity);
			
			// set the flag
			clientMovingToAnotherServer = true;

			// broadcast room change message to former room
			serverData.getChatRoom(myChatRoom).sendMessage(createRoomChangeMessage(myChatRoom, roomid, myIdentity),
					false);
		}
	}

	/**
	 * Handle create room
	 * 
	 * @param jsonObject
	 * @param parser
	 */
	private void handleCreateRoomMessage(JSONObject jsonObject, JSONParser parser) {
		// read room id
		String roomid = (String) jsonObject.get(MessageTag.roomid.name());

		// validate format of identity and if client already an owner of a
		// chat room and if the chat room id is available
		if (!validateIdentity(roomid) || serverData.isClientOwner(myIdentity)
				|| !serverData.lockChatRoom(roomid, serverData.getMyData().getId())) {
			// send denied reply to the client
			sendMessage(createCreateRoomMessage(roomid, "false"), false);
			return;
		}

		// send lock request
		List<String> responses = sendCoordinationMessageAndGetReply(
				createLockRoomMessage(serverData.getMyData().getId(), roomid));

		// process responses from other servers
		boolean lockSuccessful = true;
		for (String response : responses) {
			JSONObject lockIdentityResponse;
			try {
				lockIdentityResponse = (JSONObject) parser.parse(response);
				if (((String) lockIdentityResponse.get(MessageTag.locked.name())).equals("false")) {
					lockSuccessful = false;
					break;
				}
			} catch (ParseException e) {
				lockSuccessful = false;
				break;
			}
		}

		// create the chat room if lock was successfully acquired and send
		// acknowledgement to client
		JSONObject clientResponse = createCreateRoomMessage(roomid, lockSuccessful ? "true" : "false");
		String formerChatRoom = myChatRoom;
		if (lockSuccessful) {
			clientOutBufferBlocked = true;
			serverData.addMyNewChatRoom(roomid, myIdentity, this);
			myChatRoom = roomid;
		} else {
			serverData.releaseRoomId(roomid, serverData.getMyData().getId(), false);
		}
		sendMessage(clientResponse, true);
		clientOutBufferBlocked = false;

		// send release room id request to other servers
		sendCoordinationMessage(
				createReleaseRoomMessage(serverData.getMyData().getId(), roomid, lockSuccessful ? "true" : "false"));

		// broadcast message to the members of the previous group
		if (lockSuccessful) {
			JSONObject roomchange = createRoomChangeMessage(formerChatRoom, roomid, myIdentity);
			serverData.getChatRoom(formerChatRoom).sendMessage(roomchange, false);
			sendMessage(roomchange, false);
		}
	}

	private void handleWhoMessage() {
		sendMessage(serverData.getChatRoom(myChatRoom).getChatRoomDetails(), false);
	}

	/**
	 * handle list message
	 */
	private void handleListMessage() {
		// send response message
		sendMessage(createRoomList(serverData.getAllChatRoomNames()), false);
	}

	/**
	 * handle new identity
	 * 
	 * @param jsonObject
	 * @param parser
	 */
	private void handleNewIdentityMessage(JSONObject jsonObject, JSONParser parser) {
		// read identity
		String identity = (String) jsonObject.get(MessageTag.identity.name());

		// validate identity
		boolean validId = validateIdentity(identity);
		if (!validId) {
			// send denied reply to the client
			sendMessage(createNewIdentityResponse("false"), false);
			return;
		}

		// check if either the identity is used by my clients or
		// currently I have locked the identity.
		boolean clientIdInUse = !serverData.lockIdentity(identity, serverData.getMyData().getId());

		// check if the identity is already used by other servers
		boolean sentLockMessage = false;
		if (!clientIdInUse) {
			// send lock message
			sentLockMessage = true;
			List<String> lockIdentityResponses = sendCoordinationMessageAndGetReply(
					createLockIdentity(serverData.getMyData().getId(), identity));

			// read responses from other servers
			for (String response : lockIdentityResponses) {
				JSONObject lockIdentityResponse;
				try {
					lockIdentityResponse = (JSONObject) parser.parse(response);
					if (((String) lockIdentityResponse.get(MessageTag.locked.name())).equals("false")) {
						clientIdInUse = true;
						break;
					}
				} catch (ParseException e) {
				}
			}
		}

		// store client information and send acknowledgement to client
		JSONObject clientReply = createNewIdentityResponse(clientIdInUse ? "false" : "true");
		if (!clientIdInUse) {
			clientOutBufferBlocked = true;
			serverData.addNewClient(identity);
			serverData.getChatRoom(GlobalConstants.MAIN_HALL).addMember(identity, this);
			this.myIdentity = identity;
			this.myChatRoom = GlobalConstants.MAIN_HALL;
		}
		sendMessage(clientReply, true);
		clientOutBufferBlocked = false;

		// release the lock
		if (sentLockMessage) {
			sendCoordinationMessage(createReleaseIdentityMessage(serverData.getMyData().getId(), identity));
		}
		if (clientIdInUse) {
			serverData.releaseClientId(identity, serverData.getMyData().getId());
		}

		// notify the members in the MainHall
		if (!clientIdInUse) {
			serverData.getChatRoom(myChatRoom).sendMessage(createRoomChangeMessage("", myChatRoom, myIdentity), false);
		}
	}

	/**
	 * Validate identity. Identity (room or client identity) must have at least
	 * 3 characters and at most 16 characters and it should contain only
	 * alphanumeric characters.
	 * 
	 * @param identity
	 * @return
	 */
	private boolean validateIdentity(String identity) {
		if (identity.length() < 3 || identity.length() > 16) {
			System.err.println("Identity is not within the character limit (3-16): " + identity);
			return false;
		}
		if (identity.matches("\\w*[^0-9a-zA-Z]\\w*")) {
			System.err.println("Identity should contain only non-alphanumeric characters: " + identity);
			return false;
		}
		if (identity.matches("^[^a-zA-Z].*")) {
			System.err.println("Identity starts with a non-alphabetic character: " + identity);
			return false;
		}
		return true;
	}

	/**
	 * Send coordination messages to other servers and get reply
	 * 
	 * @param message
	 * @return
	 */
	private synchronized List<String> sendCoordinationMessageAndGetReply(JSONObject message) {
		List<String> responses = new ArrayList<String>();
		for (Server server : serverData.getOtherServers()) {
			Socket socket = null;
			BufferedReader in = null;
			BufferedWriter out = null;
			try {
				socket = new Socket(server.getIp(), server.getCoordinationPort());
				in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
				out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));

				// Send message to the server
				out.write(message + "\n");
				out.flush();
				System.out.println(Thread.currentThread().getName() + ": sent a coordination message \"" + message
						+ "\" to the server " + server.getId());

				// Receive the reply from the server
				String received = in.readLine();
				System.out.println(Thread.currentThread().getName() + ": received response \"" + received
						+ "\" from the server " + server.getId());
				responses.add(received);

			} catch (IOException e) {
				System.err.println(Thread.currentThread().getName() + ": error while communicationg to the server "
						+ server.getId() + ": " + e.getMessage());
			} finally {
				if (socket != null) {
					try {
						in.close();
						out.close();
						socket.close();
					} catch (IOException e) {
					}
				}
			}
		}
		return responses;
	}

	/**
	 * Send coordination messages to other servers and do not expect responses.
	 * 
	 * @param message
	 */
	private synchronized void sendCoordinationMessage(JSONObject message) {
		for (Server server : serverData.getOtherServers()) {
			Socket socket = null;
			BufferedWriter out = null;
			try {
				socket = new Socket(server.getIp(), server.getCoordinationPort());
				out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));

				// Send message to the server
				out.write(message + "\n");
				out.flush();
				System.out.println(Thread.currentThread().getName() + " sent a coordination message \"" + message
						+ "\" to the server " + server.getId());

			} catch (IOException e) {
				System.err.println(getName() + " error while communicationg to the server " + server.getId() + ": "
						+ e.getMessage());
			} finally {
				if (socket != null) {
					try {
						out.close();
						socket.close();
					} catch (IOException e) {
					}
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private synchronized JSONObject createRoomChangeMessage(String former, String roomid, String identity) {
		JSONObject roomChangeMessage = new JSONObject();
		roomChangeMessage.put(MessageTag.type.name(), MessageType.roomchange.name());
		roomChangeMessage.put(MessageTag.former.name(), former);
		roomChangeMessage.put(MessageTag.roomid.name(), roomid);
		roomChangeMessage.put(MessageTag.identity.name(), identity);
		return roomChangeMessage;
	}

	@SuppressWarnings("unchecked")
	private synchronized JSONObject createBroadcastChatMessage(String identity, String content) {
		JSONObject message = new JSONObject();
		message.put(MessageTag.type.name(), MessageType.message.name());
		message.put(MessageTag.identity.name(), identity);
		message.put(MessageTag.content.name(), content);
		return message;
	}

	@SuppressWarnings("unchecked")
	private synchronized JSONObject createDeleteRoomResponse(String roomId, String approved) {
		JSONObject response = new JSONObject();
		response.put(MessageTag.type.name(), MessageType.deleteroom.name());
		response.put(MessageTag.roomid.name(), roomId);
		response.put(MessageTag.approved.name(), approved);
		return response;
	}
	
	@SuppressWarnings("unchecked")
	private synchronized JSONObject createDeleteRoomNotification(String serverId, String roomId) {
		JSONObject response = new JSONObject();
		response.put(MessageTag.type.name(), MessageType.deleteroom.name());
		response.put(MessageTag.roomid.name(), roomId);
		response.put(MessageTag.serverid.name(), serverId);
		return response;
	}

	@SuppressWarnings("unchecked")
	private synchronized JSONObject createServerChangeMessage(String serverId, String approved) {
		JSONObject serverchange = new JSONObject();
		serverchange.put(MessageTag.type.name(), MessageType.serverchange.name());
		serverchange.put(MessageTag.approved.name(), approved);
		serverchange.put(MessageTag.serverid.name(), serverId);
		return serverchange;
	}

	@SuppressWarnings("unchecked")
	private synchronized JSONObject createRouteMessage(String roomId, String host, int port) {
		JSONObject route = new JSONObject();
		route.put(MessageTag.type.name(), MessageType.route.name());
		route.put(MessageTag.roomid.name(), roomId);
		route.put(MessageTag.host.name(), host);
		route.put(MessageTag.port.name(), Integer.toString(port));
		return route;
	}

	@SuppressWarnings("unchecked")
	private synchronized JSONObject createCreateRoomMessage(String roomId, String approved) {
		JSONObject message = new JSONObject();
		message.put(MessageTag.type.name(), MessageType.createroom.name());
		message.put(MessageTag.roomid.name(), roomId);
		message.put(MessageTag.approved.name(), approved);
		return message;
	}

	@SuppressWarnings("unchecked")
	private synchronized JSONObject createLockRoomMessage(String serverId, String roomId) {
		JSONObject lockroom = new JSONObject();
		lockroom.put(MessageTag.type.name(), MessageType.lockroomid.name());
		lockroom.put(MessageTag.serverid.name(), serverId);
		lockroom.put(MessageTag.roomid.name(), roomId);
		return lockroom;
	}

	@SuppressWarnings("unchecked")
	private synchronized JSONObject createReleaseRoomMessage(String serverId, String roomId, String approved) {
		JSONObject releaseRoom = new JSONObject();
		releaseRoom.put(MessageTag.type.name(), MessageType.releaseroomid.name());
		releaseRoom.put(MessageTag.serverid.name(), serverId);
		releaseRoom.put(MessageTag.roomid.name(), roomId);
		releaseRoom.put(MessageTag.approved.name(), approved);
		return releaseRoom;
	}

	@SuppressWarnings("unchecked")
	private synchronized JSONObject createRoomList(List<String> allChatRooms) {
		JSONObject roomlist = new JSONObject();
		roomlist.put(MessageTag.type.name(), MessageType.roomlist.name());
		JSONArray rooms = new JSONArray();
		for (String chatRoomName : allChatRooms) {
			rooms.add(chatRoomName);
		}
		roomlist.put(MessageTag.rooms.name(), rooms);
		return roomlist;
	}

	@SuppressWarnings("unchecked")
	private synchronized JSONObject createNewIdentityResponse(String approved) {
		JSONObject clientReply = new JSONObject();
		clientReply.put(MessageTag.type.name(), MessageType.newidentity.name());
		clientReply.put(MessageTag.approved.name(), approved);
		return clientReply;
	}

	@SuppressWarnings("unchecked")
	private synchronized JSONObject createLockIdentity(String serverId, String identity) {
		JSONObject lockIdentity = new JSONObject();
		lockIdentity.put(MessageTag.type.name(), MessageType.lockidentity.name());
		lockIdentity.put(MessageTag.serverid.name(), serverId);
		lockIdentity.put(MessageTag.identity.name(), identity);
		return lockIdentity;
	}

	@SuppressWarnings("unchecked")
	private synchronized JSONObject createReleaseIdentityMessage(String serverId, String identity) {
		JSONObject releaseLock = new JSONObject();
		releaseLock.put(MessageTag.type.name(), MessageType.releaseidentity.name());
		releaseLock.put(MessageTag.serverid.name(), serverId);
		releaseLock.put(MessageTag.identity.name(), identity);
		return releaseLock;
	}

	/**
	 * Send message to the client
	 * 
	 * @param message
	 * @param specialPriority
	 * @return
	 */
	public synchronized boolean sendMessage(JSONObject message, boolean specialPriority) {
		if (!specialPriority && clientOutBufferBlocked) {
			return false;
		}

		String type = (String) message.get(MessageTag.type.name());
		String identity = (String) message.get(MessageTag.identity.name());
		if (type.equals(MessageType.message.name()) && identity != null && identity.equals(myIdentity)) {
			return false;
		}
		try {
			out.write(message + "\n");
			out.flush();
			System.out.println(Thread.currentThread().getName() + ": sent a message to client \"" + message + "\"");
		} catch (IOException e) {
			System.err.println(
					Thread.currentThread().getName() + ": error while sending a message to the client " + myIdentity);
		}
		return true;
	}

	public synchronized void setChatRoom(String roomid) {
		this.myChatRoom = roomid;
	}

}
