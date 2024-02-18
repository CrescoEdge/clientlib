package example;

import crescoclient.*;

public class Launcher {

    public static void main(String[] args) throws Exception {

        String host = "localhost";
        int port = 8282;
        String service_key = "c988701a-5f2a-43ac-b915-156049c5d1ee";

        CrescoClient client = new CrescoClient(host,port,service_key);
        client.connect();

        if(client.connected()) {

            BinaryPerformanceTesting binaryPerformanceTesting = new BinaryPerformanceTesting(client);
            binaryPerformanceTesting.runTest();

        } else {
            System.out.println("Could not connect to remote.");
        }
    }

}
