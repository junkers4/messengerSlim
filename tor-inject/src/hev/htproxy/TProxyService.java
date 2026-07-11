package hev.htproxy;

/** JNI trieda ktoru ocakava libhev-socks5-tunnel.so (JNI_OnLoad registruje tieto metody). */
public class TProxyService {
    static { System.loadLibrary("hev-socks5-tunnel"); }
    public native void TProxyStartService(String configPath, int fd);
    public native void TProxyStopService();
    public native long[] TProxyGetStats();
}
