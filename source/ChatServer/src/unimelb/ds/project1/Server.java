package unimelb.ds.project1;

/**
 * This class contains server information.
 * 
 * @author Sewwandi Perera
 *
 */
public class Server {
	private String id;
	private String ip;
	private int clientPort;
	private int coordinationPort;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public int getClientPort() {
		return clientPort;
	}

	public void setClientPort(int clientPort) {
		this.clientPort = clientPort;
	}

	public int getCoordinationPort() {
		return coordinationPort;
	}

	public void setCoordinationPort(int coordinationPort) {
		this.coordinationPort = coordinationPort;
	}

	@Override
	public String toString() {
		return "Server [id=" + id + ", ip=" + ip + ", client port=" + clientPort + ", coordination port="
				+ coordinationPort + "]";
	}
}
