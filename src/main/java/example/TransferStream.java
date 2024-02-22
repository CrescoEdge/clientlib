package example;


import java.io.PipedInputStream;
import java.io.PipedOutputStream;

public class TransferStream {

    private PipedInputStream pis;
    private PipedOutputStream pos;

    private String transferId;

    private long startByte;
    private long bytesLength;

    private long bytesRemaining;
    private int BUFFER_SIZE = 1024 * 1024;

    private String repoRegion;
    private String repoAgent;
    private String repoPlugin;
    private String fileName;

    private boolean isActive = true;

    public boolean isCanceled() {
        return isCanceled;
    }

    public void setCanceled(boolean canceled) {
        isCanceled = canceled;
    }

    private boolean isCanceled = false;

    public TransferStream(String repoRegion, String repoAgent, String repoPlugin, String fileName, String transferId, long startByte, long byteLength) {

        try {
            this.repoRegion = repoRegion;
            this.repoAgent = repoAgent;
            this.repoPlugin = repoPlugin;
            this.fileName = fileName;
            this.transferId = transferId;
            this.startByte = startByte;
            this.bytesLength = byteLength;
            bytesRemaining = byteLength;

            pis = new PipedInputStream(BUFFER_SIZE);
            pos = new PipedOutputStream(pis);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void close() {
        try {
            pos.close();
            pis.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }
    public PipedInputStream getPis() {
        return pis;
    }

    public PipedOutputStream getPos() {
        return pos;
    }

    public String getTransferId() {
        return transferId;
    }

    public long getBytesLength() {
        return bytesLength;
    }

    public long getBytesRemaining() {
        return bytesRemaining;
    }

    public void setBytesRemaining(long bytesRemaining) {
        this.bytesRemaining = bytesRemaining;
        /*
        if(bytesRemaining == 0) {
            try {
                pos.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

         */
    }

    public long getStartByte() {
        return startByte;
    }

    public int getBUFFER_SIZE() {
        return BUFFER_SIZE;
    }

    public String getRepoRegion() {
        return repoRegion;
    }

    public String getRepoAgent() {
        return repoAgent;
    }

    public String getRepoPlugin() {
        return repoPlugin;
    }

    public String getFileName() {
        return fileName;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }
}
