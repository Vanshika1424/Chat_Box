import java.util.*;
import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;


// the server that can be run as a console
public class server {
    // a unique ID for each connection
    private static int uniqueId;
    // an ArrayList to keep the list of the Client
    private ArrayList<ClientThread> al;
    // to display time
    private SimpleDateFormat sdf;
    // the port number to listen for connection
    private int port;
    // to check if server is running
    private boolean keepGoing;
    // notification
    private String notif = " *** ";

    //constructor that receive the port to listen to for connection as parameter

    public server(int port) {
        // the port
        this.port = port;
        // to display hh:mm:ss
        sdf = new SimpleDateFormat("HH:mm:ss");
        // an ArrayList to keep the list of the Client
        al = new ArrayList<ClientThread>();
    }

    public void start() {
        keepGoing = true;
        //create socket server and wait for connection requests
        try
        {
            // the socket used by the server
            ServerSocket serverSocket = new ServerSocket(port);

            // infinite loop to wait for connections ( till server is active )
            while(keepGoing)
            {
                display("Server waiting for Clients on port " + port + ".");

                // accept connection if requested from client
                Socket socket = serverSocket.accept();
                // break if server stopped
                if(!keepGoing)
                    break;
                // if client is connected, create its thread
                ClientThread t = new ClientThread(socket);
                //add this client to arraylist
                al.add(t);

                t.start();
            }
            // try to stop the server
            try {
                serverSocket.close();
                for(int i = 0; i < al.size(); ++i) {
                    ClientThread tc = al.get(i);
                    try {
                        // close all data streams and socket
                        tc.sInput.close();
                        tc.sOutput.close();
                        tc.socket.close();
                    }
                    catch(IOException ioE) {
                    }
                }
            }
            catch(Exception e) {
                display("Exception closing the server and clients: " + e);
            }
        }
        catch (IOException e) {
            String msg = sdf.format(new Date()) + " Exception on new ServerSocket: " + e + "\n";
            display(msg);
        }
    }

    // to stop the server
    protected void stop() {
        keepGoing = false;
        try {
            new Socket("localhost", port);
        }
        catch(Exception e) {
        }
    }

    // Display an event to the console
    private void display(String msg) {
        String time = sdf.format(new Date()) + " " + msg;
        System.out.println(time);
    }

    // to broadcast a message to all Clients
    private synchronized boolean broadcast(String message) {
        // add timestamp to the message
        String time = sdf.format(new Date());

        // to check if message is private i.e. client to client message
        String[] w = message.split(" ",3);

        boolean isPrivate = false;
        if(w[1].charAt(0)=='@')
            isPrivate=true;


        // if private message, send message to mentioned username only
        if(isPrivate==true)
        {
            String tocheck=w[1].substring(1, w[1].length());

            message=w[0]+w[2];
            String messageLf = time + " " + message + "\n";
            boolean found=false;
            // we loop in reverse order to find the mentioned username
            for(int y=al.size(); --y>=0;)
            {
                ClientThread ct1=al.get(y);
                String check=ct1.getUsername();
                if(check.equals(tocheck))
                {
                    // try to write to the Client if it fails remove it from the list
                    if(!ct1.writeMsg(messageLf)) {
                        al.remove(y);
                        display("Disconnected Client " + ct1.username + " removed from list.");
                    }
                    // username found and delivered the message
                    found=true;
                    break;
                }



            }
            // mentioned user not found, return false
            if(found!=true)
            {
                return false;
            }
        }
        // if message is a broadcast message
        else
        {
            String messageLf = time + " " + message + "\n";
            // display message
            System.out.print(messageLf);

            // we loop in reverse order in case we would have to remove a Client
            // because it has disconnected
            for(int i = al.size(); --i >= 0;) {
                ClientThread ct = al.get(i);
                // try to write to the Client if it fails remove it from the list
                if(!ct.writeMsg(messageLf)) {
                    al.remove(i);
                    display("Disconnected Client " + ct.username + " removed from list.");
                }
            }
        }
        return true;


    }

    // if client sent LOGOUT message to exit
    synchronized void remove(int id) {

        String disconnectedClient = "";
        // scan the array list until we found the Id
        for(int i = 0; i < al.size(); ++i) {
            ClientThread ct = al.get(i);
            // if found remove it
            if(ct.id == id) {
                disconnectedClient = ct.getUsername();
                al.remove(i);
                break;
            }
        }
        broadcast(notif + disconnectedClient + " has left the chat room." + notif);
    }

    /*
     *  To run as a console application
     * > java Server
     * > java Server portNumber
     * If the port number is not specified 1500 is used
     */
    public static void main(String[] args) {
        // start server on port 1500 unless a PortNumber is specified
        int portNumber = 1500;
        switch(args.length) {
            case 1:
                try {
                    portNumber = Integer.parseInt(args[0]);
                }
                catch(Exception e) {
                    System.out.println("Invalid port number.");
                    System.out.println("Usage is: > java Server [portNumber]");
                    return;
                }
            case 0:
                break;
            default:
                System.out.println("Usage is: > java Server [portNumber]");
                return;

        }
        // create a server object and start it
        server server = new server(portNumber);
        server.start();
    }

    // One instance of this thread will run for each client
    class ClientThread extends Thread {
        // the socket to get messages from client
        Socket socket;
        ObjectInputStream sInput;
        ObjectOutputStream sOutput;
        // my unique id (easier for disconnection)
        int id;
        // the Username of the Client
        String username;
        // message object to receive message and its type
        ChatMessage cm;
        // timestamp
        String date;

        // Constructor
        ClientThread(Socket socket) {
            // a unique id
            id = ++uniqueId;
            this.socket = socket;
            //Creating both Data Stream
            System.out.println("Thread trying to create Object Input/Output Streams");
            try
            {
                sOutput = new ObjectOutputStream(socket.getOutputStream());
                sInput  = new ObjectInputStream(socket.getInputStream());
                // read the username
                username = (String) sInput.readObject();
                broadcast(notif + username + " has joined the chat room." + notif);
            }
            catch (IOException e) {
                display("Exception creating new Input/output Streams: " + e);
                return;
            }
            catch (ClassNotFoundException e) {
            }
            date = new Date().toString() + "\n";
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        // infinite loop to read and forward message
        public void run() {
            // to loop until LOGOUT
            boolean keepGoing = true;
            while(keepGoing) {
                // read a String (which is an object)
                try {
                    cm = (ChatMessage) sInput.readObject();
                }
                catch (IOException e) {
                    display(username + " Exception reading Streams: " + e);
                    break;
                }
                catch(ClassNotFoundException e2) {
                    break;
                }
                // get the message from the ChatMessage object received
                String message = cm.getMessage();

                // different actions based on type message
                switch(cm.getType()) {

                    case ChatMessage.MESSAGE:
                        boolean confirmation =  broadcast(username + ": " + message);
                        if(confirmation==false){
                            String msg = notif + "Sorry. No such user exists." + notif;
                            writeMsg(msg);
                        }
                        break;
                    case ChatMessage.LOGOUT:
                        display(username + " disconnected with a LOGOUT message.");
                        keepGoing = false;
                        break;
                    case ChatMessage.WHOISIN:
                        writeMsg("List of the users connected at " + sdf.format(new Date()) + "\n");
                        // send list of active clients
                        for(int i = 0; i < al.size(); ++i) {
                            ClientThread ct = al.get(i);
                            writeMsg((i+1) + ") " + ct.username + " since " + ct.date);
                        }
                        break;
                }
            }
            // if out of the loop then disconnected and remove from client list
            remove(id);
            close();
        }

        // close everything
        private void close() {
            try {
                if(sOutput != null) sOutput.close();
            }
            catch(Exception e) {}
            try {
                if(sInput != null) sInput.close();
            }
            catch(Exception e) {};
            try {
                if(socket != null) socket.close();
            }
            catch (Exception e) {}
        }

        // write a String to the Client output stream
        private boolean writeMsg(String msg) {
            // if Client is still connected send the message to it
            if(!socket.isConnected()) {
                close();
                return false;
            }
            // write the message to the stream
            try {
                sOutput.writeObject(msg);
            }
            // if an error occurs, do not abort just inform the user
            catch(IOException e) {
                display(notif + "Error sending message to " + username + notif);
                display(e.toString());
            }
            return true;
        }
    }
}



























































//public class server {
//
//    //unique id for eachh connection
//    private static int uniqueid;
//    //ArrayList to keep list of all clients
//    private ArrayList<ClientThread> al;
//    //To display time
//    private SimpleDateFormat sdf;
//    //Port connection
//    private int port;
//    //Check server running
//    private boolean keepGoing;
//    //notification
//    private String notif = "***";
//
//    //Constructor to receive the port to listen to for connection
//    //the port
//    public server(int port) {
//
//        //The port
//        this.port = port;
//        //display date
//        sdf = new SimpleDateFormat("HH:mm:ss");
//        //ArrayList for clients
//        al = new ArrayList<ClientThread>();
//    }
//
//
//    public void start() {
//        keepGoing = true;//Server is live
//        //Create socket server & wait for connection request
//        try {
//            //Socket used by server
//            ServerSocket serverSocket = new ServerSocket(port);
//
//            while (keepGoing) {
//                display("Server waiting for Clients on port" + port + ".");
//
//                Socket socket = serverSocket.accept();
//
//                if (!keepGoing)
//                    break;
//
//                ClientThread t = new ClientThread(socket);
//
//                al.add(t);
//                t.start();
//            }
//
//            try {
//                serverSocket.close();
//                for (int i = 0; i < al.size(); ++i) {
//
//                    ClientThread tc = al.get(i);
//                    try {
//                        //Close all the data streams and socket
//                        tc.sInput.close();
//                        tc.sOutput.close();
//                        tc.socket.close();
//                    } catch (IOException e) {
//                    }
//                }
//            } catch (Exception e) {
//                display("Exception closing the server and clients: " + e);
//            }
//        } catch (IOException e) {
//            String msg = sdf.format(new Date()) + "Exception on new ServerSocket";
//            display(msg);
//        }
//    }
//
//    protected void stop() {
//        keepGoing = false;
//        try {
//            new Socket("localhost", port);
//        } catch (Exception e) {
//            System.out.println(e);
//        }
//    }
//
//    private void display(String string) {
//        String time = sdf.format(new Date()) + "" + messageLf;
//        System.out.println(time);
//    }
//
//
//    //To broadcast message to all
//    private synchronized boolean broadcast(String message) {
//        String time = sdf.format(new Date());
//        String[] w = message.split(" ", 3);
//        boolean isPrivate = false;
//        if (w[1].charAt(0) == '@')
//            isPrivate = true;
//
//        if (isPrivate == true) {
//            String tocheck = w[1].substring(1, w[1].length());
//            message = w[0] + w[2];
//            boolean found = false;
//
//            for (int y = al.size(); --y >= 0; ) {
//                ClientThread ct1 = al.get(y);
//                String check = ct1.getUsername();
//
//                if (check.equals(tocheck)) {
//                    if (!ct1.writeMsg(messageLf)) {
//                        al.remove(y);
//                        display("Disconnected Client" + ct1.username + "Remove");
//                    }
//                    found = true;
//                    break;
//                }
//
//                //Mentioned user not found,return false
//                if (found != true) return false;
//
//                else {
//                    String messageLf = time + " " + message + "\n";
//
//                    System.out.print(messageLf);
//
//                    for (int i = al.size(); --i >= 0; ) {
//                        ClientThread ct = al.get(i);
//
//                        if (!ct1.writeMsg(messageLf)) {
//                            al.remove(i);
//                            display("Disconnected Client" + ct1.username + "Remove from the list");
//                        }
//                    }
//                }
//                return true;
//            }
//        }
//        return isPrivate;
//    }
//
//    synchronized void remove(int id) {
//        String disconnectedClient = "";
//
//        for (int i = 0; i < al.size(); ++i) {
//            ClientThread ct = al.size();
//            if (ct.id == id) {
//                disconnectedClient = ct.getUsername();
//                al.remove(i);
//                break;
//            }
//        }
//        broadcast(notif + disconnectedClient + " has left the chat room " + notif);
//    }
//
//
//    public static void main(String args[]) {
//        int portNumber = 1500;
//
//        switch (args.length) {
//            case 1:
//                try {
//                    portNumber = Integer.parseInt(args[0]);
//                } catch (Exception e) {
//                    System.out.println("Invalid port number");
//                    System.out.println("Usage is :> java Server[portNumber]");
//                    return;
//                }
//            case 0:
//                break;
//            default:
//                System.out.println("Usage is :> java Server[portNumber]");
//                return;
//        }
//        server server = new server(portNumber);
//        server.start();
//    }
//
//    //one instance of this thread will run for each client
//    class ClientThread extends Thread
//    {
//        Socket socket;
//        ObjectInputStream sInput;
//        ObjectOutputStream sOutput;
//        int id;
//        String username;
//        ChatMessage cm;
//        String date;
//
//        ClientThread(Socket socket) {
//            id = ++uniqueid;
//            this.socket = socket;
//            System.out.println("Thread trying to create object Input/Output Streams");
//
//            try{
//                sOutput=new ObjectOutputStream(socket.getOutputStream());
//                sInput=new ObjectInputStream(socket.getInputStream());
//
//                username=(String)sInput.readObject();
//                broadcast(notif+username+"has joined the chat room"+notif);
//            }
//
//            catch (IOException e)
//            {
//                display("Exception creating new Input/Output Streams :");
//                return;
//            }
//
//            catch (ClassNotFoundException e) {
//                System.out.println(e);
//            }
//            date=new Date().toString()+"\n";
//        }
//        public String getUsername(){
//            return username;
//        }
//
//        public void setUsername(){
//            this.username=username;
//        }
//
//        public void run(){
//            boolean keepGoing =true;
//            while(keepGoing){
//                try{
//                    ChatMessage sm = (ChatMessage) sInput.readObject();
//                }
//                catch(IOException e){
//                    display(username+"Exception reading Streams :");
//                    break;
//                }
//                catch(ClassNotFoundException e2){
//                    break;
//                }
//
//                String message=cm.getMessage();
//                switch(cm.getType())
//                {
//                    case ChatMessage.MESSAGE:
//                        boolean confirmation=broadcast((username+":"));
//                        //something incomplete
//                        if(confirmation==false){
//                            String msg=notif+"Sorry. No such user exits";
//                            writeMsg(msg);
//                        }
//                        break;
//
//                    case ChatMessage.LOGOUT:
//                        display(username+"disconnected with a LOGOUT");
//                        keepGoing=false;
//                        break;
//
//                    case ChatMessage.WHOISIN:
//                        writeMsg("List of the users connected at "+sdf.format(new Date())+"\n");
//                        for(int i=0;i<al.size();++i){
//                            ClientThread ct= al.get(i);
//                            writeMsg((i+1)+")"+ct.username+" since "+ct.date);
//                        }
//                        break;
//                }
//            }
//                        remove(id);
//                        close();
//        }
//                private void close()
//                {
//                    try{
//                        if(sOutput!=null)
//                            sOutput.close();
//                    }
//                    catch (Exception e){
//
//                    }
//                    try{
//                        if(sInput!=null)
//                            sInput.close();
//                    }
//                    catch (Exception e){
//
//                    }
//                    try{
//                        if(socket!=null)
//                            socket.close();
//                    }
//                    catch (Exception e){
//
//                    }
//                }
//
//                private boolean writeMsg(String msg)
//                {
//                    if(!socket.isConnected())
//                    {
//                        close();
//                        return false;
//                    }
//
//                    try{
//                        sOutput.writeObject(msg);
//                    }
//                    catch (IOException e)
//                    {
//                        display(notif+"Error Sending message  to"+username);
//                        display(e.toString());
//                    }
//                    return true;
//
//                }
//    }
//}