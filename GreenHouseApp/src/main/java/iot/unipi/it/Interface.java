package iot.unipi.it;

import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.paho.client.mqttv3.MqttException;


public class Interface {

    public boolean lightingReq = false;
    public boolean ventilationReq = false;
    public boolean lit_set = false;
    public boolean temp_set = false;
    public String lighting_status = null;
    public String ventilation_status = null;
    static CoAPServer coapServer = new CoAPServer(5683);
    static public Map<String,Resource> registeredResources = new TreeMap<String,Resource>();
    
    public static void main(String[] args) throws MqttException {

        startServer();
        
        try {
        	ClientMqttLight MqttLit = new ClientMqttLight();
        }catch (Error e) {
        	System.out.print("Here, don't know why");
        	System.out.print(e);
        }
        
        try {
        	ClientMqttTemperature MqttTemp = new ClientMqttTemperature();
        }catch (MqttException me) {
            me.printStackTrace();
        }
     

        showMenu();

    }


    private static void startServer() {
        new Thread() {
            public void run() {
                coapServer.start();
            }
        }.start();
    }

    public static void showMenu() {
    	System.out.print("\nxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\n"
                + "\nWelcome to SmartGreenHouse!\n"
                + "\nxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\n\n");
				System.out.print("\nxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\n"
                + "\nHere you can observe temperature and the light-intensity of your greenhouse.\n"
				+"You can also observe Lighting and Ventilation operation"
				+ "\nxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\n\n");
    }
    
    public void MonitorLight() throws SQLException {
		int lit = 0;
		
		try {
			Class.forName("com.mysql.cj.jdbc.Driver");
		} catch (ClassNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		  String connectionUrl = "jdbc:mysql://localhost:3306/greenhousesql";
		  String query = "SELECT * FROM mqtt_light ORDER BY time DESC LIMIT 1;";
		  try {
			  Connection conn = DriverManager.getConnection(connectionUrl,"root","admin");
			  Statement st = conn.createStatement();
			  ResultSet rs = st.executeQuery(query);
			  
			  if (rs.next()) {//get first result
				  lit = rs.getInt(3);
		        }
			  
			  
			  if(lit < 20) {
				  System.out.println("Lighting is required.\n");
				  lightingReq = true;
				  actuatorActivation("lighting-actuator");
				  regulateLight();
			  } else {System.out.println("Lighting is not required.\n");}
			  
			  lightingReq = false;
			  lit_set = false;
			  conn.close();
			  
		  }catch(SQLException e){
			  e.printStackTrace();
		  }  
	}
    
    public void regulateLight() {
		
		int min = 20;
		int max = 40;
		int newlit = (int)Math.floor(Math.random()*(max-min+1)+min);
		lit_set = true;
		lighting_status = "ON";
		
		SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
		Date d = new Date();
		String[] tokens = dateFormat.format(d).split(" ");
		String date = tokens[0];
		String time = tokens[1];
		  
    	System.out.println("Lighting actuator: " + lighting_status + "\n");
    	
    	storeMqttData(time, date, newlit, lightingReq, "mqtt_light");
    	
    	try {	
			TimeUnit.SECONDS.sleep(5);
			
			System.out.println("Lighting Complete!");
			actuatorDeactivation("lighting-actuator");
			lighting_status = "OFF";
			System.out.println("Lighting actuator: " + lighting_status);
			
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    	

    	System.out.println("Light after regulation: " + newlit + "\n");
    
    }
    
    public void MonitorTemperature() throws SQLException {
    	int temp = 0;
		
		try {
			Class.forName("com.mysql.cj.jdbc.Driver");
		} catch (ClassNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		  String connectionUrl = "jdbc:mysql://localhost:3306/greenhousesql";
		  String query = "SELECT * FROM mqtt_temperature ORDER BY time DESC LIMIT 1;";
		  try {
			  Connection conn = DriverManager.getConnection(connectionUrl,"root","admin");
			  Statement st = conn.createStatement();
			  ResultSet rs = st.executeQuery(query);
			  
			  if (rs.next()) {//get first result
				  temp = rs.getInt(3);
		        }
			  
			  
			  if(temp < 10) {
				  System.out.println("Ventilation is required.\n");
				  ventilationReq = true;
				  actuatorActivation("ventilation-actuator");
				  regulateTemperature();
			  } else {System.out.println("Ventilation is not required.\n");}
			  
			  ventilationReq = false;
			  temp_set = false;
			  conn.close();
			  
		  }catch(SQLException e){
			  e.printStackTrace();
		  }  
	}
    
    public void regulateTemperature() {
		
		int min = 10;
		int max = 20;
		int newtemp = (int)Math.floor(Math.random()*(max-min+1)+min);
		temp_set = true;
		ventilation_status = "ON";
		
		SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
		Date d = new Date();
		String[] tokens = dateFormat.format(d).split(" ");
		String date = tokens[0];
		String time = tokens[1];
		  
    	System.out.println("Ventilation actuator: " + ventilation_status + "\n");
    	
    	storeMqttData(time, date, newtemp, ventilationReq, "mqtt_temperature");
    	
    	try {	
			TimeUnit.SECONDS.sleep(5);
			
			System.out.println("Ventilation Complete!");
			actuatorDeactivation("ventilation-actuator");
			ventilation_status = "OFF";
			System.out.println("Ventilation actuator: " + ventilation_status );
			
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    	

    	System.out.println("Light after regulation: " + newtemp + "\n");
    
    }
    
    public void storeMqttData(String time, String date, int value, boolean req, String tableName) {
    	String query = null;
    	
	    if (tableName == "mqtt_light") {
	  	  if (req && !lit_set) { lighting_status = "Required";
	  	  }else if (req && lit_set) { lighting_status = "Regulated";
	  	  }else if (!req) { lighting_status = "Non-Required";}
	  	  
	  	  query = "INSERT INTO "+ tableName +" (time, date, light, lighting) VALUES ('"+time+"','"+date+"','"+value+"','"+lighting_status+"')";
	    
	    }else if (tableName == "mqtt_temperature") {
	  	  if (req && !temp_set) { ventilation_status = "Required";
	  	  }else if (req && temp_set) { ventilation_status = "Regulated";
	  	  }else if (!req) { ventilation_status = "Non-Required";}
	  	  
		  query = "INSERT INTO "+ tableName +" (time, date, temperature, ventilation) VALUES ('"+time+"','"+date+"','"+value+"','"+ventilation_status+"')";
		}
	       	
		  try {
			Class.forName("com.mysql.cj.jdbc.Driver");
		  } catch (ClassNotFoundException e1) {
			e1.printStackTrace();
		  }
		  
		  String connectionUrl = "jdbc:mysql://localhost:3306/greenhousesql";
	  	  try {
	  		  Connection conn = DriverManager.getConnection(connectionUrl,"root","admin");
	  		  PreparedStatement ps = conn.prepareStatement(query);
	  		  ps.executeUpdate();
	  		  conn.close();
	  		  
	  	  }catch(SQLException e){
	  		  e.printStackTrace();
	  	  }
	  		  
	}
    
 	public void actuatorActivation(String name) {
		/* Resource discovery */

		CoapClient client = new CoapClient(registeredResources.get(name).getCoapURI());
		
		CoapResponse res = client.post("mode="+ "on", MediaTypeRegistry.TEXT_PLAIN);
		
		String code = res.getCode().toString();
		
		registeredResources.get(name).setActuatorState(true);
		
		if(!code.startsWith("2")) {	
			System.err.print("error: " + code);
			throw new Error ("Actuator Not Turned ON!!");
		}	
			    	
	}
 	
 	public void actuatorDeactivation(String name) {
		/* Resource discovery */

		CoapClient client = new CoapClient(registeredResources.get(name).getCoapURI());
		
		CoapResponse res = client.post("mode="+ "off", MediaTypeRegistry.TEXT_PLAIN);
	
		String code = res.getCode().toString();
		
		registeredResources.get(name).setActuatorState(false);
		
		if(!code.startsWith("2")) {	
			System.err.println("error: " + code);
			throw new Error ("Actuator Not Turned OFF!!");
		}		    	
	}
 	
}