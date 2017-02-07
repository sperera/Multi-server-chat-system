package unimelb.ds.project1;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.kohsuke.args4j.Option;

/**
 * This class create input command line arguments and parse the config file.
 * 
 * @author Sewwandi Perera
 *
 */
public class Config {

	/**
	 * Name of the server.
	 */
	@Option(required = true, name = "-n", usage = "Name of the server.")
	private String serverId;

	/**
	 * Path to the configuration file.
	 */
	@Option(required = true, name = "-l", usage = "Path to the configuration file.")
	private String configFile;

	public String getServerId() {
		return serverId;
	}

	public void setServerId(String serverId) {
		this.serverId = serverId;
	}

	public String getConfigFile() {
		return configFile;
	}

	public void setConfigFile(String configFile) {
		this.configFile = configFile;
	}

	/**
	 * Read the config file.
	 * 
	 * @return
	 * @throws RuntimeException
	 */
	public List<Server> readConfigFile() throws RuntimeException {
		List<Server> servers = new ArrayList<Server>();
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(getConfigFile()));
			String line = reader.readLine().trim();
			while (line != null) {
				String[] data = line.split("\\t");
				if (line.isEmpty() || data.length < 4) {
					throw new RuntimeException("Invalid config file format.");
				}

				Server server = new Server();
				server.setId(data[0]);
				server.setIp(data[1]);
				server.setClientPort(Integer.parseInt(data[2]));
				server.setCoordinationPort(Integer.parseInt(data[3]));
				servers.add(server);
				line = reader.readLine();
			}
		} catch (IOException e) {
			System.out.println("Error while reading configuration file " + getConfigFile() + ".");
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
				}
			}
		}
		return servers;
	}

}
