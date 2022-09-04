package crescoclient.core;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

public class Utils {


    public Map<String,Object> get_jar_info(String filePath) {

        Map<String,Object> jar_info = null;

        try {

            Path checkFile = Paths.get(filePath);

            if (checkFile.toFile().isFile()) {

                JarInputStream jarInputStream = new JarInputStream(new FileInputStream(checkFile.toFile()));

                    //Manifest manifest = new JarInputStream(new FileInputStream(checkFile.toFile())).getManifest();
                    Manifest manifest = jarInputStream.getManifest();

                    Attributes mainAttributess = manifest.getMainAttributes();
                    String aName = mainAttributess.getValue("Bundle-SymbolicName");
                    String aVersion = mainAttributess.getValue("Bundle-Version");
                    String aMD5 = getMD5(filePath);
                    jar_info = new HashMap<>();
                    jar_info.put("pluginname",aName);
                    jar_info.put("version",aVersion);
                    jar_info.put("md5",aMD5);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return jar_info;

    }

    public String getMD5(String jarLocation) {
        String hashString = null;

        try {
            InputStream inputStream = new FileInputStream(jarLocation);

            if(inputStream != null) {

                MessageDigest digest = MessageDigest.getInstance("MD5");

                //Create byte array to read data in chunks
                byte[] byteArray = new byte[1024];
                int bytesCount = 0;

                //Read file data and update in message digest
                while ((bytesCount = inputStream.read(byteArray)) != -1) {
                    digest.update(byteArray, 0, bytesCount);
                }
                ;

                //close the stream; We don't need it now.
                inputStream.close();

                //Get the hash's bytes
                byte[] bytes = digest.digest();

                //This bytes[] has bytes in decimal format;
                //Convert it to hexadecimal format
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < bytes.length; i++) {
                    sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
                }

                hashString = sb.toString();
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        //return complete hash
        return hashString;
    }
}
