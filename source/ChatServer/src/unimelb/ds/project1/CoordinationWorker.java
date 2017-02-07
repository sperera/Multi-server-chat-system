package unimelb.ds.project1;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import unimelb.ds.project1.GlobalConstants.MessageTag;
import unimelb.ds.project1.GlobalConstants.MessageType;

/**
 * This class should be created per coordination connection. This processes
 * incoming coordination messages.
 * 
 * @author Sewwandi Perera
 *
 */
public class CoordinationWorker extends Thread {
	/**
	 * TCP socket to communicate with coordinating server.
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
	 * Constructor
	 * 
	 * @param socket
	 */
	public CoordinationWorker(Socket socket) {
		try {
			this.socket = socket;
			in = new BufferedReader(new InputStreamReader(this.socket.getInputStream(), "UTF-8"));
			out = new BufferedWriter(new OutputStreamWriter(this.socket.getOutputStream(), "UTF-8"));
		} catch (IOException e) {
			System.err.println(e.getMessage());
			// Close the socket
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException ioe) {
				}
			}
		}
	}

	@Override
	public void run() {
		try {
			String message = in.readLine();
			processMessage(message);
		} catch (IOException e) {
			System.err.println(e.getMessage());
		} finally {
			if (socket != null) {
				try {
					in.close();
					out.close();
					socket.close();
				} catch (IOException e) {
					System.err.println(e.getMessage());
				}
			}
			System.out.println(getName() + " end of thread.");
		}
	}

	/**
	 * Process incoming messages.
	 * 
	 * @param messageString
	 */
	private void processMessage(String messageString) {
		System.out.println(Thread.currentThread().getName() + ": received a coordination message: " + messageString);
		ServerData serverData = ServerData.getInstance();
		try {
			JSONParser parser = new JSONParser();
			JSONObject jsonObject = (JSONObject) parser.parse(messageString);
			MessageType type = MessageType.valueOf((String) jsonObject.get("type"));
			switch (type) {
			case lockidentity:
				// read identity
				String identity = (String) jsonObject.get(MessageTag.identity.name());
				String requestingServer = (String) jsonObject.get(MessageTag.serverid.name());

				// lock the identity if available
				boolean isAvailable = serverData.lockIdentity(identity, requestingServer);

				// send reply
				sendMessage(createLockIdentityResponse(serverData.getMyData().getId(), identity, isAvailable));
				break;

			case releaseidentity:
				// read data from message
				String clientId = (String) jsonObject.get(MessageTag.identity.name());
				String serverId = (String) jsonObject.get(MessageTag.serverid.name());

				// release lock
				serverData.releaseClientId(clientId, serverId);
				break;

			case lockroomid:
				// read data from message
				String roomId = (String) jsonObject.get(MessageTag.roomid.name());
				String server = (String) jsonObject.get(MessageTag.serverid.name());

				// lock room id
				boolean locked = serverData.lockChatRoom(roomId, server);

				// send reply
				sendMessage(createLockRoomResponse(serverData.getMyData().getId(), roomId, locked));
				break;

			case releaseroomid:
				// read data from message
				String releaseRoomId = (String) jsonObject.get(MessageTag.roomid.name());
				String releaseServerId = (String) jsonObject.get(MessageTag.serverid.name());
				String roomApproved = (String) jsonObject.get(MessageTag.approved.name());

				// release the room id
				serverData.releaseRoomId(releaseRoomId, releaseServerId, roomApproved.equals("true"));
				break;
			case deleteroom:
				// read data from message
				String deleteRoomId = (String) jsonObject.get(MessageTag.roomid.name());
				String deleteServerId = (String) jsonObject.get(MessageTag.serverid.name());

				// release the room id
				serverData.deleteOthersChatRoom(deleteRoomId, deleteServerId);
				break;
			default:
				break;
			}
		} catch (ParseException e) {
			System.err.println(Thread.currentThread().getName() + ": Error while parsing message :" + e.getMessage());
		}
		serverData.printData();
	}

	/**
	 * Send message
	 * 
	 * @param message
	 */
	private synchronized void sendMessage(JSONObject message) {
		try {
			out.write(message + "\n");
			out.flush();
			System.out.println(Thread.currentThread().getName() + ": sent a coordination response: " + message);
		} catch (IOException e) {
			System.err.println(
					Thread.currentThread().getName() + ": error while sending coordination response: " + message);
		}
	}

	@SuppressWarnings("unchecked")
	private synchronized JSONObject createLockIdentityResponse(String serverId, String identity, boolean isAvailable) {
		JSONObject lockIdentity = new JSONObject();
		lockIdentity.put(MessageTag.type.name(), MessageType.lockidentity.name());
		lockIdentity.put(MessageTag.serverid.name(), serverId);
		lockIdentity.put(MessageTag.identity.name(), identity);
		lockIdentity.put(MessageTag.locked.name(), isAvailable ? "true" : "false");
		return lockIdentity;
	}

	@SuppressWarnings("unchecked")
	private synchronized JSONObject createLockRoomResponse(String serverId, String roomId, boolean locked) {
		JSONObject lockroom = new JSONObject();
		lockroom.put(MessageTag.type.name(), MessageType.lockroomid.name());
		lockroom.put(MessageTag.serverid.name(), serverId);
		lockroom.put(MessageTag.roomid.name(), roomId);
		lockroom.put(MessageTag.locked.name(), locked ? "true" : "false");
		return lockroom;
	}
}
