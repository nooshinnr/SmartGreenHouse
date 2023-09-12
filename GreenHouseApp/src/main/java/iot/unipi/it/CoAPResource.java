package iot.unipi.it;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.server.resources.CoapExchange;
import java.net.InetAddress;
import org.eclipse.californium.core.CoapClient;

public class CoAPResource extends CoapResource {
	
	String uri = null;
	
	public CoAPResource(String name) {
		super(name);
		setObservable(true);
 	}
	
 	public void handleGET(CoapExchange exchange) {

		exchange.accept();

		InetAddress source = exchange.getSourceAddress();
		uri = source.getHostAddress();
		
		/* Resource discovery */

		CoapClient client = new CoapClient("coap://["+ source.getHostAddress() +"]:5683/.well-known/core");
		CoapResponse response = client.get();
		
		String code = response.getCode().toString();
		
		//System.out.println(code);
		if(!code.startsWith("2")) {	
			System.err.println("error: " + code);
			return;
		}

		String responseText = response.getResponseText();

		addResources(source.getHostAddress(), responseText);
	}
 

	public static void addResources(String source, String response) {
	
		String[] resources = response.split(",");

		for(int i = 1; i < resources.length; i++) {
			try{
			
				String[] parameters = resources[i].split(";");
				String path = parameters[0].split("<")[1].split(">")[0];
				String name = path.split("/")[1];


				Resource newResource = new Resource(name, path, source);

				Interface.registeredResources.put(name,newResource);
				
				System.out.println(name + " is registered.\n");
				
			}catch(Exception e) {
				
				e.printStackTrace();
			
			}
		}
	}
}
