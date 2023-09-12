package iot.unipi.it;

public class Resource {

    public String name;
    private String path;
    private String address;
    private boolean actuatorState;

    public Resource (String n,String p, String a){
        this.name = n;
        this.path = p;
        this.address = a;
        
    }
    public String getName(){ return this.name; }

    public String getPath(){ return this.path; }

    public String getAddress(){ return this.address; }

    public String getCoapURI(){ return "coap://[" + this.address+"]:5683"+ this.path;}

    public void setActuatorState(boolean s) { this.actuatorState = s; }
    
    public boolean getActuatorState () { return this.actuatorState; }
}