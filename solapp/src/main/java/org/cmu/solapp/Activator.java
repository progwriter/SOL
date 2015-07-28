package org.cmu.solapp;


import org.apache.felix.dm.Component;
import org.opendaylight.controller.sal.core.ComponentActivatorAbstractBase;
import org.opendaylight.controller.sal.flowprogrammer.IFlowProgrammerService;
//import org.opendaylight.controller.sal.packet.IDataPacketService;
//import org.opendaylight.controller.sal.packet.IListenDataPacket;
import org.opendaylight.controller.switchmanager.ISwitchManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Activator extends ComponentActivatorAbstractBase
{
	private static final Logger log = LoggerFactory.getLogger(PacketHandler.class);
	
	public Object[] getImplementations()
	{
		log.trace("Getting Implementations :-");
		Object[] res = {PacketHandler.class};
		return res;
	}
	public void configureInstance(Component c, Object imp, String containerName) {
        log.trace("Configuring instance..");
 
        if (imp.equals(PacketHandler.class)) {
 
            // Define exported and used services for PacketHandler component.
 
            //Dictionary<String, Object> props = new Hashtable<String, Object>();
            //props.put("salListenerName", "mypackethandler");
 
            // Export IListenDataPacket interface to receive packet-in events.
            //c.setInterface(new String[] {IListenDataPacket.class.getName()}, props);
 
            // Need the DataPacketService for encoding, decoding, sending data packets
            /*c.add(createContainerServiceDependency(containerName)
            		.setService(IDataPacketService.class)
            		.setCallbacks("setDataPacketService", "unsetDataPacketService")
            		.setRequired(true));*/
        	c.setInterface(new String[] {PacketHandler.class.getName()},null);
            c.add(createContainerServiceDependency(containerName)
            		.setService(IFlowProgrammerService.class)
            		.setCallbacks("setFlowProgrammerService", "unsetFlowProgrammerService")
            		.setRequired(true));
            
            c.add(createContainerServiceDependency(containerName)
            		.setService(ISwitchManager.class)
            		.setCallbacks("setSwitchManagerService", "unsetSwitchManagerService")
            		.setRequired(true));
 
        }
    }
}

