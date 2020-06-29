package cn.t0mpi9.lock;

import org.apache.curator.framework.CuratorFramework;

/**
 * <br/>
 * Created on 2020/6/29 11:18.
 *
 * @author zhubenle
 */
public class ZkLockFactory {

    private CuratorFramework client;
    private String applicationName;
    private String currentServerIp;
    private int currentServerPort;

    private ZkLockFactory() {
    }

    public static ZkLockFactory create() {
        return new ZkLockFactory();
    }

    public ZkLockFactory client(CuratorFramework client) {
        this.client = client;
        return this;
    }

    public ZkLockFactory applicationName(String applicationName) {
        this.applicationName = applicationName;
        return this;
    }

    public ZkLockFactory currentServerIp(String currentServerIp) {
        this.currentServerIp = currentServerIp;
        return this;
    }

    public ZkLockFactory currentServerPort(int currentServerPort) {
        this.currentServerPort = currentServerPort;
        return this;
    }

    public ZkLock getLock(String lockName) {
        return new ZkLock(client, new ZkLockConfig(applicationName, lockName, currentServerIp, currentServerPort));
    }
}
