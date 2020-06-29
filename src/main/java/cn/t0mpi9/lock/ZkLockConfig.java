package cn.t0mpi9.lock;

import java.io.Serializable;
import java.util.regex.Pattern;

/**
 * <br/>
 * Created on 2020/6/28 13:55.
 *
 * @author zhubenle
 */
public class ZkLockConfig implements Serializable {

    private static final long serialVersionUID = -1196927490883612407L;

    public static final Pattern PATTERN_NAME = Pattern.compile("^[A-Za-z0-9\\-_]+$");

    static final String ROOT_PATH = "/zk-lock";
    static final String SLASH = "/";
    static final String COLON = ":";
    static final String STRIKE = "-";

    private String applicationName;
    private String lockName;
    private String currentServerIp;
    private int currentServerPort;

    public ZkLockConfig(String applicationName, String lockName, String currentServerIp, int currentServerPort) {
        if (!PATTERN_NAME.matcher(applicationName).matches() || !PATTERN_NAME.matcher(lockName).matches()) {
            throw new IllegalArgumentException("applicationName或lockName格式错误");
        }
        this.applicationName = applicationName;
        this.lockName = lockName;
        this.currentServerIp = currentServerIp;
        this.currentServerPort = currentServerPort;
    }

    public String getPath() {
        return ROOT_PATH + SLASH + applicationName + SLASH + lockName + SLASH + "lock-";
    }

    public String getParentPath() {
        return ROOT_PATH + SLASH + applicationName + SLASH + lockName;
    }

    public String getIpPort() {
        return currentServerIp + COLON + currentServerPort;
    }

    public String getLockName() {
        return lockName;
    }

    public void setLockName(String lockName) {
        this.lockName = lockName;
    }

    public String getCurrentServerIp() {
        return currentServerIp;
    }

    public void setCurrentServerIp(String currentServerIp) {
        this.currentServerIp = currentServerIp;
    }

    public int getCurrentServerPort() {
        return currentServerPort;
    }

    public void setCurrentServerPort(int currentServerPort) {
        this.currentServerPort = currentServerPort;
    }

    @Override
    public String toString() {
        return "ZkLockConfig{"
                + "lockName='" + lockName + '\''
                + ", currentServerIp='" + currentServerIp + '\''
                + ", currentServerPort=" + currentServerPort
                + '}';
    }
}
