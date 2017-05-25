import hudson.slaves.SlaveComputer;

String slaves = "${slaves}";

for (String slave : slaves.split(",")) {
	Hudson hudson = Hudson.instance;

	Slave slaveObject = hudson.getNode(slave.trim());

	SlaveComputer slaveComputer = slaveObject.getComputer();

	try {
		boolean offlineStatus = ${offline.status};

		slaveComputer.setTemporarilyOffline(offlineStatus)
	}
	catch (NullPointerException npe) {
	}
}