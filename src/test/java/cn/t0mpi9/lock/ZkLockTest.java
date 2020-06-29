package cn.t0mpi9.lock;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.zookeeper.CreateMode;
import org.junit.Test;

/**
 * <br/>
 * Created on 2020/6/28 15:07.
 *
 * @author zhubenle
 */
public class ZkLockTest {

    @Test
    public void testLock1() throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.newClient("127.0.0.1", new RetryOneTime(10000));
        client.start();
        ZkLock zkLock = new ZkLock(client, new ZkLockConfig("demo", "test", "217.0.0.1", 8080));
        new Thread(() -> {
            zkLock.lock();
            try {
                System.out.println("in lock");
            } finally {
                zkLock.unlock();
            }
        }).start();
        Thread.sleep(300000);
    }

    @Test
    public void testLock2() throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.newClient("127.0.0.1", new RetryOneTime(10000));
        client.start();
        ZkLock zkLock = new ZkLock(client, new ZkLockConfig("demo", "test", "217.0.0.1", 8080));
        new Thread(() -> {
            zkLock.lock();
            try {
                System.out.println("in lock");
            } finally {
                zkLock.unlock();
            }
        }).start();
        Thread.sleep(300000);
    }

    @Test
    public void testCurator() throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.newClient("127.0.0.1", new RetryOneTime(10000));
        client.start();
        String result = client.create()
                .creatingParentsIfNeeded()
                .withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
                .forPath("/zk-lock/test/lock-");
        System.out.println(result);
        result = client.create()
                .creatingParentsIfNeeded()
                .withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
                .forPath("/zk-lock/test/lock-");
        System.out.println(result);
        Thread.sleep(30000);
    }
}
