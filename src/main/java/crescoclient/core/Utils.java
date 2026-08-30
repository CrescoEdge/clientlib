package crescoclient.core;

import io.cresco.library.crypto.CrescoCrypto;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    /**
     * Content hash via the shared library crypto facade (SHA-256, FIPS-approved). Retains the
     * {@code getMD5} name and the {@code "md5"} jar_info key as the fabric-wide compatibility alias;
     * the value is now SHA-256 and matches what the server computes (same facade).
     */
    public String getMD5(String jarLocation) {
        try {
            return CrescoCrypto.contentHashHex(jarLocation);
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }
}
