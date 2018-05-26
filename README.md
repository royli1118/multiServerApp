# COMP90015 Assignment 2 High Availability and Eventual Consistency


1. Two JAR files included:
	client.jar
	server.jar

2. To execute the JAR file, use

    Client:
	java -cp client.jar activitystreamer.Client 
    Server:
	java -cp server.jar activitystreamer.Server

    if the Server is of Master type, option fields are left empty when
    typing the command.
	
    if the Server is of Slave type, the command line must contain -rh 
    and -rp and -s options to connect to the Master Server. Otherwise
    this server will be regarded as a Master Server.

3. For the client, our group set up a Login GUI to perform login, 
   register and anonymous login function.
	
        The field of Login UI includes:
	    Remote Host
	    Port Number
            Username
	    Secret
	    Login Button
	    Register Button
	    Anonymous login Button
	Remote host needs to be set up, otherwise default value is 
	127.0.0.1, and default port number is 3780
	
	Username and Secret need to be filled if the user would like to
	register and login with username, otherwise anonymous login can
	be applied if having them blank.
	
	When registering a new user, the client will send a request to
	the server, and the server will reply if the request is allowed.

	When users login the System, the server will check if the server
	load is full. If yes, it will send redirect messages to other
	servers. And the one which has lowest server load will be returned.

4. After users login/anonymous login to the system, a new pop up GUI is 
where users can type in the messages that they want to send (left part), 
the right area is the JSON messages receiving from the server(It will 
also display the broadcast messages from the other servers).
	
In this scenario, user must type in JSON message in the left text area 
or the server will send back an invalid message, and also disconnect 
the connection. GUI will also be closed.
	
When users click Disconnect button, the GUI will be closed immediately.
