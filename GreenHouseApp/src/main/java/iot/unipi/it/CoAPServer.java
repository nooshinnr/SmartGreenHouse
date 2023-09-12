package iot.unipi.it;

import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.CaliforniumLogger;

public class CoAPServer extends CoapServer {
	
	public CoAPServer (int port) {
		super(port);
		
		System.out.println ("Coap Server Started.\n");
		this.add(new CoAPResource("register"));
		
		CaliforniumLogger.disableLogging();
	}

}

