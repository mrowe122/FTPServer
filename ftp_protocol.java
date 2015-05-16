import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;

public class FTP_Protocol implements Runnable {
    private String path = null;
    private String root = null;
    private File file = null;
    private PrintWriter out = null;
    private BufferedReader in = null;
    private String clientName = null;
    private String password = null;
    private Socket clientSocket = null;

    FTP_Protocol(Socket newClient) {
        this.clientSocket = newClient;
    }
    
    /*
        setup input and output streams and create user folder if it doesnt exist
    */
    public void newConnection() {
        try {
            //set root to "\" when new user logins
            root = "\\";
            //create input/output streams for communication between server and client
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out.println("220");
            //ask for username and password
            clientName = in.readLine();
            //readLine() will return the string "USER <username>" need to remove "USER "
            clientName = clientName.replace("USER ", "");
            //User name okay, need password.
            out.println("331 User name okay, need password.");
            password = in.readLine();
            //readLine() will return the string "PASS <password>" need to remove "PASS "
            password = password.replace("PASS ", "");
            //setup path for own user
            path = System.getProperty("user.dir") + "\\user_files\\" + clientName;
            //create user own folder
            file = new File(path + root);
            if(!file.exists() && !file.isDirectory())
                file.mkdir();
        }
        catch(IOException e) {
            out.println(e);
        }
    }
    /*
        Creates a new directory in the current path the user is in. Will check if
        exists or not and respond accordingly.
    */
    public void createNewDirectory(String directory) {
        //directory variable is now just the new directory name
        file = new File(path + root + directory);
        if(!file.exists() && !file.isDirectory()) {
            file.mkdir();
            out.println("257 directory is made");
        }
        else {
            out.println("550 Directory already exist");
        }
        resetFile();
    }
    /*
        Changes the working directory to the specified path the client wants
        if the path doesnt exist, error will appear
    */
    public void changeWorkingDirectory(String cwd) {
        //250 Requested file action okay, completed.
        cwd = cwd.replace("CWD ", "");
        file = new File(path + "\\" + cwd);
        if("..\\".equals(cwd) && !"\\".equals(root)) {
            file = new File(path + root);
            root = "\\" + file.getParentFile().getName() + "\\";
            out.println("250 OK. Current directory is " + root.replace("..\\", ""));
        }
        else if(file.isDirectory()) {
            root = "\\" + cwd + "\\";
            out.println("250 OK. Current directory is " + root.replace("\\\\", ""));
        }
        else {
            out.println("550 Can't change directory to \"" + cwd + "\" No such file or directory");
        }
        resetFile();
    }
    /*
        handle the local file transfers
    */
    public void transferLocalFile(String local) {
        try {
            System.out.println(local);
            //grab just the port number
            local = local.substring(12, 17);
            Socket clientDataConnection = new Socket(clientSocket.getInetAddress(), Integer.parseInt(local));
            //respond back to the client
            out.println("200 Command okay");
            //client responds with either to send or recieve a file
            local = in.readLine();
            if(local.startsWith("RETR")) {
                sendFile(local, clientDataConnection);
            }
            else if(local.startsWith("STOR")) {
                recieveFile(local, clientDataConnection);
            }
            else if(local.startsWith("LIST")) {
                listFiles();
                clientDataConnection.close();
            }
            else {
                out.println("500 unknown command");
            }
        }
        catch(IOException e) {
            out.println(e);
        }
    }
    /*
        handle outside connections
    */
    private void transferFile(String transfer){
        try {
            transfer = transfer.replace(",", ".");
            //break apart command string
            String[] net = transfer.split("\\.");
            //first 4 integers are for ip
            String ip = net[0] + "." + net[1] + "." + net[2] + "." + net[3];
            //last integers are for port, determine port number by (n1 * 256) + n2
            int port = ((Integer.parseInt(net[4]) * 256) + Integer.parseInt(net[5]));
            //respond to client, opening data connection
            out.println("200 Command okay.");
            //open new data socket at clients ip and port.
            Socket clientDataConnection = new Socket(ip, port);
            transfer = in.readLine();
            //determine whether to retrieve or send a file
            if(transfer.startsWith("RETR")) {
                //send file
                sendFile(transfer, clientDataConnection);
            }
            else if(transfer.startsWith("STOR")) {
                //recieve file
                recieveFile(transfer, clientDataConnection);
            }
            else if(transfer.startsWith("LIST")) {
                listFiles();
            }
            clientDataConnection.close();
        }
        catch(IOException e) {
            out.println(e);
        }
    }
    /*
        private method to send a file to the client
    */
    private void sendFile(String send, Socket clientDataConnection) {
        try {
            //remove extra string
            file = new File (send);
            //check whether file can be read AND is a file AND it exists
            if(file.canRead() && file.isFile() && file.exists()) {
                out.println("150 File status okay; about to open data connection.");
                byte[] bytes  = new byte [(int)file.length()];
                //create streams for sending file to client through opened data connection
                FileInputStream fis = new FileInputStream(file);
                OutputStream os = clientDataConnection.getOutputStream();
                BufferedInputStream bis = new BufferedInputStream(fis);
                //read the data
                bis.read(bytes,0,bytes.length);
                //write the data
                os.write(bytes,0,bytes.length);
                //tell client action was successfull
                out.println("250 Requested file action completed");
                //close all streams and close data connection and reset File
                os.flush();
                os.close();
                fis.close();
                bis.close();
                resetFile();
            }
            else {
                out.println("550 file not found");
            }
        }
        catch(IOException e) {
            out.println(e);
        }
    }
    /*
        private method to recieve a file from the client
    */
    private void recieveFile(String recieve, Socket clientDataConnection) {
        try {
            //remove extra string
            System.out.println(recieve);
            recieve = recieve.replace("STOR ", "");
            System.out.println(recieve);
            int bytesRead = 0;
            out.println("150 About to open data connection.");
            //create streams for reading incoming file through opened data connection
            InputStream is = clientDataConnection.getInputStream();
            FileOutputStream fos = new FileOutputStream(path + root + recieve);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            //keep reading and writing until end of fle
            while ((bytesRead = is.read()) != -1){
                bos.write(bytesRead);
            }
            //write to client action is completed
            out.println("250 File action completed");
            //close all streams and socket
            bos.flush();
            bos.close();
            fos.close();
            is.close();
        }
        catch(IOException e) {
            out.println(e);
        }
    }
    /*
        will delete file from server in the remote-path
    */
    public void deleteFile(String del) {
        //full path to file
        file = new File(path + root + del);
        if(file.exists() && !file.isDirectory()) {
            file.delete();
            //respond to client, file has been deleted
            out.println("250  Deleted " + del);
        }
        else {
            out.println("553 file doesn't exist.");
        }
        resetFile();
    }
    /*
        list all the files and folders in the current directory
    */
    private void listFiles() {
        out.println("150");
        File folder = new File(path + root);
        File[] listOfFiles = folder.listFiles();
        StringBuilder dir = new StringBuilder();
        //create streams for sending file to client through opened data connection
        if(listOfFiles.length == 0) {
            out.println("250 \nDirectory is empty!");
        }
        else {
            dir.append("\n");
            for (File listOfFile : listOfFiles) {
                if (listOfFile.isFile()) {
                    dir.append("File: \t").append(listOfFile.getName()).append("\n");
                } else if (listOfFile.isDirectory()) {
                    dir.append("Directory: \t").append(listOfFile.getName()).append("\n");
                }
            }
            out.println("250 \n" + dir.toString());
        }
    }
    /*
        will close the clientSocket connection
    */
    public void closeConnection() {
        try {
            out.println("200 disconnected from server");
            clientSocket.close();
        }
        catch(IOException e) {
            out.println(e);
        }
    }
    /*
        simple method to reset the file back to null after its done being used
    */
    private void resetFile() {
        file = null;
    }
    
    //all threads will run in this method
    @Override
    public void run() {
        try {
            String command = null;
            //setup new connection of client
            newConnection();
            //if client username and password equal, login and proceed.
            if(clientName.equals(password) && !password.equals("")) {
                out.println("230 Successfully logged in");
                System.out.println("user <" + clientName + "> logged in");
                //waiting for command from client
                while(true) {
                    command = in.readLine();
                    switch(command.substring(0, 4)) {
                        //create a new directory in the server
                        case "XMKD":
                            command = command.replace("XMKD ", "");
                            createNewDirectory(command);
                            break;
                        //change working directory
                        case "CWD ":
                            command = command.replace("CWD ", "");
                            changeWorkingDirectory(command);
                            break;
                        //return the working directory
                        case "XPWD":
                            out.println("257 " + root.replace("\\\\", "") + " is your current working directory");
                            break;
                            
                        //transfer of outside file
                        case "PORT":
                            command = command.replace("PORT ", "");
                            transferFile(command);
                            break;
                        //transfer of local file
                        case "EPRT":
                            command = command.replace("EPRT  ", "");
                            transferLocalFile(command);
                            break;
                        //delete remote-file
                        case "DELE":
                            command = command.replace("DELE ", "");
                            deleteFile(command);
                            break;
                        //list all files in current directory
                        case "LIST":
                            transferLocalFile(command);
                            break;
                        //disonnect from the client
                        case "QUIT":
                            out.println("200 Command okay.");
                            closeConnection();
                            System.out.println("user <" + clientName + "> logged off");
                            break;
                        //if command is not known
                        default:
                            out.println("500 unknown command");
                            break;
                    }
                }
            }
            //if client entered incorrect information, close connection.
            else {
                out.println("530 login incorrect");
                closeConnection();
            }
        }
        catch(IOException e) {
            out.println(e);
        }
    }
}
