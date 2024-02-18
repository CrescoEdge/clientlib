package crescoclient.core;

import com.google.common.primitives.Bytes;

import java.io.InputStream;

public interface OnMessageCallback {

    void onMessage(String msg);
    void onMessage(byte[] b, int offset, int length);

}
