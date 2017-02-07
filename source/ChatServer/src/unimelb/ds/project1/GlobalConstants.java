package unimelb.ds.project1;

/**
 * All global constants used in the project.
 * 
 * @author Sewwandi Perera
 *
 */
public class GlobalConstants {

	/**
	 * All message types
	 * 
	 * @author Sewwandi Perera
	 *
	 */
	public enum MessageType {
		newidentity, lockidentity, releaseidentity, roomchange, list, roomlist, who, roomcontents, createroom, lockroomid, releaseroomid, join, route, movejoin, serverchange, deleteroom, message, quit;
	}

	/**
	 * All message tags
	 * 
	 * @author Sewwandi Perera
	 *
	 */
	public enum MessageTag {
		type, approved, serverid, identity, locked, former, roomid, rooms, identities, owner, host, port, content;
	}

	/**
	 * Main chat room identity prefix
	 */
	public final static String MAIN_HALL_PREFIX = "MainHall-";

	/**
	 * Identity of the main chat room in this server.
	 */
	public final static String MAIN_HALL = MAIN_HALL_PREFIX + ServerData.getInstance().getMyData().getId();
}
