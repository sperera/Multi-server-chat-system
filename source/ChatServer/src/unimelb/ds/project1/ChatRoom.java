package unimelb.ds.project1;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import unimelb.ds.project1.GlobalConstants.MessageTag;
import unimelb.ds.project1.GlobalConstants.MessageType;

/**
 * This class contains all chat room information and actions.
 * 
 * @author Sewwandi Perera
 *
 */
public class ChatRoom {
	/**
	 * Chat room identifier
	 */
	private String id;

	/**
	 * Identifier of the owner of the chat room
	 */
	private String ownerId;

	/**
	 * Thread safe list of members in the chat room
	 */
	private Map<String, ClientWorker> members;

	/**
	 * Chat room can block broadcasting chat messages to group members in
	 * special cases like when the room is preparing to delete. So, this lock
	 * can be used in those cases.
	 */
	private boolean blockChatMessages = false;

	/**
	 * Constructor of the chat room
	 * 
	 * @param id
	 *            Chat room identifier
	 * @param owner
	 *            Identifier of the owner of the room
	 */
	public ChatRoom(String id, String owner) {
		this.id = id;
		this.ownerId = owner;
		this.members = new ConcurrentHashMap<String, ClientWorker>();
	}

	/**
	 * Thread safe method to remove members from the chat room
	 * 
	 * @param member
	 *            member identifier
	 */
	public synchronized void ifContainsRemoveMember(String member) {
		if (members.containsKey(member)) {
			members.remove(member);
		}
	}

	/**
	 * Thread safe method to add a member to the chat room
	 * 
	 * @param id
	 *            new member identifier
	 * @param worker
	 *            {@link ClientWorker} thread of the new member
	 */
	public synchronized void addMember(String id, ClientWorker worker) {
		this.members.put(id, worker);
	}

	/**
	 * Thread safe method to broadcast a message to all members in the group
	 * 
	 * @param message
	 *            message content as a {@link JSONObject}
	 * @param chatMessage
	 *            whether the message is a chat message from a user in the group
	 *            or a system message to send some information like user
	 *            joining/ leaving group.
	 */
	public synchronized void sendMessage(JSONObject message, boolean chatMessage) {
		if (blockChatMessages && chatMessage) {
			return;
		}
		System.out.println(
				Thread.currentThread().getName() + ": Broadcasting message \"" + message + "\" to the group " + id);
		for (ClientWorker worker : members.values()) {
			worker.sendMessage(message, false);
		}
	}

	/**
	 * Thread safe method to prepare the room to be deleted. First of all, all
	 * chat messages are blocked, because since we are going to delete the room
	 * chat messages do not need to be broadcasted. Then if the room is deleted
	 * because owner has quit, then move the owner to empty room and notify all
	 * users. Next, move all members to the new room (this is the MainHall room
	 * of the server) and notify all members in new room and the current room.
	 * 
	 * @param newRoom
	 *            to where the members of the chat room should be moved. (this
	 *            is the MainHall room of the server)
	 * @param ownerQuit
	 *            true if the room is deleted because owner has quit.
	 */
	@SuppressWarnings("unchecked")
	public synchronized void prepareToDelete(ChatRoom newRoom, boolean ownerQuit) {
		this.blockChatMessages = true;

		JSONObject roomChangeMessage = new JSONObject();
		roomChangeMessage.put(MessageTag.type.name(), MessageType.roomchange.name());
		roomChangeMessage.put(MessageTag.former.name(), this.getId());
		roomChangeMessage.put(MessageTag.roomid.name(), newRoom.getId());

		// handle owner separately if owner quits
		if (ownerQuit) {
			roomChangeMessage.put(MessageTag.identity.name(), ownerId);
			roomChangeMessage.put(MessageTag.roomid.name(), "");
			sendMessage(roomChangeMessage, false);
			members.remove(ownerId);
		}

		// handle members
		roomChangeMessage.put(MessageTag.roomid.name(), newRoom.getId());
		Map<String, ClientWorker> membersClone = new ConcurrentHashMap<String, ClientWorker>(members);
		for (String memberId : membersClone.keySet()) {
			roomChangeMessage.put(MessageTag.identity.name(), memberId);
			ClientWorker worker = membersClone.get(memberId);

			// remove the member from the chat room
			members.remove(memberId);

			// add member to the new group
			newRoom.addMember(memberId, worker);
			worker.setChatRoom(newRoom.getId());

			// broadcast messages to both groups
			sendMessage(roomChangeMessage, false);
			newRoom.sendMessage(roomChangeMessage, false);
		}
	}

	/**
	 * Thread safe method to get chat room information as a {@link JSONObject}
	 * 
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public synchronized JSONObject getChatRoomDetails() {
		JSONObject chatroom = new JSONObject();
		chatroom.put(MessageTag.type.name(), MessageType.roomcontents.name());
		chatroom.put(MessageTag.roomid.name(), id);
		chatroom.put(MessageTag.owner.name(), ownerId);
		JSONArray members = new JSONArray();
		for (String member : this.members.keySet()) {
			members.add(member);
		}
		chatroom.put(MessageTag.identities.name(), members);
		return chatroom;
	}

	/**
	 * Returns the identifier of the room
	 * 
	 * @return
	 */
	public String getId() {
		return id;
	}

	/**
	 * Returns the identifier of the owner of the room.
	 * 
	 * @return
	 */
	public String getOwnerId() {
		return ownerId;
	}
}
