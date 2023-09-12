package iot.unipi.it;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class ClientMqttLight implements MqttCallback{
	
	String subscriber_topic = "light";
	String broker = "tcp://127.0.0.1:1883";
	String clientId = "Application";
	int value = 0;
	Interface i = new Interface();
	
	MqttClient mqttClient;
	
	public ClientMqttLight() throws MqttException{
		try {
		mqttClient = new MqttClient(broker,clientId);
		}catch (Error e) {
			System.out.println(e);
		}
		mqttClient.setCallback(this);
		mqttClient.connect();
		mqttClient.subscribe(subscriber_topic);
		
		System.out.println("Subscribing to the " +subscriber_topic+ " topic..\n");
		
	}
	
	public void connectionLost(Throwable cause) {
  		System.out.println(cause.getMessage());
	}

	public void messageArrived(String topic, MqttMessage message) throws Exception {
		
		String json_message = new String(message.getPayload());

		//parsing
		JSONParser parser = new JSONParser();
		JSONObject jsonObject = null;
		try {
			jsonObject = (JSONObject) parser.parse(json_message);
			//System.out.println(jsonObject);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		if(jsonObject != null) {
			Long v = (Long) jsonObject.get("HUM");
			this.value = v.intValue();
			System.out.println("Light observed is: "+ this.value);
			
			if (this.value < 20) {
				i.lightingReq = true;
			} else i.lightingReq = false;
			
			  SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
			  Date d = new Date();
			  String[] tokens = dateFormat.format(d).split(" ");
			  String date = tokens[0];
			  String time = tokens[1];
			  
			i.storeMqttData(time, date, this.value, i.lightingReq, "mqtt_light");
			i.MonitorLight();
		}
	}

	public void deliveryComplete(IMqttDeliveryToken token) {
		System.out.println("Delivery Complete ");
	}
}
