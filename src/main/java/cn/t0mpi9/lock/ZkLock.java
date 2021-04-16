package cn.t0mpi9.lock;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.data.Stat;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

/**
 * <br/>
 * Created on 2020/6/28 13:55.
 *
 * @author zhubenle
 */
public class ZkLock implements Lock, Serializable {

    private static final long serialVersionUID = -5349087034705521390L;

    private static final Map<String, Sync> S_MAP = new ConcurrentHashMap<>();

    private Sync s;

    public ZkLock(CuratorFramework client, ZkLockConfig lockConfig) {
        synchronized (S_MAP) {
            s = S_MAP.get(lockConfig.getLockName());
            if (Objects.isNull(s)) {
                s = new FairSync(client, lockConfig);
                S_MAP.put(lockConfig.getLockName(), s);
            }
        }
    }

    @Override
    public void lock() {
        s.lock();
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        s.acquireInterruptibly(1);
    }

    @Override
    public boolean tryLock() {
        return s.tryAcquire(1);
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return s.tryAcquireNanos(1, unit.toNanos(time));
    }

    @Override
    public void unlock() {
        s.release(1);
    }

    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException();
    }

    abstract static class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = -5179523762034025860L;

        protected final CuratorFramework client;
        protected final ZkLockConfig lockConfig;

        public Sync(CuratorFramework client, ZkLockConfig lockConfig) {
            client.getConnectionStateListenable()
                    .addListener((c, newState) -> {
                        System.out.println("ConnectionState->" + newState);
                        if (ConnectionState.RECONNECTED.equals(newState)) {
                            release(0);
                        }
                    });
            this.client = client;
            this.lockConfig = lockConfig;
        }

        /**
         * Performs {@link Lock#lock}. The main reason for subclassing
         * is to allow fast path for nonfair version.
         */
        abstract void lock();

        @Override
        public boolean tryAcquire(int arg) {
            return super.tryAcquire(arg);
        }

        @Override
        protected final boolean isHeldExclusively() {
            return getExclusiveOwnerThread() == Thread.currentThread();
        }

        final Thread getOwner() {
            return getState() == 0 ? null : getExclusiveOwnerThread();
        }

        final int getHoldCount() {
            return isHeldExclusively() ? getState() : 0;
        }

        final boolean isLocked() {
            return getState() != 0;
        }

        /**
         * Reconstitutes the instance from a stream (that is, deserializes it).
         */
        private void readObject(java.io.ObjectInputStream s)
                throws java.io.IOException, ClassNotFoundException {
            s.defaultReadObject();
            setState(0); // reset to unlocked state
        }
    }

    /**
     * Sync object for fair locks
     */
    static final class FairSync extends Sync {
        private static final long serialVersionUID = -3000897897090466540L;

        private volatile String lockPath;
        private volatile boolean hasZkLock = false;
        private AtomicBoolean createPathState = new AtomicBoolean(false);

        public FairSync(CuratorFramework client, ZkLockConfig lockConfig) {
            super(client, lockConfig);
        }

        @Override
        final void lock() {
            acquire(1);
        }

        @Override
        protected final boolean tryRelease(int releases) {
            if (releases == 0) {
                return true;
            }
            int c = getState() - releases;
            if (Thread.currentThread() != getExclusiveOwnerThread()) {
                throw new IllegalMonitorStateException();
            }
            boolean free = false;
            if (c == 0) {
                free = true;
                setExclusiveOwnerThread(null);
                tryReleaseZk();
            }
            setState(c);
            return free;
        }

        private void tryReleaseZk() {
            try {
                if (hasZkLock) {
                    client.delete().quietly().guaranteed().forPath(lockPath);
                    lockPath = null;
                    hasZkLock = false;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /**
         * Fair version of tryAcquire.  Don't grant access unless
         * recursive call or no waiters or is first.
         */
        @Override
        public final boolean tryAcquire(int acquires) {
            final Thread current = Thread.currentThread();
            int c = getState();
            if (c == 0) {
                if (!hasQueuedPredecessors() &&
                        tryAcquireZk() &&
                        compareAndSetState(0, acquires)) {
                    setExclusiveOwnerThread(current);
                    return true;
                }
            } else if (current == getExclusiveOwnerThread()) {
                int nextc = c + acquires;
                if (nextc < 0) {
                    throw new Error("Maximum lock count exceeded");
                }
                setState(nextc);
                return true;
            }
            return false;
        }

        private boolean tryAcquireZk() {
            if (hasZkLock) {
                return false;
            }
            if (createPathState.compareAndSet(false, true)) {
                try {

                    String path = lockConfig.getPath();
                    String data = lockConfig.getIpPort();
                    if (lockPath == null) {
                        lockPath = client.create()
                                .creatingParentsIfNeeded()
                                .withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
                                .forPath(path, data.getBytes());
                    }

                    String currentPath = lockPath.substring(lockPath.lastIndexOf("/") + 1);
                    List<String> childPathList = client.getChildren()
                            .forPath(lockConfig.getParentPath())
                            .stream()
                            .sorted()
                            .collect(Collectors.toList());
                    if (!childPathList.isEmpty()) {
                        if (currentPath.equals(childPathList.get(0))) {
                            //当前顺序节点是最小，获取锁
                            hasZkLock = true;
                            return true;
                        } else {
                            String prePath = childPathList.get(childPathList.indexOf(currentPath) - 1);
                            Stat stat = client.checkExists()
                                    .usingWatcher((CuratorWatcher) event -> {
                                        if (event.getType().equals(Watcher.Event.EventType.NodeDeleted)) {
                                            release(0);
                                        }
                                    })
                                    .forPath(lockConfig.getParentPath() + ZkLockConfig.SLASH + prePath);
                            return Objects.isNull(stat);
                        }
                    } else {
                        return tryAcquireZk();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    createPathState.compareAndSet(true, false);
                }
            }
            return false;
        }
    }
}
