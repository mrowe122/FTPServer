import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.ServerSocket;

public class FTPServerThreaded {
    private File user = null;
    
    private void systemReady() {
        user = new File("user_files");
        if(!user.exists() && !user.isDirectory()) {
            user.mkdir();
        }
    }
    
    public static void main(String args[]) {
        FTPServerThreaded ftp_server = new FTPServerThreaded();
        try {
            //create a new ServerSocket on port 8000
            ServerSocket serverSocket = new ServerSocket(8000);
            Socket newClient = null;
            ftp_server.systemReady();
            System.out.println("Waiting for a Connection...");
            //loop that will continuously run and always listen and accept to new connections
            while(true) {
                newClient = serverSocket.accept();
                Runnable ftp_client = new FTP_Protocol(newClient);
                //create new threads for each connection
                Thread newConnection = new Thread(ftp_client);
                newConnection.start();
            }
        }
        catch(IOException e) {
            System.out.println(e);
        }
    }
}
